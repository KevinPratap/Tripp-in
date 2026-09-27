import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AIPlannerService } from './ai-planner.service';
import { PlacesModule } from '../places/places.module';
import { RoutesModule } from '../routes/routes.module';
import { WeatherModule } from '../weather/weather.module';
import { EngineModule } from '../engine/engine.module';

@Module({
  imports: [ConfigModule, PlacesModule, RoutesModule, WeatherModule, EngineModule],
  providers: [AIPlannerService],
  exports: [AIPlannerService]
})
export class AIModule {}
