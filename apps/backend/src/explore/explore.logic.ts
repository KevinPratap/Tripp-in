/**
 * Pure helpers for the explore endpoints: sorting OpenStreetMap elements into the handful of kinds a
 * traveller scans for, measuring distances, and working out which leg of a walk a place sits beside.
 * Kept free of I/O so every rule here is covered by a plain unit test.
 */

export type PlaceKind =
  | 'coffee'
  | 'food'
  | 'drinks'
  | 'sweets'
  | 'sight'
  | 'museum'
  | 'viewpoint'
  | 'park'
  | 'market';

export const PLACE_KINDS: PlaceKind[] = [
  'coffee',
  'food',
  'drinks',
  'sweets',
  'sight',
  'museum',
  'viewpoint',
  'park',
  'market'
];

export interface LatLng {
  latitude: number;
  longitude: number;
}

/** A named place from OpenStreetMap. Every field is as OSM lists it; nothing is filled in. */
export interface ExplorePlace {
  id: string;
  name: string;
  kind: PlaceKind;
  latitude: number;
  longitude: number;
  /** Straight-line distance from the point asked about, in metres. */
  distanceMeters: number;
  /** The raw OSM opening_hours tag, shown as listed. Absent when OSM has none. */
  openingHours?: string;
  cuisine?: string;
  website?: string;
  source: 'OSM';
}

export interface AlongPlace extends ExplorePlace {
  /** Which walk it is beside: 0 is from the first stop to the second. */
  legIndex: number;
  /** How far off the walking line it sits, in metres. */
  offRouteMeters: number;
}

export interface RouteLeg {
  /** The line to draw, as [latitude, longitude] pairs. */
  coordinates: Array<[number, number]>;
  distanceMeters: number;
  /** Walking time from OSRM. Null when only a straight line could be drawn. */
  durationMinutes: number | null;
  /** OSRM when the line follows real streets; straight when routing was unavailable. */
  source: 'OSRM' | 'straight';
}

export function kindOf(tags: Record<string, string | undefined>): PlaceKind | null {
  const amenity = tags.amenity;
  const shop = tags.shop;
  const tourism = tags.tourism;
  if (amenity === 'cafe') return 'coffee';
  if (amenity === 'ice_cream' || ['bakery', 'pastry', 'confectionery', 'chocolate'].includes(shop ?? '')) {
    return 'sweets';
  }
  if (['restaurant', 'fast_food', 'food_court'].includes(amenity ?? '') || shop === 'deli') return 'food';
  if (['bar', 'pub', 'biergarten'].includes(amenity ?? '')) return 'drinks';
  if (amenity === 'marketplace') return 'market';
  if (tourism === 'viewpoint') return 'viewpoint';
  if (tourism === 'museum' || tourism === 'gallery') return 'museum';
  if (['park', 'garden'].includes(tags.leisure ?? '')) return 'park';
  if (tourism === 'attraction' || tourism === 'artwork' || tags.historic) return 'sight';
  return null;
}

export function distanceMeters(a: LatLng, b: LatLng): number {
  const r = 6371e3;
  const p1 = (a.latitude * Math.PI) / 180;
  const p2 = (b.latitude * Math.PI) / 180;
  const dp = ((b.latitude - a.latitude) * Math.PI) / 180;
  const dl = ((b.longitude - a.longitude) * Math.PI) / 180;
  const h = Math.sin(dp / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2;
  return Math.round(2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h)));
}

/** Distance from a point to a segment, on a local flat projection (fine at walking scale). */
export function distanceToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): number {
  const k = Math.cos((p.latitude * Math.PI) / 180);
  const m = 111_320;
  const ax = a.longitude * k * m, ay = a.latitude * m;
  const bx = b.longitude * k * m, by = b.latitude * m;
  const px = p.longitude * k * m, py = p.latitude * m;
  const dx = bx - ax, dy = by - ay;
  const len2 = dx * dx + dy * dy;
  const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
  return Math.round(Math.hypot(px - (ax + t * dx), py - (ay + t * dy)));
}

interface OverpassElement {
  type: string;
  id: number;
  lat?: number;
  lon?: number;
  center?: { lat: number; lon: number };
  tags?: Record<string, string>;
}

/** Turns an Overpass answer into places, nearest first, one per name. */
export function placesFromOverpass(elements: OverpassElement[], origin: LatLng, limit: number): ExplorePlace[] {
  const seen = new Set<string>();
  const places: ExplorePlace[] = [];
  for (const el of elements) {
    const tags = el.tags ?? {};
    const name = tags.name?.trim();
    const lat = el.lat ?? el.center?.lat;
    const lon = el.lon ?? el.center?.lon;
    if (!name || lat === undefined || lon === undefined) continue;
    const kind = kindOf(tags);
    if (!kind) continue;
    const key = `${name.toLowerCase()}|${kind}`;
    if (seen.has(key)) continue;
    seen.add(key);
    places.push({
      id: `osm_${el.type}_${el.id}`,
      name,
      kind,
      latitude: lat,
      longitude: lon,
      distanceMeters: distanceMeters(origin, { latitude: lat, longitude: lon }),
      openingHours: tags.opening_hours || undefined,
      cuisine: tags.cuisine?.replace(/_/g, ' ').replace(/;/g, ', ') || undefined,
      website: tags.website || tags['contact:website'] || undefined,
      source: 'OSM'
    });
  }
  return places.sort((a, b) => a.distanceMeters - b.distanceMeters).slice(0, limit);
}

/** Every Overpass filter the explore endpoints use, for the given area clause. */
export function overpassQuery(area: string, max: number): string {
  return `[out:json][timeout:15];
(
  nwr["amenity"~"^(cafe|restaurant|bar|pub|ice_cream|fast_food|food_court|biergarten|marketplace)$"]["name"](${area});
  nwr["shop"~"^(bakery|pastry|confectionery|chocolate|deli)$"]["name"](${area});
  nwr["tourism"~"^(museum|attraction|viewpoint|gallery|artwork)$"]["name"](${area});
  nwr["leisure"~"^(park|garden)$"]["name"](${area});
  nwr["historic"~"^(monument|memorial|castle|ruins|archaeological_site)$"]["name"](${area});
);
out center tags ${max};`;
}

/** Keeps a long line to at most [max] points for a query, always keeping both ends. */
export function thinPath(path: LatLng[], max: number): LatLng[] {
  if (path.length <= max) return path;
  const step = (path.length - 1) / (max - 1);
  return Array.from({ length: max }, (_, i) => path[Math.round(i * step)]);
}

/**
 * Pins each place to the walk it is closest to, and keeps the ones actually beside the route. A
 * place is only listed for one leg, and the list is spread across kinds so a street of twenty
 * restaurants does not crowd out the one viewpoint.
 */
export function placeAlong(places: ExplorePlace[], legs: RouteLeg[], maxOff: number, limit: number): AlongPlace[] {
  const along: AlongPlace[] = [];
  for (const place of places) {
    let best = { leg: -1, off: Number.POSITIVE_INFINITY };
    legs.forEach((leg, li) => {
      for (let i = 0; i < leg.coordinates.length - 1; i++) {
        const [aLat, aLng] = leg.coordinates[i];
        const [bLat, bLng] = leg.coordinates[i + 1];
        const off = distanceToSegmentMeters(
          place,
          { latitude: aLat, longitude: aLng },
          { latitude: bLat, longitude: bLng }
        );
        if (off < best.off) best = { leg: li, off };
      }
    });
    if (best.leg >= 0 && best.off <= maxOff) {
      along.push({ ...place, legIndex: best.leg, offRouteMeters: best.off });
    }
  }
  along.sort((a, b) => a.offRouteMeters - b.offRouteMeters);
  const perKind = new Map<PlaceKind, number>();
  const spread: AlongPlace[] = [];
  const cap = Math.max(3, Math.ceil(limit / 4));
  for (const p of along) {
    const n = perKind.get(p.kind) ?? 0;
    if (n >= cap) continue;
    perKind.set(p.kind, n + 1);
    spread.push(p);
    if (spread.length >= limit) break;
  }
  return spread.sort((a, b) => a.legIndex - b.legIndex || a.offRouteMeters - b.offRouteMeters);
}

/** Parses "lat,lng;lat,lng;..." into points, or null if any part is not a real coordinate. */
export function parsePoints(raw: string): LatLng[] | null {
  const points = raw.split(';').map((pair) => {
    const [lat, lng] = pair.split(',').map(Number);
    return { latitude: lat, longitude: lng };
  });
  const ok = points.every(
    (p) =>
      Number.isFinite(p.latitude) &&
      Number.isFinite(p.longitude) &&
      Math.abs(p.latitude) <= 90 &&
      Math.abs(p.longitude) <= 180
  );
  return ok ? points : null;
}
