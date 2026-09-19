import {
  BadRequestException,
  Injectable,
  Logger,
  UnauthorizedException
} from '@nestjs/common';
import { hashToken, newToken } from '../common/auth/token-hash';
import { PrismaService } from '../common/prisma/prisma.service';
import { RequestMagicLinkDto } from './dto/request-link.dto';
import { VerifyMagicLinkDto } from './dto/verify-link.dto';

/** Magic links are short lived on purpose. */
export const MAGIC_LINK_TTL_MINUTES = 15;
/** A signed in browser stays signed in for a month. */
export const SESSION_TTL_DAYS = 30;

export interface RequestedMagicLink {
  email: string;
  expiresAt: string;
  delivery: 'console';
  /** The link is logged rather than emailed, because no mail provider is wired up. */
  loginUrl: string;
}

export interface VerifiedSession {
  sessionToken: string;
  expiresAt: string;
  user: { id: string; email: string; displayName: string };
  migratedTrips: number;
}

export interface SavedTripSummary {
  id: string;
  destinationName: string;
  startDate: string;
  endDate: string;
  status: string;
  itineraryStatus: string | null;
  dayCount: number;
  stopCount: number;
  currency: string | null;
  totalEstimatedCost: number | null;
  shareToken: string | null;
}

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Creates a single use magic link.
   *
   * Only the SHA-256 hash of the token is stored, so the database never holds
   * something that can be replayed. No mail provider is configured in this project,
   * so the link is written to the log and the response says so instead of claiming an
   * email was delivered.
   */
  async requestMagicLink(dto: RequestMagicLinkDto): Promise<RequestedMagicLink> {
    const email = this.normalizeEmail(dto.email);
    const rawToken = newToken();
    const expiresAt = new Date(Date.now() + MAGIC_LINK_TTL_MINUTES * 60 * 1000);

    await this.prisma.authToken.create({
      data: {
        email,
        tokenHash: hashToken(rawToken),
        expiresAt
      }
    });

    const siteUrl = process.env.PUBLIC_WEB_URL || 'https://web-production-a9ec6.up.railway.app';
    const loginUrl = `${siteUrl}/login?token=${rawToken}&email=${encodeURIComponent(email)}`;
    this.logger.log(`Magic link for ${email} (valid ${MAGIC_LINK_TTL_MINUTES} minutes): ${loginUrl}`);

    return {
      email,
      expiresAt: expiresAt.toISOString(),
      delivery: 'console',
      loginUrl
    };
  }

  /**
   * Consumes a magic link and issues a session token.
   *
   * When the caller passes the guest session id it was already using, every trip that
   * guest account owns is moved to the account being signed into, so a plan made
   * before signing up is not stranded.
   */
  async verifyMagicLink(dto: VerifyMagicLinkDto): Promise<VerifiedSession> {
    const tokenHash = hashToken(dto.token);
    const record = await this.prisma.authToken.findUnique({ where: { tokenHash } });

    if (!record) {
      throw new UnauthorizedException('That link is not valid. Request a new one.');
    }
    if (record.consumedAt) {
      throw new UnauthorizedException('That link was already used. Request a new one.');
    }
    if (record.expiresAt.getTime() < Date.now()) {
      throw new UnauthorizedException('That link expired. Request a new one.');
    }
    if (dto.email && this.normalizeEmail(dto.email) !== record.email) {
      throw new BadRequestException('That link belongs to a different email address.');
    }

    const email = record.email;
    const displayName = this.displayNameFromEmail(email);

    const user = await this.prisma.user.upsert({
      where: { email },
      update: {},
      create: {
        email,
        firebaseUid: `magic:${email}`
      }
    });

    await this.prisma.userProfile.upsert({
      where: { userId: user.id },
      update: {},
      create: { userId: user.id, displayName }
    });

    await this.prisma.authToken.update({
      where: { id: record.id },
      data: { consumedAt: new Date() }
    });

    const rawSessionToken = newToken();
    const expiresAt = new Date(Date.now() + SESSION_TTL_DAYS * 24 * 60 * 60 * 1000);

    await this.prisma.authSession.create({
      data: {
        userId: user.id,
        tokenHash: hashToken(rawSessionToken),
        expiresAt
      }
    });

    const migratedTrips = dto.guestSessionId
      ? await this.migrateGuestTrips(dto.guestSessionId, user.id)
      : 0;

    return {
      sessionToken: rawSessionToken,
      expiresAt: expiresAt.toISOString(),
      user: { id: user.id, email, displayName },
      migratedTrips
    };
  }

  /** Resolves a session token to its user, or null when it is unknown or dead. */
  async resolveSession(rawToken: string): Promise<{ id: string; email: string } | null> {
    if (!rawToken) return null;

    const session = await this.prisma.authSession
      .findUnique({
        where: { tokenHash: hashToken(rawToken) },
        include: { user: true }
      })
      .catch(() => null);

    if (!session || session.revokedAt || session.expiresAt.getTime() < Date.now()) {
      return null;
    }

    await this.prisma.authSession
      .update({ where: { id: session.id }, data: { lastUsedAt: new Date() } })
      .catch(() => undefined);

    return { id: session.user.id, email: session.user.email };
  }

  /** Every trip the account owns, with the counts the saved trips page shows. */
  async listMyTrips(userId: string): Promise<SavedTripSummary[]> {
    const trips = await this.prisma.trip.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' },
      include: {
        shares: { where: { revokedAt: null }, orderBy: { createdAt: 'desc' }, take: 1 },
        itineraries: {
          where: { isCurrent: true },
          orderBy: { version: 'desc' },
          take: 1,
          include: { days: { include: { activities: { select: { id: true } } } } }
        }
      }
    });

    return trips.map((trip) => {
      const itinerary = trip.itineraries[0];
      const days = itinerary?.days ?? [];
      return {
        id: trip.id,
        destinationName: trip.destinationName,
        startDate: trip.startDate.toISOString(),
        endDate: trip.endDate.toISOString(),
        status: trip.status,
        itineraryStatus: itinerary?.status ?? null,
        dayCount: days.length,
        stopCount: days.reduce((total, day) => total + day.activities.length, 0),
        currency: itinerary?.currency ?? trip.currency ?? null,
        totalEstimatedCost: itinerary?.totalEstimatedCost ?? null,
        shareToken: trip.shares[0]?.token ?? null
      };
    });
  }

  /** Moves every trip owned by the guest identity onto the signed in account. */
  private async migrateGuestTrips(guestSessionId: string, userId: string): Promise<number> {
    const normalized = this.normalizeSessionId(guestSessionId);
    if (!normalized) return 0;

    const guest = await this.prisma.user
      .findUnique({ where: { firebaseUid: `guest:${normalized}` } })
      .catch(() => null);

    if (!guest || guest.id === userId) return 0;

    const result = await this.prisma.trip.updateMany({
      where: { userId: guest.id },
      data: { userId }
    });

    if (result.count > 0) {
      this.logger.log(`Moved ${result.count} trip(s) from guest ${normalized} to account ${userId}`);
    }

    return result.count;
  }

  private normalizeEmail(raw: string): string {
    return String(raw || '').trim().toLowerCase();
  }

  private displayNameFromEmail(email: string): string {
    const localPart = email.split('@')[0] || 'Traveller';
    return localPart.charAt(0).toUpperCase() + localPart.slice(1);
  }

  private normalizeSessionId(raw: string): string {
    return String(raw || '')
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, '')
      .slice(0, 64);
  }
}
