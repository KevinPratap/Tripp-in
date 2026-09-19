import { Controller, Get, Post, Body, Param, Query, UseGuards, HttpStatus, HttpCode } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth, ApiQuery } from '@nestjs/swagger';
import { TripsService } from './trips.service';
import { FirebaseAuthGuard, AuthenticatedUser } from '../common/guards/firebase-auth.guard';
import { RateLimit } from '../common/guards/rate-limit.guard';
import { CurrentUser } from '../common/decorators/user.decorator';
import {
  CreateTripResponse,
  GenerateTripResponse,
  TripDetailsResponse,
  TripGenerationStatusResponse
} from '@trippin/api-contracts';
import { CreateTripRequestDto } from './dto/create-trip.dto';
import { ReplanTripDto } from './dto/replan-trip.dto';

@ApiTags('Trips')
@ApiBearerAuth()
@UseGuards(FirebaseAuthGuard)
@Controller('trips')
export class TripsController {
  constructor(private readonly tripsService: TripsService) {}

  @Post()
  @RateLimit({ limit: 10, windowMs: 60000 })
  @ApiOperation({ summary: 'Create a new trip in DRAFT status' })
  async create(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: CreateTripRequestDto
  ): Promise<CreateTripResponse> {
    return this.tripsService.createTrip(user.id, dto);
  }

  @Post(':id/generate')
  @RateLimit({ limit: 5, windowMs: 60000 })
  @HttpCode(HttpStatus.ACCEPTED)
  @ApiOperation({
    summary: 'Trigger asynchronous AI itinerary generation job',
    description: 'Enqueues generation task and returns 202 Accepted immediately'
  })
  async generate(@Param('id') id: string): Promise<GenerateTripResponse> {
    const { jobId } = await this.tripsService.triggerGeneration(id);
    return {
      tripId: id,
      jobId,
      status: 'GENERATING',
      estimatedSeconds: 8
    };
  }

  @Get(':id/status')
  @ApiOperation({ summary: 'Poll generation job progress and current status' })
  async getStatus(@Param('id') id: string): Promise<TripGenerationStatusResponse> {
    return this.tripsService.getGenerationStatus(id);
  }

  @Get(':id/versions')
  @ApiOperation({ summary: 'List all historical and current itinerary versions for a trip' })
  async getVersions(@Param('id') id: string) {
    return this.tripsService.getTripVersions(id);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Get full trip details with verified itinerary' })
  @ApiQuery({ name: 'version', required: false, description: 'Optional specific itinerary version to retrieve' })
  async getDetails(
    @Param('id') id: string,
    @Query('version') version?: string
  ): Promise<TripDetailsResponse> {
    const verNum = version ? parseInt(version, 10) : undefined;
    return this.tripsService.getTripDetails(id, Number.isNaN(verNum) ? undefined : verNum);
  }

  @Post(':id/replan')
  @RateLimit({ limit: 6, windowMs: 60000 })
  @ApiOperation({
    summary: 'One-tap replan an existing itinerary',
    description: 'Accepts intent triggers (running-late, rain, tired, swap-activity, add-stop, budget-cut) and returns a newly verified version'
  })
  async replan(
    @Param('id') id: string,
    @Body() dto: ReplanTripDto
  ) {
    return this.tripsService.replanTrip(id, dto);
  }

  @Post(':id/lock')
  @ApiOperation({ summary: 'Lock trip itinerary to finalize schedule and block further edits' })
  async lock(@Param('id') id: string) {
    return this.tripsService.lockTrip(id);
  }

  @Post(':id/unlock')
  @ApiOperation({ summary: 'Unlock trip itinerary to re-allow modifications and replanning' })
  async unlock(@Param('id') id: string) {
    return this.tripsService.unlockTrip(id);
  }
}
