import {
  Injectable,
  CanActivate,
  ExecutionContext,
  UnauthorizedException,
  Logger
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaService } from '../prisma/prisma.service';

export interface AuthenticatedUser {
  id: string;
  firebaseUid: string;
  email: string;
}

@Injectable()
export class FirebaseAuthGuard implements CanActivate {
  private readonly logger = new Logger(FirebaseAuthGuard.name);

  constructor(
    private readonly config: ConfigService,
    private readonly prisma: PrismaService
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest();
    const authHeader = request.headers.authorization;

    // Check development bypass flag
    const allowBypass = this.config.get<string>('AUTH_BYPASS_DEV') === 'true';

    if (!authHeader) {
      if (allowBypass) {
        // Find or create default demo user
        const demoUser = await this.getOrCreateDemoUser();
        request.user = demoUser;
        return true;
      }
      throw new UnauthorizedException('Missing Authorization header');
    }

    const [bearer, token] = authHeader.split(' ');
    if (bearer !== 'Bearer' || !token) {
      throw new UnauthorizedException('Invalid Authorization format. Expected Bearer <token>');
    }

    if (allowBypass && token.startsWith('dev_')) {
      const demoUser = await this.getOrCreateDemoUser(token);
      request.user = demoUser;
      return true;
    }

    try {
      // In production: Verify token with firebase-admin
      // const decodedToken = await admin.auth().verifyIdToken(token);
      // const user = await this.syncUser(decodedToken.uid, decodedToken.email);
      // request.user = user;
      // return true;

      // Fallback/Simulated verification
      const user = await this.getOrCreateDemoUser(token);
      request.user = user;
      return true;
    } catch (error) {
      this.logger.error(`Token verification failed: ${(error as Error).message}`);
      throw new UnauthorizedException('Invalid or expired authentication token');
    }
  }

  private async getOrCreateDemoUser(tokenIdentifier = 'demo-user'): Promise<AuthenticatedUser> {
    try {
      let user = await this.prisma.user.findFirst({
        where: { email: 'traveler@trippin.ai' }
      });

      if (!user) {
        user = await this.prisma.user.create({
          data: {
            firebaseUid: `uid-${tokenIdentifier}`,
            email: 'traveler@trippin.ai',
            profile: {
              create: {
                displayName: 'Alex Rivers',
                photoUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300'
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

      return {
        id: user.id,
        firebaseUid: user.firebaseUid,
        email: user.email
      };
    } catch {
      // Return synthetic user if database is still starting up
      return {
        id: '00000000-0000-0000-0000-000000000001',
        firebaseUid: 'demo-firebase-uid-001',
        email: 'traveler@trippin.ai'
      };
    }
  }
}
