const { AIPlannerService } = require('../apps/backend/dist/src/ai/ai-planner.service.js');
const { MockPlaceProvider } = require('../apps/backend/dist/src/places/mock-places.provider.js');
const { MockRouteProvider } = require('../apps/backend/dist/src/routes/mock-routes.provider.js');
const { ItineraryValidator } = require('../apps/backend/dist/src/engine/itinerary-validator.js');
const { WeatherService } = require('../apps/backend/dist/src/weather/weather.service.js');

const fs = require('fs');
const path = require('path');

const envPath = path.resolve(__dirname, '../.env');
if (fs.existsSync(envPath)) {
  fs.readFileSync(envPath, 'utf8')
    .split('\n')
    .forEach((line) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) return;
      const [k, ...v] = trimmed.split('=');
      if (k && v.length) {
        process.env[k.trim()] = v.join('=').trim().replace(/^["']|["']$/g, '');
      }
    });
}

async function testGeminiLive() {
  console.log("🚀 Testing Trippin' AI with Free Google Gemini Model...");

  const mockConfig = {
    get: (key, fallback) => {
      if (key === 'OPENAI_BASE_URL') return process.env.OPENAI_BASE_URL || 'https://generativelanguage.googleapis.com/v1beta/openai/';
      if (key === 'OPENAI_API_KEY') return process.env.OPENAI_API_KEY || process.env.GEMINI_API_KEY;
      if (key === 'OPENAI_MODEL') return process.env.OPENAI_MODEL || 'gemini-flash-latest';
      return fallback;
    }
  };

  const placeProvider = new MockPlaceProvider();
  const routeProvider = new MockRouteProvider();

  const placeServiceMock = {
    searchPlaces: (q) => placeProvider.search({ query: q }),
    getPlaceDetails: (id) => placeProvider.details(id)
  };

  const routeServiceMock = {
    estimateTravelTimeMinutes: (o, d, m) =>
      routeProvider.calculateRoute(o, d, m).then((r) => r.durationMinutes)
  };

  const mockRedis = {
    get: async () => null,
    set: async () => {}
  };

  const weatherService = new WeatherService(mockRedis);
  const validator = new ItineraryValidator(placeServiceMock, routeServiceMock);

  const planner = new AIPlannerService(
    mockConfig,
    placeServiceMock,
    routeServiceMock,
    weatherService,
    validator
  );

  const req = {
    destination: 'Paris',
    startDate: '2026-06-01',
    endDate: '2026-06-03',
    travelersCount: 2,
    pace: 'MODERATE',
    budgetTotal: 1500,
    currency: 'USD',
    travelStyles: ['CULTURAL'],
    interests: ['Art', 'Museums', 'Cuisine']
  };

  try {
    const outcome = await planner.planItinerary(req);
    console.log('\n=============================================');
    console.log(`✅ Planning Succeeded: ${outcome.success}`);
    console.log(`🤖 Model Used: ${outcome.modelUsed}`);
    console.log(`🔄 Repair Iterations: ${outcome.repairIterations}`);
    if (outcome.itinerary) {
      console.log(`📍 Trip Title: ${outcome.itinerary.tripTitle || outcome.itinerary.title}`);
      console.log(`📅 Days Planned: ${outcome.itinerary.days?.length}`);
      outcome.itinerary.days?.forEach((d) => {
        console.log(`   Day ${d.dayIndex || d.dayNumber} (${d.date}): ${d.activities?.length} activities`);
        d.activities?.forEach((a) => {
          console.log(`      * [${a.startTime} - ${a.endTime}] ${a.placeName || a.title} (${a.activityType || a.category})`);
        });
      });
    }
    console.log('=============================================\n');
  } catch (err) {
    console.error('❌ Error during Gemini planning:', err);
  }
}

testGeminiLive();
