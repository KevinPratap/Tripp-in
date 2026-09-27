import { Module } from '@nestjs/common';
import { ItineraryValidator } from './itinerary-validator';
import { PlacesModule } from '../places/places.module';
import { RoutesModule } from '../routes/routes.module';

@Module({
  imports: [PlacesModule, RoutesModule],
  providers: [ItineraryValidator],
  exports: [ItineraryValidator]
})
export class EngineModule {}
