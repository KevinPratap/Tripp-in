import { Controller, Get, Post, Body, Param, UseGuards, HttpStatus, HttpCode } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth } from '@nestjs/swagger';
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

  @Get(':id')
  @ApiOperation({ summary: 'Get full trip details with verified itinerary' })
  async getDetails(@Param('id') id: string): Promise<TripDetailsResponse> {
    return this.tripsService.getTripDetails(id);
  }
}
