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
        findMany: jest.fn().mockResolvedValue([]),
        create: jest.fn().mockResolvedValue({}),
        deleteMany: jest.fn().mockResolvedValue({ count: 1 }),
        updateMany: jest.fn().mockResolvedValue({ count: 1 })
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

    it('throws NotFoundException when share token is revoked or invalid', async () => {
      mockPrisma.tripShare.findUnique.mockResolvedValue({
        tripId: 'shared-trip-1',
        token: 'revoked-token',
        revokedAt: new Date()
      });

      await expect(
        service.joinTrip({
          token: 'revoked-token',
          name: 'Intruder'
        })
      ).rejects.toThrow('Trip invite token is not valid or has been revoked.');
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
        },
        {
          id: 't-4',
          name: 'Over Max Only',
          budgetCap: 400, // 350 min <= 400 cap < 450 max => overCap true
          interests: [],
          dislikes: [],
          pace: null,
          joinedAt: new Date().toISOString()
        }
      ];

      const split = service.computePerTravellerCost(travellers, mockCost);
      expect(split[0].overCap).toBe(false); // 500 cap >= 450 max
      expect(split[1].overCap).toBe(true);  // 300 cap < 350 min & 450 max
      expect(split[2].overCap).toBe(false); // null cap
      expect(split[3].overCap).toBe(true);  // 400 cap < 450 max (surfaces range conflict)
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
    it('returns empty array when engine has not generated distinct objective itineraries', () => {
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
      expect(options).toEqual([]);
    });

    it('returns generated options verbatim when engine provides them', () => {
      const generated = [
        {
          id: 'opt-1',
          objective: 'cheapest' as const,
          totalMin: 150,
          totalMax: 200,
          currency: 'USD',
          isFloor: false,
          headline: 'Verified low cost schedule'
        }
      ];
      const options = service.computePlanOptions('trip-123', 'USD', undefined, generated);
      expect(options).toEqual(generated);
    });
  });

  describe('Postgres persistence & cache miss restoration', () => {
    it('restores traveller details including caps and interests from Postgres on cache miss', async () => {
      const mockSavedTravellers = [
        {
          id: 'traveller-db-1',
          name: 'Morgan',
          budgetCap: 400,
          interests: ['food', 'shopping'],
          dislikes: ['nightlife'],
          pace: 'RELAXED' as const,
          joinedAt: new Date().toISOString()
        }
      ];

      mockPrisma.trip.findUnique.mockResolvedValue({
        id: 'trip-persisted',
        costAssumptionsJson: {
          stayPerNightMin: 50,
          _travellers: mockSavedTravellers
        }
      });

      const travellers = await service.getTravellers('trip-persisted');
      expect(travellers).toHaveLength(1);
      expect(travellers[0].name).toBe('Morgan');
      expect(travellers[0].budgetCap).toBe(400);
      expect(travellers[0].interests).toEqual(['food', 'shopping']);
      expect(travellers[0].dislikes).toEqual(['nightlife']);
      expect(travellers[0].pace).toBe('RELAXED');
    });
  });
});
