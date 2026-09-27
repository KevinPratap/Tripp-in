import { Injectable, Logger } from '@nestjs/common';
import { WeatherDayForecast, WeatherCondition } from '@trippin/shared-types';

@Injectable()
export class WeatherService {
  private readonly logger = new Logger(WeatherService.name);
  private readonly cache = new Map<string, { data: WeatherDayForecast[]; expiresAt: number }>();
  private readonly CACHE_TTL_MS = 60 * 60 * 1000; // 1-hour in-memory cache

  /**
   * Main forecast method called by AIPlannerService: takes destination name or coordinates.
   */
  async getForecast(destination: string, days = 5): Promise<WeatherDayForecast[]> {
    try {
      // 1. Geocode destination using free OpenStreetMap Photon geocoder
      const coords = await this.geocodeDestination(destination);
      return await this.getForecastByCoords(coords.lat, coords.lng, days);
    } catch (error: any) {
      this.logger.warn(`Could not geocode destination "${destination}" for weather: ${error.message}`);
      return this.getFallbackForecast(days);
    }
  }

  /**
   * Fetches real-time daily forecast from Open-Meteo API (100% Free, no API key).
   */
  async getForecastByCoords(lat: number, lng: number, days = 7): Promise<WeatherDayForecast[]> {
    const cacheKey = `${lat.toFixed(2)},${lng.toFixed(2)},${days}`;
    const cached = this.cache.get(cacheKey);

    if (cached && cached.expiresAt > Date.now()) {
      return cached.data;
    }

    try {
      const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lng}&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max&timezone=auto`;
      const res = await fetch(url);
      if (!res.ok) {
        throw new Error(`Open-Meteo HTTP ${res.status}`);
      }

      const json = await res.json();
      const daily = json?.daily;

      if (!daily || !daily.time) {
        return this.getFallbackForecast(days);
      }

      const forecasts: WeatherDayForecast[] = daily.time.slice(0, days).map((date: string, i: number) => {
        const code = daily.weather_code[i] ?? 0;
        const precipProb = daily.precipitation_probability_max[i] ?? 0;
        const tMax = Math.round(daily.temperature_2m_max[i] ?? 22);
        const tMin = Math.round(daily.temperature_2m_min[i] ?? 15);
        const condition = this.mapWmoToCondition(code);

        let advisoryNote: string | undefined;
        if (precipProb >= 60 || condition === 'RAIN') {
          advisoryNote = 'Rain alert: Plan indoor activities or pack an umbrella';
        } else if (condition === 'THUNDERSTORM') {
          advisoryNote = 'Severe weather warning: Postpone outdoor exploration';
        }

        return {
          date,
          temperatureCelsius: Math.round((tMax + tMin) / 2),
          temperatureMinCelsius: tMin,
          temperatureMaxCelsius: tMax,
          condition,
          precipitationProbability: precipProb,
          windSpeedKmh: Math.round(daily.wind_speed_10m_max[i] ?? 10),
          iconCode: this.getIconCode(condition),
          advisoryNote,
        };
      });

      this.cache.set(cacheKey, { data: forecasts, expiresAt: Date.now() + this.CACHE_TTL_MS });
      return forecasts;
    } catch (err: any) {
      this.logger.warn(`Open-Meteo request failed for (${lat}, ${lng}): ${err.message}`);
      return this.getFallbackForecast(days);
    }
  }

  private async geocodeDestination(destination: string): Promise<{ lat: number; lng: number }> {
    const encoded = encodeURIComponent(destination.trim());
    const res = await fetch(`https://photon.komoot.io/api/?q=${encoded}&limit=1`, {
      headers: { 'User-Agent': 'TrippinAI/1.0' },
    });
    if (!res.ok) throw new Error(`Photon HTTP ${res.status}`);
    const data = await res.json();
    const first = data?.features?.[0];
    if (!first?.geometry?.coordinates) {
      // Default to Tokyo coordinates if unresolved
      return { lat: 35.6762, lng: 139.6503 };
    }
    const [lng, lat] = first.geometry.coordinates;
    return { lat, lng };
  }

  private mapWmoToCondition(code: number): WeatherCondition {
    if (code === 0) return 'SUNNY';
    if (code === 1 || code === 2) return 'PARTLY_CLOUDY';
    if (code === 3) return 'CLOUDY';
    if (code >= 51 && code <= 67) return 'RAIN';
    if (code >= 71 && code <= 77) return 'SNOW';
    if (code >= 80 && code <= 82) return 'HEAVY_RAIN';
    if (code >= 95) return 'THUNDERSTORM';
    return 'CLOUDY';
  }

  private getIconCode(condition: WeatherCondition): string {
    switch (condition) {
      case 'SUNNY': return '01d';
      case 'PARTLY_CLOUDY': return '02d';
      case 'CLOUDY': return '03d';
      case 'RAIN': return '10d';
      case 'HEAVY_RAIN': return '09d';
      case 'SNOW': return '13d';
      case 'THUNDERSTORM': return '11d';
      case 'WINDY': return '50d';
      default: return '02d';
    }
  }

  private getFallbackForecast(days: number): WeatherDayForecast[] {
    const today = new Date();
    return Array.from({ length: days }, (_, i) => {
      const d = new Date(today);
      d.setDate(today.getDate() + i);
      return {
        date: d.toISOString().split('T')[0],
        temperatureCelsius: 22,
        temperatureMinCelsius: 16,
        temperatureMaxCelsius: 26,
        condition: 'SUNNY',
        precipitationProbability: 10,
        windSpeedKmh: 12,
        iconCode: '01d',
      };
    });
  }
}
