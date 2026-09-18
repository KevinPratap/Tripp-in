import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Res,
  Header,
  HttpStatus
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { Response } from 'express';
import { CollabService, TripCollabResponse } from './collab.service';

export class VoteActivityDto {
  activityId!: string;
  voterName?: string;
  vote!: number; // 1 or -1
  comment?: string;
}

@ApiTags('Trips Collaboration & Calendar')
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
  @ApiOperation({ summary: 'Submit an upvote/downvote and optional swap comment on an activity' })
  @ApiResponse({ status: 201, description: 'Updated collaboration vote state' })
  async voteActivity(
    @Param('id') tripId: string,
    @Body() dto: VoteActivityDto
  ): Promise<TripCollabResponse> {
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
