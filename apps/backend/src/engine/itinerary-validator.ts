import { Injectable, Logger } from '@nestjs/common';
import {
  TripRequirement,
  ValidationResult,
  ValidationViolation,
  PlaceModel,
  RouteMode
} from '@trippin/shared-types';
import { ItineraryV1, ActivityV1 } from '@trippin/itinerary-schema';
import { PlaceService } from '../places/places.service';
import { RouteService } from '../routes/routes.service';

@Injectable()
export class ItineraryValidator {
  private readonly logger = new Logger(ItineraryValidator.name);

  constructor(
    private readonly placeService: PlaceService,
    private readonly routeService: RouteService
  ) {}

  /**
   * Converts HH:mm string to minutes from midnight (0 - 1439)
   */
  private parseTimeToMinutes(timeStr: string): number {
    const [hours, minutes] = timeStr.split(':').map(Number);
    return hours * 60 + minutes;
  }

  /**
   * Determines Day of Week integer for a given YYYY-MM-DD date (0 = Sunday, 1 = Monday ... 6 = Saturday)
   */
  private getDayOfWeek(dateStr: string): number {
    const [year, month, day] = dateStr.split('-').map(Number);
    return new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  }

  /**
   * Validates a candidate itinerary against all physical and business constraints
   */
  async validate(
    candidate: ItineraryV1,
    requirements: TripRequirement
  ): Promise<ValidationResult> {
    const violations: ValidationViolation[] = [];
    let totalActiveMinutes = 0;
    let totalTransitMinutes = 0;
    let estimatedCostTotal = 0;
    let totalActivities = 0;

    // Determine max active hours allowed per day according to pace
    const maxActiveMinutesPerDay =
      requirements.pace === 'RELAXED'
        ? 330 // 5.5 hours
        : requirements.pace === 'FAST'
        ? 570 // 9.5 hours
        : 450; // MODERATE: 7.5 hours

    for (const day of candidate.days) {
      let dayActiveMinutes = 0;
      const dayOfWeek = this.getDayOfWeek(day.date);
      const activities = day.activities;

      for (let i = 0; i < activities.length; i++) {
        const current = activities[i];
        totalActivities++;
        const currentStart = this.parseTimeToMinutes(current.startTime);
        const currentEnd = this.parseTimeToMinutes(current.endTime);
        const duration = currentEnd - currentStart;

        // 1. Duration check
        if (duration <= 0) {
          violations.push({
            code: 'INVALID_DURATION',
            message: `Activity "${current.placeName}" has endTime (${current.endTime}) not after startTime (${current.startTime}).`,
            dayIndex: day.dayIndex,
            activityIndices: [i]
          });
        }

        if (current.estimatedCost) {
          estimatedCostTotal += current.estimatedCost;
        }

        dayActiveMinutes += duration;

        // 2. Day start & end bounds (e.g. not before 08:00 or past 23:30 for regular activities)
        if (currentStart < 480 && current.activityType !== 'TRANSIT') {
          // before 08:00
          violations.push({
            code: 'INVALID_TIME_BOUNDS',
            message: `Activity "${current.placeName}" starts at ${current.startTime}, which is earlier than the standard 08:00 start.`,
            dayIndex: day.dayIndex,
            activityIndices: [i]
          });
        }

        // 3. Opening hours check
        const place = await this.placeService.getPlaceDetails(current.placeId);
        if (place?.openingHours?.periods) {
          const periodsForDay = place.openingHours.periods.filter(
            (p) => p.open.day === dayOfWeek
          );

          if (periodsForDay.length === 0) {
            // Closed all day
            violations.push({
              code: 'PLACE_CLOSED',
              message: `Place "${current.placeName}" is closed on ${day.date} (Day of week: ${dayOfWeek}).`,
              dayIndex: day.dayIndex,
              activityIndices: [i],
              details: { placeId: current.placeId, dayOfWeek }
            });
          } else {
            // Check if activity interval is contained within any open period
            const isWithinHours = periodsForDay.some((p) => {
              const openMin = this.parseTimeToMinutes(p.open.time);
              const closeMin = this.parseTimeToMinutes(p.close.time);
              // Handle overnight hours (close time past midnight e.g. 02:00)
              const effectiveCloseMin = closeMin < openMin ? closeMin + 1440 : closeMin;
              return currentStart >= openMin && currentEnd <= effectiveCloseMin;
            });

            if (!isWithinHours) {
              violations.push({
                code: 'PLACE_CLOSED',
                message: `Activity "${current.placeName}" scheduled for ${current.startTime}-${current.endTime} is outside operating hours for ${day.date}.`,
                dayIndex: day.dayIndex,
                activityIndices: [i],
                details: {
                  periods: periodsForDay,
                  scheduled: { start: current.startTime, end: current.endTime }
                }
              });
            }
          }
        }

        // 4. Consecutive activity comparisons (Overlap and Route Transit Feasibility)
        if (i > 0) {
          const previous = activities[i - 1];
          const prevEnd = this.parseTimeToMinutes(previous.endTime);

          // Rule A: Overlap check
          if (currentStart < prevEnd) {
            violations.push({
              code: 'TIME_OVERLAP',
              message: `Activity "${current.placeName}" starts at ${current.startTime} before previous activity "${previous.placeName}" ends at ${previous.endTime}.`,
              dayIndex: day.dayIndex,
              activityIndices: [i - 1, i]
            });
          } else {
            // Rule B: Physical travel time feasibility
            const availableGapMinutes = currentStart - prevEnd;

            // Fetch or calculate transit travel time between previous and current place
            const prevPlace = await this.placeService.getPlaceDetails(previous.placeId);
            const currPlace = place;

            if (prevPlace && currPlace) {
              const mode: RouteMode = (current.transitModeFromPrevious as RouteMode) || 'TRANSIT';
              const requiredTransitMinutes = await this.routeService.estimateTravelTimeMinutes(
                prevPlace.location,
                currPlace.location,
                mode
              );

              totalTransitMinutes += requiredTransitMinutes;

              if (availableGapMinutes < requiredTransitMinutes) {
                violations.push({
                  code: 'INSUFFICIENT_TRAVEL_TIME',
                  message: `Insufficient travel time between "${previous.placeName}" and "${current.placeName}". Requires at least ${requiredTransitMinutes} mins via ${mode}, but only ${availableGapMinutes} mins allocated.`,
                  dayIndex: day.dayIndex,
                  activityIndices: [i - 1, i],
                  details: {
                    requiredMinutes: requiredTransitMinutes,
                    availableMinutes: availableGapMinutes
                  }
                });
              }
            }
          }
        }
      }

      // 5. Pace check per day
      totalActiveMinutes += dayActiveMinutes;
      if (dayActiveMinutes > maxActiveMinutesPerDay) {
        violations.push({
          code: 'EXCEEDS_PACE',
          message: `Day ${day.dayIndex} has ${Math.round(dayActiveMinutes / 60)} hours of scheduled activities, exceeding the ${requirements.pace || 'MODERATE'} pace maximum of ${Math.round(maxActiveMinutesPerDay / 60)} hours.`,
          dayIndex: day.dayIndex,
          activityIndices: activities.map((_, idx) => idx)
        });
      }
    }

    // 6. Overall Budget check
    if (requirements.budgetTotal && estimatedCostTotal > requirements.budgetTotal) {
      violations.push({
        code: 'EXCEEDS_BUDGET',
        message: `Total estimated cost (${estimatedCostTotal} ${requirements.currency || 'USD'}) exceeds total trip budget (${requirements.budgetTotal} ${requirements.currency || 'USD'}).`,
        dayIndex: 0,
        activityIndices: [],
        details: { budget: requirements.budgetTotal, cost: estimatedCostTotal }
      });
    }

    return {
      isValid: violations.length === 0,
      violations,
      metrics: {
        totalActivities,
        totalActiveMinutes,
        totalTransitMinutes,
        estimatedCostTotal
      }
    };
  }
}
