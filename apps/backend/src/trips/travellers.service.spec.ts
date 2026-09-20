import { TravellersService } from './travellers.service';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';
import { ActivityModel, TripCostModel } from '@trippin/shared-types';

describe('TravellersService', () => {
  let service: TravellersService;
  let mockPrisma: any;
  let mockRedis: any;

  beforeEach(() => {
    mockPrisma = {
      trip: {
        findUnique: jest.fn(),
        update: jest.fn()
      },
      tripTraveler: {
        findMany: jest.fn().mockResolvedValue([])
      },
      tripShare: {
        findUnique: jest.fn()
      }
    };

    mockRedis = {
      get: jest.fn().mockResolvedValue(null),
      set: jest.fn().mockResolvedValue('OK')
    };

    service = new TravellersService(mockPrisma as PrismaService, mockRedis as RedisService);
  });

  describe('Traveller CRUD', () => {
    const tripId = 'test-trip-uuid';

    it('adds a traveller with filtered vocabulary and updates travelersCount', async () => {
      mockPrisma.trip.findUnique.mockResolvedValue({
        id: tripId,
        travelersCount: 1
      });

      const traveller = await service.addTraveller(tripId, {
        name: 'Alex',
        budgetCap: 250,
        interests: ['food', 'museums', 'invalid_tag'],
        dislikes: ['nightlife'],
        pace: 'balanced'
      });

      expect(traveller.name).toBe('Alex');
      expect(traveller.budgetCap).toBe(250);
      expect(traveller.interests).toEqual(['food', 'museums']);
      expect(traveller.dislikes).toEqual(['nightlife']);
      expect(traveller.pace).toBe('balanced');
      expect(traveller.id).toBeDefined();

      const list = await service.getTravellers(tripId);
      expect(list).toHaveLength(1);
      expect(list[0].id).toBe(traveller.id);
    });

    it('updates traveller fields', async () => {
      mockPrisma.trip.findUnique.mockResolvedValue({ id: tripId, travelersCount: 1 });
      const created = await service.addTraveller(tripId, { name: 'Alex' });

      const updated = await service.updateTraveller(tripId, created.id, {
        budgetCap: 400,
        interests: ['nature', 'history'],
        pace: 'relaxed'
      });

      expect(updated.budgetCap).toBe(400);
      expect(updated.interests).toEqual(['nature', 'history']);
      expect(updated.pace).toBe('relaxed');
    });

    it('deletes a traveller', async () => {
      mockPrisma.trip.findUnique.mockResolvedValue({ id: tripId, travelersCount: 1 });
      const created = await service.addTraveller(tripId, { name: 'Alex' });

      await service.deleteTraveller(tripId, created.id);
      const list = await service.getTravellers(tripId);
      expect(list).toHaveLength(0);
    });
  });

  describe('joinTrip', () => {
    it('resolves trip by share token and adds traveller', async () => {
      mockPrisma.tripShare.findUnique.mockResolvedValue({
        tripId: 'shared-trip-1',
        token: 'share123',
        revokedAt: null
      });
      mockPrisma.trip.findUnique.mockResolvedValue({
        id: 'shared-trip-1',
        travelersCount: 1
      });

      const result = await service.joinTrip({
        token: 'share123',
        name: 'Jordan',
        budgetCap: 300,
        interests: ['food', 'landmark']
      });

      expect(result.tripId).toBe('shared-trip-1');
      expect(result.traveller.name).toBe('Jordan');
      expect(result.traveller.budgetCap).toBe(300);
    });
  });

  describe('computePerTravellerCost and overCap', () => {
    it('correctly flags overCap only when share exceeds traveller cap', () => {
      const mockCost: TripCostModel = {
        currency: 'EUR',
        basis: 'PER_PERSON',
        nights: 3,
        days: 4,
        stay: { unit: 'PER_NIGHT', units: 3, source: 'USER_INPUT' },
        food: { unit: 'PER_DAY', units: 4, source: 'USER_INPUT' },
        localTransit: { unit: 'PER_DAY', units: 4, source: 'NOT_SET' },
        entries: { lines: [], knownMin: 50, knownMax: 70, freeCount: 1, unknownCount: 0, foreignCurrencyCount: 0 },
        totalMin: 350,
        totalMax: 450,
        isFloor: false,
        pricedStops: 3,
        unpricedStops: 0,
        notes: []
      };

      const travellers = [
        {
          id: 't-1',
          name: 'Under Cap',
          budgetCap: 500,
          interests: [],
          dislikes: [],
          pace: null,
          joinedAt: new Date().toISOString()
        },
        {
          id: 't-2',
          name: 'Over Cap',
          budgetCap: 300, // 350 min > 300 cap => overCap true
          interests: [],
          dislikes: [],
          pace: null,
          joinedAt: new Date().toISOString()
        },
        {
          id: 't-3',
          name: 'No Cap',
          budgetCap: null,
          interests: [],
          dislikes: [],
          pace: null,
          joinedAt: new Date().toISOString()
        }
      ];

      const split = service.computePerTravellerCost(travellers, mockCost);
      expect(split[0].overCap).toBe(false);
      expect(split[1].overCap).toBe(true);
      expect(split[2].overCap).toBe(false);
    });
  });

  describe('computeStopSupport', () => {
    it('computes want, total, and against based on real preferences only', () => {
      const travellers = [
        {
          id: 't-1',
          name: 'Foodie',
          budgetCap: null,
          interests: ['food'],
          dislikes: ['museums'],
          pace: null,
          joinedAt: new Date().toISOString()
        },
        {
          id: 't-2',
          name: 'Historian',
          budgetCap: null,
          interests: ['museums', 'history'],
          dislikes: [],
          pace: null,
          joinedAt: new Date().toISOString()
        }
      ];

      const museumActivity: ActivityModel = {
        id: 'act-1',
        placeId: 'p-1',
        title: 'National History Museum',
        type: 'MUSEUM',
        startTime: '10:00',
        endTime: '12:00',
        durationMinutes: 120
      };

      const support = service.computeStopSupport(museumActivity, travellers);
      expect(support).toBeDefined();
      expect(support?.want).toBe(1); // Historian
      expect(support?.total).toBe(2);
      expect(support?.against).toEqual(['Foodie']);
    });
  });

  describe('computePlanOptions', () => {
    it('produces 3 objective options with honest numbers and headlines', () => {
      const mockCost: TripCostModel = {
        currency: 'USD',
        basis: 'PER_PERSON',
        nights: 2,
        days: 3,
        stay: { unit: 'PER_NIGHT', units: 2, source: 'USER_INPUT' },
        food: { unit: 'PER_DAY', units: 3, source: 'USER_INPUT' },
        localTransit: { unit: 'PER_DAY', units: 3, source: 'NOT_SET' },
        entries: { lines: [], knownMin: 20, knownMax: 40, freeCount: 2, unknownCount: 0, foreignCurrencyCount: 0 },
        totalMin: 200,
        totalMax: 300,
        isFloor: false,
        pricedStops: 2,
        unpricedStops: 0,
        notes: []
      };

      const options = service.computePlanOptions('trip-123', 'USD', mockCost);
      expect(options).toHaveLength(3);
      expect(options.map((o) => o.objective)).toEqual(['cheapest', 'balanced', 'experience']);
      expect(options[0].totalMin).toBe(170); // 200 * 0.85
      expect(options[1].totalMin).toBe(200); // 200
      expect(options[2].totalMin).toBe(230); // 200 * 1.15
      expect(options[0].headline).toBeDefined();
    });
  });
});
