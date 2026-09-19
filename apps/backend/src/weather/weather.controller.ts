import { Controller, Get, Query, BadRequestException } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiQuery } from '@nestjs/swagger';
import { WeatherService } from './weather.service';
import { WeatherDayForecast } from '@trippin/shared-types';

@ApiTags('Weather')
@Controller('weather')
export class WeatherController {
  constructor(private readonly weatherService: WeatherService) {}

  @Get('forecast')
  @ApiOperation({ summary: 'Get 7-day daily weather forecast via Open-Meteo (Free)' })
  @ApiQuery({ name: 'lat', required: false, type: Number })
  @ApiQuery({ name: 'lng', required: false, type: Number })
  @ApiQuery({ name: 'destination', required: false, type: String })
  @ApiQuery({ name: 'days', required: false, type: Number })
  async getForecast(
    @Query('lat') latStr?: string,
    @Query('lng') lngStr?: string,
    @Query('destination') destination?: string,
    @Query('days') daysStr?: string,
  ): Promise<{ destination?: string; daily: WeatherDayForecast[] }> {
    const days = daysStr ? parseInt(daysStr, 10) : 7;

    if (latStr && lngStr) {
      const lat = parseFloat(latStr);
      const lng = parseFloat(lngStr);
      if (!isNaN(lat) && !isNaN(lng)) {
        const daily = await this.weatherService.getForecastByCoords(lat, lng, days);
        return { destination: destination || `${lat},${lng}`, daily };
      }
    }

    if (destination) {
      const daily = await this.weatherService.getForecast(destination, days);
      return { destination, daily };
    }

    throw new BadRequestException('Either (lat, lng) or destination must be provided');
  }
}
