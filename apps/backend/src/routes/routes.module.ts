import { Module } from '@nestjs/common';
import { RouteService } from './routes.service';
import { GoogleRouteProvider } from './google-routes.provider';
import { OSRMRouteProvider } from './osrm-routes.provider';
import { MockRouteProvider } from './mock-routes.provider';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';

@Module({
  providers: [
    RouteService,
    GoogleRouteProvider,
    OSRMRouteProvider,
    MockRouteProvider,
    PrismaService,
    RedisService
  ],
  exports: [RouteService]
})
export class RoutesModule {}
