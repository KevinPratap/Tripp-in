import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { RouteProvider, RouteMatrixRequest, RouteMatrixElement } from './route.interface';
import { GeoLocation, RouteMode, RouteSegment } from '@trippin/shared-types';
import { MockRouteProvider } from './mock-routes.provider';

@Injectable()
export class GoogleRouteProvider implements RouteProvider {
  private readonly logger = new Logger(GoogleRouteProvider.name);
  private readonly apiKey: string;
  private readonly isMockEnabled: boolean;

  constructor(
    private readonly config: ConfigService,
    private readonly mockProvider: MockRouteProvider
  ) {
    this.apiKey = this.config.get<string>('GOOGLE_MAPS_API_KEY', '');
    this.isMockEnabled =
      this.config.get<string>('ENABLE_MOCK_GOOGLE_PROVIDERS') === 'true' || !this.apiKey || this.apiKey.includes('placeholder');
  }

  async calculateRoute(
    origin: GeoLocation,
    destination: GeoLocation,
    mode: RouteMode = 'TRANSIT'
  ): Promise<RouteSegment> {
    if (this.isMockEnabled) {
      return this.mockProvider.calculateRoute(origin, destination, mode);
    }

    try {
      const response = await fetch(
        'https://routes.googleapis.com/directions/v2:computeRoutes',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Goog-Api-Key': this.apiKey,
            'X-Goog-FieldMask':
              'routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline'
          },
          body: JSON.stringify({
            origin: {
              location: {
                latLng: { latitude: origin.latitude, longitude: origin.longitude }
              }
            },
            destination: {
              location: {
                latLng: { latitude: destination.latitude, longitude: destination.longitude }
              }
            },
            travelMode: mode === 'TRANSIT' ? 'TRANSIT' : mode === 'WALKING' ? 'WALK' : 'DRIVE'
          })
        }
      );

      if (!response.ok) {
        return this.mockProvider.calculateRoute(origin, destination, mode);
      }

      const data = await response.json();
      const route = data.routes?.[0];
      if (!route) {
        return this.mockProvider.calculateRoute(origin, destination, mode);
      }

      const durationSeconds = parseInt(route.duration?.replace('s', '') || '600', 10);
      return {
        originPlaceId: `loc_${origin.latitude.toFixed(4)},${origin.longitude.toFixed(4)}`,
        destinationPlaceId: `loc_${destination.latitude.toFixed(4)},${destination.longitude.toFixed(4)}`,
        mode,
        distanceMeters: route.distanceMeters || 1000,
        durationMinutes: Math.ceil(durationSeconds / 60),
        encodedPolyline: route.polyline?.encodedPolyline
      };
    } catch {
      return this.mockProvider.calculateRoute(origin, destination, mode);
    }
  }

  async calculateMatrix(request: RouteMatrixRequest): Promise<RouteMatrixElement[]> {
    return this.mockProvider.calculateMatrix(request);
  }
}
