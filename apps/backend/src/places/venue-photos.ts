import { OSMPlacesProvider } from './osm-places.provider';

/**
 * How many venue lookups run at once, and the most a single plan read may spend in total.
 *
 * The cap exists so a plan with a hundred activities cannot turn one read into a hundred calls to
 * Wikipedia. Thirty covers every plan in the product several times over, and anything beyond it is
 * left empty rather than slowing the response down.
 */
const LOOKUP_CONCURRENCY = 6;
const LOOKUP_LIMIT = 30;

interface PhotoCarryingPlace {
  name?: string;
  photoUrls?: string[];
}

interface PhotoCarryingActivity {
  place?: PhotoCarryingPlace;
}

interface PhotoCarryingItinerary {
  days?: Array<{ activities?: PhotoCarryingActivity[] }>;
}

/**
 * Fills in a photograph for every venue on a plan that has none, in place, and returns how many it
 * filled.
 *
 * The reason this is needed at all: a place is stored with its photograph at the moment the plan is
 * generated, so every plan built before photographs were resolved carries an empty photoUrls for ever,
 * and it renders as text on paper. Filling on read fixes the plans a traveller already has, which is
 * what makes the app stop looking empty without anyone regenerating anything.
 *
 * The same honesty rule as the write path applies here: a photograph is only ever the venue's own, and
 * a venue with no open record is left empty so the UI can fall back to its initials. A stand-in image
 * would be worse than no image, and the serializer strips external stock URLs on the way out anyway.
 */
export async function backfillVenuePhotos(
  itinerary: PhotoCarryingItinerary | null | undefined
): Promise<number> {
  if (!itinerary?.days?.length) return 0;

  const missing: PhotoCarryingPlace[] = [];
  for (const day of itinerary.days) {
    for (const activity of day.activities || []) {
      const place = activity.place;
      if (!place?.name) continue;
      const usable = (place.photoUrls || []).filter(
        (url) => !url.includes('images.unsplash.com')
      );
      if (usable.length) continue;
      missing.push(place);
      if (missing.length >= LOOKUP_LIMIT) break;
    }
    if (missing.length >= LOOKUP_LIMIT) break;
  }

  if (!missing.length) return 0;

  let filled = 0;
  for (let index = 0; index < missing.length; index += LOOKUP_CONCURRENCY) {
    const batch = missing.slice(index, index + LOOKUP_CONCURRENCY);
    await Promise.all(
      batch.map(async (place) => {
        const url = await OSMPlacesProvider.photoForVenueName(place.name || '');
        if (url) {
          place.photoUrls = [url];
          filled += 1;
        }
      })
    );
  }

  return filled;
}
