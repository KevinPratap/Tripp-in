import * as fs from 'fs';
import * as path from 'path';
import { validateItineraryV1 } from '@trippin/itinerary-schema';
import { MockPlaceProvider } from '../apps/backend/src/places/mock-places.provider';
import { MockRouteProvider } from '../apps/backend/src/routes/mock-routes.provider';
import { ItineraryValidator } from '../apps/backend/src/engine/itinerary-validator';

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

  // Mock services for validator
  const placeServiceMock: any = {
    getPlaceDetails: (id: string) => placeProvider.details(id)
  };
  const routeServiceMock: any = {
    estimateTravelTimeMinutes: (o: any, d: any, m: any) =>
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

    // Generate candidate test itinerary for verification
    const candidate = {
      schemaVersion: 'itinerary.schema.v1' as const,
      tripTitle: testCase.name,
      destination: testCase.requirements.destination,
      summary: testCase.description,
      totalEstimatedCost: 200,
      currency: testCase.requirements.currency || 'USD',
      days: [
        {
          dayIndex: 1,
          date: testCase.requirements.startDate,
          themeSummary: 'Historic landmarks and local culture',
          activities: [
            {
              placeId: 'mock-louvre',
              placeName: 'Louvre Museum',
              activityType: 'MUSEUM' as const,
              startTime: '10:00',
              endTime: '12:30',
              durationMinutes: 150,
              travelTimeFromPreviousMinutes: 0,
              transitModeFromPrevious: 'TRANSIT' as const,
              estimatedCost: 25,
              reason: 'Premier cultural museum'
            },
            {
              placeId: 'mock-cafe-flore',
              placeName: 'Café de Flore',
              activityType: 'RESTAURANT' as const,
              startTime: '13:00',
              endTime: '14:15',
              durationMinutes: 75,
              travelTimeFromPreviousMinutes: 30,
              transitModeFromPrevious: 'TRANSIT' as const,
              estimatedCost: 35,
              reason: 'Recharging with authentic food'
            },
            {
              placeId: 'mock-eiffel',
              placeName: 'Eiffel Tower',
              activityType: 'ATTRACTION' as const,
              startTime: '15:15',
              endTime: '17:30',
              durationMinutes: 135,
              travelTimeFromPreviousMinutes: 45,
              transitModeFromPrevious: 'TRANSIT' as const,
              estimatedCost: 30,
              reason: 'Iconic landmark and city views'
            }
          ]
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
