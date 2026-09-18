import { WeatherDayForecast, GeoLocation } from '@trippin/shared-types';

export interface WeatherProvider {
  getForecast(destination: string, location?: GeoLocation, days?: number): Promise<WeatherDayForecast[]>;
}
