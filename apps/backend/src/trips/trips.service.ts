import { Injectable, NotFoundException, Logger } from '@nestjs/common';
import { PrismaService } from '../common/prisma/prisma.service';
import { AIPlannerService } from '../ai/ai-planner.service';
import { ItinerariesService } from '../itineraries/itineraries.service';
import {
  CreateTripRequestDto,
  CreateTripResponse,
  HomeFeedResponse,
  TripDetailsResponse,
  TripGenerationStatusResponse
} from '@trippin/api-contracts';
import { TripSummary, TripStatus, UserProfile } from '@trippin/shared-types';
import { DestinationsService } from '../destinations/destinations.service';

@Injectable()
export class TripsService {
  private readonly logger = new Logger(TripsService.name);

  // In-memory generation job tracker for local / dev polling
  private readonly activeJobs = new Map<
    string,
    { status: TripStatus; progress: number; message: string; error?: string }
  >();

  constructor(
    private readonly prisma: PrismaService,
    private readonly aiPlanner: AIPlannerService,
    private readonly itinerariesService: ItinerariesService,
    private readonly destinationsService: DestinationsService
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
      recommendedDestinations,
      popularDestinations
    };
  }

  /**
   * Create a new Trip in DRAFT status (POST /api/v1/trips)
   */
  async createTrip(userId: string, dto: CreateTripRequestDto): Promise<CreateTripResponse> {
    const trip = await this.prisma.trip.create({
      data: {
        userId,
        destinationName: dto.destination,
        startDate: new Date(dto.startDate),
        endDate: new Date(dto.endDate),
        travelersCount: dto.travelersCount,
        budgetTotal: dto.budgetTotal,
        currency: dto.currency || 'USD',
        status: 'DRAFT',
        pace: dto.pace || 'MODERATE',
        transportPreference: dto.transportPreference || 'MIXED',
        notes: dto.notes,
        heroImageUrl:
          'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=800'
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
      progress: 10,
      message: 'Analyzing travel dates and destination...'
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
        progress: 100,
        message: 'Generation failed',
        error: err.message
      });
    });

    return { jobId };
  }

  private async processGenerationJob(tripId: string): Promise<void> {
    const trip = await this.prisma.trip.findUnique({ where: { id: tripId } });
    if (!trip) return;

    // Step 1: Places & Weather discovery
    this.activeJobs.set(tripId, {
      status: 'GENERATING',
      progress: 30,
      message: `Fetching verified places and weather for ${trip.destinationName}...`
    });

    const requirements = {
      destination: trip.destinationName,
      startDate: trip.startDate.toISOString().split('T')[0],
      endDate: trip.endDate.toISOString().split('T')[0],
      travelersCount: trip.travelersCount,
      budgetTotal: trip.budgetTotal || undefined,
      currency: trip.currency,
      pace: trip.pace as any,
      transportPreference: trip.transportPreference as any,
      notes: trip.notes || undefined
    };

    // Step 2: AI Planning & Deterministic Validation
    this.activeJobs.set(tripId, {
      status: 'GENERATING',
      progress: 60,
      message: 'Generating optimal route schedule and validating physical transit times...'
    });

    const outcome = await this.aiPlanner.planItinerary(requirements);

    if (!outcome.itinerary) {
      throw new Error('Planner failed to generate candidate itinerary');
    }

    // Step 3: Persist verified itinerary
    this.activeJobs.set(tripId, {
      status: 'GENERATING',
      progress: 85,
      message: 'Persisting verified schedule...'
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
      progress: 100,
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
        errorMessage: inMemory.error
      };
    }

    const trip = await this.prisma.trip.findUnique({ where: { id: tripId } });
    if (!trip) throw new NotFoundException('Trip not found');

    return {
      tripId,
      status: trip.status as TripStatus,
      progressPercentage: trip.status === 'READY' ? 100 : 0,
      currentStepMessage: trip.status === 'READY' ? 'Ready' : 'Draft'
    };
  }

  async getTripDetails(tripId: string): Promise<TripDetailsResponse> {
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

    if (!trip) throw new NotFoundException('Trip not found');

    const latestItinerary = await this.itinerariesService.getLatestItinerary(tripId);

    const tripSummary: TripSummary = {
      id: trip.id,
      userId: trip.userId,
      destination: trip.destinationName,
      startDate: trip.startDate.toISOString().split('T')[0],
      endDate: trip.endDate.toISOString().split('T')[0],
      travelersCount: trip.travelersCount,
      status: trip.status as TripStatus,
      heroImageUrl: trip.heroImageUrl || undefined,
      totalActivitiesCount: latestItinerary
        ? latestItinerary.days.reduce((acc, d) => acc + d.activities.length, 0)
        : 0,
      currentVersion: latestItinerary?.version || 1,
      createdAt: trip.createdAt.toISOString(),
      updatedAt: trip.updatedAt.toISOString()
    };

    return {
      trip: tripSummary,
      requirements: {
        destination: trip.destinationName,
        startDate: tripSummary.startDate,
        endDate: tripSummary.endDate,
        travelersCount: trip.travelersCount,
        budgetTotal: trip.budgetTotal || undefined,
        currency: trip.currency,
        pace: trip.pace as any,
        transportPreference: trip.transportPreference as any,
        notes: trip.notes || undefined
      },
      itinerary: latestItinerary || undefined
    };
  }

  async getUserTrips(userId: string, limit = 10): Promise<TripSummary[]> {
    try {
      const trips = await this.prisma.trip.findMany({
        where: { userId },
        orderBy: { createdAt: 'desc' },
        take: limit,
        include: {
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

      return trips.map((t) => {
        const currentItinerary = t.itineraries[0];
        const activityCount = currentItinerary
          ? currentItinerary.days.reduce((acc, d) => acc + d.activities.length, 0)
          : 0;

        return {
          id: t.id,
          userId: t.userId,
          destination: t.destinationName,
          startDate: t.startDate.toISOString().split('T')[0],
          endDate: t.endDate.toISOString().split('T')[0],
          travelersCount: t.travelersCount,
          status: t.status as TripStatus,
          heroImageUrl: t.heroImageUrl || undefined,
          totalActivitiesCount: activityCount,
          currentVersion: currentItinerary?.version || 1,
          createdAt: t.createdAt.toISOString(),
          updatedAt: t.updatedAt.toISOString()
        };
      });
    } catch {
      return [];
    }
  }
}
