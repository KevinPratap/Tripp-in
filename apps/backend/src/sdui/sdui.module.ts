import { Module } from '@nestjs/common';
import { SDUIService } from './sdui.service';
import { SDUIController } from './sdui.controller';
import { TripsModule } from '../trips/trips.module';
import { DestinationsModule } from '../destinations/destinations.module';
import { ItinerariesModule } from '../itineraries/itineraries.module';
import { PlacesModule } from '../places/places.module';
import { PrismaService } from '../common/prisma/prisma.service';
import { ConfigService } from '@nestjs/config';

@Module({
  imports: [TripsModule, DestinationsModule, ItinerariesModule, PlacesModule],
  controllers: [SDUIController],
  providers: [SDUIService, PrismaService, ConfigService],
  exports: [SDUIService]
})
export class SDUIModule {}
