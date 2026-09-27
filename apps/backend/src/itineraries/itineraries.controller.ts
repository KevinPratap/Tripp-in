import { Controller, Post, Get, Body, Param, UseGuards } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiBearerAuth } from '@nestjs/swagger';
import { ItinerariesService } from './itineraries.service';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { RateLimit } from '../common/guards/rate-limit.guard';
import { ModifyItineraryRequestDto, ModifyItineraryResponse } from '@trippin/api-contracts';

@ApiTags('Itineraries')
@ApiBearerAuth()
@UseGuards(FirebaseAuthGuard)
@Controller('itineraries')
export class ItinerariesController {
  constructor(private readonly itinerariesService: ItinerariesService) {}

  @Post(':id/modify')
  @RateLimit({ limit: 6, windowMs: 60000 })
  @ApiOperation({
    summary: 'Conversationally modify an itinerary (e.g. "Day 2 is too busy")',
    description: 'Triggers AI re-planning on affected activities and produces a new verified version (vN+1)'
  })
  async modify(
    @Param('id') id: string,
    @Body() dto: ModifyItineraryRequestDto
  ): Promise<ModifyItineraryResponse> {
    const result = await this.itinerariesService.modifyItinerary(id, dto.instruction);
    return {
      itineraryId: id,
      newVersion: result.newVersion,
      appliedChangesSummary: result.appliedChangesSummary,
      updatedItinerary: result.updatedItinerary
    };
  }
}
