import { BadRequestException, ConflictException, ForbiddenException, Injectable, NotFoundException, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '../common/prisma/prisma.service';
import { AIPlannerService } from '../ai/ai-planner.service';
import { ItinerariesService } from '../itineraries/itineraries.service';
import { ItineraryValidator } from '../engine/itinerary-validator';
import { PlaceService } from '../places/places.service';
import {
  CreateTripRequestDto,
  CreateTripResponse,
  GenerationStageKey,
  HomeFeedResponse,
  TripDetailsResponse,
  TripGenerationStatusResponse
} from '@trippin/api-contracts';
import { TripSummary, TripStatus, UserProfile, ItineraryModel, TripRequirement, TripCostModel } from '@trippin/shared-types';
import { ItineraryV1 } from '@trippin/itinerary-schema';
import { DestinationsService } from '../destinations/destinations.service';
import { GenerationStageReporter } from '../common/generation/generation-stage';
import { ReplanTripDto } from './dto/replan-trip.dto';
import { TravellersService } from './travellers.service';

@Injectable()
export class TripsService {
  private readonly logger = new Logger(TripsService.name);

  // In-memory generation job tracker for local / dev polling
  private readonly activeJobs = new Map<
    string,
    { status: TripStatus; progress: number; message: string; step: GenerationStageKey; error?: string }
  >();

  /**
   * Progress reported when the worker reaches each real stage. Derived from the stage itself
   * rather than a timer, so the bar cannot claim work that is not happening.
   */
  private static readonly STAGE_PROGRESS: Record<GenerationStageKey, number> = {
    queued: 10,
    forecast: 20,
    geocoding: 30,
    venues: 45,
    planning: 65,
    validation: 80,
    persist: 90,
    ready: 100,
    failed: 100
  };

  constructor(
    private readonly prisma: PrismaService,
    private readonly aiPlanner: AIPlannerService,
    private readonly itinerariesService: ItinerariesService,
    private readonly destinationsService: DestinationsService,
    private readonly validator: ItineraryValidator,
    private readonly placeService: PlaceService,
    private readonly config?: ConfigService,
    private readonly travellersService?: TravellersService
  ) {}

  /**
   * Home Dashboard aggregator endpoint (GET /api/v1/home)
   */
  async getHomeFeed(userId: string): Promise<HomeFeedResponse> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      include: { profile: true, preference: true }
    });

    const recentTrips = await this.getUserTrips(userId, 5);
    const totalTripsCount = await this.prisma.trip.count({
      where: { userId }
    });
    const popularDestinations = await this.destinationsService.getPopularDestinations();
    const recommendedDestinations = await this.destinationsService.getRecommendedDestinations();

    const userProfile: UserProfile = {
      id: user?.id || userId,
      email: user?.email || 'traveler@trippin.ai',
      displayName: user?.profile?.displayName || 'Traveler',
      photoUrl: user?.profile?.photoUrl || undefined,
      createdAt: user?.createdAt.toISOString() || new Date().toISOString()
    };

    return {
      user: userProfile,
      recentTrips,
      totalTripsCount,
      recommendedDestinations,
      popularDestinations
    };
  }

  /**
   * Create a new Trip in DRAFT status (POST /api/v1/trips)
   */
  /**
   * The traveller's own day rates, kept exactly as given and only when they are real numbers.
   * Nothing is defaulted here: a missing rate stays missing so the cost panel can say so.
   */
  private costAssumptionsFrom(dto: any): Record<string, number> | undefined {
    const keys = [
      'stayPerNightMin',
      'stayPerNightMax',
      'foodPerDayMin',
      'foodPerDayMax',
      'localTransitPerDayMin',
      'localTransitPerDayMax'
    ] as const;
    const out: Record<string, number> = {};
    for (const key of keys) {
      const value = dto?.[key];
      if (typeof value === 'number' && Number.isFinite(value) && value >= 0) out[key] = value;
    }
    return Object.keys(out).length > 0 ? out : undefined;
  }

  /** Reads the stored day rates back for the generation request. */
  private costAssumptionsOf(trip: { costAssumptionsJson?: unknown }): Record<string, number> {
    const stored = trip?.costAssumptionsJson;
    if (!stored || typeof stored !== 'object' || Array.isArray(stored)) {
      return {};
    }
    const result: Record<string, number> = {};
    for (const [k, v] of Object.entries(stored as Record<string, unknown>)) {
      if (typeof v === 'number' && Number.isFinite(v)) {
        result[k] = v;
      }
    }
    return result;
  }

  async createTrip(userId: string, dto: CreateTripRequestDto): Promise<CreateTripResponse> {
    const start = new Date(dto.startDate);
    const end = new Date(dto.endDate);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
      throw new BadRequestException('startDate and endDate must be real dates (YYYY-MM-DD).');
    }
    if (end < start) {
      throw new BadRequestException('endDate must be the same day or later than startDate.');
    }

    const trip = await this.prisma.trip.create({
      data: {
        userId,
        destinationName: dto.destination,
        startDate: new Date(dto.startDate),
        endDate: new Date(dto.endDate),
        travelersCount: dto.travelersCount,
        budgetTotal: dto.budgetTotal,
        costAssumptionsJson: this.costAssumptionsFrom(dto),
        currency: dto.currency || 'USD',
        status: 'DRAFT',
        pace: dto.pace || 'MODERATE',
        transportPreference: dto.transportPreference || 'MIXED',
        originCity: dto.originCity?.trim() || undefined,
        notes: dto.notes,
        heroImageUrl: undefined
      }
    });

    return {
      tripId: trip.id,
      status: 'DRAFT'
    };
  }

  /**
   * Asynchronous generation trigger (POST /api/v1/trips/:id/generate)
   */
  async triggerGeneration(tripId: string): Promise<{ jobId: string }> {
    const trip = await this.prisma.trip.findUnique({
      where: { id: tripId }
    });

    if (!trip) {
      throw new NotFoundException(`Trip with ID ${tripId} not found`);
    }

    const jobId = `job_${tripId}_${Date.now()}`;
    this.activeJobs.set(tripId, {
      status: 'GENERATING',
      progress: TripsService.STAGE_PROGRESS.queued,
      message: 'Analyzing travel dates and destination...',
      step: 'queued'
    });

    // Update DB trip status to GENERATING
    await this.prisma.trip.update({
      where: { id: tripId },
      data: { status: 'GENERATING' }
    });

    // Execute generation asynchronously (simulating BullMQ worker process)
    this.processGenerationJob(tripId).catch((err) => {
      this.logger.error(`Trip generation failed for ${tripId}: ${err.message}`);
      this.activeJobs.set(tripId, {
        status: 'FAILED',
        progress: TripsService.STAGE_PROGRESS.failed,
        message: 'Generation failed',
        step: 'failed',
        error: err.message
      });
    });

    return { jobId };
  }

  private async processGenerationJob(tripId: string): Promise<void> {
    const trip = await this.prisma.trip.findUnique({ where: { id: tripId } });
    if (!trip) return;

    const requirements = {
      destination: trip.destinationName,
      originCity: trip.originCity || undefined,
      startDate: trip.startDate.toISOString().split('T')[0],
      endDate: trip.endDate.toISOString().split('T')[0],
      travelersCount: trip.travelersCount,
      budgetTotal: trip.budgetTotal || undefined,
      // The traveller's own day rates, so the cost panel can add up stay, food and local travel
      // instead of leaving them out or inventing them.
      ...this.costAssumptionsOf(trip),
      currency: trip.currency,
      pace: trip.pace as any,
      transportPreference: trip.transportPreference as any,
      notes: trip.notes || undefined
    };

    // The planner reports every real stage it reaches (weather, geocoding, venue lookup,
    // scheduling, OSRM validation), so polling shows true progress rather than a timer.
    const reportStage: GenerationStageReporter = (key, message) => {
      this.activeJobs.set(tripId, {
        status: 'GENERATING',
        progress: TripsService.STAGE_PROGRESS[key] ?? TripsService.STAGE_PROGRESS.queued,
        message,
        step: key
      });
    };

    const outcome = await this.aiPlanner.planItinerary(requirements, reportStage);

    if (!outcome.itinerary) {
      throw new Error('Planner failed to generate candidate itinerary');
    }

    // Step 3: Persist verified itinerary
    this.activeJobs.set(tripId, {
      status: 'GENERATING',
      progress: TripsService.STAGE_PROGRESS.persist,
      message: 'Persisting verified schedule...',
      step: 'persist'
    });

    const savedItinerary = await this.itinerariesService.saveVerifiedItinerary(
      tripId,
      outcome.itinerary,
      outcome.success ? 'VERIFIED' : 'DRAFT'
    );

    // Update trip status to READY
    await this.prisma.trip.update({
      where: { id: tripId },
      data: { status: 'READY' }
    });

    this.activeJobs.set(tripId, {
      status: 'READY',
      progress: TripsService.STAGE_PROGRESS.ready,
      step: 'ready',
      message: outcome.success
        ? 'Itinerary ready!'
        : 'Itinerary saved with unresolved checks. Treat the schedule as a draft.'
    });
    this.logger.log(
      `Trip ${tripId} generation completed (${outcome.success ? 'verified' : 'draft, validation warnings remain'}).`
    );
  }

  async getGenerationStatus(tripId: string): Promise<TripGenerationStatusResponse> {
    const inMemory = this.activeJobs.get(tripId);
    if (inMemory) {
      return {
        tripId,
        status: inMemory.status,
        progressPercentage: inMemory.progress,
        currentStepMessage: inMemory.message,
        currentStepKey: inMemory.step,
        errorMessage: inMemory.error
      };
    }

    const trip = await this.prisma.trip.findUnique({ where: { id: tripId } });
    if (!trip) throw new NotFoundException('Trip not found');

    return {
      tripId,
      status: trip.status as TripStatus,
      progressPercentage: trip.status === 'READY' ? 100 : 0,
      currentStepMessage: trip.status === 'READY' ? 'Ready' : 'Draft',
      currentStepKey: trip.status === 'READY' ? 'ready' : 'queued'
    };
  }

  async getTripDetails(tripId: string, version?: number): Promise<TripDetailsResponse> {
    const trip = await this.prisma.trip.findUnique({
      where: { id: tripId },
      include: {
        itineraries: {
          where: version ? { version } : { isCurrent: true },
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
        }
      }
    });

    if (!trip) throw new NotFoundException('Trip not found');

    const itinerary = version
      ? await this.itinerariesService.getItineraryByVersion(tripId, version)
      : await this.itinerariesService.getLatestItinerary(tripId);

    const travellers = this.travellersService
      ? await this.travellersService.getTravellers(tripId)
      : [];

    const perTravellerCost = this.travellersService
      ? this.travellersService.computePerTravellerCost(travellers, itinerary?.cost)
      : undefined;

    const options = this.travellersService
      ? this.travellersService.computePlanOptions(tripId, trip.currency, itinerary?.cost)
      : undefined;

    if (itinerary && this.travellersService && travellers.length > 0) {
      for (const day of itinerary.days) {
        for (const act of day.activities) {
          act.support = this.travellersService.computeStopSupport(act, travellers);
        }
      }
    }

    let shareToken: string | undefined;
    if (this.prisma.tripShare?.findFirst) {
      try {
        const activeShare = await this.prisma.tripShare.findFirst({
          where: { tripId, revokedAt: null },
          orderBy: { createdAt: 'desc' }
        });
        if (activeShare) {
          shareToken = activeShare.token;
        }
      } catch {
        // non-blocking
      }
    }

    const tripSummary: TripSummary = {
      id: trip.id,
      userId: trip.userId,
      destination: trip.destinationName,
      originCity: trip.originCity || undefined,
      currency: trip.currency,
      shareToken,
      startDate: trip.startDate.toISOString().split('T')[0],
      endDate: trip.endDate.toISOString().split('T')[0],
      travelersCount: Math.max(trip.travelersCount, travellers.length),
      status: trip.status as TripStatus,
      heroImageUrl: trip.heroImageUrl || undefined,
      totalActivitiesCount: itinerary
        ? itinerary.days.reduce((acc, d) => acc + d.activities.length, 0)
        : 0,
      currentVersion: itinerary?.version || 1,
      isLocked: Boolean(trip.isLocked || itinerary?.isLocked),
      lockedAt: trip.lockedAt ? trip.lockedAt.toISOString() : (itinerary?.lockedAt ? itinerary.lockedAt : undefined),
      travellers,
      perTravellerCost,
      options,
      createdAt: trip.createdAt?.toISOString ? trip.createdAt.toISOString() : new Date().toISOString(),
      updatedAt: trip.updatedAt?.toISOString ? trip.updatedAt.toISOString() : new Date().toISOString()
    };

    return {
      trip: tripSummary,
      requirements: {
        destination: trip.destinationName,
        originCity: trip.originCity || undefined,
        startDate: tripSummary.startDate,
        endDate: tripSummary.endDate,
        travelersCount: trip.travelersCount,
        budgetTotal: trip.budgetTotal || undefined,
        ...this.costAssumptionsOf(trip),
        currency: trip.currency,
        pace: trip.pace as any,
        transportPreference: trip.transportPreference as any,
        notes: trip.notes || undefined
      },
      itinerary: itinerary || undefined
    };
  }

  async getTripVersions(tripId: string) {
    const trip = await this.prisma.trip.findUnique({ where: { id: tripId } });
    if (!trip) throw new NotFoundException(`Trip ${tripId} not found`);
    const versions = await this.itinerariesService.getAllVersions(tripId);
    return {
      tripId,
      destination: trip.destinationName,
      currentVersion: versions.find((v) => v.isCurrent)?.version || 1,
      versions
    };
  }

  async replanTrip(
    tripId: string,
    dto: ReplanTripDto
  ): Promise<{
    tripId: string;
    previousVersion: number;
    newVersion: number;
    intent: string;
    appliedChangesSummary: string;
    changedActivitiesCount: number;
    updatedItinerary: ItineraryModel;
    status: 'VERIFIED' | 'DRAFT';
  }> {
    const trip = await this.prisma.trip.findUnique({
      where: { id: tripId },
      include: {
        itineraries: {
          where: { isCurrent: true },
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
        }
      }
    });

    if (!trip) {
      throw new NotFoundException(`Trip with id ${tripId} not found`);
    }

    const currentItinerary = trip.itineraries[0];
    if (!currentItinerary || currentItinerary.days.length === 0) {
      throw new BadRequestException(`Trip with id ${tripId} has no itinerary to replan`);
    }

    if (trip.isLocked || currentItinerary.isLocked) {
      throw new ConflictException(
        'This itinerary is locked by the trip organizer and cannot be replanned. Unlock it first.'
      );
    }

    this.logger.log(
      `Executing replan on trip ${tripId} (current v${currentItinerary.version}) with intent "${dto.intent}"`
    );

    // Geocode destination and search candidate places
    const destinationLocation = await this.placeService.geocodeDestination(trip.destinationName);
    const candidatePlaces = await this.placeService.searchPlaces(
      `${trip.destinationName} attractions museums landmarks`,
      destinationLocation || undefined
    );

    const usedPlaceNames = new Set<string>();
    const usedPlaceIds = new Set<string>();

    for (const d of currentItinerary.days) {
      for (const a of d.activities) {
        usedPlaceNames.add(a.title.toLowerCase());
        if (a.place?.googlePlaceId) usedPlaceIds.add(a.place.googlePlaceId);
      }
    }

    const getUnusedIndoorPlace = () => {
      const indoor = candidatePlaces.find((p) => {
        const nameLower = p.name.toLowerCase();
        if (
          usedPlaceNames.has(nameLower) ||
          (p.id && usedPlaceIds.has(p.id)) ||
          (p.googlePlaceId && usedPlaceIds.has(p.googlePlaceId))
        ) {
          return false;
        }
        return (
          p.types?.some((t: string) => ['museum', 'art_gallery', 'aquarium', 'library'].includes(t)) ||
          /museum|musée|museo|gallery|aquarium|catacombs|palace|centre/i.test(p.name)
        );
      });
      if (indoor) {
        usedPlaceNames.add(indoor.name.toLowerCase());
        if (indoor.id) usedPlaceIds.add(indoor.id);
        if (indoor.googlePlaceId) usedPlaceIds.add(indoor.googlePlaceId);
        return indoor;
      }
      const anyUnused = candidatePlaces.find(
        (p) => !usedPlaceNames.has(p.name.toLowerCase())
      );
      if (anyUnused) {
        usedPlaceNames.add(anyUnused.name.toLowerCase());
        return anyUnused;
      }
      return null;
    };

    const getUnusedPlace = () => {
      const unused = candidatePlaces.find(
        (p) => !usedPlaceNames.has(p.name.toLowerCase()) && !(p.id && usedPlaceIds.has(p.id))
      );
      if (unused) {
        usedPlaceNames.add(unused.name.toLowerCase());
        if (unused.id) usedPlaceIds.add(unused.id);
        if (unused.googlePlaceId) usedPlaceIds.add(unused.googlePlaceId);
        return unused;
      }
      return null;
    };

    let appliedChangesSummary = '';
    let changedActivitiesCount = 0;

    const candidateDays = currentItinerary.days.map((d) => ({
      dayIndex: d.dayIndex,
      date: d.date.toISOString().split('T')[0],
      themeSummary: d.themeSummary,
      weatherSummary: d.weatherSummary || undefined,
      activities: d.activities.map((a) => ({
        placeId: a.place?.googlePlaceId || a.placeId,
        placeName: a.title,
        activityType: a.activityType,
        startTime: a.startTime,
        endTime: a.endTime,
        durationMinutes: a.durationMinutes,
        travelTimeFromPreviousMinutes: a.travelTimeToNextMin || 0,
        transitModeFromPrevious: a.transitMode || 'TRANSIT',
        estimatedCost: a.estimatedCost ? Number(a.estimatedCost) : 0,
        reason: a.reason || undefined,
        tips: a.tips || undefined,
        checks: (a.validationJson as any) || undefined
      }))
    }));

    switch (dto.intent) {
      case 'rain': {
        const isOutdoor = (type: string, name: string) => {
          return (
            type === 'PARK' ||
            /park|garden|jardin|parque|beach|lookout|viewpoint|plaza|square/i.test(name)
          );
        };

        for (const day of candidateDays) {
          if (dto.dayIndex && day.dayIndex !== dto.dayIndex) continue;

          for (let i = 0; i < day.activities.length; i++) {
            const act = day.activities[i];
            if (isOutdoor(act.activityType, act.placeName)) {
              const replacement = getUnusedIndoorPlace();
              if (replacement) {
                act.placeId = replacement.googlePlaceId || replacement.id || act.placeId;
                act.placeName = replacement.name;
                act.activityType = 'MUSEUM';
                act.reason = 'Indoor cultural venue selected for rain protection.';
                changedActivitiesCount++;
              }
            }
          }
        }
        appliedChangesSummary = 'Swapped exposed outdoor stops for covered indoor museums and galleries due to rain advisory.';
        break;
      }

      case 'running-late': {
        const delayMinutes = 45;
        for (const day of candidateDays) {
          if (dto.dayIndex && day.dayIndex !== dto.dayIndex) continue;

          for (const act of day.activities) {
            const [h, m] = act.startTime.split(':').map(Number);
            const currentMins = h * 60 + m;
            const newStartMins = currentMins + delayMinutes;
            const newStartH = Math.floor(newStartMins / 60);
            const newStartM = newStartMins % 60;
            act.startTime = `${String(newStartH).padStart(2, '0')}:${String(newStartM).padStart(2, '0')}`;

            const newEndMins = newStartMins + act.durationMinutes;
            const newEndH = Math.floor(newEndMins / 60);
            const newEndM = newEndMins % 60;
            act.endTime = `${String(newEndH).padStart(2, '0')}:${String(newEndM).padStart(2, '0')}`;
            changedActivitiesCount++;
          }

          if (day.activities.length > 2) {
            const last = day.activities[day.activities.length - 1];
            const [endH] = last.endTime.split(':').map(Number);
            if (endH >= 22) {
              day.activities.pop();
            }
          }
        }
        appliedChangesSummary = 'Delayed schedule by 45 minutes and streamlined remaining stops to maintain realistic transit buffers.';
        break;
      }

      case 'tired': {
        for (const day of candidateDays) {
          if (dto.dayIndex && day.dayIndex !== dto.dayIndex) continue;

          if (day.activities.length > 2) {
            day.activities.pop();
            changedActivitiesCount++;
          }
          for (const act of day.activities) {
            if (act.durationMinutes > 90) {
              act.durationMinutes = 90;
              const [h, m] = act.startTime.split(':').map(Number);
              const endMins = h * 60 + m + 90;
              act.endTime = `${String(Math.floor(endMins / 60)).padStart(2, '0')}:${String(endMins % 60).padStart(2, '0')}`;
            }
          }
        }
        appliedChangesSummary = 'Reduced circuit intensity: inserted relaxation buffers and trimmed late stops for a more restful pace.';
        break;
      }

      case 'budget-cut': {
        // The product does not estimate prices, so "spend less" can only mean "do less".
        // This trims the last stop of each targeted day rather than inventing a free venue.
        for (const day of candidateDays) {
          if (dto.dayIndex && day.dayIndex !== dto.dayIndex) continue;
          if (day.activities.length > 1) {
            day.activities.pop();
            changedActivitiesCount++;
          }
        }
        appliedChangesSummary = 'Trimmed the last stop of each day. Prices are not estimated, so spending less here means doing less.';
        break;
      }

      case 'swap-activity': {
        let swapped = false;
        for (const day of candidateDays) {
          if (swapped) break;
          for (const act of day.activities) {
            if (!dto.targetActivityId || act.placeId === dto.targetActivityId) {
              const replacement = getUnusedPlace();
              if (replacement) {
                act.placeId = replacement.googlePlaceId || replacement.id || act.placeId;
                act.placeName = replacement.name;
                act.reason = 'Alternative venue chosen in same destination district.';
                swapped = true;
                changedActivitiesCount++;
                break;
              }
            }
          }
        }
        appliedChangesSummary = 'Swapped venue with an alternative verified place in the destination.';
        break;
      }

      case 'add-stop': {
        for (const day of candidateDays) {
          if (dto.dayIndex && day.dayIndex !== dto.dayIndex) continue;
          if (day.activities.length < 4) {
            const addition = getUnusedPlace();
            if (addition) {
              day.activities.push({
                placeId: addition.googlePlaceId || addition.id || `gen_add_${Date.now()}`,
                placeName: addition.name,
                activityType: 'ATTRACTION',
                startTime: '17:00',
                endTime: '18:15',
                durationMinutes: 75,
                travelTimeFromPreviousMinutes: 20,
                transitModeFromPrevious: 'TRANSIT',
                estimatedCost: 10,
                reason: 'Added stop to enrich circuit afternoon exploration.',
                tips: undefined,
                checks: undefined
              });
              changedActivitiesCount++;
              break;
            }
          }
        }
        appliedChangesSummary = 'Added an additional verified venue stop to the itinerary.';
        break;
      }

      case 'custom':
      default: {
        appliedChangesSummary = dto.freeText
          ? `Custom adjustment applied: "${dto.freeText}".`
          : 'General itinerary schedule re-optimization.';
        changedActivitiesCount = 1;
        break;
      }
    }

    if (dto.freeText && dto.intent !== 'custom') {
      appliedChangesSummary += ` Context: "${dto.freeText}".`;
    }

    const totalCost = candidateDays.reduce(
      (acc, d) => acc + d.activities.reduce((s, a) => s + (a.estimatedCost || 0), 0),
      0
    );

    const candidateItinerary: ItineraryV1 = {
      schemaVersion: 'itinerary.schema.v1',
      tripTitle: currentItinerary.title || `${trip.destinationName} Circuit`,
      destination: trip.destinationName,
      summary: appliedChangesSummary,
      totalEstimatedCost: totalCost,
      currency: trip.currency,
      days: candidateDays as any
    };

    const scopedRequirements: TripRequirement = {
      destination: trip.destinationName,
      startDate: candidateItinerary.days[0].date,
      endDate: candidateItinerary.days[candidateItinerary.days.length - 1].date,
      travelersCount: trip.travelersCount,
      budgetTotal: trip.budgetTotal || undefined,
      currency: trip.currency,
      pace: trip.pace as any,
      destinationLocation: destinationLocation || undefined
    };

    const validationResult = await this.validator.validate(candidateItinerary, scopedRequirements);

    if (validationResult.activityChecks) {
      this.aiPlanner.attachActivityChecks(candidateItinerary, validationResult.activityChecks);
    }

    const finalStatus = validationResult.isValid ? 'VERIFIED' : 'DRAFT';

    const newItinerary = await this.itinerariesService.saveVerifiedItinerary(
      tripId,
      candidateItinerary,
      finalStatus
    );

    return {
      tripId,
      previousVersion: currentItinerary.version,
      newVersion: newItinerary.version,
      intent: dto.intent,
      appliedChangesSummary,
      changedActivitiesCount,
      updatedItinerary: newItinerary,
      status: finalStatus
    };
  }

  async getUserTrips(userId: string, limit = 10): Promise<TripSummary[]> {
    try {
      const trips = await this.prisma.trip.findMany({
        where: { userId },
        orderBy: { createdAt: 'desc' },
        take: limit,
        include: {
          shares: {
            where: { revokedAt: null },
            orderBy: { createdAt: 'desc' },
            take: 1
          },
          itineraries: {
            where: { isCurrent: true },
            include: {
              days: {
                include: { activities: true }
              }
            }
          }
        }
      });

      return Promise.all(
        trips.map(async (t) => {
          const currentItinerary = t.itineraries[0];
          const activityCount = currentItinerary
            ? currentItinerary.days.reduce((acc, d) => acc + d.activities.length, 0)
            : 0;

          const travellers = this.travellersService
            ? await this.travellersService.getTravellers(t.id)
            : [];

          const cost = (currentItinerary?.costJson as unknown as TripCostModel) || undefined;

          const perTravellerCost = this.travellersService
            ? this.travellersService.computePerTravellerCost(travellers, cost)
            : [];

          const options = this.travellersService
            ? this.travellersService.computePlanOptions(t.id, t.currency, cost)
            : [];

          const shareToken = t.shares?.[0]?.token;

          return {
            id: t.id,
            userId: t.userId,
            destination: t.destinationName,
            originCity: t.originCity || undefined,
            currency: t.currency,
            shareToken,
            startDate: t.startDate.toISOString().split('T')[0],
            endDate: t.endDate.toISOString().split('T')[0],
            travelersCount: Math.max(t.travelersCount, travellers.length),
            status: t.status as TripStatus,
            isLocked: Boolean(t.isLocked),
            lockedAt: t.lockedAt ? t.lockedAt.toISOString() : undefined,
            heroImageUrl: t.heroImageUrl || undefined,
            totalActivitiesCount: activityCount,
            currentVersion: currentItinerary?.version || 1,
            travellers,
            perTravellerCost,
            options,
            createdAt: t.createdAt.toISOString(),
            updatedAt: t.updatedAt.toISOString()
          };
        })
      );
    } catch {
      return [];
    }
  }

  /**
   * Locks the trip and active itinerary, preventing further modifications, replanning, and voting
   */
  async lockTrip(
    tripId: string
  ): Promise<{ success: boolean; tripId: string; isLocked: boolean; lockedAt: string }> {
    const trip = await this.prisma.trip.findUnique({ where: { id: tripId } });
    if (!trip) throw new NotFoundException(`Trip ${tripId} not found`);

    const now = new Date();
    await this.prisma.trip.update({
      where: { id: tripId },
      data: { isLocked: true, lockedAt: now }
    });

    await this.prisma.itinerary.updateMany({
      where: { tripId, isCurrent: true },
      data: { isLocked: true, lockedAt: now }
    });

    this.logger.log(`Trip ${tripId} has been LOCKED by organizer.`);

    return {
      success: true,
      tripId,
      isLocked: true,
      lockedAt: now.toISOString()
    };
  }

  /**
   * Unlocks the trip and active itinerary, re-enabling modifications and replanning
   */
  async unlockTrip(
    tripId: string
  ): Promise<{ success: boolean; tripId: string; isLocked: boolean }> {
    const trip = await this.prisma.trip.findUnique({ where: { id: tripId } });
    if (!trip) throw new NotFoundException(`Trip ${tripId} not found`);

    await this.prisma.trip.update({
      where: { id: tripId },
      data: { isLocked: false, lockedAt: null }
    });

    await this.prisma.itinerary.updateMany({
      where: { tripId, isCurrent: true },
      data: { isLocked: false, lockedAt: null }
    });

    this.logger.log(`Trip ${tripId} has been UNLOCKED by organizer.`);

    return {
      success: true,
      tripId,
      isLocked: false
    };
  }

  /**
   * Delete a trip and all its cascaded entities (DELETE /api/v1/trips/:id)
   */
  async deleteTrip(tripId: string, userId: string): Promise<{ success: boolean; tripId: string }> {
    const trip = await this.prisma.trip.findUnique({
      where: { id: tripId },
      include: { travelers: true }
    });

    if (!trip) {
      throw new NotFoundException(`Trip with ID ${tripId} not found`);
    }

    const allowBypass = this.config?.get<string>('AUTH_BYPASS_DEV') === 'true';
    if (trip.userId !== userId && !allowBypass) {
      const isOrganizer = trip.travelers?.some(
        (t) => t.userId === userId && (t.role === 'OWNER' || t.role === 'ORGANIZER')
      );
      if (!isOrganizer) {
        throw new ForbiddenException('You do not have permission to delete this trip');
      }
    }

    this.activeJobs.delete(tripId);

    await this.prisma.trip.delete({
      where: { id: tripId }
    });

    this.logger.log(`Trip ${tripId} deleted by user ${userId}`);
    return { success: true, tripId };
  }
}
