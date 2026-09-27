import { Injectable, Logger } from '@nestjs/common';
import { RouteProvider, RouteMatrixRequest, RouteMatrixElement } from './route.interface';
import { GeoLocation, RouteMode, RouteSegment } from '@trippin/shared-types';
import { MockRouteProvider } from './mock-routes.provider';

@Injectable()
export class OSRMRouteProvider implements RouteProvider {
  private readonly logger = new Logger(OSRMRouteProvider.name);

  constructor(private readonly mockFallback: MockRouteProvider) {}

  private haversineMeters(p1: GeoLocation, p2: GeoLocation): number {
    const R = 6371e3;
    const phi1 = (p1.latitude * Math.PI) / 180;
    const phi2 = (p2.latitude * Math.PI) / 180;
    const deltaPhi = ((p2.latitude - p1.latitude) * Math.PI) / 180;
    const deltaLambda = ((p2.longitude - p1.longitude) * Math.PI) / 180;

    const a =
      Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
      Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return Math.round(R * c);
  }

  private estimateMinutes(meters: number, mode: RouteMode): number {
    switch (mode) {
      case 'WALKING':
        return Math.max(2, Math.round(meters / 75)); // 4.5 km/h
      case 'BICYCLING':
        return Math.max(3, Math.round(meters / 233)); // 14 km/h
      case 'DRIVING':
        return Math.max(5, Math.round(meters / 416) + 3); // 25 km/h + traffic
      case 'TRANSIT':
      default:
        return Math.max(5, Math.round(meters / 333) + 5); // 20 km/h + 5m transfer
    }
  }

  async calculateRoute(
    origin: GeoLocation,
    destination: GeoLocation,
    mode: RouteMode = 'TRANSIT'
  ): Promise<RouteSegment> {
    const profile = mode === 'WALKING' ? 'walking' : 'driving';
    const coords = `${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}`;
    const url = `https://router.project-osrm.org/route/v1/${profile}/${coords}?overview=simplified`;

    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(3500) });
      if (response.ok) {
        const data = await response.json();
        const route = data.routes?.[0];
        if (route) {
          return {
            originPlaceId: `loc_${origin.latitude.toFixed(4)},${origin.longitude.toFixed(4)}`,
            destinationPlaceId: `loc_${destination.latitude.toFixed(4)},${destination.longitude.toFixed(4)}`,
            mode,
            distanceMeters: Math.round(route.distance),
            durationMinutes: Math.max(1, Math.ceil(route.duration / 60)),
            encodedPolyline: route.geometry
          };
        }
      }
    } catch {
      // Fall through to deterministic physics model
    }

    const distanceMeters = this.haversineMeters(origin, destination);
    const durationMinutes = this.estimateMinutes(distanceMeters, mode);
    return {
      originPlaceId: `loc_${origin.latitude.toFixed(4)},${origin.longitude.toFixed(4)}`,
      destinationPlaceId: `loc_${destination.latitude.toFixed(4)},${destination.longitude.toFixed(4)}`,
      mode,
      distanceMeters,
      durationMinutes
    };
  }

  async calculateMatrix(request: RouteMatrixRequest): Promise<RouteMatrixElement[]> {
    const elements: RouteMatrixElement[] = [];
    for (let i = 0; i < request.origins.length; i++) {
      for (let j = 0; j < request.destinations.length; j++) {
        const origin = request.origins[i];
        const dest = request.destinations[j];
        const distanceMeters = this.haversineMeters(origin, dest);
        const durationMinutes = this.estimateMinutes(distanceMeters, request.mode || 'TRANSIT');
        elements.push({
          originIndex: i,
          destinationIndex: j,
          status: 'OK',
          distanceMeters,
          durationMinutes
        });
      }
    }
    return elements;
  }
}
