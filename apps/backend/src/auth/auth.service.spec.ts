import { BadRequestException, UnauthorizedException } from '@nestjs/common';
import { AuthService } from './auth.service';
import { hashToken } from '../common/auth/token-hash';

/**
 * The rules that matter for accounts: a raw token is never stored, a magic link is
 * single use and time limited, and signing in hands the guest's trips to the account.
 */
function buildPrismaMock() {
  return {
    authToken: {
      create: jest.fn().mockResolvedValue({ id: 'token-1' }),
      findUnique: jest.fn(),
      update: jest.fn().mockResolvedValue({ id: 'token-1' })
    },
    authSession: {
      create: jest.fn().mockResolvedValue({ id: 'session-1' }),
      findUnique: jest.fn(),
      update: jest.fn().mockResolvedValue({ id: 'session-1' })
    },
    user: {
      upsert: jest.fn().mockResolvedValue({ id: 'user-1', email: 'kevin@example.com' }),
      findUnique: jest.fn(),
      create: jest.fn()
    },
    userProfile: {
      upsert: jest.fn().mockResolvedValue({ id: 'profile-1' })
    },
    trip: {
      updateMany: jest.fn().mockResolvedValue({ count: 0 }),
      findMany: jest.fn().mockResolvedValue([])
    }
  };
}

function validTokenRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'token-1',
    email: 'kevin@example.com',
    tokenHash: 'irrelevant',
    expiresAt: new Date(Date.now() + 10 * 60 * 1000),
    consumedAt: null,
    createdAt: new Date(),
    ...overrides
  };
}

describe('AuthService', () => {
  let prisma: ReturnType<typeof buildPrismaMock>;
  let service: AuthService;

  beforeEach(() => {
    prisma = buildPrismaMock();
    service = new AuthService(prisma as any);
  });

  describe('requestMagicLink', () => {
    it('stores a hash of the token and never the token itself', async () => {
      await service.requestMagicLink({ email: 'Kevin@Example.com' });

      const written = prisma.authToken.create.mock.calls[0][0].data;
      expect(written.email).toBe('kevin@example.com');
      expect(written.tokenHash).toMatch(/^[a-f0-9]{64}$/);
      // The raw token only exists in the returned login url, not in the row we stored.
      expect(JSON.stringify(written)).not.toContain('token=');
    });

    it('says delivery is by console rather than claiming an email was sent', async () => {
      const result = await service.requestMagicLink({ email: 'kevin@example.com' });
      expect(result.delivery).toBe('console');
      expect(result.loginUrl).toContain('/login?token=');
      expect(result.expiresAt).toBeTruthy();
    });
  });

  describe('verifyMagicLink', () => {
    it('rejects a token that does not exist', async () => {
      prisma.authToken.findUnique.mockResolvedValue(null);
      await expect(service.verifyMagicLink({ token: 'a'.repeat(43) })).rejects.toBeInstanceOf(
        UnauthorizedException
      );
    });

    it('rejects an expired token', async () => {
      prisma.authToken.findUnique.mockResolvedValue(
        validTokenRow({ expiresAt: new Date(Date.now() - 1000) })
      );
      await expect(service.verifyMagicLink({ token: 'a'.repeat(43) })).rejects.toBeInstanceOf(
        UnauthorizedException
      );
    });

    it('rejects a token that was already used', async () => {
      prisma.authToken.findUnique.mockResolvedValue(
        validTokenRow({ consumedAt: new Date() })
      );
      await expect(service.verifyMagicLink({ token: 'a'.repeat(43) })).rejects.toBeInstanceOf(
        UnauthorizedException
      );
    });

    it('rejects a token used with a different email', async () => {
      prisma.authToken.findUnique.mockResolvedValue(validTokenRow());
      await expect(
        service.verifyMagicLink({ token: 'a'.repeat(43), email: 'someone@else.com' })
      ).rejects.toBeInstanceOf(BadRequestException);
    });

    it('issues a session token and consumes the magic link', async () => {
      prisma.authToken.findUnique.mockResolvedValue(validTokenRow());

      const result = await service.verifyMagicLink({ token: 'a'.repeat(43) });

      expect(result.sessionToken).toBeTruthy();
      expect(result.user.email).toBe('kevin@example.com');
      expect(result.user.displayName).toBe('Kevin');
      expect(prisma.userProfile.upsert).toHaveBeenCalled();
      expect(prisma.authToken.update).toHaveBeenCalledWith({
        where: { id: 'token-1' },
        data: { consumedAt: expect.any(Date) }
      });

      const sessionRow = prisma.authSession.create.mock.calls[0][0].data;
      expect(sessionRow.tokenHash).toBe(hashToken(result.sessionToken));
      expect(sessionRow.tokenHash).not.toBe(result.sessionToken);
      expect(result.migratedTrips).toBe(0);
    });

    it('moves guest trips onto the account when a guest session id is supplied', async () => {
      prisma.authToken.findUnique.mockResolvedValue(validTokenRow());
      prisma.user.findUnique.mockResolvedValue({ id: 'guest-user-1' });
      prisma.trip.updateMany.mockResolvedValue({ count: 3 });

      const result = await service.verifyMagicLink({
        token: 'a'.repeat(43),
        guestSessionId: 'browser-abc-123'
      });

      expect(prisma.user.findUnique).toHaveBeenCalledWith({
        where: { firebaseUid: 'guest:browser-abc-123' }
      });
      expect(prisma.trip.updateMany).toHaveBeenCalledWith({
        where: { userId: 'guest-user-1' },
        data: { userId: 'user-1' }
      });
      expect(result.migratedTrips).toBe(3);
    });

    it('never migrates trips when the guest identity is the same account', async () => {
      prisma.authToken.findUnique.mockResolvedValue(validTokenRow());
      prisma.user.findUnique.mockResolvedValue({ id: 'user-1' });

      const result = await service.verifyMagicLink({
        token: 'a'.repeat(43),
        guestSessionId: 'browser-abc-123'
      });

      expect(prisma.trip.updateMany).not.toHaveBeenCalled();
      expect(result.migratedTrips).toBe(0);
    });
  });

  describe('resolveSession', () => {
    it('returns null for an expired session', async () => {
      prisma.authSession.findUnique.mockResolvedValue({
        id: 'session-1',
        revokedAt: null,
        expiresAt: new Date(Date.now() - 1000),
        user: { id: 'user-1', email: 'kevin@example.com' }
      });
      expect(await service.resolveSession('token')).toBeNull();
    });

    it('returns null for a revoked session', async () => {
      prisma.authSession.findUnique.mockResolvedValue({
        id: 'session-1',
        revokedAt: new Date(),
        expiresAt: new Date(Date.now() + 1000),
        user: { id: 'user-1', email: 'kevin@example.com' }
      });
      expect(await service.resolveSession('token')).toBeNull();
    });

    it('resolves a live session to its user', async () => {
      prisma.authSession.findUnique.mockResolvedValue({
        id: 'session-1',
        revokedAt: null,
        expiresAt: new Date(Date.now() + 1000),
        user: { id: 'user-1', email: 'kevin@example.com' }
      });
      expect(await service.resolveSession('token')).toEqual({
        id: 'user-1',
        email: 'kevin@example.com'
      });
    });
  });

  describe('listMyTrips', () => {
    it('counts days and stops and reports the current itinerary', async () => {
      prisma.trip.findMany.mockResolvedValue([
        {
          id: 'trip-1',
          destinationName: 'Lisbon, Portugal',
          startDate: new Date('2026-09-21T00:00:00Z'),
          endDate: new Date('2026-09-22T00:00:00Z'),
          status: 'READY',
          currency: 'EUR',
          itineraries: [
            {
              status: 'VERIFIED',
              currency: 'EUR',
              totalEstimatedCost: 135,
              days: [
                { activities: [{ id: 'a1' }, { id: 'a2' }] },
                { activities: [{ id: 'a3' }] }
              ]
            }
          ],
          shares: [{ token: 'shr-token' }]
        }
      ]);

      const [trip] = await service.listMyTrips('user-1');

      expect(trip).toMatchObject({
        id: 'trip-1',
        status: 'READY',
        itineraryStatus: 'VERIFIED',
        dayCount: 2,
        stopCount: 3,
        currency: 'EUR',
        totalEstimatedCost: 135,
        shareToken: 'shr-token'
      });
    });

    it('reports no itinerary rather than inventing one', async () => {
      prisma.trip.findMany.mockResolvedValue([
        {
          id: 'trip-2',
          destinationName: 'Porto, Portugal',
          startDate: new Date('2026-10-01T00:00:00Z'),
          endDate: new Date('2026-10-02T00:00:00Z'),
          status: 'DRAFT',
          currency: 'EUR',
          itineraries: [],
          shares: []
        }
      ]);

      const [trip] = await service.listMyTrips('user-1');

      expect(trip).toMatchObject({
        itineraryStatus: null,
        dayCount: 0,
        stopCount: 0,
        shareToken: null
      });
    });
  });
});
