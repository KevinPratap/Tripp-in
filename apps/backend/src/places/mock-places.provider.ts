import { Injectable } from '@nestjs/common';
import { PlaceProvider, PlaceSearchParams } from './place.interface';
import { PlaceModel, GeoLocation, PlaceOpeningHours } from '@trippin/shared-types';

@Injectable()
export class MockPlaceProvider implements PlaceProvider {
  private readonly mockPlaces: PlaceModel[] = [
    {
      id: 'mock-louvre',
      googlePlaceId: 'ChIJD7fiBh9u5kcRYJSMaMOCCwQ',
      name: 'Louvre Museum',
      description: 'World landmark art museum featuring Mona Lisa and Venus de Milo.',
      formattedAddress: 'Rue de Rivoli, 75001 Paris, France',
      location: { latitude: 48.8606, longitude: 2.3376 },
      types: ['museum', 'tourist_attraction'],
      rating: 4.7,
      userRatingsTotal: 284000,
      priceLevel: 2,
      photoUrls: ['https://images.unsplash.com/photo-1565099824688-e93eb20fe622?w=800'],
      openingHours: {
        weekdayDescriptions: [
          'Monday: 09:00 - 18:00',
          'Tuesday: Closed',
          'Wednesday: 09:00 - 21:00',
          'Thursday: 09:00 - 18:00',
          'Friday: 09:00 - 21:00',
          'Saturday: 09:00 - 18:00',
          'Sunday: 09:00 - 18:00'
        ],
        periods: [
          { open: { day: 1, time: '09:00' }, close: { day: 1, time: '18:00' } },
          { open: { day: 3, time: '09:00' }, close: { day: 3, time: '21:00' } },
          { open: { day: 4, time: '09:00' }, close: { day: 4, time: '18:00' } },
          { open: { day: 5, time: '09:00' }, close: { day: 5, time: '21:00' } },
          { open: { day: 6, time: '09:00' }, close: { day: 6, time: '18:00' } },
          { open: { day: 0, time: '09:00' }, close: { day: 0, time: '18:00' } }
        ]
      }
    },
    {
      id: 'mock-eiffel',
      googlePlaceId: 'ChIJLU7jZClu5kcR4PcOOO6p3I0',
      name: 'Eiffel Tower',
      description: 'Iconic 19th-century wrought-iron tower on the Champ de Mars.',
      formattedAddress: 'Champ de Mars, 5 Av. Anatole France, 75007 Paris, France',
      location: { latitude: 48.8584, longitude: 2.2945 },
      types: ['tourist_attraction', 'point_of_interest'],
      rating: 4.6,
      userRatingsTotal: 340000,
      priceLevel: 3,
      photoUrls: ['https://images.unsplash.com/photo-1511739001486-6bfe10ce785f?w=800'],
      openingHours: {
        weekdayDescriptions: ['Open daily: 09:00 - 23:45'],
        periods: [0, 1, 2, 3, 4, 5, 6].map((day) => ({
          open: { day, time: '09:00' },
          close: { day, time: '23:45' }
        }))
      }
    },
    {
      id: 'mock-orsay',
      googlePlaceId: 'ChIJ9T6R0tBv5kcRt726Z1aGg3w',
      name: "Musée d'Orsay",
      description: '19th- & 20th-century European art housed in a monumental Beaux-Arts railway station.',
      formattedAddress: '1 Rue de la Légion d\'Honneur, 75007 Paris, France',
      location: { latitude: 48.8599, longitude: 2.3265 },
      types: ['museum', 'tourist_attraction'],
      rating: 4.7,
      userRatingsTotal: 85000,
      priceLevel: 2,
      photoUrls: ['https://images.unsplash.com/photo-1582561424760-0321d75e81fa?w=800'],
      openingHours: {
        weekdayDescriptions: ['Monday: Closed', 'Tuesday-Sunday: 09:30 - 18:00'],
        periods: [2, 3, 4, 5, 6, 0].map((day) => ({
          open: { day, time: '09:30' },
          close: { day, time: day === 4 ? '21:45' : '18:00' }
        }))
      }
    },
    {
      id: 'mock-montmartre',
      googlePlaceId: 'ChIJ79FvE-Bv5kcRRJ3tQeK1fio',
      name: 'Sacré-Cœur Basilica & Montmartre',
      description: 'Iconic hilltop basilica offering panoramic city views and bohemian streets.',
      formattedAddress: '35 Rue du Chevalier de la Barre, 75018 Paris, France',
      location: { latitude: 48.8867, longitude: 2.3431 },
      types: ['church', 'tourist_attraction'],
      rating: 4.8,
      userRatingsTotal: 120000,
      priceLevel: 1,
      photoUrls: ['https://images.unsplash.com/photo-1549144511-f099e773c147?w=800'],
      openingHours: {
        periods: [0, 1, 2, 3, 4, 5, 6].map((day) => ({
          open: { day, time: '06:30' },
          close: { day, time: '22:30' }
        }))
      }
    },
    {
      id: 'mock-cafe-flore',
      googlePlaceId: 'ChIJZ3UvTzdu5kcRM9x1Vj4w8Yg',
      name: 'Café de Flore',
      description: 'Famed Saint-Germain café steeped in literary history.',
      formattedAddress: '172 Bd Saint-Germain, 75006 Paris, France',
      location: { latitude: 48.8542, longitude: 2.3328 },
      types: ['cafe', 'restaurant'],
      rating: 4.3,
      userRatingsTotal: 18000,
      priceLevel: 3,
      photoUrls: ['https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800'],
      openingHours: {
        periods: [0, 1, 2, 3, 4, 5, 6].map((day) => ({
          open: { day, time: '07:30' },
          close: { day, time: '01:30' }
        }))
      }
    }
  ];

  async search(params: PlaceSearchParams): Promise<PlaceModel[]> {
    const q = (params.query || '').toLowerCase().trim();
    if (!q) return this.mockPlaces;
    const tokens = q.split(/\s+/).filter((t) => t.length > 2);
    const matched = this.mockPlaces.filter((p) => {
      const haystack = `${p.name} ${p.description || ''} ${p.types.join(' ')} ${p.formattedAddress}`.toLowerCase();
      return tokens.some((t) => haystack.includes(t));
    });
    return matched.length > 0 ? matched : this.mockPlaces;
  }

  async details(placeId: string): Promise<PlaceModel | null> {
    const pId = (placeId || '').toLowerCase().trim();
    const place = this.mockPlaces.find(
      (p) =>
        p.id.toLowerCase() === pId ||
        p.googlePlaceId.toLowerCase() === pId ||
        p.name.toLowerCase() === pId ||
        p.name.toLowerCase().includes(pId) ||
        pId.includes(p.name.toLowerCase())
    );
    return place || null;
  }

  async nearby(location: GeoLocation, radiusMeters = 5000, type?: string): Promise<PlaceModel[]> {
    return this.mockPlaces;
  }

  async openingHours(placeId: string): Promise<PlaceOpeningHours | null> {
    const place = await this.details(placeId);
    return place?.openingHours || null;
  }
}
