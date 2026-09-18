import { Module } from '@nestjs/common';
import { TripsService } from './trips.service';
import { TripsController } from './trips.controller';
import { HomeController } from './home.controller';
import { PrismaService } from '../common/prisma/prisma.service';
import { AIModule } from '../ai/ai.module';
import { ItinerariesModule } from '../itineraries/itineraries.module';
import { DestinationsModule } from '../destinations/destinations.module';
import { ConfigService } from '@nestjs/config';

@Module({
  imports: [AIModule, ItinerariesModule, DestinationsModule],
  controllers: [TripsController, HomeController],
  providers: [TripsService, PrismaService, ConfigService],
  exports: [TripsService]
})
export class TripsModule {}
