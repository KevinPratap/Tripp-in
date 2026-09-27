import { Module } from '@nestjs/common';
import { WeatherService } from './weather.service';

/**
 * WeatherService is used by the planner while it builds an itinerary. There is no public
 * weather route: the web reads Open-Meteo directly and no client ever called the route that
 * used to live here, so it was removed rather than left wired to nothing.
 */
@Module({
  providers: [WeatherService],
  exports: [WeatherService],
})
export class WeatherModule {}
