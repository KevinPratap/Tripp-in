import { Module } from '@nestjs/common';
import { WeatherService } from './weather.service';
import { RedisService } from '../common/redis/redis.service';

@Module({
  providers: [WeatherService, RedisService],
  exports: [WeatherService]
})
export class WeatherModule {}
