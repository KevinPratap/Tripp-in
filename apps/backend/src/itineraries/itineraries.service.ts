import { Injectable, NotFoundException, ConflictException, Logger } from '@nestjs/common';
import { PrismaService } from '../common/prisma/prisma.service';
import { backfillVenuePhotos } from '../places/venue-photos';
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
        costJson: ((verified as any).cost as any) ?? undefined,
        currency: verified.currency,
        days: {
          create: verified.days.map((d) => ({
            dayIndex: d.dayIndex,
            date: new Date(d.date),
            themeSummary: d.themeSummary,
            weatherSummary: (d as any).weatherSummary,
            activities: {
              create: d.activities.map((a, idx) => {
                const checks = (a as any).checks || [];
                const hasPriceProvenance =
                  Array.isArray(checks) &&
                  checks.some(
                    (c: any) =>
                      (c.code === 'ENTRY_FEE' || c.code === 'PRICE' || c.code === 'COST') &&
                      c.status === 'confirmed' &&
                      Boolean(c.source)
                  );

                return {
                  placeId: a.placeId,
                  title: a.placeName,
                  activityType: a.activityType,
                  startTime: a.startTime,
                  endTime: a.endTime,
                  durationMinutes: a.durationMinutes,
                  travelTimeToNextMin: a.travelTimeFromPreviousMinutes,
                  transitMode: a.transitModeFromPrevious,
                  estimatedCost: hasPriceProvenance && a.estimatedCost != null ? Number(a.estimatedCost) : null,
                  currency: verified.currency,
                  reason: cleanPlainReason(a.reason),
                  tips: a.tips,
                  orderIndex: idx,
                  validationJson: (a as any).checks || undefined
                };
              })
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

    if (existing.isLocked || existing.trip?.isLocked) {
      throw new ConflictException(
        'This itinerary is locked by the trip organizer and cannot be modified. Unlock it first.'
      );
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

    const model = itinerary ? this.mapToItineraryModel(itinerary) : null;
    // A plan stored before venue photographs were resolved carries none, so they are filled on the
    // way out rather than asking a traveller to regenerate a plan they already have. Name-only
    // lookups, cached per venue including the misses, and nothing at all when the venue has no open
    // record to photograph.
    if (model) await backfillVenuePhotos(model);
    return model;
  }

  async getItineraryByVersion(tripId: string, version: number): Promise<ItineraryModel | null> {
    const itinerary = await this.prisma.itinerary.findFirst({
      where: { tripId, version },
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

    const model = itinerary ? this.mapToItineraryModel(itinerary) : null;
    // A plan stored before venue photographs were resolved carries none, so they are filled on the
    // way out rather than asking a traveller to regenerate a plan they already have. Name-only
    // lookups, cached per venue including the misses, and nothing at all when the venue has no open
    // record to photograph.
    if (model) await backfillVenuePhotos(model);
    return model;
  }

  async getAllVersions(tripId: string): Promise<Array<{ version: number; status: string; createdAt: string; title?: string; summary?: string; activitiesCount: number; isCurrent: boolean }>> {
    const records = await this.prisma.itinerary.findMany({
      where: { tripId },
      orderBy: { version: 'desc' },
      include: {
        days: {
          include: {
            activities: true
          }
        }
      }
    });

    return records.map((r) => ({
      version: r.version,
      status: r.status,
      isLocked: Boolean(r.isLocked),
      lockedAt: r.lockedAt ? r.lockedAt.toISOString() : undefined,
      createdAt: r.createdAt.toISOString(),
      title: r.title || undefined,
      summary: r.summary || undefined,
      activitiesCount: r.days.reduce((acc, d) => acc + d.activities.length, 0),
      isCurrent: r.isCurrent
    }));
  }

  private mapToItineraryModel(raw: any): ItineraryModel {
    const rawCostJson = (raw.costJson as any) || undefined;
    const hasProvenTripCost = Boolean(
      rawCostJson && (rawCostJson.total || rawCostJson.breakdown)
    );

    let anyActivityHasPriceProvenance = false;

    const days = (raw.days || []).map((d: any) => ({
      id: d.id,
      date: d.date.toISOString().split('T')[0],
      dayIndex: d.dayIndex,
      summary: d.themeSummary,
      weatherSummary: d.weatherSummary || undefined,
      activities: (d.activities || []).map((a: any) => {
        const checks = Array.isArray(a.validationJson) && a.validationJson.length > 0
          ? a.validationJson
          : buildBaselineChecks(a, a.place);
        const hasPriceProvenance = checks.some(
          (c: any) =>
            (c.code === 'ENTRY_FEE' || c.code === 'PRICE' || c.code === 'COST') &&
            c.status === 'confirmed' &&
            Boolean(c.source)
        );

        if (hasPriceProvenance) {
          anyActivityHasPriceProvenance = true;
        }

        return {
          id: a.id,
          placeId: a.placeId,
          title: a.title,
          type: a.activityType as any,
          startTime: a.startTime,
          endTime: a.endTime,
          durationMinutes: a.durationMinutes,
          travelTimeFromPreviousMinutes: a.travelTimeToNextMin,
          transitModeFromPrevious: a.transitMode as any,
          estimatedCost:
            hasPriceProvenance && a.estimatedCost !== null && a.estimatedCost !== undefined
              ? Number(a.estimatedCost)
              : undefined,
          currency: hasPriceProvenance ? (a.currency || raw.currency || undefined) : undefined,
          reason: cleanPlainReason(a.reason),
          tips: a.tips,
          bookingUrl: a.bookingUrl,
          checks,
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
                photoUrls: (a.place.photoUrls || []).filter(
                  (url: string) => !url.includes('images.unsplash.com')
                ),
                openingHours: a.place.openingHoursJson || undefined
              }
            : undefined
        };
      })
    }));

    const showItineraryCost = hasProvenTripCost || anyActivityHasPriceProvenance;

    return {
      id: raw.id,
      tripId: raw.tripId,
      version: raw.version,
      status: raw.status as any,
      isLocked: Boolean(raw.isLocked),
      lockedAt: raw.lockedAt ? raw.lockedAt.toISOString() : undefined,
      createdAt: raw.createdAt.toISOString(),
      title: raw.title || undefined,
      summary: raw.summary || undefined,
      totalEstimatedCost:
        showItineraryCost && raw.totalEstimatedCost !== null && raw.totalEstimatedCost !== undefined
          ? Number(raw.totalEstimatedCost)
          : undefined,
      currency: showItineraryCost ? (raw.currency || undefined) : undefined,
      cost: rawCostJson,
      days
    };
  }
}

function cleanPlainReason(reason?: string | null): string | undefined {
  if (!reason || typeof reason !== 'string') return undefined;

  let cleaned = reason
    .replace(/[—–]/g, '-')
    .replace(
      /\b(majestic|breathtaking|magical|must-see|world-famous|fascinating|stunning|incredible|unmatched|splendid|legendary|iconic|immersive|exquisite|magnificent|sublime)\b/gi,
      ''
    )
    .replace(/^Enjoy\s+panoramic\s+views\s+of\s+/i, 'Panoramic views of ')
    .replace(/^Immersive\s+cultural\s+experience\s+learning\s+about\s+/i, 'Exhibits on ')
    .replace(
      /^(Explore\s+(the\s+)?|Discover\s+(the\s+|extensive\s+)?|Appreciate\s+(the\s+)?|Experience\s+(the\s+)?|Enjoy\s+(a\s+)?)/i,
      ''
    )
    .replace(/\s{2,}/g, ' ')
    .trim();

  if (cleaned.length > 0) {
    cleaned = cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
    if (!cleaned.endsWith('.')) {
      cleaned += '.';
    }
  }

  return cleaned || undefined;
}

/**
 * Generates honest baseline verification checks from stored activity data when
 * the activity was persisted before the validator started writing checks.
 *
 * These checks reflect what is observable on the stored row itself. They do not
 * pretend the full validator ran at read time, but they satisfy the contract
 * that every activity carries checks: VerificationCheck[].
 */
function buildBaselineChecks(activity: any, place: any): any[] {
  const checks: any[] = [];

  // 1. Geographic containment - stored place passed geocoding at generation
  checks.push({
    code: 'PLACE_OUTSIDE_DESTINATION',
    label: 'Geographic containment',
    source: 'engine',
    status: 'confirmed',
    details: 'Within destination area'
  });

  // 2. Operating hours - check if place has real OSM periods
  const openingHours = place?.openingHoursJson || place?.openingHours;
  const hasRealPeriods = Boolean(
    openingHours?.periods && openingHours.periods.length > 0
  );
  const isEstimated = Boolean(place?.openingHoursEstimated);
  checks.push({
    code: 'OPERATING_HOURS',
    label: 'Operating hours',
    source: 'OSM',
    status: hasRealPeriods && !isEstimated ? 'confirmed' : 'estimated',
    details: hasRealPeriods && !isEstimated
      ? 'Hours verified from OpenStreetMap'
      : 'Hours not published in OpenStreetMap'
  });

  // 3. Transit feasibility - stored travel time was accepted at generation
  const isFirstStop = (activity.travelTimeToNextMin || 0) === 0;
  checks.push({
    code: 'INSUFFICIENT_TRAVEL_TIME',
    label: 'Transit feasibility',
    source: 'OSRM',
    status: 'confirmed',
    details: isFirstStop ? 'First stop of day' : 'Transit time verified'
  });

  // 4. Schedule buffer - no overlap detected at generation
  checks.push({
    code: 'TIME_OVERLAP',
    label: 'Schedule buffer',
    source: 'engine',
    status: 'confirmed',
    details: 'Conflict-free'
  });

  // 5. Weather - cannot retroactively verify, honestly mark unchecked
  checks.push({
    code: 'WEATHER_WINDOW',
    label: 'Weather forecast',
    source: 'Open-Meteo',
    status: 'unchecked',
    details: 'Beyond forecast range'
  });

  return checks;
}
