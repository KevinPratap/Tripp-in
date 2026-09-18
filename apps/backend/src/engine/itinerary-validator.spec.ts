import { ItineraryValidator } from './itinerary-validator';
import { TripRequirement } from '@trippin/shared-types';
import { ItineraryV1 } from '@trippin/itinerary-schema';

describe('ItineraryValidator (Deterministic Engine)', () => {
  let validator: ItineraryValidator;
  let mockPlaceService: any;
  let mockRouteService: any;

  beforeEach(() => {
    mockPlaceService = {
      getPlaceDetails: jest.fn().mockImplementation(async (id: string) => {
        if (id === 'museum-closed-monday') {
          return {
            id,
            googlePlaceId: id,
            name: 'Closed Museum',
            location: { latitude: 48.86, longitude: 2.33 },
            openingHours: {
              periods: [
                // Open Wednesday only
                { open: { day: 3, time: '09:00' }, close: { day: 3, time: '18:00' } }
              ]
            }
          };
        }
        return {
          id,
          googlePlaceId: id,
          name: 'Regular Place',
          location: { latitude: 48.85, longitude: 2.32 },
          openingHours: {
            periods: [
              { open: { day: 1, time: '09:00' }, close: { day: 1, time: '20:00' } },
              { open: { day: 2, time: '09:00' }, close: { day: 2, time: '20:00' } },
              { open: { day: 3, time: '09:00' }, close: { day: 3, time: '20:00' } }
            ]
          }
        };
      })
    };

    mockRouteService = {
      estimateTravelTimeMinutes: jest.fn().mockResolvedValue(45) // requires 45 mins
    };

    validator = new ItineraryValidator(mockPlaceService, mockRouteService);
  });

  const baseRequirements: TripRequirement = {
    destination: 'Paris',
    startDate: '2026-06-01', // Monday (day 1)
    endDate: '2026-06-02',
    travelersCount: 2,
    pace: 'MODERATE',
    budgetTotal: 500,
    currency: 'USD'
  };

  it('should reject schedule with overlapping activities (TIME_OVERLAP)', async () => {
    const candidate: ItineraryV1 = {
      schemaVersion: 'itinerary.schema.v1',
      tripTitle: 'Overlap Test',
      destination: 'Paris',
      summary: 'Testing overlap',
      currency: 'USD',
      days: [
        {
          dayIndex: 1,
          date: '2026-06-01',
          themeSummary: 'Museum day',
          activities: [
            {
              placeId: 'place-1',
              placeName: 'Place 1',
              activityType: 'MUSEUM',
              startTime: '10:00',
              endTime: '12:00',
              durationMinutes: 120,
              travelTimeFromPreviousMinutes: 0,
              transitModeFromPrevious: 'TRANSIT',
              reason: 'First stop'
            },
            {
              placeId: 'place-2',
              placeName: 'Place 2',
              activityType: 'RESTAURANT',
              startTime: '11:30', // OVERLAP: starts before 12:00
              endTime: '13:00',
              durationMinutes: 90,
              travelTimeFromPreviousMinutes: 0,
              transitModeFromPrevious: 'TRANSIT',
              reason: 'Second stop'
            }
          ]
        }
      ]
    };

    const result = await validator.validate(candidate, baseRequirements);
    expect(result.isValid).toBe(false);
    expect(result.violations.some((v) => v.code === 'TIME_OVERLAP')).toBe(true);
  });

  it('should reject schedule when venue is closed on that day (PLACE_CLOSED)', async () => {
    const candidate: ItineraryV1 = {
      schemaVersion: 'itinerary.schema.v1',
      tripTitle: 'Closed Test',
      destination: 'Paris',
      summary: 'Testing closed venue',
      currency: 'USD',
      days: [
        {
          dayIndex: 1,
          date: '2026-06-01', // Monday
          themeSummary: 'Museum day',
          activities: [
            {
              placeId: 'museum-closed-monday',
              placeName: 'Closed Museum',
              activityType: 'MUSEUM',
              startTime: '10:00',
              endTime: '12:00',
              durationMinutes: 120,
              travelTimeFromPreviousMinutes: 0,
              transitModeFromPrevious: 'TRANSIT',
              reason: 'First stop'
            }
          ]
        }
      ]
    };

    const result = await validator.validate(candidate, baseRequirements);
    expect(result.isValid).toBe(false);
    expect(result.violations.some((v) => v.code === 'PLACE_CLOSED')).toBe(true);
  });

  it('should reject schedule when physical route travel time does not fit (INSUFFICIENT_TRAVEL_TIME)', async () => {
    // mockRouteService returns 45 minutes, but gap is only 15 minutes
    const candidate: ItineraryV1 = {
      schemaVersion: 'itinerary.schema.v1',
      tripTitle: 'Transit Buffer Test',
      destination: 'Paris',
      summary: 'Testing transit gap',
      currency: 'USD',
      days: [
        {
          dayIndex: 1,
          date: '2026-06-01',
          themeSummary: 'Museum day',
          activities: [
            {
              placeId: 'place-1',
              placeName: 'Place 1',
              activityType: 'MUSEUM',
              startTime: '10:00',
              endTime: '12:00',
              durationMinutes: 120,
              travelTimeFromPreviousMinutes: 0,
              transitModeFromPrevious: 'TRANSIT',
              reason: 'First stop'
            },
            {
              placeId: 'place-2',
              placeName: 'Place 2',
              activityType: 'RESTAURANT',
              startTime: '12:15', // Only 15 mins gap, route needs 45 mins
              endTime: '13:30',
              durationMinutes: 75,
              travelTimeFromPreviousMinutes: 15,
              transitModeFromPrevious: 'TRANSIT',
              reason: 'Second stop'
            }
          ]
        }
      ]
    };

    const result = await validator.validate(candidate, baseRequirements);
    expect(result.isValid).toBe(false);
    expect(result.violations.some((v) => v.code === 'INSUFFICIENT_TRAVEL_TIME')).toBe(true);
  });

  it('should pass validation when schedule satisfies all constraints', async () => {
    mockRouteService.estimateTravelTimeMinutes.mockResolvedValue(20); // 20 mins needed

    const candidate: ItineraryV1 = {
      schemaVersion: 'itinerary.schema.v1',
      tripTitle: 'Valid Test',
      destination: 'Paris',
      summary: 'Testing valid itinerary',
      currency: 'USD',
      days: [
        {
          dayIndex: 1,
          date: '2026-06-01',
          themeSummary: 'Museum day',
          activities: [
            {
              placeId: 'place-1',
              placeName: 'Place 1',
              activityType: 'MUSEUM',
              startTime: '10:00',
              endTime: '12:00',
              durationMinutes: 120,
              travelTimeFromPreviousMinutes: 0,
              transitModeFromPrevious: 'TRANSIT',
              reason: 'First stop'
            },
            {
              placeId: 'place-2',
              placeName: 'Place 2',
              activityType: 'RESTAURANT',
              startTime: '12:30', // 30 mins gap >= 20 mins transit
              endTime: '13:45',
              durationMinutes: 75,
              travelTimeFromPreviousMinutes: 30,
              transitModeFromPrevious: 'TRANSIT',
              reason: 'Second stop'
            }
          ]
        }
      ]
    };

    const result = await validator.validate(candidate, baseRequirements);
    expect(result.isValid).toBe(true);
    expect(result.violations.length).toBe(0);
  });
});
