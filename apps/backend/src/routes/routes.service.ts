import { Injectable, Logger } from '@nestjs/common';
import { GoogleRouteProvider } from './google-routes.provider';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';
import { GeoLocation, RouteMode, RouteSegment } from '@trippin/shared-types';

@Injectable()
export class RouteService {
  private readonly logger = new Logger(RouteService.name);

  constructor(
    private readonly provider: GoogleRouteProvider,
    private readonly prisma: PrismaService,
    private readonly redis: RedisService
  ) {}

  async getRoute(
    origin: GeoLocation,
    destination: GeoLocation,
    mode: RouteMode = 'TRANSIT'
  ): Promise<RouteSegment> {
    const originKey = `${origin.latitude.toFixed(4)},${origin.longitude.toFixed(4)}`;
    const destKey = `${destination.latitude.toFixed(4)},${destination.longitude.toFixed(4)}`;
    const cacheKey = `route:${originKey}:${destKey}:${mode}`;

    const cached = await this.redis.get<RouteSegment>(cacheKey);
    if (cached) return cached;

    const route = await this.provider.calculateRoute(origin, destination, mode);
    await this.redis.set(cacheKey, route, 86400 * 7); // 7 days cache

    return route;
  }

  async estimateTravelTimeMinutes(
    origin: GeoLocation,
    destination: GeoLocation,
    mode: RouteMode = 'TRANSIT'
  ): Promise<number> {
    const route = await this.getRoute(origin, destination, mode);
    return route.durationMinutes;
  }
}
