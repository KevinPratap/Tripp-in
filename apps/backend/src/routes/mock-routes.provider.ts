import { Injectable } from '@nestjs/common';
import { RouteProvider, RouteMatrixRequest, RouteMatrixElement } from './route.interface';
import { GeoLocation, RouteMode, RouteSegment } from '@trippin/shared-types';

@Injectable()
export class MockRouteProvider implements RouteProvider {
  /**
   * Calculates Haversine distance in meters between two lat/lng coordinates
   */
  private haversineMeters(c1: GeoLocation, c2: GeoLocation): number {
    const R = 6371000; // Earth radius in meters
    const dLat = ((c2.latitude - c1.latitude) * Math.PI) / 180;
    const dLon = ((c2.longitude - c1.longitude) * Math.PI) / 180;
    const lat1 = (c1.latitude * Math.PI) / 180;
    const lat2 = (c2.latitude * Math.PI) / 180;

    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return Math.round(R * c);
  }

  /**
   * Estimates transit duration in minutes
   */
  private estimateMinutes(meters: number, mode: RouteMode): number {
    // City transit speeds with realistic overhead
    switch (mode) {
      case 'WALKING':
        // ~4.5 km/h -> 75 meters/minute
        return Math.max(2, Math.round(meters / 75));
      case 'BICYCLING':
        // ~14 km/h -> 233 meters/minute
        return Math.max(3, Math.round(meters / 233));
      case 'DRIVING':
        // ~25 km/h city average -> 416 meters/minute + 3 min parking/traffic buffer
        return Math.max(5, Math.round(meters / 416) + 3);
      case 'TRANSIT':
      default:
        // ~20 km/h average + 5 min wait/transfer buffer
        return Math.max(5, Math.round(meters / 333) + 5);
    }
  }

  async calculateRoute(
    origin: GeoLocation,
    destination: GeoLocation,
    mode: RouteMode = 'TRANSIT'
  ): Promise<RouteSegment> {
    const distanceMeters = this.haversineMeters(origin, destination);
    const durationMinutes = this.estimateMinutes(distanceMeters, mode);

    return {
      originPlaceId: `loc_${origin.latitude.toFixed(4)},${origin.longitude.toFixed(4)}`,
      destinationPlaceId: `loc_${destination.latitude.toFixed(4)},${destination.longitude.toFixed(4)}`,
      mode,
      distanceMeters,
      durationMinutes,
      encodedPolyline: 'mock_polyline_sample'
    };
  }

  async calculateMatrix(request: RouteMatrixRequest): Promise<RouteMatrixElement[]> {
    const elements: RouteMatrixElement[] = [];
    const mode = request.mode || 'TRANSIT';

    request.origins.forEach((origin, oIdx) => {
      request.destinations.forEach((dest, dIdx) => {
        const distanceMeters = this.haversineMeters(origin, dest);
        const durationMinutes = this.estimateMinutes(distanceMeters, mode);

        elements.push({
          originIndex: oIdx,
          destinationIndex: dIdx,
          distanceMeters,
          durationMinutes,
          status: 'OK'
        });
      });
    });

    return elements;
  }
}
