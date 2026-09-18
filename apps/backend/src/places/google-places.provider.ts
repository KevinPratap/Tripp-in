import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PlaceProvider, PlaceSearchParams } from './place.interface';
import { PlaceModel, GeoLocation, PlaceOpeningHours } from '@trippin/shared-types';
import { MockPlaceProvider } from './mock-places.provider';

@Injectable()
export class GooglePlacesProvider implements PlaceProvider {
  private readonly logger = new Logger(GooglePlacesProvider.name);
  private readonly apiKey: string;
  private readonly isMockEnabled: boolean;

  constructor(
    private readonly config: ConfigService,
    private readonly mockProvider: MockPlaceProvider
  ) {
    this.apiKey = this.config.get<string>('GOOGLE_MAPS_API_KEY', '');
    this.isMockEnabled =
      this.config.get<string>('ENABLE_MOCK_GOOGLE_PROVIDERS') === 'true' || !this.apiKey || this.apiKey.includes('placeholder');
  }

  async search(params: PlaceSearchParams): Promise<PlaceModel[]> {
    if (this.isMockEnabled) {
      return this.mockProvider.search(params);
    }
    // Real Google Places Text Search (New)
    try {
      const response = await fetch('https://places.googleapis.com/v1/places:searchText', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Goog-Api-Key': this.apiKey,
          'X-Goog-FieldMask':
            'places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.userRatingCount,places.priceLevel,places.types,places.regularOpeningHours,places.websiteUri'
        },
        body: JSON.stringify({
          textQuery: params.query || 'attractions',
          ...(params.location && {
            locationBias: {
              circle: {
                center: {
                  latitude: params.location.latitude,
                  longitude: params.location.longitude
                },
                radius: params.radiusMeters || 5000.0
              }
            }
          })
        })
      });

      if (!response.ok) {
        this.logger.warn(`Google Places search returned status ${response.status}. Using fallback.`);
        return this.mockProvider.search(params);
      }

      const data = await response.json();
      const places: PlaceModel[] = (data.places || []).map((p: any) => ({
        id: p.id,
        googlePlaceId: p.id,
        name: p.displayName?.text || 'Unnamed Place',
        formattedAddress: p.formattedAddress || '',
        location: {
          latitude: p.location?.latitude || 0,
          longitude: p.location?.longitude || 0
        },
        types: p.types || [],
        rating: p.rating,
        userRatingsTotal: p.userRatingCount,
        priceLevel: p.priceLevel ? parseInt(p.priceLevel.replace('PRICE_LEVEL_', '')) || 2 : undefined,
        photoUrls: [],
        websiteUrl: p.websiteUri,
        openingHours: p.regularOpeningHours ? {
          weekdayDescriptions: p.regularOpeningHours.weekdayDescriptions,
          periods: (p.regularOpeningHours.periods || []).map((per: any) => ({
            open: { day: per.open?.day || 0, time: `${String(per.open?.hour || 0).padStart(2, '0')}:${String(per.open?.minute || 0).padStart(2, '0')}` },
            close: { day: per.close?.day || 0, time: `${String(per.close?.hour || 0).padStart(2, '0')}:${String(per.close?.minute || 0).padStart(2, '0')}` }
          }))
        } : undefined
      }));

      return places.length > 0 ? places : this.mockProvider.search(params);
    } catch (err) {
      this.logger.error(`Error querying Google Places: ${(err as Error).message}`);
      return this.mockProvider.search(params);
    }
  }

  async details(placeId: string): Promise<PlaceModel | null> {
    if (this.isMockEnabled) {
      return this.mockProvider.details(placeId);
    }
    try {
      const response = await fetch(`https://places.googleapis.com/v1/places/${placeId}`, {
        headers: {
          'X-Goog-Api-Key': this.apiKey,
          'X-Goog-FieldMask':
            'id,displayName,formattedAddress,location,rating,userRatingCount,priceLevel,types,regularOpeningHours,websiteUri'
        }
      });
      if (!response.ok) {
        return this.mockProvider.details(placeId);
      }
      const p = await response.json();
      return {
        id: p.id,
        googlePlaceId: p.id,
        name: p.displayName?.text || 'Place',
        formattedAddress: p.formattedAddress || '',
        location: {
          latitude: p.location?.latitude || 0,
          longitude: p.location?.longitude || 0
        },
        types: p.types || [],
        rating: p.rating,
        userRatingsTotal: p.userRatingCount,
        photoUrls: [],
        websiteUrl: p.websiteUri,
        openingHours: p.regularOpeningHours ? {
          weekdayDescriptions: p.regularOpeningHours.weekdayDescriptions,
          periods: (p.regularOpeningHours.periods || []).map((per: any) => ({
            open: { day: per.open?.day || 0, time: `${String(per.open?.hour || 0).padStart(2, '0')}:${String(per.open?.minute || 0).padStart(2, '0')}` },
            close: { day: per.close?.day || 0, time: `${String(per.close?.hour || 0).padStart(2, '0')}:${String(per.close?.minute || 0).padStart(2, '0')}` }
          }))
        } : undefined
      };
    } catch {
      return this.mockProvider.details(placeId);
    }
  }

  async nearby(location: GeoLocation, radiusMeters = 5000, type?: string): Promise<PlaceModel[]> {
    return this.search({ location, radiusMeters, type });
  }

  async openingHours(placeId: string): Promise<PlaceOpeningHours | null> {
    const details = await this.details(placeId);
    return details?.openingHours || null;
  }
}
