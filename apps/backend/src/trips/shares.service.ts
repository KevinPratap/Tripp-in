import {
  ForbiddenException,
  Injectable,
  NotFoundException
} from '@nestjs/common';
import { randomBytes } from 'node:crypto';
import { PrismaService } from '../common/prisma/prisma.service';
import { TripsService } from './trips.service';
import { TripDetailsResponse } from '@trippin/api-contracts';

export interface ShareLink {
  token: string;
  url: string;
  createdAt: string;
}

/**
 * Public share links.
 *
 * A share is a second way to read one trip, not a second copy of it. Resolving a share
 * returns exactly the same envelope as GET /trips/:id, so there is one code path that
 * decides what a trip looks like and one place where its data is assembled.
 */
@Injectable()
export class SharesService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly trips: TripsService
  ) {}

  /** Creates a share link for a trip the caller owns, or returns the existing one. */
  async createShare(tripId: string, userId: string): Promise<ShareLink> {
    const trip = await this.prisma.trip.findUnique({ where: { id: tripId } });
    if (!trip) {
      throw new NotFoundException('Trip not found');
    }
    if (trip.userId !== userId) {
      throw new ForbiddenException('Only the owner of a trip can create a share link for it.');
    }

    const existing = await this.prisma.tripShare.findFirst({
      where: { tripId, revokedAt: null },
      orderBy: { createdAt: 'desc' }
    });

    if (existing) {
      return this.toShareLink(existing.token, existing.createdAt);
    }

    // 12 random bytes, url safe, so the token is short enough to paste in a chat.
    const token = randomBytes(12).toString('base64url');
    const created = await this.prisma.tripShare.create({ data: { tripId, token } });
    return this.toShareLink(created.token, created.createdAt);
  }

  /** Revokes every live share link for a trip. The owner only. */
  async revokeShares(tripId: string, userId: string): Promise<{ revoked: number }> {
    const trip = await this.prisma.trip.findUnique({ where: { id: tripId } });
    if (!trip) {
      throw new NotFoundException('Trip not found');
    }
    if (trip.userId !== userId) {
      throw new ForbiddenException('Only the owner of a trip can revoke its share links.');
    }

    const result = await this.prisma.tripShare.updateMany({
      where: { tripId, revokedAt: null },
      data: { revokedAt: new Date() }
    });

    return { revoked: result.count };
  }

  /** Public read of a shared trip. Returns the same envelope as GET /trips/:id. */
  async resolveShare(token: string): Promise<TripDetailsResponse> {
    const trimmed = String(token || '').trim();
    if (!trimmed) {
      throw new NotFoundException('That share link is not valid.');
    }

    const share = await this.prisma.tripShare.findUnique({ where: { token: trimmed } });
    if (!share || share.revokedAt) {
      throw new NotFoundException('That share link is not valid.');
    }

    return this.trips.getTripDetails(share.tripId);
  }

  private toShareLink(token: string, createdAt: Date): ShareLink {
    const siteUrl = process.env.PUBLIC_WEB_URL || 'https://web-production-a9ec6.up.railway.app';
    return {
      token,
      url: `${siteUrl}/t/${token}`,
      createdAt: createdAt.toISOString()
    };
  }
}
