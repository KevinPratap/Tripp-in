import { TripsService } from './trips.service';
import { ItineraryValidator } from '../engine/itinerary-validator';

describe('TripsService - One-Tap Replanning (Phase 6)', () => {
  let tripsService: TripsService;
  let mockPrisma: any;
  let mockAIPlanner: any;
  let mockItinerariesService: any;
  let mockDestinationsService: any;
  let mockValidator: any;
  let mockPlaceService: any;

  const sampleTripId = 'trip-tokyo-101';
  const sampleTrip = {
    id: sampleTripId,
    userId: 'user-1',
    destinationName: 'Tokyo, Japan',
    startDate: new Date('2026-11-10T00:00:00.000Z'),
    endDate: new Date('2026-11-10T00:00:00.000Z'),
    travelersCount: 2,
    budgetTotal: 500,
    currency: 'JPY',
    pace: 'MODERATE',
    transportPreference: 'TRANSIT',
    notes: null,
    heroImageUrl: null,
    createdAt: new Date('2026-11-01T10:00:00.000Z'),
    updatedAt: new Date('2026-11-01T10:00:00.000Z'),
    itineraries: [
      {
        id: 'itin-v1',
        tripId: sampleTripId,
        version: 1,
        isCurrent: true,
        status: 'VERIFIED',
        title: 'Tokyo Urban Run',
        summary: 'Original schedule before replan',
        totalEstimatedCost: 120,
        currency: 'JPY',
        days: [
          {
            id: 'day-1',
            dayIndex: 1,
            date: new Date('2026-11-10T00:00:00.000Z'),
            themeSummary: 'Modern & Traditional',
            weatherSummary: 'Sunny, 18C',
            activities: [
              {
                id: 'act-1',
                placeId: 'p-park',
                title: 'Yoyogi Park',
                activityType: 'PARK',
                startTime: '10:00',
                endTime: '12:00',
                durationMinutes: 120,
                travelTimeToNextMin: 0,
                transitMode: 'TRANSIT',
                estimatedCost: 0,
                reason: 'Morning stroll in the park',
                tips: null,
                validationJson: [],
                place: {
                  id: 'p-park',
                  googlePlaceId: 'osm_park_1',
                  name: 'Yoyogi Park'
                }
              },
              {
                id: 'act-2',
                placeId: 'p-lunch',
                title: 'Ginza Dining',
                activityType: 'RESTAURANT',
                startTime: '12:30',
                endTime: '14:00',
                durationMinutes: 90,
                travelTimeToNextMin: 30,
                transitMode: 'TRANSIT',
                estimatedCost: 100,
                reason: 'Traditional lunch',
                tips: null,
                validationJson: [],
                place: {
                  id: 'p-lunch',
                  googlePlaceId: 'osm_lunch_2',
                  name: 'Ginza Dining'
                }
              },
              {
                id: 'act-3',
                placeId: 'p-tower',
                title: 'Tokyo Tower Observation Deck',
                activityType: 'ATTRACTION',
                startTime: '14:30',
                endTime: '16:30',
                durationMinutes: 120,
                travelTimeToNextMin: 30,
                transitMode: 'TRANSIT',
                estimatedCost: 20,
                reason: 'Iconic observation tower',
                tips: null,
                validationJson: [],
                place: {
                  id: 'p-tower',
                  googlePlaceId: 'osm_tower_3',
                  name: 'Tokyo Tower'
                }
              }
            ]
          }
        ]
      }
    ]
  };

  const sampleCandidatePlaces = [
    {
      id: 'p-morp-museum',
      googlePlaceId: 'osm_museum_4',
      name: 'Mori Art Museum',
      types: ['museum', 'art_gallery']
    },
    {
      id: 'p-national-museum',
      googlePlaceId: 'osm_museum_5',
      name: 'Tokyo National Museum',
      types: ['museum']
    },
    {
      id: 'p-cafe-relax',
      googlePlaceId: 'osm_cafe_6',
      name: 'Omotesando Roastery',
      types: ['cafe']
    }
  ];

  beforeEach(() => {
    mockPrisma = {
      trip: {
        findUnique: jest.fn().mockResolvedValue(sampleTrip)
      }
    };

    mockAIPlanner = {
      attachActivityChecks: jest.fn((itinerary, checks) => itinerary)
    };

    mockItinerariesService = {
      saveVerifiedItinerary: jest.fn(async (tripId, candidate, status) => ({
        id: 'itin-v2',
        tripId,
        version: 2,
        status,
        createdAt: new Date().toISOString(),
        title: candidate.tripTitle,
        summary: candidate.summary,
        totalEstimatedCost: candidate.totalEstimatedCost,
        currency: candidate.currency,
        days: candidate.days
      })),
      getLatestItinerary: jest.fn().mockResolvedValue({
        id: 'itin-v2',
        tripId: sampleTripId,
        version: 2,
        status: 'VERIFIED',
        days: []
      }),
      getItineraryByVersion: jest.fn(async (tripId, version) => {
        if (version === 1) {
          return {
            id: 'itin-v1',
            tripId,
            version: 1,
            status: 'VERIFIED',
            days: []
          };
        }
        return null;
      }),
      getAllVersions: jest.fn().mockResolvedValue([
        { version: 2, status: 'VERIFIED', isCurrent: true, activitiesCount: 3 },
        { version: 1, status: 'VERIFIED', isCurrent: false, activitiesCount: 3 }
      ])
    };

    mockDestinationsService = {};

    mockValidator = {
      validate: jest.fn().mockResolvedValue({
        isValid: true,
        violations: [],
        activityChecks: {}
      })
    };

    mockPlaceService = {
      geocodeDestination: jest.fn().mockResolvedValue({ latitude: 35.6762, longitude: 139.6503 }),
      searchPlaces: jest.fn().mockResolvedValue(sampleCandidatePlaces)
    };

    tripsService = new TripsService(
      mockPrisma,
      mockAIPlanner,
      mockItinerariesService,
      mockDestinationsService,
      mockValidator,
      mockPlaceService
    );
  });

  it('replans for "rain" by replacing outdoor park with indoor museum and increments version', async () => {
    const result = await tripsService.replanTrip(sampleTripId, { intent: 'rain' });

    expect(result.previousVersion).toBe(1);
    expect(result.newVersion).toBe(2);
    expect(result.intent).toBe('rain');
    expect(result.status).toBe('VERIFIED');
    expect(result.changedActivitiesCount).toBeGreaterThanOrEqual(1);
    expect(mockValidator.validate).toHaveBeenCalled();
    expect(mockItinerariesService.saveVerifiedItinerary).toHaveBeenCalled();

    // Verify outdoor Yoyogi park was swapped for Mori Art Museum
    const day1Activities = result.updatedItinerary.days[0].activities;
    const parkStillPresent = day1Activities.some((a: any) => a.placeName === 'Yoyogi Park');
    expect(parkStillPresent).toBe(false);

    const museumFound = day1Activities.some((a: any) => a.placeName === 'Mori Art Museum');
    expect(museumFound).toBe(true);
  });

  it('replans for "running-late" by delaying start and end times', async () => {
    const result = await tripsService.replanTrip(sampleTripId, { intent: 'running-late' });

    expect(result.newVersion).toBe(2);
    expect(result.intent).toBe('running-late');

    const day1Activities = result.updatedItinerary.days[0].activities;
    // Original 10:00 start time should now be 10:45
    expect(day1Activities[0].startTime).toBe('10:45');
    expect(day1Activities[0].endTime).toBe('12:45');
  });

  it('replans for "tired" by trimming pacing', async () => {
    const result = await tripsService.replanTrip(sampleTripId, { intent: 'tired' });

    expect(result.newVersion).toBe(2);
    expect(result.intent).toBe('tired');
    expect(result.status).toBe('VERIFIED');
    // Activities reduced from 3 to 2
    expect(result.updatedItinerary.days[0].activities.length).toBe(2);
  });

  it('replans for "budget-cut" by trimming the last stop, since prices are not estimated', async () => {
    const result = await tripsService.replanTrip(sampleTripId, { intent: 'budget-cut' });

    expect(result.newVersion).toBe(2);
    expect(result.intent).toBe('budget-cut');
    // The day had 3 stops; the honest cost lever is doing less.
    expect(result.updatedItinerary.days[0].activities.length).toBe(2);
    // Prices are not ours to rewrite: the replan trims the plan, it never zeroes a stored cost.
    const storedCosts = (result.updatedItinerary.days[0].activities as any[])
      .map((a) => a.estimatedCost)
      .filter((c) => c !== undefined);
    expect(storedCosts.length).toBeGreaterThan(0);
  });

  it('preserves and retrieves previous version v1 when requested', async () => {
    const v1Details = await tripsService.getTripDetails(sampleTripId, 1);
    expect(v1Details.itinerary?.version).toBe(1);
    expect(mockItinerariesService.getItineraryByVersion).toHaveBeenCalledWith(sampleTripId, 1);
  });
});
