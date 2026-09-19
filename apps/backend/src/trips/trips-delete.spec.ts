import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { TripsService } from './trips.service';

describe('Trip Deletion & Lifecycle (DELETE /api/v1/trips/:id)', () => {
  let tripsService: TripsService;
  let mockPrisma: any;
  let mockConfig: any;

  const sampleTripId = 'trip-to-delete-101';
  const ownerUserId = 'user-owner-001';
  const strangerUserId = 'user-stranger-999';

  beforeEach(() => {
    mockPrisma = {
      trip: {
        findUnique: jest.fn(),
        delete: jest.fn()
      }
    };

    mockConfig = {
      get: jest.fn().mockReturnValue('false')
    };

    const mockAIPlanner: any = {};
    const mockValidator: any = {};
    const mockPlaces: any = {};
    const mockDestinations: any = {};
    const mockItinerariesService: any = {};

    tripsService = new TripsService(
      mockPrisma,
      mockAIPlanner,
      mockItinerariesService,
      mockDestinations,
      mockValidator,
      mockPlaces,
      mockConfig
    );
  });

  it('deletes trip successfully when called by the trip owner', async () => {
    mockPrisma.trip.findUnique.mockResolvedValue({
      id: sampleTripId,
      userId: ownerUserId,
      travelers: []
    });
    mockPrisma.trip.delete.mockResolvedValue({ id: sampleTripId });

    const result = await tripsService.deleteTrip(sampleTripId, ownerUserId);

    expect(result).toEqual({ success: true, tripId: sampleTripId });
    expect(mockPrisma.trip.delete).toHaveBeenCalledWith({
      where: { id: sampleTripId }
    });
  });

  it('throws ForbiddenException when a non-owner/non-organizer attempts to delete', async () => {
    mockPrisma.trip.findUnique.mockResolvedValue({
      id: sampleTripId,
      userId: ownerUserId,
      travelers: [{ userId: strangerUserId, role: 'TRAVELER' }]
    });

    await expect(tripsService.deleteTrip(sampleTripId, strangerUserId)).rejects.toThrow(
      ForbiddenException
    );
    expect(mockPrisma.trip.delete).not.toHaveBeenCalled();
  });

  it('allows deletion if stranger has ORGANIZER role in travelers', async () => {
    mockPrisma.trip.findUnique.mockResolvedValue({
      id: sampleTripId,
      userId: ownerUserId,
      travelers: [{ userId: strangerUserId, role: 'ORGANIZER' }]
    });
    mockPrisma.trip.delete.mockResolvedValue({ id: sampleTripId });

    const result = await tripsService.deleteTrip(sampleTripId, strangerUserId);

    expect(result).toEqual({ success: true, tripId: sampleTripId });
    expect(mockPrisma.trip.delete).toHaveBeenCalledWith({
      where: { id: sampleTripId }
    });
  });

  it('throws NotFoundException when trip does not exist', async () => {
    mockPrisma.trip.findUnique.mockResolvedValue(null);

    await expect(tripsService.deleteTrip('non-existent-trip', ownerUserId)).rejects.toThrow(
      NotFoundException
    );
    expect(mockPrisma.trip.delete).not.toHaveBeenCalled();
  });
});
