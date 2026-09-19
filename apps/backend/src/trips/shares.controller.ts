import {
  Controller,
  Delete,
  Get,
  Param,
  Post,
  UseGuards
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth } from '@nestjs/swagger';
import { TripDetailsResponse } from '@trippin/api-contracts';
import { FirebaseAuthGuard, AuthenticatedUser } from '../common/guards/firebase-auth.guard';
import { RateLimit } from '../common/guards/rate-limit.guard';
import { CurrentUser } from '../common/decorators/user.decorator';
import { SharesService, ShareLink } from './shares.service';

/** Owner facing: create and revoke a public link for one of their trips. */
@ApiTags('Shares')
@ApiBearerAuth()
@UseGuards(FirebaseAuthGuard)
@Controller('trips')
export class TripSharesController {
  constructor(private readonly sharesService: SharesService) {}

  @Post(':id/share')
  @RateLimit({ limit: 10, windowMs: 60000 })
  @ApiOperation({ summary: 'Create a public share link for a trip, or return the existing one' })
  async create(
    @Param('id') id: string,
    @CurrentUser() user: AuthenticatedUser
  ): Promise<ShareLink> {
    return this.sharesService.createShare(id, user.id);
  }

  @Delete(':id/share')
  @RateLimit({ limit: 10, windowMs: 60000 })
  @ApiOperation({ summary: 'Revoke every live share link for a trip' })
  async revoke(
    @Param('id') id: string,
    @CurrentUser() user: AuthenticatedUser
  ): Promise<{ revoked: number }> {
    return this.sharesService.revokeShares(id, user.id);
  }
}

/**
 * Public: anyone holding the token can read that one trip.
 *
 * No auth guard here on purpose. The token is the credential, and the response is the
 * same envelope the trip page already consumes.
 */
@ApiTags('Shares')
@Controller('t')
export class PublicShareController {
  constructor(private readonly sharesService: SharesService) {}

  @Get(':token')
  @RateLimit({ limit: 60, windowMs: 60000 })
  @ApiOperation({ summary: 'Resolve a public share token to the trip envelope' })
  async resolve(@Param('token') token: string): Promise<TripDetailsResponse> {
    return this.sharesService.resolveShare(token);
  }
}
