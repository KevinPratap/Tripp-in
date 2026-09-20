import {
  Controller,
  Get,
  Post,
  Patch,
  Delete,
  Body,
  Param,
  HttpCode,
  HttpStatus,
  UseGuards
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse, ApiBearerAuth } from '@nestjs/swagger';
import { TravellersService } from './travellers.service';
import {
  CreateTravellerDto,
  UpdateTravellerDto,
  JoinTripDto
} from './dto/traveller.dto';
import { TravellerDto } from '@trippin/shared-types';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { RateLimit } from '../common/guards/rate-limit.guard';

@ApiTags('Trips Travellers & Group Collaboration')
@Controller('trips')
export class TravellersController {
  constructor(private readonly travellersService: TravellersService) {}

  @Get(':id/travellers')
  @ApiOperation({ summary: 'Get all registered travellers on a trip with budget caps and interests' })
  @ApiResponse({ status: 200, description: 'List of travellers' })
  async getTravellers(@Param('id') tripId: string): Promise<TravellerDto[]> {
    return this.travellersService.getTravellers(tripId);
  }

  @Post(':id/travellers')
  @RateLimit({ limit: 30, windowMs: 60000 })
  @ApiBearerAuth()
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Add a new traveller to a trip' })
  @ApiResponse({ status: 201, description: 'Created traveller details' })
  async addTraveller(
    @Param('id') tripId: string,
    @Body() dto: CreateTravellerDto
  ): Promise<TravellerDto> {
    return this.travellersService.addTraveller(tripId, dto);
  }

  @Patch(':id/travellers/:tid')
  @RateLimit({ limit: 30, windowMs: 60000 })
  @ApiBearerAuth()
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Update traveller preferences, budget cap, or pace' })
  @ApiResponse({ status: 200, description: 'Updated traveller details' })
  async updateTraveller(
    @Param('id') tripId: string,
    @Param('tid') travellerId: string,
    @Body() dto: UpdateTravellerDto
  ): Promise<TravellerDto> {
    return this.travellersService.updateTraveller(tripId, travellerId, dto);
  }

  @Delete(':id/travellers/:tid')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiBearerAuth()
  @UseGuards(FirebaseAuthGuard)
  @ApiOperation({ summary: 'Remove a traveller from a trip' })
  @ApiResponse({ status: 204, description: 'Traveller removed' })
  async deleteTraveller(
    @Param('id') tripId: string,
    @Param('tid') travellerId: string
  ): Promise<void> {
    await this.travellersService.deleteTraveller(tripId, travellerId);
  }

  @Post('join')
  @RateLimit({ limit: 20, windowMs: 60000 })
  @ApiOperation({ summary: 'Join a trip by invite or share token' })
  @ApiResponse({ status: 201, description: 'Joined trip id and traveller profile' })
  async joinTrip(
    @Body() dto: JoinTripDto
  ): Promise<{ tripId: string; traveller: TravellerDto }> {
    return this.travellersService.joinTrip(dto);
  }
}
