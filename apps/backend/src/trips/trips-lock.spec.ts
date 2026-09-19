import { ConflictException } from '@nestjs/common';
import { TripsService } from './trips.service';
import { CollabService } from './collab.service';
import { ItinerariesService } from '../itineraries/itineraries.service';

describe('Phase 5: Decision Locking (Trips, Itineraries & Collab)', () => {
  let tripsService: TripsService;
  let itinerariesService: ItinerariesService;
  let collabService: CollabService;
  let mockPrisma: any;
  let mockRedis: any;

  const sampleTripId = 'trip-locked-101';
  const sampleItineraryId = 'itin-locked-v1';

  beforeEach(() => {
    mockPrisma = {
      trip: {
        findUnique: jest.fn(),
        update: jest.fn()
      },
      itinerary: {
        findUnique: jest.fn(),
        updateMany: jest.fn(),
        findFirst: jest.fn()
      },
      activity: {
        findFirst: jest.fn()
      }
    };

    mockRedis = {
      get: jest.fn().mockResolvedValue(null),
      set: jest.fn().mockResolvedValue('OK')
    };

    const mockAIPlanner: any = {};
    const mockValidator: any = {};
    const mockPlaces: any = {};
    const mockDestinations: any = {};

    itinerariesService = new ItinerariesService(mockPrisma, mockAIPlanner, mockValidator);
    collabService = new CollabService(mockPrisma, mockRedis);
    tripsService = new TripsService(
      mockPrisma,
      mockAIPlanner,
      itinerariesService,
      mockDestinations,
      mockValidator,
      mockPlaces
    );
  });

  describe('TripsService.lockTrip & unlockTrip', () => {
    it('locks the trip and current itinerary and returns confirmation', async () => {
      mockPrisma.trip.findUnique.mockResolvedValue({ id: sampleTripId, isLocked: false });
      mockPrisma.trip.update.mockResolvedValue({ id: sampleTripId, isLocked: true });
      mockPrisma.itinerary.updateMany.mockResolvedValue({ count: 1 });

      const result = await tripsService.lockTrip(sampleTripId);

      expect(result.success).toBe(true);
      expect(result.isLocked).toBe(true);
      expect(result.lockedAt).toBeDefined();
      expect(mockPrisma.trip.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: sampleTripId },
          data: expect.objectContaining({ isLocked: true })
        })
      );
      expect(mockPrisma.itinerary.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { tripId: sampleTripId, isCurrent: true },
          data: expect.objectContaining({ isLocked: true })
        })
      );
    });

    it('unlocks the trip and current itinerary', async () => {
      mockPrisma.trip.findUnique.mockResolvedValue({ id: sampleTripId, isLocked: true });
      mockPrisma.trip.update.mockResolvedValue({ id: sampleTripId, isLocked: false });
      mockPrisma.itinerary.updateMany.mockResolvedValue({ count: 1 });

      const result = await tripsService.unlockTrip(sampleTripId);

      expect(result.success).toBe(true);
      expect(result.isLocked).toBe(false);
      expect(mockPrisma.trip.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: sampleTripId },
          data: expect.objectContaining({ isLocked: false, lockedAt: null })
        })
      );
      expect(mockPrisma.itinerary.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { tripId: sampleTripId, isCurrent: true },
          data: expect.objectContaining({ isLocked: false, lockedAt: null })
        })
      );
    });
  });

  describe('Lock Guards on Modification & Replanning', () => {
    it('refuses replanning when trip is locked and throws ConflictException', async () => {
      mockPrisma.trip.findUnique.mockResolvedValue({
        id: sampleTripId,
        isLocked: true,
        itineraries: [{ id: sampleItineraryId, version: 1, isLocked: true, days: [{ activities: [] }] }]
      });

      await expect(
        tripsService.replanTrip(sampleTripId, { intent: 'rain' })
      ).rejects.toThrow(ConflictException);
    });

    it('refuses modifyItinerary when itinerary is locked and throws ConflictException', async () => {
      mockPrisma.itinerary.findUnique.mockResolvedValue({
        id: sampleItineraryId,
        isLocked: true,
        tripId: sampleTripId,
        trip: { isLocked: false }
      });

      await expect(
        itinerariesService.modifyItinerary(sampleItineraryId, 'Make it more relaxing')
      ).rejects.toThrow(ConflictException);
    });

    it('refuses collab voting when trip is locked and throws ConflictException', async () => {
      mockPrisma.trip.findUnique.mockResolvedValue({
        id: sampleTripId,
        isLocked: true
      });

      await expect(
        collabService.vote(sampleTripId, 'act-123', 'Companion', 1)
      ).rejects.toThrow(ConflictException);
    });

    it('returns lock status in getCollabData', async () => {
      mockPrisma.trip.findUnique.mockResolvedValue({
        id: sampleTripId,
        isLocked: true,
        lockedAt: new Date('2026-09-19T12:00:00.000Z')
      });

      const collab = await collabService.getCollabData(sampleTripId);
      expect(collab.isLocked).toBe(true);
      expect(collab.lockedAt).toBe('2026-09-19T12:00:00.000Z');
    });
  });
});
