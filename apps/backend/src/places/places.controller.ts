import { Controller, Get, Query, Param } from '@nestjs/common';
import { ApiTags, ApiOperation } from '@nestjs/swagger';
import { PlaceService } from './places.service';

@ApiTags('Places')
@Controller('places')
export class PlacesController {
  constructor(private readonly placeService: PlaceService) {}

  @Get('search')
  @ApiOperation({ summary: 'Search places by keyword query or location' })
  async search(
    @Query('q') q: string,
    @Query('lat') lat?: number,
    @Query('lng') lng?: number
  ) {
    const location = lat && lng ? { latitude: Number(lat), longitude: Number(lng) } : undefined;
    return this.placeService.searchPlaces(q || '', location);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Get details and operating hours for a specific place' })
  async getDetails(@Param('id') id: string) {
    return this.placeService.getPlaceDetails(id);
  }
}
