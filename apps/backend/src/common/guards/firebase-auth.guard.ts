import {
  Injectable,
  CanActivate,
  ExecutionContext,
  UnauthorizedException,
  Logger
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Reflector } from '@nestjs/core';
import { PrismaService } from '../prisma/prisma.service';
import { hashToken } from '../auth/token-hash';
import { REQUIRE_IDENTITY_KEY } from '../decorators/require-identity.decorator';

export interface AuthenticatedUser {
  id: string;
  firebaseUid: string;
  email: string;
}

/** Header the web and Android clients use to identify one browser or install. */
export const GUEST_SESSION_HEADER = 'x-guest-session';

/**
 * Authentication, honestly named.
 *
 * No Firebase project is wired up yet, so this guard does not verify third party
 * tokens. What it does instead:
 *
 *  - a real session token (Bearer) is looked up in the database, never invented;
 *  - `dev_` tokens and X-Guest-Session values map to their own guest account, so
 *    every browser and device gets a separate identity with its own trips;
 *  - reads stay public, because a trip link is meant to be shareable;
 *  - anything that writes needs an identity, otherwise it is a 401.
 */
@Injectable()
export class FirebaseAuthGuard implements CanActivate {
  private readonly logger = new Logger(FirebaseAuthGuard.name);

  constructor(
    private readonly config: ConfigService,
    private readonly prisma: PrismaService,
    private readonly reflector: Reflector
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest();
    const method = String(request.method || 'GET').toUpperCase();
    const isRead = method === 'GET' || method === 'HEAD' || method === 'OPTIONS';
    const allowBypass = this.config.get<string>('AUTH_BYPASS_DEV') === 'true';
    const requireIdentity = Boolean(
      this.reflector.getAllAndOverride<boolean>(REQUIRE_IDENTITY_KEY, [
        context.getHandler(),
        context.getClass()
      ])
    );

    const authHeader: string | undefined = request.headers?.authorization;
    const guestHeaderRaw = request.headers?.[GUEST_SESSION_HEADER] as string | undefined;

    // 1. Explicit session token
    if (authHeader) {
      const [scheme, token] = authHeader.split(' ');
      if (scheme !== 'Bearer' || !token) {
        throw new UnauthorizedException(
          'Invalid Authorization format. Expected Bearer <token>'
        );
      }

      const existing = await this.prisma.user
        .findUnique({ where: { firebaseUid: token } })
        .catch(() => null);
      if (existing) {
        request.user = this.toAuthUser(existing);
        return true;
      }

      // 1b. A session token issued by POST /auth/verify after a magic link.
      const session = await this.prisma.authSession
        .findUnique({ where: { tokenHash: hashToken(token) }, include: { user: true } })
        .catch(() => null);

      if (session && !session.revokedAt && session.expiresAt.getTime() >= Date.now()) {
        request.user = this.toAuthUser(session.user);
        void this.prisma.authSession
          .update({ where: { id: session.id }, data: { lastUsedAt: new Date() } })
          .catch(() => undefined);
        return true;
      }

      if (allowBypass && token.startsWith('dev_')) {
        request.user = await this.getOrCreateGuest(token);
        return true;
      }

      if (isRead) {
        // A stale token should not break a public trip page.
        if (requireIdentity) {
          throw new UnauthorizedException('Sign in to open this.');
        }
        request.user = await this.getDemoUser();
        return true;
      }

      throw new UnauthorizedException('Unknown session token. Start a new session.');
    }

    // 2. Guest session header: one identity per browser or app install
    if (guestHeaderRaw) {
      request.user = await this.getOrCreateGuest(guestHeaderRaw);
      return true;
    }

    // 3. No identity supplied
    if (requireIdentity) {
      throw new UnauthorizedException('Sign in to open this.');
    }

    if (isRead) {
      request.user = await this.getDemoUser();
      return true;
    }

    if (allowBypass) {
      // Local development: a header-less write gets its own dev identity rather
      // than silently merging into the shared demo account.
      request.user = await this.getOrCreateGuest('local-dev');
      return true;
    }

    throw new UnauthorizedException(
      `This endpoint needs an identity. Send the ${GUEST_SESSION_HEADER} header.`
    );
  }

  private toAuthUser(user: {
    id: string;
    firebaseUid: string;
    email: string;
  }): AuthenticatedUser {
    return { id: user.id, firebaseUid: user.firebaseUid, email: user.email };
  }

  private normalizeSessionId(raw: string): string {
    return String(raw)
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, '')
      .slice(0, 64);
  }

  /** Finds or creates the account that belongs to one browser or install. */
  private async getOrCreateGuest(
    sessionIdentifier: string
  ): Promise<AuthenticatedUser> {
    const sessionId = this.normalizeSessionId(sessionIdentifier);
    if (!sessionId) {
      throw new UnauthorizedException('Empty session identifier');
    }

    const firebaseUid = `guest:${sessionId}`;
    const email = `guest+${sessionId}@trippin.ai`;

    try {
      let user = await this.prisma.user.findUnique({ where: { firebaseUid } });
      if (!user) {
        user = await this.prisma.user.create({
          data: {
            firebaseUid,
            email,
            profile: {
              create: {
                displayName: 'Traveler',
                photoUrl:
                  'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300'
              }
            },
            preference: {
              create: {
                travelStyles: ['CULTURAL', 'RELAXATION'],
                pace: 'MODERATE',
                foodPreferences: ['LOCAL_CUISINE'],
                transportPreference: 'PUBLIC_TRANSIT'
              }
            }
          }
        });
      }
      return this.toAuthUser(user);
    } catch (error) {
      this.logger.error(
        `Could not resolve guest session ${sessionId}: ${(error as Error).message}`
      );
      // Last resort so a database blip does not take the planner offline.
      return {
        id: '00000000-0000-0000-0000-000000000002',
        firebaseUid,
        email
      };
    }
  }

  /** The seeded demo account, used for public reads so the home feed has content. */
  private async getDemoUser(): Promise<AuthenticatedUser | undefined> {
    try {
      const user = await this.prisma.user.findFirst({
        where: { email: 'traveler@trippin.ai' }
      });
      return user ? this.toAuthUser(user) : undefined;
    } catch {
      return undefined;
    }
  }
}
