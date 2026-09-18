import { Injectable, Logger } from '@nestjs/common';
import { RouteProvider, RouteMatrixRequest, RouteMatrixElement } from './route.interface';
import { GeoLocation, RouteMode, RouteSegment } from '@trippin/shared-types';
import { MockRouteProvider } from './mock-routes.provider';

@Injectable()
export class OSRMRouteProvider implements RouteProvider {
  private readonly logger = new Logger(OSRMRouteProvider.name);

  constructor(private readonly mockFallback: MockRouteProvider) {}

  async calculateRoute(
    origin: GeoLocation,
    destination: GeoLocation,
    mode: RouteMode = 'TRANSIT'
  ): Promise<RouteSegment> {
    const profile = mode === 'WALKING' ? 'walking' : 'driving';
    const coords = `${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}`;
    const url = `https://router.project-osrm.org/route/v1/${profile}/${coords}?overview=simplified`;

    try {
      const response = await fetch(url, {
        signal: AbortSignal.timeout(4000)
      });

      if (!response.ok) {
        return this.mockFallback.calculateRoute(origin, destination, mode);
      }

      const data = await response.json();
      const route = data.routes?.[0];

      if (!route) {
        return this.mockFallback.calculateRoute(origin, destination, mode);
      }

      const durationMinutes = Math.max(1, Math.ceil(route.duration / 60));
      const distanceMeters = Math.round(route.distance);

      return {
        originPlaceId: `loc_${origin.latitude.toFixed(4)},${origin.longitude.toFixed(4)}`,
        destinationPlaceId: `loc_${destination.latitude.toFixed(4)},${destination.longitude.toFixed(4)}`,
        mode,
        distanceMeters,
        durationMinutes,
        encodedPolyline: route.geometry
      };
    } catch {
      return this.mockFallback.calculateRoute(origin, destination, mode);
    }
  }

  async calculateMatrix(request: RouteMatrixRequest): Promise<RouteMatrixElement[]> {
    return this.mockFallback.calculateMatrix(request);
  }
}
