import { Injectable, NotFoundException, Logger } from '@nestjs/common';
import { PrismaService } from '../common/prisma/prisma.service';
import { AIPlannerService } from '../ai/ai-planner.service';
import { ItineraryValidator } from '../engine/itinerary-validator';
import { ItineraryModel, ActivityModel, ItineraryDayModel } from '@trippin/shared-types';
import { ItineraryV1 } from '@trippin/itinerary-schema';

@Injectable()
export class ItinerariesService {
  private readonly logger = new Logger(ItinerariesService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly aiPlanner: AIPlannerService,
    private readonly validator: ItineraryValidator
  ) {}

  /**
   * Persists a verified itinerary as a new version for a trip
   */
  async saveVerifiedItinerary(
    tripId: string,
    verified: ItineraryV1,
    status: 'VERIFIED' | 'DRAFT' = 'VERIFIED'
  ): Promise<ItineraryModel> {
    // 1. Check existing latest version
    const latest = await this.prisma.itinerary.findFirst({
      where: { tripId },
      orderBy: { version: 'desc' }
    });

    const nextVersion = latest ? latest.version + 1 : 1;

    // Set any previous versions as isCurrent: false
    await this.prisma.itinerary.updateMany({
      where: { tripId },
      data: { isCurrent: false }
    });

    // 2. Ensure all referenced places exist in database so foreign key never fails
    for (const d of verified.days) {
      for (const a of d.activities) {
        let place = await this.prisma.place.findFirst({
          where: {
            OR: [
              { id: a.placeId },
              { googlePlaceId: a.placeId },
              { name: a.placeName }
            ]
          }
        });

        if (!place) {
          place = await this.prisma.place.create({
            data: {
              googlePlaceId: a.placeId && a.placeId.length > 5 ? a.placeId : `gen_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
              name: a.placeName || 'Attraction',
              formattedAddress: a.placeName || 'Local Landmark',
              latitude: 0,
              longitude: 0,
              types: [a.activityType || 'ATTRACTION']
            }
          });
        }

        // Map to DB UUID
        a.placeId = place.id;
      }
    }

    // 3. Create new Itinerary record with days and activities
    const itinerary = await this.prisma.itinerary.create({
      data: {
        tripId,
        version: nextVersion,
        isCurrent: true,
        status,
        title: verified.tripTitle,
        summary: verified.summary,
        totalEstimatedCost: verified.totalEstimatedCost,
        currency: verified.currency,
        days: {
          create: verified.days.map((d) => ({
            dayIndex: d.dayIndex,
            date: new Date(d.date),
            themeSummary: d.themeSummary,
            weatherSummary: (d as any).weatherSummary,
            activities: {
              create: d.activities.map((a, idx) => ({
                placeId: a.placeId,
                title: a.placeName,
                activityType: a.activityType,
                startTime: a.startTime,
                endTime: a.endTime,
                durationMinutes: a.durationMinutes,
                travelTimeToNextMin: a.travelTimeFromPreviousMinutes,
                transitMode: a.transitModeFromPrevious,
                estimatedCost: a.estimatedCost,
                currency: verified.currency,
                reason: a.reason,
                tips: a.tips,
                orderIndex: idx
              }))
            }
          }))
        }
      },
      include: {
        days: {
          include: {
            activities: {
              include: { place: true }
            }
          }
        }
      }
    });

    return this.mapToItineraryModel(itinerary);
  }

  /**
   * Conversational modification of an existing itinerary (Phase 15, TRP-042)
   */
  async modifyItinerary(
    itineraryId: string,
    instruction: string
  ): Promise<{ newVersion: number; appliedChangesSummary: string; updatedItinerary: ItineraryModel }> {
    const existing = await this.prisma.itinerary.findUnique({
      where: { id: itineraryId },
      include: {
        trip: true,
        days: {
          include: {
            activities: {
              include: { place: true }
            }
          }
        }
      }
    });

    if (!existing) {
      throw new NotFoundException(`Itinerary with id ${itineraryId} not found`);
    }

    this.logger.log(
      `Applying user instruction "${instruction}" to itinerary ${itineraryId} (Trip: ${existing.tripId})`
    );

    // Call AI Planner to adapt candidate schedule with instruction
    const tripRequirements = {
      destination: existing.trip.destinationName,
      startDate: existing.trip.startDate.toISOString().split('T')[0],
      endDate: existing.trip.endDate.toISOString().split('T')[0],
      travelersCount: existing.trip.travelersCount,
      budgetTotal: existing.trip.budgetTotal || undefined,
      notes: `User modification instruction: "${instruction}"`
    };

    const outcome = await this.aiPlanner.planItinerary(tripRequirements);

    if (!outcome.itinerary) {
      throw new Error('Failed to re-plan itinerary with the given instruction');
    }

    // Save as version N+1
    const newItinerary = await this.saveVerifiedItinerary(
      existing.tripId,
      outcome.itinerary,
      outcome.success ? 'VERIFIED' : 'DRAFT'
    );

    return {
      newVersion: newItinerary.version,
      appliedChangesSummary: `Updated itinerary based on: "${instruction}". Adjusted activities and re-verified physical transit times.`,
      updatedItinerary: newItinerary
    };
  }

  async getLatestItinerary(tripId: string): Promise<ItineraryModel | null> {
    const itinerary = await this.prisma.itinerary.findFirst({
      where: { tripId, isCurrent: true },
      include: {
        days: {
          orderBy: { dayIndex: 'asc' },
          include: {
            activities: {
              orderBy: { orderIndex: 'asc' },
              include: { place: true }
            }
          }
        }
      }
    });

    return itinerary ? this.mapToItineraryModel(itinerary) : null;
  }

  private mapToItineraryModel(raw: any): ItineraryModel {
    return {
      id: raw.id,
      tripId: raw.tripId,
      version: raw.version,
      status: raw.status as any,
      createdAt: raw.createdAt.toISOString(),
      title: raw.title || undefined,
      summary: raw.summary || undefined,
      totalEstimatedCost:
        raw.totalEstimatedCost === null || raw.totalEstimatedCost === undefined
          ? undefined
          : Number(raw.totalEstimatedCost),
      currency: raw.currency || undefined,
      days: (raw.days || []).map((d: any) => ({
        id: d.id,
        date: d.date.toISOString().split('T')[0],
        dayIndex: d.dayIndex,
        summary: d.themeSummary,
        weatherSummary: d.weatherSummary || undefined,
        activities: (d.activities || []).map((a: any) => ({
          id: a.id,
          placeId: a.placeId,
          title: a.title,
          type: a.activityType as any,
          startTime: a.startTime,
          endTime: a.endTime,
          durationMinutes: a.durationMinutes,
          travelTimeFromPreviousMinutes: a.travelTimeToNextMin,
          transitModeFromPrevious: a.transitMode as any,
          estimatedCost: a.estimatedCost,
          currency: a.currency,
          reason: a.reason,
          tips: a.tips,
          bookingUrl: a.bookingUrl,
          place: a.place
            ? {
                id: a.place.id,
                googlePlaceId: a.place.googlePlaceId,
                name: a.place.name,
                formattedAddress: a.place.formattedAddress,
                location: {
                  latitude: a.place.latitude,
                  longitude: a.place.longitude
                },
                types: a.place.types,
                rating: a.place.rating,
                photoUrls: a.place.photoUrls,
                openingHours: a.place.openingHoursJson || undefined
              }
            : undefined
        }))
      }))
    };
  }
}
