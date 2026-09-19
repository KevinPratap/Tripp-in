import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Post,
  UseGuards
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth } from '@nestjs/swagger';
import { FirebaseAuthGuard, AuthenticatedUser } from '../common/guards/firebase-auth.guard';
import { RateLimit } from '../common/guards/rate-limit.guard';
import { CurrentUser } from '../common/decorators/user.decorator';
import { RequireIdentity } from '../common/decorators/require-identity.decorator';
import { AuthService, RequestedMagicLink, SavedTripSummary, VerifiedSession } from './auth.service';
import { RequestMagicLinkDto } from './dto/request-link.dto';
import { VerifyMagicLinkDto } from './dto/verify-link.dto';

/**
 * Passwordless sign in.
 *
 * These two routes are deliberately unguarded: they are how a caller obtains an
 * identity, so they cannot require one. Both are rate limited instead.
 */
@ApiTags('Auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('request-link')
  @RateLimit({ limit: 5, windowMs: 60000 })
  @HttpCode(HttpStatus.ACCEPTED)
  @ApiOperation({
    summary: 'Request a single use magic link',
    description:
      'Stores only the hash of the token. No mail provider is configured, so the link is written to the server log and the response states that plainly.'
  })
  async requestLink(@Body() dto: RequestMagicLinkDto): Promise<RequestedMagicLink> {
    return this.authService.requestMagicLink(dto);
  }

  @Post('verify')
  @RateLimit({ limit: 10, windowMs: 60000 })
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Consume a magic link and receive a session token',
    description:
      'Pass the guest session id the browser was already using to move its trips onto the account being signed into.'
  })
  async verify(@Body() dto: VerifyMagicLinkDto): Promise<VerifiedSession> {
    return this.authService.verifyMagicLink(dto);
  }
}

/** The signed in traveller's own data. Anonymous callers are refused, not answered as the demo account. */
@ApiTags('Me')
@ApiBearerAuth()
@UseGuards(FirebaseAuthGuard)
@RequireIdentity()
@Controller('me')
export class MeController {
  constructor(private readonly authService: AuthService) {}

  @Get('trips')
  @ApiOperation({ summary: 'Every trip the signed in account owns, newest first' })
  async trips(@CurrentUser() user: AuthenticatedUser): Promise<{ trips: SavedTripSummary[] }> {
    const trips = await this.authService.listMyTrips(user.id);
    return { trips };
  }
}
