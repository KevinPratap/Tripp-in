import { OSMPlacesProvider } from './osm-places.provider';

/**
 * A real photograph of a city.
 *
 * Why this exists: a trip in the product has no photograph of its own, so the Trips list and every
 * destination card used to carry either nothing at all or a stock image lifted from a photo site.
 * A stand-in photograph is worse than none, because it claims to show a place it has never seen.
 * This resolves the city's own lead image from its own encyclopaedia entry through the same lookup
 * the venues use, so what a card shows is a photograph of the place it names.
 *
 * The name is the only input, and it is used unchanged: the lead image of the Kyoto entry is a
 * photograph of Kyoto. Where there is no entry there is no photograph, the caller sends nothing, and
 * the UI falls back to the city's initials as a deliberate mark rather than a broken image. Results
 * are cached per city inside the provider, including the misses, so a feed read costs only the first
 * trip to each place.
 */

/** How many cities may be resolved at once, so a feed read cannot fan out without limit. */
const CITY_CONCURRENCY = 6;

/** "Kyoto, Japan" is a city address, not a city name. The lookup wants the city. */
export function cityNameOnly(destination: string | undefined | null): string {
  return String(destination || '')
    .split(',')[0]
    .trim();
}

/** The photograph for one destination string, or undefined when the place has none. */
export async function cityPhotoUrl(
  destination: string | undefined | null
): Promise<string | undefined> {
  const city = cityNameOnly(destination);
  if (city.length < 4) return undefined;
  return OSMPlacesProvider.photoForVenueName(city);
}

/**
 * Photographs for a batch of destinations, keyed by the city name that was looked up. One lookup per
 * distinct city, so a list of eight trips to three cities costs three.
 */
export async function cityPhotoMap(
  destinations: Array<string | undefined | null>
): Promise<Map<string, string>> {
  const cities = [
    ...new Set(destinations.map(cityNameOnly).filter((city) => city.length >= 4))
  ];
  const photos = new Map<string, string>();

  for (let index = 0; index < cities.length; index += CITY_CONCURRENCY) {
    const batch = cities.slice(index, index + CITY_CONCURRENCY);
    await Promise.all(
      batch.map(async (city) => {
        const url = await OSMPlacesProvider.photoForVenueName(city);
        if (url) photos.set(city, url);
      })
    );
  }

  return photos;
}
