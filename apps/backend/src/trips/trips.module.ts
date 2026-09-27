import { Module } from '@nestjs/common';
import { TripsService } from './trips.service';
import { TripsController } from './trips.controller';
import { HomeController } from './home.controller';
import { PrismaService } from '../common/prisma/prisma.service';
import { AIModule } from '../ai/ai.module';
import { ItinerariesModule } from '../itineraries/itineraries.module';
import { DestinationsModule } from '../destinations/destinations.module';
import { EngineModule } from '../engine/engine.module';
import { PlacesModule } from '../places/places.module';
import { ConfigService } from '@nestjs/config';

import { CollabController } from './collab.controller';
import { CollabService } from './collab.service';
import { TravellersController } from './travellers.controller';
import { TravellersService } from './travellers.service';
import { RedisService } from '../common/redis/redis.service';

@Module({
  imports: [AIModule, ItinerariesModule, DestinationsModule, EngineModule, PlacesModule],
  controllers: [TripsController, HomeController, CollabController, TravellersController],
  providers: [TripsService, CollabService, TravellersService, PrismaService, RedisService, ConfigService],
  exports: [TripsService, CollabService, TravellersService]
})
export class TripsModule {}
