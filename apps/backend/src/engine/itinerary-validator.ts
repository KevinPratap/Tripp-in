import { Injectable, Logger } from '@nestjs/common';
import {
  TripRequirement,
  ValidationResult,
  ValidationViolation,
  PlaceModel,
  RouteMode,
  GeoLocation,
  VerificationCheck
} from '@trippin/shared-types';
import { ItineraryV1 } from '@trippin/itinerary-schema';
import { PlaceService } from '../places/places.service';
import { RouteService } from '../routes/routes.service';

/** A venue may not sit further than this from the destination anchor or from the rest of the trip. */
const DEFAULT_MAX_VENUE_RADIUS_KM = 250;
/** Consecutive days may not jump further than this without an explicit transit leg. */
const MAX_CROSS_DAY_HOP_KM = 1500;
/** A scheduled day with fewer stops than this is treated as an empty day. */
const MIN_ACTIVITIES_PER_DAY = 2;
/** Food venues may be revisited once, everything else only once per trip. */
const FOOD_ACTIVITY_TYPES = ['RESTAURANT', 'CAFE'];

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

  /** Great-circle distance in kilometres. */
  private haversineKm(a: GeoLocation, b: GeoLocation): number {
    if (!a || !b) return Number.POSITIVE_INFINITY;
    const toRad = (deg: number) => (deg * Math.PI) / 180;
    const R = 6371;
    const dLat = toRad(b.latitude - a.latitude);
    const dLon = toRad(b.longitude - a.longitude);
    const lat1 = toRad(a.latitude);
    const lat2 = toRad(b.latitude);
    const h =
      Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(h));
  }

  /**
   * Validates a candidate itinerary against all physical and business constraints.
   *
   * Checks performed:
   *  1. durations and day time bounds
   *  2. opening hours (exact when real periods exist, sane visiting window otherwise)
   *  3. overlaps and transit feasibility inside a day
   *  4. geographic containment: no venue stranded outside the destination area
   *  5. cross-day continuity: no unrealistic overnight hop
   *  6. venue repetition and empty days
   *  7. pace (transit included) and budget (trip currency only)
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

    const tripCurrency = requirements.currency || 'USD';
    const maxVenueRadiusKm = requirements.maxVenueRadiusKm || DEFAULT_MAX_VENUE_RADIUS_KM;

    const maxActiveMinutesPerDay =
      requirements.pace === 'RELAXED'
        ? 330 // 5.5 hours
        : requirements.pace === 'FAST'
        ? 570 // 9.5 hours
        : 450; // MODERATE: 7.5 hours

    // Resolve each referenced place once, and reuse it for every rule.
    const placeCache = new Map<string, PlaceModel | null>();
    const getPlace = async (placeId: string): Promise<PlaceModel | null> => {
      if (!placeCache.has(placeId)) {
        let place: PlaceModel | null = null;
        try {
          place = await this.placeService.getPlaceDetails(placeId);
        } catch {
          place = null;
        }
        placeCache.set(placeId, place);
      }
      return placeCache.get(placeId) ?? null;
    };

    const activitiesOf = (day: any): any[] => day.activities || [];

    // Flatten the schedule once: the cluster test and the repetition test need it.
    const flat: Array<{
      dayIndex: number;
      activityIndex: number;
      placeId: string;
      activityType?: string;
      place: PlaceModel | null;
    }> = [];

    for (const day of candidate.days) {
      const activities = activitiesOf(day);
      for (let i = 0; i < activities.length; i++) {
        const a = activities[i];
        flat.push({
          dayIndex: day.dayIndex,
          activityIndex: i,
          placeId: a.placeId,
          activityType: a.activityType,
          place: await getPlace(a.placeId)
        });
      }
    }

    const placed = flat.filter((f) => Boolean(f.place?.location));
    const destinationLocation = requirements.destinationLocation;

    // 4. Geographic containment. A venue passes when it sits near the destination
    // anchor or near at least one other venue in the trip (so multi-city legs and
    // day trips still work while a venue on another continent fails).
    if (placed.length > 0) {
      const nearDestination = destinationLocation
        ? placed.filter(
            (f) => this.haversineKm(f.place!.location, destinationLocation) <= maxVenueRadiusKm
          )
        : [];
      const destinationAnchorUsable = Boolean(destinationLocation) && nearDestination.length > 0;

      for (const entry of placed) {
        const location = entry.place!.location;
        const distanceFromDestination = destinationLocation
          ? this.haversineKm(location, destinationLocation)
          : Number.POSITIVE_INFINITY;
        const distanceFromClosestPeer = placed
          .filter((other) => other !== entry)
          .reduce(
            (min, other) => Math.min(min, this.haversineKm(location, other.place!.location)),
            Number.POSITIVE_INFINITY
          );

        const anchoredToDestination = distanceFromDestination <= maxVenueRadiusKm;
        const anchoredToCluster = distanceFromClosestPeer <= maxVenueRadiusKm;

        // The destination anchor only applies when the geocode actually matched a venue,
        // so a bad geocode cannot condemn an otherwise consistent itinerary.
        if (!anchoredToCluster && (!destinationAnchorUsable || !anchoredToDestination)) {
          violations.push({
            code: 'PLACE_OUTSIDE_DESTINATION',
            message: `"${entry.place!.name}" is ${Math.round(
              Math.min(distanceFromDestination, distanceFromClosestPeer)
            )} km away from every other stop in this trip, so it cannot be part of a ${requirements.destination} itinerary.`,
            dayIndex: entry.dayIndex,
            activityIndices: [entry.activityIndex],
            details: {
              placeId: entry.placeId,
              distanceFromDestinationKm: Number.isFinite(distanceFromDestination)
                ? Math.round(distanceFromDestination)
                : null,
              distanceFromClosestPeerKm: Number.isFinite(distanceFromClosestPeer)
                ? Math.round(distanceFromClosestPeer)
                : null,
              maxVenueRadiusKm
            }
          });
        }
      }
    }

    // 6a. Venue repetition: the same stop should not be rescheduled every day.
    const venueOccurrences = new Map<string, number[]>();
    for (const entry of flat) {
      const key = entry.placeId;
      if (!key) continue;
      venueOccurrences.set(key, [...(venueOccurrences.get(key) || []), entry.dayIndex]);
    }
    for (const [placeId, days] of venueOccurrences.entries()) {
      const isFood = flat.some(
        (f) =>
          f.placeId === placeId &&
          FOOD_ACTIVITY_TYPES.includes((f.activityType || '').toUpperCase())
      );
      const limit = isFood ? 2 : 1;
      if (days.length > limit) {
        const name = flat.find((f) => f.placeId === placeId)?.place?.name || placeId;
        violations.push({
          code: 'REPEATED_VENUE',
          message: `"${name}" is scheduled ${days.length} times. A verified trip visits each venue ${
            limit === 1 ? 'once' : 'at most twice'
          }.`,
          dayIndex: days[days.length - 1],
          activityIndices: [],
          details: { placeId, occurrences: days.length, limit }
        });
      }
    }

    for (const day of candidate.days) {
      let dayActiveMinutes = 0;
      let dayTransitMinutes = 0;
      const dayOfWeek = this.getDayOfWeek(day.date);
      const activities = activitiesOf(day);

      // 6b. Empty day check
      if (activities.length < MIN_ACTIVITIES_PER_DAY) {
        violations.push({
          code: 'DAY_TOO_SPARSE',
          message: `Day ${day.dayIndex} only has ${activities.length} scheduled stop(s). A verified day needs at least ${MIN_ACTIVITIES_PER_DAY}.`,
          dayIndex: day.dayIndex,
          activityIndices: activities.map((_, idx) => idx)
        });
      }

      for (let i = 0; i < activities.length; i++) {
        const current = activities[i];
        totalActivities++;
        const currentStart = this.parseTimeToMinutes(current.startTime);
        const currentEnd = this.parseTimeToMinutes(current.endTime);
        const duration = currentEnd - currentStart;
        const place = await getPlace(current.placeId);

        // 1. Duration check
        if (duration <= 0) {
          violations.push({
            code: 'INVALID_DURATION',
            message: `Activity "${current.placeName}" has endTime (${current.endTime}) not after startTime (${current.startTime}).`,
            dayIndex: day.dayIndex,
            activityIndices: [i]
          });
        }

        // 7a. Cost accumulation, trip currency only
        if (current.estimatedCost) {
          const activityCurrency = (current as any).currency || tripCurrency;
          if (activityCurrency !== tripCurrency) {
            violations.push({
              code: 'CURRENCY_MISMATCH',
              message: `Activity "${current.placeName}" is priced in ${activityCurrency} but the trip is budgeted in ${tripCurrency}.`,
              dayIndex: day.dayIndex,
              activityIndices: [i],
              details: { activityCurrency, tripCurrency }
            });
          } else {
            estimatedCostTotal += current.estimatedCost;
          }
        }

        dayActiveMinutes += duration;

        // 2. Day time bounds
        if (currentStart < 480 && current.activityType !== 'TRANSIT') {
          violations.push({
            code: 'INVALID_TIME_BOUNDS',
            message: `Activity "${current.placeName}" starts at ${current.startTime}, which is earlier than the standard 08:00 start.`,
            dayIndex: day.dayIndex,
            activityIndices: [i]
          });
        }
        if (
          currentEnd > 1410 &&
          current.activityType !== 'TRANSIT' &&
          current.activityType !== 'NIGHTLIFE'
        ) {
          violations.push({
            code: 'INVALID_TIME_BOUNDS',
            message: `Activity "${current.placeName}" ends at ${current.endTime}, past the 23:30 cut-off for a verified day.`,
            dayIndex: day.dayIndex,
            activityIndices: [i]
          });
        }

        // 3. Opening hours. Real periods are enforced exactly. When the hours were
        // derived from the venue category we only enforce a sane visiting window,
        // because validating fabricated hours would prove nothing.
        const hasRealPeriods =
          Boolean(place?.openingHours?.periods && place.openingHours.periods.length > 0) &&
          !(place as any)?.openingHoursEstimated;

        if (hasRealPeriods) {
          const periods = place?.openingHours?.periods || [];
          const periodsForDay = periods.filter((p) => p.open.day === dayOfWeek);

          if (periodsForDay.length === 0) {
            violations.push({
              code: 'PLACE_CLOSED',
              message: `Place "${current.placeName}" is closed on ${day.date} (Day of week: ${dayOfWeek}).`,
              dayIndex: day.dayIndex,
              activityIndices: [i],
              details: { placeId: current.placeId, dayOfWeek }
            });
          } else {
            const isWithinHours = periodsForDay.some((p) => {
              const openMin = this.parseTimeToMinutes(p.open.time);
              const closeMin = this.parseTimeToMinutes(p.close.time);
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
        } else if (
          currentEnd > 1350 &&
          current.activityType !== 'TRANSIT' &&
          current.activityType !== 'NIGHTLIFE' &&
          current.activityType !== 'HOTEL_CHECKIN'
        ) {
          violations.push({
            code: 'INVALID_TIME_BOUNDS',
            message: `Activity "${current.placeName}" ends at ${current.endTime}. Without verified opening hours, stops must finish by 22:30.`,
            dayIndex: day.dayIndex,
            activityIndices: [i],
            details: { reason: 'opening_hours_estimated' }
          });
        }

        // 5. Consecutive activity comparisons (overlap and transit feasibility)
        if (i > 0) {
          const previous = activities[i - 1];
          const prevEnd = this.parseTimeToMinutes(previous.endTime);

          if (currentStart < prevEnd) {
            violations.push({
              code: 'TIME_OVERLAP',
              message: `Activity "${current.placeName}" starts at ${current.startTime} before previous activity "${previous.placeName}" ends at ${previous.endTime}.`,
              dayIndex: day.dayIndex,
              activityIndices: [i - 1, i]
            });
          } else {
            const availableGapMinutes = currentStart - prevEnd;
            const prevPlace = await getPlace(previous.placeId);

            if (prevPlace && place) {
              const mode: RouteMode = (current.transitModeFromPrevious as RouteMode) || 'TRANSIT';
              const requiredTransitMinutes = await this.routeService.estimateTravelTimeMinutes(
                prevPlace.location,
                place.location,
                mode
              );

              totalTransitMinutes += requiredTransitMinutes;
              dayTransitMinutes += requiredTransitMinutes;

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

      // 7b. Pace check per day, transit included so the day is actually walkable
      totalActiveMinutes += dayActiveMinutes + dayTransitMinutes;
      if (dayActiveMinutes + dayTransitMinutes > maxActiveMinutesPerDay) {
        violations.push({
          code: 'EXCEEDS_PACE',
          message: `Day ${day.dayIndex} has ${Math.round(
            (dayActiveMinutes + dayTransitMinutes) / 60
          )} hours of activities and transit, exceeding the ${
            requirements.pace || 'MODERATE'
          } pace maximum of ${Math.round(maxActiveMinutesPerDay / 60)} hours.`,
          dayIndex: day.dayIndex,
          activityIndices: activities.map((_, idx) => idx)
        });
      }
    }

    // 5b. Cross-day continuity: an overnight hop across the map is not a day trip.
    for (let d = 1; d < candidate.days.length; d++) {
      const previousDay = candidate.days[d - 1];
      const currentDay = candidate.days[d];
      const previousActivities = activitiesOf(previousDay);
      const currentActivities = activitiesOf(currentDay);
      const lastOfPrevious = previousActivities[previousActivities.length - 1];
      const firstOfCurrent = currentActivities[0];
      if (!lastOfPrevious || !firstOfCurrent) continue;

      const hasTransitLeg = [...previousActivities, ...currentActivities].some(
        (a) => a.activityType === 'TRANSIT'
      );
      if (hasTransitLeg) continue;

      const prevPlace = await getPlace(lastOfPrevious.placeId);
      const nextPlace = await getPlace(firstOfCurrent.placeId);
      if (!prevPlace?.location || !nextPlace?.location) continue;

      const hopKm = this.haversineKm(prevPlace.location, nextPlace.location);
      if (hopKm > MAX_CROSS_DAY_HOP_KM) {
        violations.push({
          code: 'CROSS_DAY_HOP_UNREALISTIC',
          message: `Day ${currentDay.dayIndex} starts at "${firstOfCurrent.placeName}", ${Math.round(
            hopKm
          )} km from where day ${previousDay.dayIndex} ended ("${
            lastOfPrevious.placeName
          }"). Add a transit leg or keep each day in one area.`,
          dayIndex: currentDay.dayIndex,
          activityIndices: [0],
          details: { hopKm: Math.round(hopKm), maxHopKm: MAX_CROSS_DAY_HOP_KM }
        });
      }
    }

    // 7c. Overall budget check
    if (requirements.budgetTotal && estimatedCostTotal > requirements.budgetTotal) {
      violations.push({
        code: 'EXCEEDS_BUDGET',
        message: `Total estimated cost (${estimatedCostTotal} ${tripCurrency}) exceeds total trip budget (${requirements.budgetTotal} ${tripCurrency}).`,
        dayIndex: 0,
        activityIndices: [],
        details: { budget: requirements.budgetTotal, cost: estimatedCostTotal }
      });
    }

    // 8. Build verification receipts per activity
    const activityChecks: Record<string, VerificationCheck[]> = {};
    for (const day of candidate.days) {
      const acts = activitiesOf(day);
      for (let i = 0; i < acts.length; i++) {
        const current = acts[i];
        const key = `${day.dayIndex}_${i}`;
        const place = await getPlace(current.placeId);
        const checks: VerificationCheck[] = [];

        // 1. Geographic containment
        const isOutside = violations.some(
          (v) =>
            v.code === 'PLACE_OUTSIDE_DESTINATION' &&
            v.dayIndex === day.dayIndex &&
            v.activityIndices.includes(i)
        );
        checks.push({
          code: 'PLACE_OUTSIDE_DESTINATION',
          label: 'Geographic containment',
          source: 'engine',
          status: isOutside ? 'unchecked' : 'confirmed',
          details: isOutside ? 'Exceeds perimeter' : 'Within destination area'
        });

        // 2. Operating hours (OSM)
        const isClosed = violations.some(
          (v) =>
            v.code === 'PLACE_CLOSED' &&
            v.dayIndex === day.dayIndex &&
            v.activityIndices.includes(i)
        );
        const hasRealPeriods =
          Boolean(place?.openingHours?.periods && place.openingHours.periods.length > 0) &&
          !(place as any)?.openingHoursEstimated;

        if (isClosed) {
          checks.push({
            code: 'PLACE_CLOSED',
            label: 'Operating hours',
            source: 'OSM',
            status: 'unchecked',
            details: 'Outside operating hours'
          });
        } else if (hasRealPeriods) {
          checks.push({
            code: 'PLACE_CLOSED',
            label: 'Operating hours',
            source: 'OSM',
            status: 'confirmed',
            details: 'Verified against OSM schedule'
          });
        } else if ((place as any)?.openingHoursEstimated) {
          checks.push({
            code: 'PLACE_CLOSED',
            label: 'Operating hours',
            source: 'OSM',
            status: 'estimated',
            details: 'Estimated from category'
          });
        } else {
          checks.push({
            code: 'PLACE_CLOSED',
            label: 'Operating hours',
            source: 'OSM',
            status: 'unchecked',
            details: 'No hours published'
          });
        }

        // 3. Transit feasibility (OSRM)
        const hasTransitIssue = violations.some(
          (v) =>
            v.code === 'INSUFFICIENT_TRAVEL_TIME' &&
            v.dayIndex === day.dayIndex &&
            v.activityIndices.includes(i)
        );
        checks.push({
          code: 'INSUFFICIENT_TRAVEL_TIME',
          label: 'Transit feasibility',
          source: 'OSRM',
          status: hasTransitIssue ? 'unchecked' : 'confirmed',
          details: hasTransitIssue
            ? 'Transit time insufficient'
            : i === 0
            ? 'First stop of day'
            : 'Transit time verified'
        });

        // 4. Schedule buffer / overlap
        const hasOverlap = violations.some(
          (v) =>
            v.code === 'TIME_OVERLAP' &&
            v.dayIndex === day.dayIndex &&
            v.activityIndices.includes(i)
        );
        checks.push({
          code: 'TIME_OVERLAP',
          label: 'Schedule buffer',
          source: 'engine',
          status: hasOverlap ? 'unchecked' : 'confirmed',
          details: hasOverlap ? 'Time overlap detected' : 'Conflict-free'
        });

        // 5. Weather forecast (Open-Meteo)
        const hasWeather = Boolean((day as any).weatherSummary || (day as any).weather);
        checks.push({
          code: 'WEATHER_WINDOW',
          label: 'Weather forecast',
          source: 'Open-Meteo',
          status: hasWeather ? 'confirmed' : 'unchecked',
          details: hasWeather ? 'Forecast linked' : 'Beyond forecast range'
        });

        activityChecks[key] = checks;
        if (current.id) {
          activityChecks[current.id] = checks;
        }
      }
    }

    if (violations.length > 0) {
      this.logger.debug(
        `Validation found ${violations.length} violation(s): ${violations
          .map((v) => v.code)
          .join(', ')}`
      );
    }

    return {
      isValid: violations.length === 0,
      violations,
      metrics: {
        totalActivities,
        totalActiveMinutes,
        totalTransitMinutes,
        estimatedCostTotal
      },
      activityChecks
    };
  }
}
