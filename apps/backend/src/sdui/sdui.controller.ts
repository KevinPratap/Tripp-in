import { Controller, Get, Param, UseGuards } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth } from '@nestjs/swagger';
import { SDUIService } from './sdui.service';
import { FirebaseAuthGuard, AuthenticatedUser } from '../common/guards/firebase-auth.guard';
import { CurrentUser } from '../common/decorators/user.decorator';
import { SDUIScreenResponse } from '@trippin/api-contracts';

@ApiTags('SDUI')
@ApiBearerAuth()
@UseGuards(FirebaseAuthGuard)
@Controller('sdui')
export class SDUIController {
  constructor(private readonly sduiService: SDUIService) {}

  @Get('screens/home')
  @ApiOperation({
    summary: 'Fetch dynamic Server-Driven UI (SDUI) layout for Home Screen',
    description: 'Delivers dynamic widget tree rendered natively by Android Jetpack Compose or Web'
  })
  async getHomeScreen(@CurrentUser() user: AuthenticatedUser): Promise<SDUIScreenResponse> {
    return this.sduiService.getHomeScreen(user.id);
  }

  @Get('screens/itinerary/:tripId')
  @ApiOperation({
    summary: 'Fetch dynamic Server-Driven UI (SDUI) layout for an Itinerary Screen'
  })
  async getItineraryScreen(@Param('tripId') tripId: string): Promise<SDUIScreenResponse> {
    return this.sduiService.getItineraryScreen(tripId);
  }
}
