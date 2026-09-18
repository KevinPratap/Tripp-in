import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  console.log('🌱 Seeding Trippin AI database...');

  // 1. Create Demo User
  const demoUser = await prisma.user.upsert({
    where: { email: 'traveler@trippin.ai' },
    update: {},
    create: {
      id: '00000000-0000-0000-0000-000000000001',
      firebaseUid: 'demo-firebase-uid-001',
      email: 'traveler@trippin.ai',
      profile: {
        create: {
          displayName: 'Alex Rivers',
          photoUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300',
          phoneNumber: '+15551234567'
        }
      },
      preference: {
        create: {
          travelStyles: ['CULTURAL', 'RELAXATION'],
          pace: 'MODERATE',
          foodPreferences: ['LOCAL_CUISINE', 'VEGETARIAN'],
          transportPreference: 'PUBLIC_TRANSIT',
          currency: 'USD',
          interests: ['Art', 'History', 'Food', 'Architecture'],
          accessibilityRequirements: []
        }
      }
    }
  });

  console.log(`👤 User created: ${demoUser.email}`);

  // 2. Seed Destinations
  const destinationsData = [
    {
      id: '10000000-0000-0000-0000-000000000001',
      name: 'Paris',
      city: 'Paris',
      country: 'France',
      description: 'The City of Light, world capital of art, gastronomy and culture.',
      latitude: 48.8566,
      longitude: 2.3522,
      imageUrl: 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=800',
      popularityScore: 9.8
    },
    {
      id: '10000000-0000-0000-0000-000000000002',
      name: 'Tokyo',
      city: 'Tokyo',
      country: 'Japan',
      description: 'Ultra-modern skyscrapers meet historic temples and unmatched street food.',
      latitude: 35.6762,
      longitude: 139.6503,
      imageUrl: 'https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=800',
      popularityScore: 9.9
    },
    {
      id: '10000000-0000-0000-0000-000000000003',
      name: 'Rome',
      city: 'Rome',
      country: 'Italy',
      description: 'An open-air museum of nearly 3,000 years of globally influential art, architecture and culture.',
      latitude: 41.9028,
      longitude: 12.4964,
      imageUrl: 'https://images.unsplash.com/photo-1552832230-c0197dd311b5?w=800',
      popularityScore: 9.5
    },
    {
      id: '10000000-0000-0000-0000-000000000004',
      name: 'London',
      city: 'London',
      country: 'United Kingdom',
      description: 'From iconic red buses and royalty to thriving culinary markets and West End theater.',
      latitude: 51.5074,
      longitude: -0.1278,
      imageUrl: 'https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?w=800',
      popularityScore: 9.4
    },
    {
      id: '10000000-0000-0000-0000-000000000005',
      name: 'Bali',
      city: 'Denpasar',
      country: 'Indonesia',
      description: 'Tropical paradise known for volcanic mountains, iconic rice paddies, beaches and coral reefs.',
      latitude: -8.4095,
      longitude: 115.1889,
      imageUrl: 'https://images.unsplash.com/photo-1537996194471-e657df975ab4?w=800',
      popularityScore: 9.6
    }
  ];

  for (const dest of destinationsData) {
    await prisma.destination.upsert({
      where: { id: dest.id },
      update: {},
      create: dest
    });
  }
  console.log(`🌍 Seeded ${destinationsData.length} destinations.`);

  // 3. Seed Places in Paris
  const parisPlaces = [
    {
      id: '20000000-0000-0000-0000-000000000001',
      googlePlaceId: 'ChIJD7fiBh9u5kcRYJSMaMOCCwQ',
      name: 'Louvre Museum',
      formattedAddress: 'Rue de Rivoli, 75001 Paris, France',
      latitude: 48.8606,
      longitude: 2.3376,
      rating: 4.7,
      userRatingsTotal: 284000,
      priceLevel: 2,
      photoUrls: ['https://images.unsplash.com/photo-1565099824688-e93eb20fe622?w=800'],
      types: ['museum', 'point_of_interest', 'establishment'],
      openingHoursJson: {
        weekdayDescriptions: [
          'Monday: 9:00 AM - 6:00 PM',
          'Tuesday: Closed',
          'Wednesday: 9:00 AM - 9:00 PM',
          'Thursday: 9:00 AM - 6:00 PM',
          'Friday: 9:00 AM - 9:00 PM',
          'Saturday: 9:00 AM - 6:00 PM',
          'Sunday: 9:00 AM - 6:00 PM'
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
      id: '20000000-0000-0000-0000-000000000002',
      googlePlaceId: 'ChIJLU7jZClu5kcR4PcOOO6p3I0',
      name: 'Eiffel Tower',
      formattedAddress: 'Champ de Mars, 5 Av. Anatole France, 75007 Paris, France',
      latitude: 48.8584,
      longitude: 2.2945,
      rating: 4.6,
      userRatingsTotal: 340000,
      priceLevel: 3,
      photoUrls: ['https://images.unsplash.com/photo-1511739001486-6bfe10ce785f?w=800'],
      types: ['tourist_attraction', 'point_of_interest', 'establishment'],
      openingHoursJson: {
        weekdayDescriptions: ['Open daily: 9:00 AM - 11:45 PM'],
        periods: [
          { open: { day: 0, time: '09:00' }, close: { day: 0, time: '23:45' } },
          { open: { day: 1, time: '09:00' }, close: { day: 1, time: '23:45' } },
          { open: { day: 2, time: '09:00' }, close: { day: 2, time: '23:45' } },
          { open: { day: 3, time: '09:00' }, close: { day: 3, time: '23:45' } },
          { open: { day: 4, time: '09:00' }, close: { day: 4, time: '23:45' } },
          { open: { day: 5, time: '09:00' }, close: { day: 5, time: '23:45' } },
          { open: { day: 6, time: '09:00' }, close: { day: 6, time: '23:45' } }
        ]
      }
    },
    {
      id: '20000000-0000-0000-0000-000000000003',
      googlePlaceId: 'ChIJ9T6R0tBv5kcRt726Z1aGg3w',
      name: "Musée d'Orsay",
      formattedAddress: '1 Rue de la Légion d\'Honneur, 75007 Paris, France',
      latitude: 48.8599,
      longitude: 2.3265,
      rating: 4.7,
      userRatingsTotal: 85000,
      priceLevel: 2,
      photoUrls: ['https://images.unsplash.com/photo-1582561424760-0321d75e81fa?w=800'],
      types: ['museum', 'tourist_attraction'],
      openingHoursJson: {
        weekdayDescriptions: ['Monday: Closed', 'Tuesday-Sunday: 9:30 AM - 6:00 PM'],
        periods: [
          { open: { day: 2, time: '09:30' }, close: { day: 2, time: '18:00' } },
          { open: { day: 3, time: '09:30' }, close: { day: 3, time: '18:00' } },
          { open: { day: 4, time: '09:30' }, close: { day: 4, time: '21:45' } },
          { open: { day: 5, time: '09:30' }, close: { day: 5, time: '18:00' } },
          { open: { day: 6, time: '09:30' }, close: { day: 6, time: '18:00' } },
          { open: { day: 0, time: '09:30' }, close: { day: 0, time: '18:00' } }
        ]
      }
    }
  ];

  for (const place of parisPlaces) {
    await prisma.place.upsert({
      where: { googlePlaceId: place.googlePlaceId },
      update: {},
      create: place
    });
  }
  console.log(`🏛️ Seeded ${parisPlaces.length} places in Paris.`);

  // 4. Seed Weather for Paris
  const now = new Date();
  for (let i = 0; i < 5; i++) {
    const forecastDate = new Date(now);
    forecastDate.setDate(now.getDate() + i);
    forecastDate.setHours(0, 0, 0, 0);

    await prisma.weatherForecast.upsert({
      where: {
        destinationId_forecastDate: {
          destinationId: '10000000-0000-0000-0000-000000000001',
          forecastDate
        }
      },
      update: {},
      create: {
        destinationId: '10000000-0000-0000-0000-000000000001',
        forecastDate,
        temperatureCelsius: 18 + i,
        temperatureMin: 12 + i,
        temperatureMax: 22 + i,
        condition: i % 2 === 0 ? 'PARTLY_CLOUDY' : 'SUNNY',
        precipitationProb: i * 15,
        windSpeedKmh: 12.5,
        advisoryNote: 'Mild spring conditions, ideal for walking.'
      }
    });
  }
  console.log(`☀️ Seeded weather forecasts.`);
  console.log('✅ Seeding completed successfully!');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
