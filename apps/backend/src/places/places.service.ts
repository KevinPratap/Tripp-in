import { Injectable, Logger } from '@nestjs/common';
import { PlaceModel, GeoLocation } from '@trippin/shared-types';
import { GooglePlacesProvider } from './google-places.provider';
import { OSMPlacesProvider } from './osm-places.provider';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';

@Injectable()
export class PlaceService {
  private readonly logger = new Logger(PlaceService.name);

  constructor(
    private readonly provider: GooglePlacesProvider,
    private readonly prisma: PrismaService,
    private readonly redis: RedisService,
    private readonly osmProvider: OSMPlacesProvider
  ) {}

  /**
   * Fills in the published entry price for each venue, from OpenStreetMap, so every number the cost
   * panel shows has a source behind it. A venue with no published price stays unpriced rather than
   * being estimated, and the panel then says so out loud.
   */
  async attachEntryPrices(places: PlaceModel[], location?: GeoLocation): Promise<PlaceModel[]> {
    if (!location || places.length === 0) return places;
    try {
      const prices = await this.osmProvider.entryPricesNear(location);
      if (prices.size === 0) return places;
      const enriched = places.map((place) => {
        if (place.price) return place;
        const key = OSMPlacesProvider.normaliseName(place.name || '');
        if (!key) return place;
        const exact = prices.get(key);
        if (exact) return { ...place, price: exact };
        // A planner or a model may write a longer or shorter version of the same venue name.
        for (const [name, price] of prices) {
          if (name.includes(key) || key.includes(name)) return { ...place, price };
        }
        return place;
      });
      // Keep the published prices with the venues so the next lookup does not re-fetch them.
      this.persistPlaces(enriched).catch(() => undefined);
      return enriched;
    } catch (err) {
      this.logger.warn(`Entry price enrichment skipped: ${(err as Error).message}`);
      return places;
    }
  }

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

  /**
   * Resolves a free-text destination to coordinates. Used to bound place search
   * and to give the deterministic engine a geographic anchor.
   */
  async geocodeDestination(destination: string): Promise<GeoLocation | null> {
    if (!destination) return null;
    const cacheKey = `geo:destination:${destination.toLowerCase().trim()}`;
    const cached = await this.redis.get<GeoLocation>(cacheKey);
    if (cached) return cached;

    try {
      const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(
        destination
      )}&format=json&limit=1`;
      const res = await fetch(url, {
        headers: {
          'User-Agent': 'TrippinAI-Production/1.0 (contact@trippin.ai)',
          'Accept-Language': 'en'
        },
        signal: AbortSignal.timeout(4000)
      });
      if (!res.ok) return null;

      const list = await res.json();
      if (!Array.isArray(list) || list.length === 0) return null;

      const location: GeoLocation = {
        latitude: parseFloat(list[0].lat),
        longitude: parseFloat(list[0].lon)
      };
      if (!Number.isFinite(location.latitude) || !Number.isFinite(location.longitude)) {
        return null;
      }

      await this.redis.set(cacheKey, location, 604800); // a week: cities rarely move
      return location;
    } catch (err) {
      this.logger.warn(
        `Could not geocode destination "${destination}": ${(err as Error).message}`
      );
      return null;
    }
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
          price: (dbPlace.priceJson as any) || undefined,
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
            priceJson: (p.price as any) ?? undefined,
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
