import { Injectable, Logger } from '@nestjs/common';
import { PlaceProvider, PlaceSearchParams } from './place.interface';
import { PlaceModel, GeoLocation, PlaceOpeningHours } from '@trippin/shared-types';

@Injectable()
export class OSMPlacesProvider implements PlaceProvider {
  private readonly logger = new Logger(OSMPlacesProvider.name);

  // In-memory cache for details lookup by ID
  private readonly placeCache = new Map<string, PlaceModel>();

  async search(params: PlaceSearchParams): Promise<PlaceModel[]> {
    const raw = params.query || 'attractions';
    const cleanCity = raw
      .replace(/attractions|landmarks|places|monuments|sightseeing/gi, '')
      .split(',')[0]
      .trim() || raw;

    try {
      const [attractions, museums] = await Promise.all([
        this.searchNominatim(`${cleanCity} attraction`, params.location),
        this.searchNominatim(`${cleanCity} museum`, params.location)
      ]);
      // Photon has a different rate budget than Nominatim, so it contributes the
      // parks and food stops that give the planner enough variety to avoid repeats.
      const [parks, food] = await Promise.all([
        this.searchPhoton(`${cleanCity} park`, params.location),
        this.searchPhoton(`${cleanCity} restaurant`, params.location)
      ]);

      const combined = [...attractions, ...museums, ...parks, ...food];
      const inArea = this.filterByDistance(combined, params.location);
      // Deduplicate by name or coordinates
      const seen = new Set<string>();
      const places: PlaceModel[] = [];
      for (const p of inArea) {
        const key = p.name.toLowerCase().trim();
        if (!seen.has(key)) {
          seen.add(key);
          places.push(p);
        }
      }

      if (places.length > 0) {
        places.forEach((p) => this.placeCache.set(p.id, p));
        return places;
      }
    } catch (err) {
      this.logger.debug(`Nominatim multi-search error: ${(err as Error).message}. Falling back to Photon.`);
    }

    // Secondary fallback: Photon
    try {
      const places = await this.searchPhoton(cleanCity, params.location);
      places.forEach((p) => this.placeCache.set(p.id, p));
      return places;
    } catch (err) {
      this.logger.warn(`Photon fallback error: ${(err as Error).message}`);
      return [];
    }
  }

  async details(placeId: string): Promise<PlaceModel | null> {
    if (this.placeCache.has(placeId)) {
      return this.placeCache.get(placeId)!;
    }

    // Attempt lookup via Nominatim by OSM ID if formatted as osm_TYPE_ID
    if (placeId.startsWith('osm_')) {
      try {
        const parts = placeId.split('_');
        const osmType = parts[1] || 'N';
        const osmId = parts[2] || parts[1];
        const url = `https://nominatim.openstreetmap.org/details?osmtype=${osmType}&osmid=${osmId}&format=json&addressdetails=1`;
        const res = await fetch(url, {
          headers: { 'User-Agent': 'TrippinAI-Production/1.0 (contact@trippin.ai)' },
          signal: AbortSignal.timeout(3500)
        });
        if (res.ok) {
          const d = await res.json();
          const place = this.normalizeNominatimDetail(d, placeId);
          this.placeCache.set(placeId, place);
          return place;
        }
      } catch (err) {
        this.logger.debug(`Nominatim detail fetch failed for ${placeId}: ${(err as Error).message}`);
      }
    }

    return null;
  }

  async nearby(location: GeoLocation, radiusMeters = 5000, type?: string): Promise<PlaceModel[]> {
    return this.search({ location, radiusMeters, type });
  }

  async openingHours(placeId: string): Promise<PlaceOpeningHours | null> {
    const d = await this.details(placeId);
    return d?.openingHours || null;
  }

  private async searchPhoton(query: string, location?: GeoLocation): Promise<PlaceModel[]> {
    let url = `https://photon.komoot.io/api/?q=${encodeURIComponent(query)}&limit=12`;
    if (location) {
      url += `&lat=${location.latitude}&lon=${location.longitude}`;
    }

    const res = await fetch(url, {
      signal: AbortSignal.timeout(4000),
      headers: { 'Accept': 'application/json' }
    });

    if (!res.ok) return [];

    const data = await res.json();
    const features = data.features || [];

    const mapped = features
      .filter((f: any) => f.properties?.name && f.geometry?.coordinates)
      .map((f: any, idx: number) => {
        const props = f.properties;
        const [lon, lat] = f.geometry.coordinates;
        const osmId = props.osm_id ? `osm_${props.osm_type || 'N'}_${props.osm_id}` : `osm_poi_${lat.toFixed(4)}_${lon.toFixed(4)}`;
        const name = props.name;
        const category = props.osm_value || props.osm_key || 'attraction';
        const address = [props.street, props.city || props.district, props.state, props.country]
          .filter(Boolean)
          .join(', ') || `${name}, ${props.country || ''}`;

        return {
          id: osmId,
          googlePlaceId: osmId,
          name,
          description: `${name} — ${category.replace('_', ' ')} in ${props.city || props.country || 'the region'}`,
          formattedAddress: address,
          location: { latitude: lat, longitude: lon },
          types: [category, props.osm_key || 'point_of_interest'].filter(Boolean),
          priceLevel: this.estimatePriceLevel(category),
          photoUrls: [],
          openingHours: this.buildOpeningHours(category),
          openingHoursEstimated: true
        } as PlaceModel;
      });

    return this.filterByDistance(mapped, location);
  }

  private async searchNominatim(query: string, location?: GeoLocation): Promise<PlaceModel[]> {
    let url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&addressdetails=1&extratags=1&limit=10`;
    if (location) {
      const delta = 0.6; // roughly 65 km around the destination centre
      url += `&viewbox=${location.longitude - delta},${location.latitude + delta},${location.longitude + delta},${location.latitude - delta}&bounded=1`;
    }

    const res = await fetch(url, {
      headers: {
        'User-Agent': 'TrippinAI-Production/1.0 (contact@trippin.ai)',
        'Accept-Language': 'en'
      },
      signal: AbortSignal.timeout(4000)
    });

    if (!res.ok) return [];

    const list = await res.json();
    const places = list.map((item: any, idx: number) => {
      const lat = parseFloat(item.lat);
      const lon = parseFloat(item.lon);
      const osmId = `osm_${item.osm_type?.[0]?.toUpperCase() || 'N'}_${item.osm_id || item.place_id}`;
      const name = item.namedetails?.name || item.name || item.display_name.split(',')[0];
      const category = item.type || item.class || 'attraction';
      const realHours: string | undefined = item.extratags?.opening_hours;

      return {
        id: osmId,
        googlePlaceId: osmId,
        name,
        description: item.display_name,
        formattedAddress: item.display_name,
        location: { latitude: lat, longitude: lon },
        types: [category, item.class].filter(Boolean),
        priceLevel: this.estimatePriceLevel(category),
        photoUrls: [],
        openingHours: this.buildOpeningHours(category, realHours),
        openingHoursEstimated: !realHours
      } as PlaceModel;
    });

    // Attach a real photograph of each venue where OpenStreetMap points at one. The tags below
    // name the venue's own Wikidata item or Wikipedia article, so the picture is of that place and
    // of nothing else. If a venue has no such record it keeps no photo and the interface says so.
    await Promise.all(
      places.map(async (place: PlaceModel, i: number) => {
        const photo = await this.resolveRealPhoto(list[i]?.extratags);
        if (photo) place.photoUrls = [photo];
      })
    );

    return places;
  }

  private static readonly photoCache = new Map<string, string | null>();

  private static readonly photoUserAgent = 'TrippinAI-Production/1.0 (contact@trippin.ai)';

  /**
   * Resolves a photograph of the venue itself from its own open record. Two sources, both exact:
   * the Wikidata item referenced by the OSM wikidata tag (property P18), then the Wikipedia article
   * referenced by the OSM wikipedia tag. No name matching, no guessing, no stock imagery: when
   * neither record exists this returns undefined and the caller stores no photo at all.
   */
  private async resolveRealPhoto(extratags?: Record<string, string>): Promise<string | undefined> {
    if (!extratags) return undefined;
    const wikidata = extratags.wikidata?.trim();
    const wikipedia = extratags.wikipedia?.trim();
    const cacheKey = wikidata || wikipedia;
    if (!cacheKey) return undefined;

    const cached = OSMPlacesProvider.photoCache.get(cacheKey);
    if (cached !== undefined) return cached || undefined;

    let url: string | undefined;
    try {
      if (wikidata && /^Q\d+$/.test(wikidata)) {
        url = await this.wikidataPhoto(wikidata);
      }
      if (!url && wikipedia) {
        url = await this.wikipediaPhoto(wikipedia);
      }
    } catch {
      url = undefined;
    }

    OSMPlacesProvider.photoCache.set(cacheKey, url ?? null);
    return url;
  }

  private async wikidataPhoto(qid: string): Promise<string | undefined> {
    const api = `https://www.wikidata.org/w/api.php?action=wbgetclaims&entity=${qid}&property=P18&format=json`;
    const res = await fetch(api, {
      headers: { 'User-Agent': OSMPlacesProvider.photoUserAgent, 'Accept': 'application/json' },
      signal: AbortSignal.timeout(3000)
    });
    if (!res.ok) return undefined;
    const data = await res.json();
    const file = data?.claims?.P18?.[0]?.mainsnak?.datavalue?.value;
    if (typeof file !== 'string' || !file.trim()) return undefined;
    return `https://commons.wikimedia.org/wiki/Special:FilePath/${encodeURIComponent(file.trim())}?width=640`;
  }

  private async wikipediaPhoto(tag: string): Promise<string | undefined> {
    const parts = tag.split(':');
    const hasLang = parts.length > 1 && /^[a-z]{2,3}(-[a-z]+)?$/i.test(parts[0]);
    const lang = hasLang ? parts[0] : 'en';
    const title = hasLang ? parts.slice(1).join(':') : tag;
    const api = `https://${lang}.wikipedia.org/w/api.php?action=query&titles=${encodeURIComponent(
      title
    )}&prop=pageimages&pithumbsize=640&redirects=1&format=json`;
    const res = await fetch(api, {
      headers: { 'User-Agent': OSMPlacesProvider.photoUserAgent, 'Accept': 'application/json' },
      signal: AbortSignal.timeout(3000)
    });
    if (!res.ok) return undefined;
    const data = await res.json();
    const pages = data?.query?.pages || {};
    for (const key of Object.keys(pages)) {
      const src = pages[key]?.thumbnail?.source;
      if (typeof src === 'string' && src.startsWith('https://')) return src;
    }
    return undefined;
  }

  private normalizeNominatimDetail(d: any, placeId: string): PlaceModel {
    const lat = parseFloat(d.lat || '0');
    const lon = parseFloat(d.lon || '0');
    const name = d.names?.name || d.localname || 'Destination Point';
    const category = d.category || 'attraction';

    return {
      id: placeId,
      googlePlaceId: placeId,
      name,
      formattedAddress: d.calculated_postcode ? `${name}, ${d.calculated_postcode}` : name,
      location: { latitude: lat, longitude: lon },
      types: [category],
      priceLevel: this.estimatePriceLevel(category),
      photoUrls: [],
      openingHours: this.buildOpeningHours(category, d.extratags?.opening_hours),
      openingHoursEstimated: !d.extratags?.opening_hours
    } as PlaceModel;
  }

  /**
   * Returns the real opening hours when OpenStreetMap publishes them, otherwise an
   * explicitly marked estimate. Nothing here is presented as verified data.
   */
  private buildOpeningHours(category: string, realHours?: string): PlaceOpeningHours {
    if (realHours && realHours.trim().length > 0) {
      const parsed = this.parseOsmOpeningHours(realHours);
      return {
        weekdayDescriptions: parsed
          ? [realHours.trim()]
          : [realHours.trim(), 'Hours string from OpenStreetMap could not be machine read, so only a sane visiting window is enforced.'],
        periods: parsed || []
      } as PlaceOpeningHours;
    }
    const estimate = this.generateOpeningHours(category);
    return {
      weekdayDescriptions: [
        ...(estimate.weekdayDescriptions || []).map(
          (line) => `${line} (typical hours, not verified)`
        ),
        'Opening hours for this venue are not published in OpenStreetMap.'
      ],
      periods: []
    } as PlaceOpeningHours;
  }

  /**
   * Best effort parser for the simple OpenStreetMap opening_hours shapes
   * ("Mo-Fr 09:00-17:00", "Sa,Su 10:00-14:00", "24/7"). Returns null when the
   * string uses syntax we cannot represent, so callers fall back to a window check.
   */
  private parseOsmOpeningHours(raw: string): PlaceOpeningHours['periods'] | null {
    const DAY_TOKENS: Record<string, number> = {
      su: 0,
      mo: 1,
      tu: 2,
      we: 3,
      th: 4,
      fr: 5,
      sa: 6
    };
    const value = raw.trim();
    if (!value || /off|PH|sunrise|sunset|week|\[|\]/.test(value)) return null;

    if (/^24\/7$/.test(value)) {
      return [0, 1, 2, 3, 4, 5, 6].map((day) => ({
        open: { day, time: '00:00' },
        close: { day, time: '23:59' }
      }));
    }

    const periods: Array<{ open: { day: number; time: string }; close: { day: number; time: string } }> = [];
    const rules = value.split(';').map((r) => r.trim()).filter(Boolean);

    for (const rule of rules) {
      const match = rule.match(/^([A-Za-z,\-\s]+)\s+(\d{2}:\d{2})-(\d{2}:\d{2})$/);
      if (!match) return null;
      const [, dayPart, openTime, closeTime] = match;
      const days = new Set<number>();

      for (const chunk of dayPart.split(',').map((c) => c.trim()).filter(Boolean)) {
        const range = chunk.match(/^([A-Za-z]{2})-([A-Za-z]{2})$/);
        if (range) {
          const start = DAY_TOKENS[range[1].toLowerCase()];
          const end = DAY_TOKENS[range[2].toLowerCase()];
          if (start === undefined || end === undefined) return null;
          let day = start;
          for (let guard = 0; guard < 7; guard++) {
            days.add(day);
            if (day === end) break;
            day = (day + 1) % 7;
          }
        } else {
          const single = DAY_TOKENS[chunk.toLowerCase()];
          if (single === undefined) return null;
          days.add(single);
        }
      }

      for (const day of days) {
        periods.push({ open: { day, time: openTime }, close: { day, time: closeTime } });
      }
    }

    return periods.length > 0 ? periods : null;
  }

  /** Drops venues that are implausibly far from the requested centre. */
  private filterByDistance(
    places: PlaceModel[],
    location?: GeoLocation,
    maxKm = 120
  ): PlaceModel[] {
    if (!location) return places;
    const toRad = (deg: number) => (deg * Math.PI) / 180;
    const distanceKm = (a: GeoLocation, b: GeoLocation) => {
      const dLat = toRad(b.latitude - a.latitude);
      const dLon = toRad(b.longitude - a.longitude);
      const h =
        Math.sin(dLat / 2) ** 2 +
        Math.cos(toRad(a.latitude)) * Math.cos(toRad(b.latitude)) * Math.sin(dLon / 2) ** 2;
      return 2 * 6371 * Math.asin(Math.sqrt(h));
    };
    const kept = places.filter((p) => distanceKm(p.location, location) <= maxKm);
    if (kept.length !== places.length) {
      this.logger.debug(
        `Dropped ${places.length - kept.length} venue(s) further than ${maxKm} km from the requested centre.`
      );
    }
    return kept;
  }

  private estimatePriceLevel(category: string): number {
    const cat = category.toLowerCase();
    if (cat.includes('restaurant') || cat.includes('hotel') || cat.includes('fine_dining')) return 3;
    if (cat.includes('cafe') || cat.includes('bar') || cat.includes('museum')) return 2;
    return 1;
  }

  private generateOpeningHours(category: string): PlaceOpeningHours {
    const cat = category.toLowerCase();
    const isMuseum = cat.includes('museum') || cat.includes('gallery');
    const isNightlife = cat.includes('bar') || cat.includes('pub');
    const isOutdoor = cat.includes('park') || cat.includes('nature') || cat.includes('monument');

    let openTime = '09:00';
    let closeTime = '18:00';
    let closedDays: number[] = [];

    if (isMuseum) {
      closedDays = [1]; // Typically closed Mondays
      openTime = '09:30';
      closeTime = '18:00';
    } else if (isNightlife) {
      openTime = '17:00';
      closeTime = '23:45';
    } else if (isOutdoor) {
      openTime = '08:00';
      closeTime = '20:00';
    }

    const periods = [0, 1, 2, 3, 4, 5, 6]
      .filter((day) => !closedDays.includes(day))
      .map((day) => ({
        open: { day, time: openTime },
        close: { day, time: closeTime }
      }));

    return {
      weekdayDescriptions: [
        `Open ${closedDays.length ? 'Tue - Sun' : 'Daily'}: ${openTime} - ${closeTime}`
      ],
      periods
    };
  }

}
