import { Injectable, Logger } from '@nestjs/common';
import { currencyForCountry } from './country-currency';

/**
 * Destination autocomplete, from Photon (photon.komoot.io).
 *
 * Photon indexes OpenStreetMap and is built for type-ahead: it is free, needs no key, and unlike
 * Nominatim its usage policy permits autocomplete traffic. Nominatim stays where it already is, doing
 * one geocode per trip at generation time, which is the use its policy allows.
 *
 * Why a suggestion carries coordinates and a country code: picking a real place is what lets the trip
 * be created against a resolved location instead of a string the engine has to guess at later, and the
 * country code is what decides the trip's currency. Before this, a traveller typing a city with a typo
 * paid for it with a failed two minute generation, and the Android planner priced every trip in INR
 * because nothing told it where the trip was.
 */

/** One place a traveller can pick. Every field is from Photon or derived from it; nothing is invented. */
export interface DestinationSuggestion {
  /** Stable across requests, built from the OSM type and id so a client can key a list on it. */
  id: string;
  /** The place's own name, as OSM holds it. */
  name: string;
  /** State, region or county, when Photon gives one. */
  region: string | null;
  country: string | null;
  /** ISO 3166-1 alpha-2, lowercase from Photon, upper cased here. */
  countryCode: string | null;
  /** ISO 4217 for the country, or null when we do not know it. The client must not guess. */
  currency: string | null;
  latitude: number;
  longitude: number;
  /** The one line a client shows in the dropdown. */
  label: string;
}

/** The subset of a Photon GeoJSON feature this provider reads. */
interface PhotonFeature {
  geometry?: { coordinates?: unknown };
  properties?: Record<string, unknown>;
}

/** OSM values that name somewhere a person travels to, rather than a shop or a building. */
const TRAVELLABLE_PLACE_VALUES = new Set([
  'city',
  'town',
  'village',
  'municipality',
  'borough',
  'suburb',
  'district',
  'region',
  'province',
  'state',
  'county',
  'island',
  'archipelago',
  'country'
]);

function text(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

/**
 * Turns one Photon feature into a suggestion, or null when the feature is not somewhere a person
 * can plan a trip to, or is missing the coordinates that make it useful.
 *
 * Kept pure and exported so the shape handling is tested without reaching the network.
 */
export function parsePhotonFeature(feature: PhotonFeature | null | undefined): DestinationSuggestion | null {
  const props = feature?.properties;
  if (!props) return null;

  const name = text(props.name);
  if (!name) return null;

  const coords = feature?.geometry?.coordinates;
  if (!Array.isArray(coords) || coords.length < 2) return null;
  // GeoJSON orders a position longitude first.
  const longitude = Number(coords[0]);
  const latitude = Number(coords[1]);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;

  // Photon labels what a feature is under osm_key/osm_value, and repeats the value as `type`.
  const osmKey = text(props.osm_key);
  const osmValue = text(props.osm_value) || text(props.type);
  const isPlace = osmKey === 'place' || osmKey === 'boundary';
  if (!isPlace || !osmValue || !TRAVELLABLE_PLACE_VALUES.has(osmValue)) return null;

  const countryCode = text(props.countrycode)?.toUpperCase() || null;
  const country = text(props.country);
  const region = text(props.state) || text(props.county) || null;

  const osmType = text(props.osm_type);
  const osmId = props.osm_id === undefined || props.osm_id === null ? null : String(props.osm_id);
  const id = osmType && osmId ? `osm:${osmType}:${osmId}` : `photon:${latitude},${longitude}`;

  // The region is dropped from the label when it just repeats the name, which Photon does for a city
  // that is also its own state (Lisbon, Berlin, Singapore).
  const parts = [name, region && region !== name ? region : null, country].filter(Boolean);

  return {
    id,
    name,
    region,
    country,
    countryCode,
    currency: currencyForCountry(countryCode),
    latitude,
    longitude,
    label: parts.join(', ')
  };
}

/**
 * Parses a Photon FeatureCollection into suggestions, dropping anything unusable and de-duplicating
 * places that resolve to the same label, which happens when OSM holds a city as both a node and a
 * boundary relation.
 */
export function parsePhotonResponse(body: unknown, limit: number): DestinationSuggestion[] {
  const features = (body as { features?: unknown })?.features;
  if (!Array.isArray(features)) return [];

  const seen = new Set<string>();
  const results: DestinationSuggestion[] = [];
  for (const feature of features) {
    const suggestion = parsePhotonFeature(feature as PhotonFeature);
    if (!suggestion) continue;
    const key = suggestion.label.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    results.push(suggestion);
    if (results.length >= limit) break;
  }
  return results;
}

@Injectable()
export class PhotonProvider {
  private readonly logger = new Logger(PhotonProvider.name);
  private readonly endpoint = 'https://photon.komoot.io/api';

  /**
   * Suggestions for what the traveller has typed so far.
   *
   * An empty list is a valid answer and means "nothing matched", not "something broke": the planner
   * still lets the trip be created from free text, because the engine geocodes the destination again
   * at generation time. So a Photon outage costs the traveller the convenience, never the feature.
   */
  async suggest(query: string, limit = 6): Promise<DestinationSuggestion[]> {
    const term = query.trim();
    if (term.length < 2) return [];

    const bounded = Math.min(Math.max(limit, 1), 10);
    // Ask for more than we need: the filter below drops shops and buildings, so a raw limit of six
    // can come back with one usable city.
    const url = `${this.endpoint}?q=${encodeURIComponent(term)}&limit=${bounded * 4}&lang=en`;

    try {
      const res = await fetch(url, {
        headers: {
          'User-Agent': 'TrippinAI/1.0 (+https://github.com/KevinPratap/Tripp-in)',
          Accept: 'application/json'
        },
        signal: AbortSignal.timeout(3500)
      });
      if (!res.ok) {
        this.logger.warn(`Photon answered HTTP ${res.status} for "${term}"`);
        return [];
      }
      return parsePhotonResponse(await res.json(), bounded);
    } catch (err) {
      // A timeout here is the common case on a slow network. It is logged and swallowed: the field
      // shows no suggestions rather than an error, and typing still works.
      this.logger.warn(`Photon lookup failed for "${term}": ${(err as Error).message}`);
      return [];
    }
  }
}
