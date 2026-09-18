import { Injectable } from '@nestjs/common';
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
        return destinations.map((d) => ({
          id: d.id,
          name: d.name,
          country: d.country,
          imageUrl: d.imageUrl || 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=800',
          description: d.description || '',
          averageRating: 4.8,
          tags: ['Iconic', 'Culture', 'Popular']
        }));
      }
    } catch {
      // Fallback if DB offline
    }

    // Default static fallback destinations
    return [
      {
        id: '10000000-0000-0000-0000-000000000001',
        name: 'Paris',
        country: 'France',
        imageUrl: 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=800',
        description: 'The City of Light, world capital of art, gastronomy and culture.',
        averageRating: 4.9,
        tags: ['Museums', 'Cuisine', 'Romantic']
      },
      {
        id: '10000000-0000-0000-0000-000000000002',
        name: 'Tokyo',
        country: 'Japan',
        imageUrl: 'https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=800',
        description: 'Ultra-modern skyscrapers meet historic temples and unmatched street food.',
        averageRating: 4.9,
        tags: ['Futuristic', 'Food', 'Culture']
      },
      {
        id: '10000000-0000-0000-0000-000000000003',
        name: 'Rome',
        country: 'Italy',
        imageUrl: 'https://images.unsplash.com/photo-1552832230-c0197dd311b5?w=800',
        description: 'An open-air museum of nearly 3,000 years of globally influential art and architecture.',
        averageRating: 4.8,
        tags: ['History', 'Architecture', 'Food']
      }
    ];
  }

  async getRecommendedDestinations(): Promise<DestinationCardDto[]> {
    return this.getPopularDestinations();
  }
}
