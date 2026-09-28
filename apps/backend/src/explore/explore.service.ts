import { Injectable, Logger } from '@nestjs/common';
import { RedisService } from '../common/redis/redis.service';
import {
  AlongPlace,
  ExplorePlace,
  LatLng,
  RouteLeg,
  distanceMeters,
  overpassQuery,
  placeAlong,
  placesFromOverpass,
  thinPath
} from './explore.logic';

const USER_AGENT = 'TrippinAI-Production/1.0 (contact@trippin.ai)';
const OVERPASS_URL = 'https://overpass-api.de/api/interpreter';
/** The OSM foundation's foot profile. The public OSRM demo only routes cars. */
const FOOT_ROUTER = 'https://routing.openstreetmap.de/routed-foot/route/v1/foot';

@Injectable()
export class ExploreService {
  private readonly logger = new Logger(ExploreService.name);

  constructor(private readonly redis: RedisService) {}

  /** Named places within walking distance of a point, nearest first. Empty when OSM cannot answer. */
  async nearby(center: LatLng, radius = 800): Promise<ExplorePlace[]> {
    const key = `explore:nearby:${center.latitude.toFixed(4)},${center.longitude.toFixed(4)}:${radius}`;
    const cached = await this.redis.get<ExplorePlace[]>(key);
    if (cached) return cached;
    const elements = await this.overpass(
      overpassQuery(`around:${radius},${center.latitude},${center.longitude}`, 400)
    );
    if (elements === null) return [];
    const places = placesFromOverpass(elements, center, 120);
    await this.redis.set(key, places, 6 * 3600);
    return places;
  }

  /** The walk between each pair of stops, and the named places beside it. */
  async route(stops: LatLng[]): Promise<{ legs: RouteLeg[]; along: AlongPlace[] }> {
    const key = `explore:route:${stops.map((s) => `${s.latitude.toFixed(5)},${s.longitude.toFixed(5)}`).join(';')}`;
    const cached = await this.redis.get<{ legs: RouteLeg[]; along: AlongPlace[] }>(key);
    if (cached) return cached;

    const legs = await Promise.all(stops.slice(0, -1).map((from, i) => this.leg(from, stops[i + 1])));
    const path = thinPath(
      legs.flatMap((l) => l.coordinates.map(([latitude, longitude]) => ({ latitude, longitude }))),
      80
    );
    const area = `around:70,${path.map((p) => `${p.latitude.toFixed(5)},${p.longitude.toFixed(5)}`).join(',')}`;
    const elements = await this.overpass(overpassQuery(area, 400));
    const places = elements === null ? [] : placesFromOverpass(elements, stops[0], 400);
    const result = { legs, along: placeAlong(places, legs, 80, 40) };
    // Only a complete answer is kept; a straight-line fallback should be retried next time.
    if (legs.every((l) => l.source === 'OSRM') && elements !== null) {
      await this.redis.set(key, result, 12 * 3600);
    }
    return result;
  }

  private async leg(from: LatLng, to: LatLng): Promise<RouteLeg> {
    const url = `${FOOT_ROUTER}/${from.longitude},${from.latitude};${to.longitude},${to.latitude}?overview=full&geometries=geojson`;
    try {
      const res = await fetch(url, {
        headers: { 'User-Agent': USER_AGENT },
        signal: AbortSignal.timeout(6000)
      });
      if (res.ok) {
        const data = await res.json();
        const route = data.routes?.[0];
        const coords: Array<[number, number]> | undefined = route?.geometry?.coordinates;
        if (route && coords && coords.length >= 2) {
          return {
            coordinates: coords.map(([lng, lat]) => [lat, lng] as [number, number]),
            distanceMeters: Math.round(route.distance),
            durationMinutes: Math.max(1, Math.ceil(route.duration / 60)),
            source: 'OSRM'
          };
        }
      }
    } catch (err) {
      this.logger.debug(`Foot routing failed: ${(err as Error).message}`);
    }
    // No routing: draw the honest straight line and say so; no invented walking time.
    return {
      coordinates: [
        [from.latitude, from.longitude],
        [to.latitude, to.longitude]
      ],
      distanceMeters: distanceMeters(from, to),
      durationMinutes: null,
      source: 'straight'
    };
  }

  private async overpass(query: string): Promise<any[] | null> {
    try {
      const res = await fetch(OVERPASS_URL, {
        method: 'POST',
        headers: { 'User-Agent': USER_AGENT, 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `data=${encodeURIComponent(query)}`,
        signal: AbortSignal.timeout(16000)
      });
      if (!res.ok) {
        this.logger.warn(`Overpass answered ${res.status}`);
        return null;
      }
      const data = await res.json();
      return Array.isArray(data.elements) ? data.elements : [];
    } catch (err) {
      this.logger.warn(`Overpass failed: ${(err as Error).message}`);
      return null;
    }
  }
}
