import { Injectable, Logger } from '@nestjs/common';
import { PlaceModel, GeoLocation } from '@trippin/shared-types';
import { GooglePlacesProvider } from './google-places.provider';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';

@Injectable()
export class PlaceService {
  private readonly logger = new Logger(PlaceService.name);

  constructor(
    private readonly provider: GooglePlacesProvider,
    private readonly prisma: PrismaService,
    private readonly redis: RedisService
  ) {}

  async searchPlaces(query: string, location?: GeoLocation): Promise<PlaceModel[]> {
    const cacheKey = `places:search:${query}:${location?.latitude || 0},${location?.longitude || 0}`;
    const cached = await this.redis.get<PlaceModel[]>(cacheKey);
    if (cached) return cached;

    const places = await this.provider.search({ query, location });

    // Cache in PostgreSQL & Redis
    try {
      await this.persistPlaces(places);
    } catch (err) {
      this.logger.warn(`Failed persisting places to DB: ${(err as Error).message}`);
    }
    await this.redis.set(cacheKey, places, 3600); // 1 hour cache

    return places;
  }

  async getPlaceDetails(placeId: string): Promise<PlaceModel | null> {
    const cacheKey = `places:details:${placeId}`;
    const cached = await this.redis.get<PlaceModel>(cacheKey);
    if (cached) return cached;

    // Check DB first
    try {
      const dbPlace = await this.prisma.place.findFirst({
        where: { OR: [{ id: placeId }, { googlePlaceId: placeId }] }
      });

      if (dbPlace) {
        const normalized: PlaceModel = {
          id: dbPlace.id,
          googlePlaceId: dbPlace.googlePlaceId,
          name: dbPlace.name,
          formattedAddress: dbPlace.formattedAddress,
          location: { latitude: dbPlace.latitude, longitude: dbPlace.longitude },
          types: dbPlace.types,
          rating: dbPlace.rating || undefined,
          userRatingsTotal: dbPlace.userRatingsTotal || undefined,
          priceLevel: dbPlace.priceLevel || undefined,
          photoUrls: dbPlace.photoUrls,
          openingHours: dbPlace.openingHoursJson as any,
          websiteUrl: dbPlace.websiteUrl || undefined,
          phoneNumber: dbPlace.phoneNumber || undefined
        };
        await this.redis.set(cacheKey, normalized, 86400);
        return normalized;
      }
    } catch {
      // Continue to provider if DB offline
    }

    const place = await this.provider.details(placeId);
    if (place) {
      await this.persistPlaces([place]);
      await this.redis.set(cacheKey, place, 86400);
    }
    return place;
  }

  private async persistPlaces(places: PlaceModel[]): Promise<void> {
    for (const p of places) {
      try {
        await this.prisma.place.upsert({
          where: { googlePlaceId: p.googlePlaceId },
          update: {
            name: p.name,
            formattedAddress: p.formattedAddress,
            latitude: p.location.latitude,
            longitude: p.location.longitude,
            rating: p.rating,
            userRatingsTotal: p.userRatingsTotal,
            priceLevel: p.priceLevel,
            photoUrls: p.photoUrls,
            openingHoursJson: p.openingHours as any,
            types: p.types,
            websiteUrl: p.websiteUrl,
            phoneNumber: p.phoneNumber
          },
          create: {
            googlePlaceId: p.googlePlaceId,
            name: p.name,
            formattedAddress: p.formattedAddress,
            latitude: p.location.latitude,
            longitude: p.location.longitude,
            rating: p.rating,
            userRatingsTotal: p.userRatingsTotal,
            priceLevel: p.priceLevel,
            photoUrls: p.photoUrls,
            openingHoursJson: p.openingHours as any,
            types: p.types,
            websiteUrl: p.websiteUrl,
            phoneNumber: p.phoneNumber
          }
        });
      } catch {
        // Ignore DB save errors if table not created yet
      }
    }
  }
}
