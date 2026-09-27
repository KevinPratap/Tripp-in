import { Module } from '@nestjs/common';
import { PlaceService } from './places.service';
import { PlacesController } from './places.controller';
import { GooglePlacesProvider } from './google-places.provider';
import { OSMPlacesProvider } from './osm-places.provider';
import { PhotonProvider } from './photon.provider';
import { MockPlaceProvider } from './mock-places.provider';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';

@Module({
  controllers: [PlacesController],
  providers: [
    PlaceService,
    GooglePlacesProvider,
    OSMPlacesProvider,
    PhotonProvider,
    MockPlaceProvider,
    PrismaService,
    RedisService
  ],
  exports: [PlaceService, OSMPlacesProvider, PhotonProvider]
})
export class PlacesModule {}
