import { BadRequestException, Controller, Get, Query } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { RateLimit } from '../common/guards/rate-limit.guard';
import { NearbyQueryDto, RouteQueryDto } from './dto/explore-query.dto';
import { ExploreService } from './explore.service';
import { parsePoints } from './explore.logic';

@ApiTags('Explore')
@Controller('explore')
export class ExploreController {
  constructor(private readonly explore: ExploreService) {}

  /** Named cafes, food, sights and parks around a point, from OpenStreetMap, nearest first. */
  @Get('nearby')
  @RateLimit({ limit: 40, windowMs: 60000 })
  @ApiOperation({ summary: 'Things to do within walking distance of a point' })
  async nearby(@Query() query: NearbyQueryDto) {
    const places = await this.explore.nearby(
      { latitude: query.lat, longitude: query.lng },
      query.radius ?? 800
    );
    return { places };
  }

  /** The walking route between a day's stops, and the named places along it. */
  @Get('route')
  @RateLimit({ limit: 40, windowMs: 60000 })
  @ApiOperation({ summary: 'Walking legs between stops and the places beside them' })
  async route(@Query() query: RouteQueryDto) {
    const stops = parsePoints(query.points);
    if (!stops) throw new BadRequestException('points must be real coordinates');
    return this.explore.route(stops);
  }
}
