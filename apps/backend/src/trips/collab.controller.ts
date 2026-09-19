import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Res,
  Header,
  HttpStatus,
  UseGuards,
  BadRequestException
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse, ApiBearerAuth } from '@nestjs/swagger';
import { IsIn, IsOptional, IsString, MinLength } from 'class-validator';
import { Response } from 'express';
import { CollabService, TripCollabResponse } from './collab.service';
import { FirebaseAuthGuard } from '../common/guards/firebase-auth.guard';
import { RateLimit } from '../common/guards/rate-limit.guard';

/**
 * Decorated on purpose: the global ValidationPipe runs with whitelist: true, so a
 * plain class here would have every field stripped and voting would always fail.
 */
export class VoteActivityDto {
  @IsString()
  @MinLength(8)
  activityId!: string;

  @IsOptional()
  @IsString()
  voterName?: string;

  @IsIn([1, -1])
  vote!: number;

  @IsOptional()
  @IsString()
  comment?: string;
}

@ApiTags('Trips Collaboration & Calendar')
@ApiBearerAuth()
@UseGuards(FirebaseAuthGuard)
@Controller('trips')
export class CollabController {
  constructor(private readonly collabService: CollabService) {}

  @Get(':id/collab')
  @ApiOperation({ summary: 'Retrieve activity votes, tallies, and swap comments for a trip' })
  @ApiResponse({ status: 200, description: 'Current collaboration vote and comment state' })
  async getCollab(@Param('id') tripId: string): Promise<TripCollabResponse> {
    return this.collabService.getCollabData(tripId);
  }

  @Post(':id/vote')
  @RateLimit({ limit: 30, windowMs: 60000 })
  @ApiOperation({ summary: 'Submit an upvote/downvote and optional swap comment on an activity' })
  @ApiResponse({ status: 201, description: 'Updated collaboration vote state' })
  async voteActivity(
    @Param('id') tripId: string,
    @Body() dto: VoteActivityDto
  ): Promise<TripCollabResponse> {
    if (!dto?.activityId || typeof dto.activityId !== 'string' || dto.activityId.length < 8) {
      throw new BadRequestException('activityId is required and must be a real activity id.');
    }
    if (dto.vote !== 1 && dto.vote !== -1) {
      throw new BadRequestException('vote must be 1 (up) or -1 (down).');
    }

    const voter = dto.voterName?.trim() || 'Companion';
    return this.collabService.vote(
      tripId,
      dto.activityId,
      voter,
      dto.vote,
      dto.comment
    );
  }

  @Get(':id/calendar.ics')
  @ApiOperation({ summary: 'Export verified trip schedule as RFC-5545 iCalendar (.ics) file' })
  @Header('Content-Type', 'text/calendar; charset=utf-8')
  async getCalendarIcs(
    @Param('id') tripId: string,
    @Res() res: Response
  ): Promise<void> {
    const icsContent = await this.collabService.generateIcs(tripId);
    res.setHeader('Content-Type', 'text/calendar; charset=utf-8');
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="trippin-schedule-${tripId.substring(0, 8)}.ics"`
    );
    res.status(HttpStatus.OK).send(icsContent);
  }
}
