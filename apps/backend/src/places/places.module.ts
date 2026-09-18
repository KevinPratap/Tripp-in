import { Module } from '@nestjs/common';
import { PlaceService } from './places.service';
import { PlacesController } from './places.controller';
import { GooglePlacesProvider } from './google-places.provider';
import { MockPlaceProvider } from './mock-places.provider';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';

@Module({
  controllers: [PlacesController],
  providers: [
    PlaceService,
    GooglePlacesProvider,
    MockPlaceProvider,
    PrismaService,
    RedisService
  ],
  exports: [PlaceService]
})
export class PlacesModule {}
