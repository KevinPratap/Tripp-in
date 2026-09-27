import { PlaceModel, GeoLocation, PlaceOpeningHours } from '@trippin/shared-types';

export interface PlaceSearchParams {
  query?: string;
  location?: GeoLocation;
  radiusMeters?: number;
  type?: string;
}

export interface PlaceProvider {
  search(params: PlaceSearchParams): Promise<PlaceModel[]>;
  details(placeId: string): Promise<PlaceModel | null>;
  nearby(location: GeoLocation, radiusMeters?: number, type?: string): Promise<PlaceModel[]>;
  openingHours(placeId: string): Promise<PlaceOpeningHours | null>;
}
