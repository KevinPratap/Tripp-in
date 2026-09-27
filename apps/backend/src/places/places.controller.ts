import { Controller, Get, Query } from '@nestjs/common';
import { ApiTags, ApiOperation } from '@nestjs/swagger';
import { PlaceService } from './places.service';
import { PhotonProvider, DestinationSuggestion } from './photon.provider';
import { AutocompleteQueryDto } from './dto/autocomplete-query.dto';

@ApiTags('Places')
@Controller('places')
export class PlacesController {
  constructor(
    private readonly placeService: PlaceService,
    private readonly photon: PhotonProvider
  ) {}

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

  /**
   * Destination suggestions as the traveller types.
   *
   * Each suggestion carries the resolved coordinates and the country's currency, so picking one
   * replaces a free text guess with a real location and prices the trip in the right money. An empty
   * list means nothing matched; it is not an error, and the planner still accepts typed text.
   */
  @Get('autocomplete')
  @ApiOperation({ summary: 'Destination suggestions with coordinates and local currency' })
  async autocomplete(
    @Query() query: AutocompleteQueryDto
  ): Promise<{ suggestions: DestinationSuggestion[] }> {
    const suggestions = await this.photon.suggest(query.q, query.limit ?? 6);
    return { suggestions };
  }
}
