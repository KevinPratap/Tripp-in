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

    // 1. Try Live Open-Meteo API (100% Free, No API Key Required)
    try {
      const liveForecast = await this.fetchOpenMeteoForecast(destination, daysCount);
      if (liveForecast && liveForecast.length > 0) {
        await this.redis.set(cacheKey, liveForecast, 3600 * 6); // 6 hours cache
        return liveForecast;
      }
    } catch (err) {
      this.logger.debug(`Open-Meteo live fetch failed for ${destination}: ${(err as Error).message}. Falling back to deterministic model.`);
    }

    // 2. Deterministic mock forecast generator for destinations (Offline fallback)
    const forecast: WeatherDayForecast[] = [];
    const today = new Date();

    for (let i = 0; i < daysCount; i++) {
      const d = new Date(today);
      d.setDate(today.getDate() + i);
      const dateStr = d.toISOString().split('T')[0];

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

  private async fetchOpenMeteoForecast(destination: string, daysCount: number): Promise<WeatherDayForecast[] | null> {
    // Step 1: Free Open-Meteo geocoding
    const geoUrl = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(destination)}&count=1&language=en&format=json`;
    const geoRes = await fetch(geoUrl, { signal: AbortSignal.timeout(3000) });
    if (!geoRes.ok) return null;

    const geoData = await geoRes.json();
    const match = geoData.results?.[0];
    if (!match) return null;

    const { latitude, longitude } = match;

    // Step 2: Free Open-Meteo daily weather forecast
    const forecastUrl = `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max,windspeed_10m_max&timezone=auto`;
    const forecastRes = await fetch(forecastUrl, { signal: AbortSignal.timeout(4000) });
    if (!forecastRes.ok) return null;

    const data = await forecastRes.json();
    const daily = data.daily;
    if (!daily || !daily.time) return null;

    const result: WeatherDayForecast[] = [];
    const count = Math.min(daysCount, daily.time.length);

    for (let i = 0; i < count; i++) {
      const code = daily.weathercode[i] || 0;
      const condition = this.mapWmoCodeToCondition(code);
      const precipProb = daily.precipitation_probability_max?.[i] ?? (condition === 'RAIN' ? 70 : 10);
      const tMax = Math.round(daily.temperature_2m_max?.[i] ?? 20);
      const tMin = Math.round(daily.temperature_2m_min?.[i] ?? 12);
      const tAvg = Math.round((tMax + tMin) / 2);

      result.push({
        date: daily.time[i],
        temperatureCelsius: tAvg,
        temperatureMinCelsius: tMin,
        temperatureMaxCelsius: tMax,
        condition,
        precipitationProbability: precipProb,
        windSpeedKmh: Math.round(daily.windspeed_10m_max?.[i] ?? 15),
        advisoryNote:
          condition === 'RAIN'
            ? 'High rain probability: indoor attractions recommended'
            : condition === 'SNOW'
            ? 'Snow conditions: dress warm and check transit delays'
            : 'Pleasant weather for walking and sightseeing'
      });
    }

    return result;
  }

  private mapWmoCodeToCondition(code: number): WeatherCondition {
    if (code === 0) return 'SUNNY';
    if ([1, 2].includes(code)) return 'PARTLY_CLOUDY';
    if ([3, 45, 48].includes(code)) return 'CLOUDY';
    if ([51, 53, 55, 61, 63, 65, 80, 81, 82].includes(code)) return 'RAIN';
    if ([71, 73, 75, 77, 85, 86].includes(code)) return 'SNOW';
    if ([95, 96, 99].includes(code)) return 'THUNDERSTORM';
    return 'SUNNY';
  }
}
