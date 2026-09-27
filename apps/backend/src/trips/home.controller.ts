import { Controller, Get, UseGuards } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth } from '@nestjs/swagger';
import { TripsService } from './trips.service';
import { FirebaseAuthGuard, AuthenticatedUser } from '../common/guards/firebase-auth.guard';
import { CurrentUser } from '../common/decorators/user.decorator';
import { HomeFeedResponse } from '@trippin/api-contracts';

@ApiTags('Home')
@ApiBearerAuth()
@UseGuards(FirebaseAuthGuard)
@Controller('home')
export class HomeController {
  constructor(private readonly tripsService: TripsService) {}

  @Get()
  @ApiOperation({
    summary: 'Get unified Home Feed payload',
    description: 'Delivers user profile, recent trips, recommended & popular destinations in a single fast round-trip.'
  })
  async getHome(@CurrentUser() user: AuthenticatedUser): Promise<HomeFeedResponse> {
    return this.tripsService.getHomeFeed(user.id);
  }
}
