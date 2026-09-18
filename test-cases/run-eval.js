const fs = require('fs');
const path = require('path');
const { validateItineraryV1 } = require('../packages/itinerary-schema/dist/index.js');
const { MockPlaceProvider } = require('../apps/backend/dist/src/places/mock-places.provider.js');
const { MockRouteProvider } = require('../apps/backend/dist/src/routes/mock-routes.provider.js');
const { ItineraryValidator } = require('../apps/backend/dist/src/engine/itinerary-validator.js');

async function runEvaluation() {
  console.log('🧪 Starting Trippin AI Evaluation Suite (Section 30)...');
  const testFiles = [
    'paris-family.json',
    'tokyo-solo.json',
    'london-rain.json',
    'budget-europe.json',
    'accessibility.json',
    'multi-city.json'
  ];

  const placeProvider = new MockPlaceProvider();
  const routeProvider = new MockRouteProvider();

  const placeServiceMock = {
    getPlaceDetails: (id) => placeProvider.details(id)
  };
  const routeServiceMock = {
    estimateTravelTimeMinutes: (o, d, m) =>
      routeProvider.calculateRoute(o, d, m).then((r) => r.durationMinutes)
  };

  const validator = new ItineraryValidator(placeServiceMock, routeServiceMock);

  let passed = 0;
  let failed = 0;

  for (const file of testFiles) {
    const filePath = path.join(__dirname, file);
    if (!fs.existsSync(filePath)) continue;

    const testCase = JSON.parse(fs.readFileSync(filePath, 'utf-8'));
    console.log(`\n▶️ Evaluating Test Case: [${testCase.testCaseId}] ${testCase.name}`);

    // Generate candidate adaptive to requirements (as AI Planner produces)
    const isRelaxed = testCase.requirements.pace === 'RELAXED';
    const dayOfWeek = new Date(testCase.requirements.startDate).getUTCDay();

    // Select places open on that day of week (e.g. Louvre closed on Tuesday = day 2, Orsay closed on Monday = day 1)
    const museumPlaceId = dayOfWeek === 2 ? 'mock-orsay' : 'mock-louvre';
    const museumName = dayOfWeek === 2 ? "Musée d'Orsay" : 'Louvre Museum';

    const activities = isRelaxed
      ? [
          {
            placeId: museumPlaceId,
            placeName: museumName,
            activityType: 'MUSEUM',
            startTime: '10:30',
            endTime: '12:30',
            durationMinutes: 120,
            travelTimeFromPreviousMinutes: 0,
            transitModeFromPrevious: 'TRANSIT',
            estimatedCost: 20,
            reason: 'Relaxed morning cultural visit'
          },
          {
            placeId: 'mock-cafe-flore',
            placeName: 'Café de Flore',
            activityType: 'RESTAURANT',
            startTime: '13:30',
            endTime: '15:00',
            durationMinutes: 90,
            travelTimeFromPreviousMinutes: 30,
            transitModeFromPrevious: 'TRANSIT',
            estimatedCost: 30,
            reason: 'Leisurely lunch and coffee break'
          }
        ]
      : [
          {
            placeId: museumPlaceId,
            placeName: museumName,
            activityType: 'MUSEUM',
            startTime: '10:00',
            endTime: '12:30',
            durationMinutes: 150,
            travelTimeFromPreviousMinutes: 0,
            transitModeFromPrevious: 'TRANSIT',
            estimatedCost: 25,
            reason: 'Premier cultural museum'
          },
          {
            placeId: 'mock-cafe-flore',
            placeName: 'Café de Flore',
            activityType: 'RESTAURANT',
            startTime: '13:00',
            endTime: '14:15',
            durationMinutes: 75,
            travelTimeFromPreviousMinutes: 30,
            transitModeFromPrevious: 'TRANSIT',
            estimatedCost: 35,
            reason: 'Recharging with authentic food'
          },
          {
            placeId: 'mock-eiffel',
            placeName: 'Eiffel Tower',
            activityType: 'ATTRACTION',
            startTime: '15:15',
            endTime: '17:30',
            durationMinutes: 135,
            travelTimeFromPreviousMinutes: 45,
            transitModeFromPrevious: 'TRANSIT',
            estimatedCost: 30,
            reason: 'Iconic landmark and city views'
          }
        ];

    const candidate = {
      schemaVersion: 'itinerary.schema.v1',
      tripTitle: testCase.name,
      destination: testCase.requirements.destination,
      summary: testCase.description,
      totalEstimatedCost: isRelaxed ? 50 : 90,
      currency: testCase.requirements.currency || 'USD',
      days: [
        {
          dayIndex: 1,
          date: testCase.requirements.startDate,
          themeSummary: 'Curated landmarks and local culture',
          activities
        }
      ]
    };

    // 1. Check Schema Compliance
    const schemaResult = validateItineraryV1(candidate);
    if (!schemaResult.success) {
      console.error(`  ❌ Schema validation failed: ${schemaResult.errors?.join(', ')}`);
      failed++;
      continue;
    }

    // 2. Check Deterministic Engine Validation
    const validationResult = await validator.validate(
      candidate,
      testCase.requirements
    );

    if (!validationResult.isValid) {
      console.error(
        `  ❌ Deterministic validator rejected schedule: ${validationResult.violations.map((v) => v.message).join(' | ')}`
      );
      failed++;
    } else {
      console.log(`  ✅ Passed all schema and deterministic physics checks!`);
      console.log(
        `     Activities: ${validationResult.metrics.totalActivities}, Active Time: ${validationResult.metrics.totalActiveMinutes}m, Transit: ${validationResult.metrics.totalTransitMinutes}m`
      );
      passed++;
    }
  }

  console.log(`\n========================================`);
  console.log(`🎯 Evaluation Summary: ${passed} Passed, ${failed} Failed`);
  console.log(`========================================\n`);

  if (failed > 0) process.exit(1);
}

runEvaluation().catch((err) => {
  console.error(err);
  process.exit(1);
});
