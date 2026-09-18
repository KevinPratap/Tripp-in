import { Injectable, Logger } from '@nestjs/common';
import { WeatherDayForecast, WeatherCondition } from '@trippin/shared-types';
import { RedisService } from '../common/redis/redis.service';

@Injectable()
export class WeatherService {
  private readonly logger = new Logger(WeatherService.name);

  constructor(private readonly redis: RedisService) {}

  async getForecast(destination: string, daysCount = 5): Promise<WeatherDayForecast[]> {
    const cacheKey = `weather:forecast:${destination.toLowerCase()}:${daysCount}`;
    const cached = await this.redis.get<WeatherDayForecast[]>(cacheKey);
    if (cached) return cached;

    // Deterministic mock forecast generator for destinations
    const forecast: WeatherDayForecast[] = [];
    const today = new Date();

    for (let i = 0; i < daysCount; i++) {
      const d = new Date(today);
      d.setDate(today.getDate() + i);
      const dateStr = d.toISOString().split('T')[0];

      // Realistic variation
      const condition: WeatherCondition =
        i === 1 && destination.toLowerCase().includes('london')
          ? 'RAIN'
          : i === 2
          ? 'PARTLY_CLOUDY'
          : 'SUNNY';

      forecast.push({
        date: dateStr,
        temperatureCelsius: 19 + (i % 3),
        temperatureMinCelsius: 14 + (i % 2),
        temperatureMaxCelsius: 23 + (i % 3),
        condition,
        precipitationProbability: condition === 'RAIN' ? 75 : 10,
        windSpeedKmh: 12.0 + i,
        advisoryNote:
          condition === 'RAIN'
            ? 'Rain expected: Recommend indoor museums & covered galleries'
            : 'Mild conditions, great for walking and outdoor landmarks'
      });
    }

    await this.redis.set(cacheKey, forecast, 3600 * 6); // 6 hours cache
    return forecast;
  }
}
