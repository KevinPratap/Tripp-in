import { GeoLocation, RouteMode, RouteSegment } from '@trippin/shared-types';

export interface RouteMatrixRequest {
  origins: GeoLocation[];
  destinations: GeoLocation[];
  mode?: RouteMode;
}

export interface RouteMatrixElement {
  originIndex: number;
  destinationIndex: number;
  distanceMeters: number;
  durationMinutes: number;
  status: 'OK' | 'ZERO_RESULTS' | 'NOT_FOUND';
}

export interface RouteProvider {
  calculateRoute(
    origin: GeoLocation,
    destination: GeoLocation,
    mode?: RouteMode
  ): Promise<RouteSegment>;

  calculateMatrix(request: RouteMatrixRequest): Promise<RouteMatrixElement[]>;
}
