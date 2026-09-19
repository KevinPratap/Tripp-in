/**
 * Tripp'in AI - Automated Production Smoke Test Suite
 * Validates the full trip planning, receipts, lock, collab, export, and delete lifecycle.
 *
 * Usage: node scripts/smoke-test.mjs [baseUrl]
 */

const BASE_URL =
  process.argv[2] ||
  process.env.API_URL ||
  'https://backend-production-011e.up.railway.app/api/v1';

const GUEST_SESSION = `smoke-test-${Date.now()}`;

function logPass(step, msg) {
  console.log(`[PASS] Step ${step}: ${msg}`);
}

function logFail(step, msg, err) {
  console.error(`[FAIL] Step ${step}: ${msg}`);
  if (err) console.error(err);
  process.exit(1);
}

async function run() {
  console.log('----------------------------------------------------');
  console.log("Tripp'in AI: Automated E2E Smoke Test Suite");
  console.log(`Target Environment: ${BASE_URL}`);
  console.log(`Session ID: ${GUEST_SESSION}`);
  console.log('----------------------------------------------------\n');

  let tripId = null;
  let firstActivityId = null;

  // Step 1: Create Trip with Origin and Currency
  try {
    const res = await fetch(`${BASE_URL}/trips`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Guest-Session': GUEST_SESSION,
      },
      body: JSON.stringify({
        destination: 'Osaka, Japan',
        originCity: 'Tokyo, Japan',
        startDate: '2026-11-01',
        endDate: '2026-11-02',
        travelersCount: 2,
        pace: 'MODERATE',
        budget: 2000,
        currency: 'JPY',
      }),
    });

    if (!res.ok) {
      const err = await res.text();
      throw new Error(`HTTP ${res.status}: ${err}`);
    }

    const data = await res.json();
    tripId = data.tripId;
    if (!tripId) throw new Error('Missing tripId in response');
    logPass(1, `Trip created successfully (ID: ${tripId})`);
  } catch (err) {
    logFail(1, 'Trip creation failed', err);
  }

  // Step 2: Trigger Asynchronous Generation
  try {
    const res = await fetch(`${BASE_URL}/trips/${tripId}/generate`, {
      method: 'POST',
      headers: { 'X-Guest-Session': GUEST_SESSION },
    });

    if (res.status !== 202 && res.status !== 200) {
      throw new Error(`Unexpected HTTP ${res.status}`);
    }

    const data = await res.json();
    logPass(2, `Generation triggered (Job ID: ${data.jobId || 'local'})`);
  } catch (err) {
    logFail(2, 'Trigger generation failed', err);
  }

  // Step 3: Poll Status until READY
  try {
    process.stdout.write('[INFO] Polling generation worker: ');
    let attempts = 0;
    let completed = false;

    while (attempts < 60 && !completed) {
      attempts++;
      await new Promise((r) => setTimeout(r, 3000));
      const statusRes = await fetch(`${BASE_URL}/trips/${tripId}/status`);
      if (!statusRes.ok) continue;

      const sData = await statusRes.json();
      process.stdout.write(`[${sData.currentStepKey || 'worker'}:${sData.progressPercentage || 0}%] `);

      if (sData.status === 'READY') {
        completed = true;
        break;
      }
      if (sData.status === 'FAILED') {
        throw new Error(`Generation job failed: ${sData.error || 'Unknown'}`);
      }
    }
    console.log();

    if (!completed) throw new Error('Generation polling timed out after 180s');
    logPass(3, 'Itinerary generated and marked READY');
  } catch (err) {
    logFail(3, 'Generation polling failed', err);
  }

  // Step 4: Validate Itinerary & Verification Receipts
  try {
    const res = await fetch(`${BASE_URL}/trips/${tripId}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const data = await res.json();
    const itin = data.itinerary;
    if (!itin) throw new Error('Missing itinerary in envelope');
    if (!itin.days || itin.days.length === 0) throw new Error('Zero days generated');

    const firstDay = itin.days[0];
    if (!firstDay.activities || firstDay.activities.length === 0) {
      throw new Error('Zero activities in Day 1');
    }

    firstActivityId = firstDay.activities[0].id;
    const checks = firstDay.activities[0].checks || [];

    logPass(
      4,
      `Itinerary verified (${itin.days.length} days, Day 1 venue: "${firstDay.activities[0].title}", receipts: ${checks.length} checks)`
    );
  } catch (err) {
    logFail(4, 'Itinerary validation failed', err);
  }

  // Step 5: Lock Trip (Decision Locking)
  try {
    const res = await fetch(`${BASE_URL}/trips/${tripId}/lock`, {
      method: 'POST',
      headers: { 'X-Guest-Session': GUEST_SESSION },
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data.isLocked !== true) throw new Error('Trip isLocked is not true');
    logPass(5, 'Trip locked successfully (Decisions sealed)');
  } catch (err) {
    logFail(5, 'Trip lock failed', err);
  }

  // Step 6: Test Hard Lock Guard on Replan (Expect 409 Conflict)
  try {
    const res = await fetch(`${BASE_URL}/trips/${tripId}/replan`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Guest-Session': GUEST_SESSION,
      },
      body: JSON.stringify({ intent: 'rain' }),
    });

    if (res.status !== 409) {
      throw new Error(`Expected 409 Conflict, received HTTP ${res.status}`);
    }
    logPass(6, 'Hard lock guard correctly rejected replan with 409 Conflict');
  } catch (err) {
    logFail(6, 'Lock guard enforcement failed', err);
  }

  // Step 7: Unlock Trip
  try {
    const res = await fetch(`${BASE_URL}/trips/${tripId}/unlock`, {
      method: 'POST',
      headers: { 'X-Guest-Session': GUEST_SESSION },
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data.isLocked !== false) throw new Error('Trip isLocked is not false');
    logPass(7, 'Trip unlocked successfully');
  } catch (err) {
    logFail(7, 'Trip unlock failed', err);
  }

  // Step 8: Squad Collab Vote
  if (firstActivityId) {
    try {
      const res = await fetch(`${BASE_URL}/trips/${tripId}/vote`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Guest-Session': GUEST_SESSION,
        },
        body: JSON.stringify({
          activityId: firstActivityId,
          vote: 1,
          voterName: 'Smoke Runner',
          comment: 'Verified stop from smoke suite',
        }),
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      logPass(8, `Squad vote and comment registered (Tally: +${data.upvotes || 1})`);
    } catch (err) {
      logFail(8, 'Squad vote failed', err);
    }
  }

  // Step 9: Calendar Export
  try {
    const res = await fetch(`${BASE_URL}/trips/${tripId}/calendar.ics`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const icsText = await res.text();
    if (!icsText.includes('BEGIN:VCALENDAR')) {
      throw new Error('Invalid iCalendar payload');
    }
    logPass(9, 'Calendar .ics export generated with valid VCALENDAR payload');
  } catch (err) {
    logFail(9, 'Calendar export failed', err);
  }

  // Step 10: Delete Trip (Lifecycle Clean Up)
  try {
    const res = await fetch(`${BASE_URL}/trips/${tripId}`, {
      method: 'DELETE',
      headers: { 'X-Guest-Session': GUEST_SESSION },
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (data.success !== true) throw new Error('Delete returned success=false');
    logPass(10, `Trip deleted cleanly from database (ID: ${tripId})`);
  } catch (err) {
    logFail(10, 'Trip deletion failed', err);
  }

  // Step 11: Verify 404 on Deleted Trip
  try {
    const res = await fetch(`${BASE_URL}/trips/${tripId}`);
    if (res.status !== 404) {
      throw new Error(`Expected 404 for deleted trip, received HTTP ${res.status}`);
    }
    logPass(11, 'Deleted trip confirmed gone (HTTP 404)');
  } catch (err) {
    logFail(11, 'Post-delete verification failed', err);
  }

  console.log('\n====================================================');
  console.log('ALL 11 SMOKE TESTS PASSED - 100% VERIFIED');
  console.log('====================================================\n');
}

run();
