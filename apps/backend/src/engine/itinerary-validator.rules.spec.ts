import { ItineraryValidator } from './itinerary-validator';
import { TripRequirement } from '@trippin/shared-types';
import { ItineraryV1 } from '@trippin/itinerary-schema';

/**
 * Rules that the audit found missing: geographic containment, cross-day continuity,
 * repeated venues, thin days, estimated opening hours and currency honesty.
 */

const LISBON = { latitude: 38.7169, longitude: -9.1399 };
const WISCONSIN = { latitude: 43.1345, longitude: -88.2197 };
const TOKYO = { latitude: 35.6762, longitude: 139.6503 };
const KYOTO = { latitude: 35.0116, longitude: 135.7681 };

function makePlace(id: string, location: { latitude: number; longitude: number }, extra: any = {}) {
  return {
    id,
    googlePlaceId: id,
    name: `Venue ${id}`,
    location,
    types: ['attraction'],
    photoUrls: [],
    openingHours: { periods: [] },
    ...extra
  };
}

function buildValidator(places: Record<string, any>) {
  const placeService: any = {
    getPlaceDetails: jest.fn(async (id: string) => places[id] || null)
  };
  const routeService: any = {
    estimateTravelTimeMinutes: jest.fn().mockResolvedValue(20)
  };
  return new ItineraryValidator(placeService, routeService);
}

function activity(
  placeId: string,
  startTime: string,
  endTime: string,
  type = 'ATTRACTION',
  extra: any = {}
) {
  const durationMinutes =
    parseInt(endTime.split(':')[0], 10) * 60 +
    parseInt(endTime.split(':')[1], 10) -
    (parseInt(startTime.split(':')[0], 10) * 60 + parseInt(startTime.split(':')[1], 10));
  return {
    placeId,
    placeName: `Venue ${placeId}`,
    activityType: type,
    startTime,
    endTime,
    durationMinutes,
    travelTimeFromPreviousMinutes: 0,
    transitModeFromPrevious: 'WALK',
    reason: 'test',
    ...extra
  };
}

function itinerary(days: any[], currency = 'USD'): ItineraryV1 {
  return {
    schemaVersion: 'itinerary.schema.v1',
    tripTitle: 'Rules Test',
    destination: 'Test',
    summary: 'rules coverage',
    currency,
    days
  } as ItineraryV1;
}

const baseRequirements: TripRequirement = {
  destination: 'Lisbon, Portugal',
  startDate: '2026-06-01',
  endDate: '2026-06-02',
  travelersCount: 2,
  pace: 'MODERATE',
  budgetTotal: 500,
  currency: 'USD'
};

describe('ItineraryValidator geographic containment', () => {
  it('flags a venue that is far from the destination and from every other stop', async () => {
    const validator = buildValidator({
      'lisbon-a': makePlace('lisbon-a', LISBON),
      'lisbon-b': makePlace('lisbon-b', { latitude: 38.725, longitude: -9.15 }),
      'wisconsin-museum': makePlace('wisconsin-museum', WISCONSIN)
    });

    const candidate = itinerary([
      {
        dayIndex: 1,
        date: '2026-06-01',
        themeSummary: 'Lisbon',
        activities: [activity('lisbon-a', '09:00', '10:30'), activity('lisbon-b', '11:30', '13:00')]
      },
      {
        dayIndex: 2,
        date: '2026-06-02',
        themeSummary: 'Mistake',
        activities: [
          activity('wisconsin-museum', '09:00', '10:30'),
          activity('lisbon-a', '14:00', '15:30')
        ]
      }
    ]);

    const result = await validator.validate(candidate, {
      ...baseRequirements,
      destinationLocation: LISBON
    });

    const codes = result.violations.map((v) => v.code);
    expect(codes).toContain('PLACE_OUTSIDE_DESTINATION');
    expect(result.isValid).toBe(false);
  });

  it('allows a legitimate second city whose venues cluster together', async () => {
    const validator = buildValidator({
      'tokyo-a': makePlace('tokyo-a', TOKYO),
      'tokyo-b': makePlace('tokyo-b', { latitude: 35.68, longitude: 139.7 }),
      'kyoto-a': makePlace('kyoto-a', KYOTO),
      'kyoto-b': makePlace('kyoto-b', { latitude: 35.02, longitude: 135.77 })
    });

    const candidate = itinerary([
      {
        dayIndex: 1,
        date: '2026-06-01',
        themeSummary: 'Tokyo',
        activities: [activity('tokyo-a', '09:00', '10:30'), activity('tokyo-b', '11:30', '13:00')]
      },
      {
        dayIndex: 2,
        date: '2026-06-02',
        themeSummary: 'Kyoto',
        activities: [activity('kyoto-a', '09:00', '10:30'), activity('kyoto-b', '11:30', '13:00')]
      }
    ]);

    const result = await validator.validate(candidate, {
      ...baseRequirements,
      destination: 'Tokyo, Japan',
      destinationLocation: TOKYO
    });

    const codes = result.violations.map((v) => v.code);
    expect(codes).not.toContain('PLACE_OUTSIDE_DESTINATION');
  });
});

describe('ItineraryValidator venue and day quality', () => {
  it('flags the same sightseeing venue twice in one trip', async () => {
    const validator = buildValidator({
      'museum-1': makePlace('museum-1', LISBON),
      'cafe-1': makePlace('cafe-1', { latitude: 38.72, longitude: -9.15 })
    });

    const candidate = itinerary([
      {
        dayIndex: 1,
        date: '2026-06-01',
        themeSummary: 'Repeat',
        activities: [
          activity('museum-1', '09:00', '10:30', 'MUSEUM'),
          activity('cafe-1', '11:30', '12:30', 'CAFE'),
          activity('museum-1', '15:00', '16:30', 'MUSEUM')
        ]
      }
    ]);

    const codes = (await validator.validate(candidate, baseRequirements)).violations.map(
      (v) => v.code
    );
    expect(codes).toContain('REPEATED_VENUE');
  });

  it('tolerates returning to the same cafe once', async () => {
    const validator = buildValidator({
      'cafe-1': makePlace('cafe-1', LISBON),
      'cafe-2': makePlace('cafe-2', { latitude: 38.72, longitude: -9.15 })
    });

    const candidate = itinerary([
      {
        dayIndex: 1,
        date: '2026-06-01',
        themeSummary: 'Coffee twice',
        activities: [
          activity('cafe-1', '09:00', '10:00', 'CAFE'),
          activity('cafe-2', '11:00', '12:00', 'CAFE'),
          activity('cafe-1', '14:00', '15:00', 'CAFE')
        ]
      }
    ]);

    const codes = (await validator.validate(candidate, baseRequirements)).violations.map(
      (v) => v.code
    );
    expect(codes).not.toContain('REPEATED_VENUE');
  });

  it('flags a day with a single stop', async () => {
    const validator = buildValidator({
      'only-stop': makePlace('only-stop', LISBON)
    });

    const candidate = itinerary([
      {
        dayIndex: 1,
        date: '2026-06-01',
        themeSummary: 'Thin day',
        activities: [activity('only-stop', '09:00', '10:30')]
      }
    ]);

    const codes = (await validator.validate(candidate, baseRequirements)).violations.map(
      (v) => v.code
    );
    expect(codes).toContain('DAY_TOO_SPARSE');
  });
});

describe('ItineraryValidator opening hour honesty', () => {
  it('never reports an estimated-hours venue as closed', async () => {
    const validator = buildValidator({
      'estimated-venue': makePlace('estimated-venue', LISBON, {
        openingHoursEstimated: true,
        openingHours: {
          // estimated hours that would exclude Tuesday, yet the flag says estimate
          periods: [{ open: { day: 1, time: '10:00' }, close: { day: 1, time: '18:00' } }],
          weekdayDescriptions: ['Monday 10:00-18:00 (typical hours, not verified)']
        }
      }),
      'real-venue': makePlace('real-venue', { latitude: 38.72, longitude: -9.15 })
    });

    const candidate = itinerary([
      {
        dayIndex: 1,
        date: '2026-06-02', // Tuesday
        themeSummary: 'Estimated hours',
        activities: [
          activity('estimated-venue', '09:00', '10:30', 'MUSEUM'),
          activity('real-venue', '11:30', '13:00')
        ]
      }
    ]);

    const codes = (await validator.validate(candidate, baseRequirements)).violations.map(
      (v) => v.code
    );
    expect(codes).not.toContain('PLACE_CLOSED');
  });

  it('still enforces published hours when they exist', async () => {
    const validator = buildValidator({
      'closed-tuesday': makePlace('closed-tuesday', LISBON, {
        openingHoursEstimated: false,
        openingHours: {
          periods: [{ open: { day: 3, time: '09:00' }, close: { day: 3, time: '18:00' } }]
        }
      }),
      'real-venue': makePlace('real-venue', { latitude: 38.72, longitude: -9.15 })
    });

    const candidate = itinerary([
      {
        dayIndex: 1,
        date: '2026-06-02', // Tuesday, venue opens Wednesday only
        themeSummary: 'Published hours',
        activities: [
          activity('closed-tuesday', '09:00', '10:30', 'MUSEUM'),
          activity('real-venue', '11:30', '13:00')
        ]
      }
    ]);

    const codes = (await validator.validate(candidate, baseRequirements)).violations.map(
      (v) => v.code
    );
    expect(codes).toContain('PLACE_CLOSED');
  });
});

describe('ItineraryValidator money and cross-day continuity', () => {
  it('does not police prices, and reports no cost metric', async () => {
    const validator = buildValidator({
      'lisbon-a': makePlace('lisbon-a', LISBON),
      'lisbon-b': makePlace('lisbon-b', { latitude: 38.72, longitude: -9.15 })
    });

    // A legacy record or a model that volunteers prices must not resurrect cost checks:
    // the product does not estimate prices, so nothing may be asserted about them.
    const candidate = itinerary([
      {
        dayIndex: 1,
        date: '2026-06-01',
        themeSummary: 'Prices the product does not own',
        activities: [
          activity('lisbon-a', '09:00', '10:30', 'MUSEUM', { estimatedCost: 100 }),
          activity('lisbon-b', '11:30', '13:00', 'RESTAURANT', { estimatedCost: 999999 })
        ]
      }
    ]);

    const result = await validator.validate(candidate, baseRequirements);
    const codes = result.violations.map((v) => v.code);
    expect(codes).not.toContain('EXCEEDS_BUDGET');
    expect(codes).not.toContain('CURRENCY_MISMATCH');
    expect((result.metrics as any).estimatedCostTotal).toBeUndefined();
  });

  it('flags a day-to-day hop that no ground transport covers', async () => {
    const validator = buildValidator({
      'lisbon-a': makePlace('lisbon-a', LISBON),
      'lisbon-b': makePlace('lisbon-b', { latitude: 38.72, longitude: -9.15 }),
      'wisconsin-a': makePlace('wisconsin-a', WISCONSIN),
      'wisconsin-b': makePlace('wisconsin-b', { latitude: 43.14, longitude: -88.23 })
    });

    const candidate = itinerary([
      {
        dayIndex: 1,
        date: '2026-06-01',
        themeSummary: 'Portugal',
        activities: [activity('lisbon-a', '09:00', '10:30'), activity('lisbon-b', '11:30', '13:00')]
      },
      {
        dayIndex: 2,
        date: '2026-06-02',
        themeSummary: 'Somewhere else',
        activities: [
          activity('wisconsin-a', '09:00', '10:30'),
          activity('wisconsin-b', '11:30', '13:00')
        ]
      }
    ]);

    const codes = (
      await validator.validate(candidate, {
        ...baseRequirements,
        destinationLocation: LISBON
      })
    ).violations.map((v) => v.code);

    expect(codes).toContain('CROSS_DAY_HOP_UNREALISTIC');
  });
});
