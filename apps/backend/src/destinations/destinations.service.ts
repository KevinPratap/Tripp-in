import { Injectable } from '@nestjs/common';
import { cityNameOnly, cityPhotoMap } from '../places/city-photos';
import { PrismaService } from '../common/prisma/prisma.service';
import { DestinationCardDto } from '@trippin/api-contracts';

@Injectable()
export class DestinationsService {
  constructor(private readonly prisma: PrismaService) {}

  async getPopularDestinations(): Promise<DestinationCardDto[]> {
    try {
      const destinations = await this.prisma.destination.findMany({
        orderBy: { popularityScore: 'desc' },
        take: 6
      });

      if (destinations.length > 0) {

        return this.attachRealPhotographs(destinations.map((d) => ({
          id: d.id,
          name: d.name,
          country: d.country,
          imageUrl: '',
          description: d.description || '',
          averageRating: 0,
          tags: ['Iconic', 'Culture', 'Popular']
        })));
      }
    } catch {
      // Fallback if DB offline
    }

    // The same six places when the table is empty, with their photographs resolved the same way.
    return this.attachRealPhotographs([
      {
        id: '10000000-0000-0000-0000-000000000001',
        name: 'Paris',
        country: 'France',
        imageUrl: '',
        description: 'The City of Light, world capital of art, gastronomy and culture.',
        averageRating: 0,
        tags: ['Museums', 'Cuisine', 'Walkable']
      },
      {
        id: '10000000-0000-0000-0000-000000000002',
        name: 'Tokyo',
        country: 'Japan',
        imageUrl: '',
        description: 'Skyscrapers meet historic temples, gardens and street food circuits.',
        averageRating: 0,
        tags: ['Transit', 'Food', 'Culture']
      },
      {
        id: '10000000-0000-0000-0000-000000000003',
        name: 'Rome',
        country: 'Italy',
        imageUrl: '',
        description: 'Open-air history across millennia of art, architecture and piazzas.',
        averageRating: 0,
        tags: ['History', 'Architecture', 'Ancient']
      },
      {
        id: '10000000-0000-0000-0000-000000000004',
        name: 'Kyoto',
        country: 'Japan',
        imageUrl: '',
        description: 'Centuries of preserved wooden temples, bamboo groves and shrines.',
        averageRating: 0,
        tags: ['Temples', 'Nature', 'Heritage']
      },
      {
        id: '10000000-0000-0000-0000-000000000005',
        name: 'Lisbon',
        country: 'Portugal',
        imageUrl: '',
        description: 'Sunlit coastal capital of pastel hills, historic trams and viewpoints.',
        averageRating: 0,
        tags: ['Coastal', 'Viewpoints', 'Hills']
      },
      {
        id: '10000000-0000-0000-0000-000000000006',
        name: 'London',
        country: 'United Kingdom',
        imageUrl: '',
        description: 'Global cultural hub of world-class museums, theatre and royal parks.',
        averageRating: 0,
        tags: ['Museums', 'Parks', 'Theatre']
      }
    ]);
  }

  /**
   * Replaces stock destination imagery with a real photograph of each place.
   *
   * Every card in this file used to carry a photo-site URL, and six of them were hardcoded and could
   * never be right: the same picture stood in for whatever the destination happened to be. A card may
   * only show a photograph of the place it names, so this resolves each one from its own record and
   * sends an empty string when there is none. The UI renders the destination's initials in that case,
   * which is a deliberate mark rather than a broken image or somebody else's city.
   */
  private async attachRealPhotographs(cards: DestinationCardDto[]): Promise<DestinationCardDto[]> {
    const photos = await cityPhotoMap(cards.map((card) => card.name));
    return cards.map((card) => ({
      ...card,
      imageUrl: photos.get(cityNameOnly(card.name)) || ''
    }));
  }

  async getRecommendedDestinations(): Promise<DestinationCardDto[]> {
    return this.getPopularDestinations();
  }
}
