/**
 * LLM evaluation harness: how often do REAL model-written itineraries pass the deterministic validator?
 *
 * Unlike run-eval.ts (one hardcoded candidate, tests the validator), this drives the production
 * AIPlannerService.planItinerary() (prompt builder -> model call -> schema check -> validator ->
 * bounded repair loop -> deterministic fallback) N times per scenario and measures the outcome.
 *
 * Usage (from the repo root, see README "Measuring the LLM"):
 *   pnpm eval:llm -- --n 10                 # real model, needs GEMINI_API_KEY or OPENAI_API_KEY
 *   pnpm eval:llm -- --dry-run --n 10       # no key, no network: synthetic fake model (NOT a measurement)
 *
 * Flags: --n <int> (default 10)  --dry-run  --scenario <id[,id]>  --seed <int> (dry-run)
 *        --out-dir <path>  --delay-ms <int> (pause between runs, default 1500 real / 0 dry)
 *        --env-file <path> (explicit dotenv file; never auto-loaded)  --verbose
 */
import * as fs from 'fs';
import * as path from 'path';
import { createRequire } from 'module';
import type { PlaceModel, TripRequirement } from '../packages/shared-types/src';
import { MockPlaceProvider } from '../apps/backend/src/places/mock-places.provider';
import { MockRouteProvider } from '../apps/backend/src/routes/mock-routes.provider';
import { ItineraryValidator } from '../apps/backend/src/engine/itinerary-validator';
import { AIPlannerService } from '../apps/backend/src/ai/ai-planner.service';

// Resolve backend-only dependencies (@nestjs/common, dotenv) from the backend package, since this
// file lives outside it.
const backendRequire = createRequire(path.join(__dirname, '../apps/backend/package.json'));

const SCENARIO_FILES = [
  'paris-family.json',
  'tokyo-solo.json',
  'london-rain.json',
  'budget-europe.json',
  'accessibility.json',
  'multi-city.json'
];
const MAX_REPAIRS = 3; // mirrors `maxRepairs` in AIPlannerService.planItinerary

// ---------------------------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------------------------
interface Args {
  n: number;
  dryRun: boolean;
  scenarios: string[] | null;
  seed: number;
  outDir: string;
  delayMs: number;
  envFile: string | null;
  verbose: boolean;
}

function parseArgs(argv: string[]): Args {
  const a: Args = {
    n: 10,
    dryRun: false,
    scenarios: null,
    seed: 1,
    outDir: path.join(__dirname, 'results'),
    delayMs: -1,
    envFile: null,
    verbose: false
  };
  for (let i = 0; i < argv.length; i++) {
    const k = argv[i];
    const next = () => argv[++i];
    if (k === '--') continue;
    else if (k === '--n') a.n = parseInt(next(), 10);
    else if (k === '--dry-run') a.dryRun = true;
    else if (k === '--scenario') a.scenarios = next().split(',').map((s) => s.trim());
    else if (k === '--seed') a.seed = parseInt(next(), 10);
    else if (k === '--out-dir') a.outDir = path.resolve(process.env.INIT_CWD || process.cwd(), next());
    else if (k === '--delay-ms') a.delayMs = parseInt(next(), 10);
    else if (k === '--env-file') a.envFile = path.resolve(process.env.INIT_CWD || process.cwd(), next());
    else if (k === '--verbose') a.verbose = true;
    else throw new Error(`Unknown argument: ${k}`);
  }
  if (!Number.isInteger(a.n) || a.n < 1) throw new Error('--n must be a positive integer');
  if (a.delayMs < 0) a.delayMs = a.dryRun ? 0 : 1500;
  return a;
}

// ---------------------------------------------------------------------------------------------
// Mock world: per-scenario venue catalogue, geocoder, weather. Place/route math comes from the
// repo's own MockPlaceProvider / MockRouteProvider.
// ---------------------------------------------------------------------------------------------
const CITY_CENTRES: Record<string, { latitude: number; longitude: number }> = {
  paris: { latitude: 48.8566, longitude: 2.3522 },
  tokyo: { latitude: 35.6762, longitude: 139.6503 },
  kyoto: { latitude: 35.0116, longitude: 135.7681 },
  london: { latitude: 51.5072, longitude: -0.1276 },
  rome: { latitude: 41.9028, longitude: 12.4964 }
};

const ATTRACTION_SUFFIXES = [
  'National Museum', 'Cathedral', 'Central Park', 'Castle', 'Art Gallery', 'Historic Market',
  'Botanical Garden', 'Science Museum', 'Riverside Promenade', 'Old Quarter', 'Observation Tower',
  'City Library Hall', 'Opera House', 'Maritime Museum'
];
const FOOD_SUFFIXES = [
  'Corner Bistro', 'Harbour Kitchen', 'Market Canteen', 'Garden Cafe', 'Noodle House', 'Rooftop Grill'
];

type Period = { open: { day: number; time: string }; close: { day: number; time: string } };
const allDays = [0, 1, 2, 3, 4, 5, 6];
const daily = (open: string, close: string, except: number[] = []): Period[] =>
  allDays
    .filter((d) => !except.includes(d))
    .map((day) => ({ open: { day, time: open }, close: { day, time: close } }));

function syntheticCity(key: string, label: string): PlaceModel[] {
  const c = CITY_CENTRES[key];
  const out: PlaceModel[] = [];
  const at = (i: number, total: number) => {
    const ang = (i / total) * Math.PI * 2 * 2.3;
    const r = 0.008 + 0.004 * (i % 4);
    return { latitude: c.latitude + r * Math.sin(ang), longitude: c.longitude + r * 1.3 * Math.cos(ang) };
  };
  ATTRACTION_SUFFIXES.forEach((suffix, i) => {
    const isMuseum = /Museum|Gallery|Library/.test(suffix);
    const isPark = /Park|Garden|Promenade/.test(suffix);
    const periods = isPark
      ? daily('06:00', '22:00')
      : isMuseum
        ? daily('09:00', '18:00', [i % 2 === 0 ? 1 : 2])
        : daily('09:00', '18:00', i % 5 === 0 ? [0] : []);
    const id = `mock-${key}-a${i + 1}`;
    out.push({
      id,
      googlePlaceId: `gp-${id}`,
      name: `${label} ${suffix}`,
      formattedAddress: `${label} (mock)`,
      location: at(i, ATTRACTION_SUFFIXES.length),
      types: isMuseum ? ['museum', 'tourist_attraction'] : isPark ? ['park', 'tourist_attraction'] : ['tourist_attraction'],
      photoUrls: [],
      openingHours: { periods }
    } as PlaceModel);
  });
  FOOD_SUFFIXES.forEach((suffix, i) => {
    const id = `mock-${key}-f${i + 1}`;
    out.push({
      id,
      googlePlaceId: `gp-${id}`,
      name: `${label} ${suffix}`,
      formattedAddress: `${label} (mock)`,
      location: at(i + 3, FOOD_SUFFIXES.length + 3),
      types: i % 2 === 0 ? ['restaurant'] : ['cafe', 'restaurant'],
      photoUrls: [],
      openingHours: { periods: daily('11:00', '23:00') }
    } as PlaceModel);
  });
  return out;
}

async function buildCatalogue(destination: string): Promise<PlaceModel[]> {
  const d = destination.toLowerCase();
  if (d.includes('tokyo') && d.includes('kyoto')) {
    return [...syntheticCity('tokyo', 'Tokyo'), ...syntheticCity('kyoto', 'Kyoto')];
  }
  if (d.includes('paris')) {
    // The repo's own five Paris mock venues plus synthetic ones so a multi-day plan is possible.
    const real = await new MockPlaceProvider().search({ query: '' });
    return [...real, ...syntheticCity('paris', 'Paris')];
  }
  for (const key of Object.keys(CITY_CENTRES)) {
    if (d.includes(key)) return syntheticCity(key, key[0].toUpperCase() + key.slice(1));
  }
  throw new Error(`No mock catalogue for destination "${destination}"`);
}

function geocode(destination: string) {
  const d = destination.toLowerCase();
  for (const key of Object.keys(CITY_CENTRES)) if (d.includes(key)) return CITY_CENTRES[key];
  return null;
}

function mockForecast(startDate: string, rainy: boolean) {
  const out: any[] = [];
  const start = new Date(startDate + 'T00:00:00Z');
  for (let i = 0; i < 5; i++) {
    const dt = new Date(start.getTime() + i * 86400000);
    out.push({
      date: dt.toISOString().slice(0, 10),
      temperatureCelsius: rainy ? 11 : 22,
      temperatureMinCelsius: rainy ? 8 : 17,
      temperatureMaxCelsius: rainy ? 13 : 25,
      condition: rainy ? 'RAIN' : 'CLEAR',
      precipitationProbability: rainy ? 85 : 5,
      windSpeedKmh: 10,
      advisoryNote: rainy ? 'Heavy rain expected, prefer indoor venues' : 'Pleasant conditions'
    });
  }
  return out;
}

// ---------------------------------------------------------------------------------------------
// Seeded RNG + dry-run fake model
// ---------------------------------------------------------------------------------------------
function mulberry32(seed: number) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function hashString(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return h >>> 0;
}

const toMin = (t: string) => {
  const [h, m] = t.split(':').map(Number);
  return h * 60 + m;
};
const fmt = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;

/** Same open/closed rule as ItineraryValidator for venues with real periods. */
function isOpen(p: PlaceModel, dow: number, start: number, end: number): boolean {
  const periods = (p.openingHours?.periods || []).filter((x: any) => x.open.day === dow);
  if (!periods.length) return false;
  return periods.some((x: any) => {
    const o = toMin(x.open.time);
    const c0 = toMin(x.close.time);
    const c = c0 < o ? c0 + 1440 : c0;
    return start >= o && end <= c;
  });
}
const isFood = (p: PlaceModel) => p.types.includes('restaurant') || p.types.includes('cafe');

interface FakeCtx {
  rng: () => number;
  /** A "stubborn" run keeps producing defective plans, so the repair loop exhausts and the fallback runs. */
  stubborn: boolean;
  catalogue: PlaceModel[];
  calls: number;
}

type Stop = { place: PlaceModel; start: number; end: number; type: string };

function buildCleanItinerary(prompt: string, ctx: FakeCtx): any {
  const dates = /- Dates: (\d{4}-\d{2}-\d{2}) to (\d{4}-\d{2}-\d{2})/.exec(prompt)!;
  const pace = (/- Pace: (\w+)/.exec(prompt) || [])[1] || 'MODERATE';
  const dest = (/- Destination: (.+)/.exec(prompt) || [])[1] || 'Unknown';
  const start = new Date(dates[1]);
  const end = new Date(dates[2]);
  const nDays = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / 86400000));
  // The model may only use venues that appear in the prompt's "Available Verified Places" list.
  const listedIds = new Set([...prompt.matchAll(/PlaceID: "([^"]+)"/g)].map((m) => m[1]));
  const places = ctx.catalogue.filter((p) => listedIds.has(p.id));
  // Multi-city trips: first half of the days in Tokyo, second half in Kyoto. Otherwise one cluster.
  const clusterOf = (p: PlaceModel) => (p.id.includes('-kyoto-') ? 'kyoto' : 'main');
  const clusters = [...new Set(places.map(clusterOf))].sort().reverse(); // main (Tokyo) first, then kyoto
  const usedAttr = new Set<string>();
  const foodUse = new Map<string, number>();
  const days: any[] = [];

  for (let d = 0; d < nDays; d++) {
    const dt = new Date(start.getTime() + d * 86400000);
    const date = dt.toISOString().slice(0, 10);
    const dow = dt.getUTCDay();
    const cluster = clusters[Math.min(clusters.length - 1, Math.floor((d * clusters.length) / nDays))];
    const pool = places.filter((p) => clusterOf(p) === cluster);
    const pickAttr = (s: number, e: number): PlaceModel | undefined =>
      pool.find((p) => !isFood(p) && !usedAttr.has(p.id) && isOpen(p, dow, s, e));
    const pickFood = (s: number, e: number): PlaceModel | undefined =>
      pool.find((p) => isFood(p) && (foodUse.get(p.id) || 0) < 2 && isOpen(p, dow, s, e));

    const plan: Array<[string, number, number]> = [['a', 570, 660], ['f', 720, 780], ['a', 870, 960]];
    if (pace === 'RELAXED') plan.splice(2, 1);
    if (pace === 'FAST') plan.push(['f', 1140, 1230]);
    const stops: Stop[] = [];
    for (const [kind, s, e] of plan) {
      const p = kind === 'a' ? pickAttr(s, e) : pickFood(s, e);
      if (!p) continue;
      if (kind === 'a') usedAttr.add(p.id);
      else foodUse.set(p.id, (foodUse.get(p.id) || 0) + 1);
      stops.push({
        place: p,
        start: s,
        end: e,
        type: kind === 'f' ? 'RESTAURANT' : p.types.includes('museum') ? 'MUSEUM' : p.types.includes('park') ? 'PARK' : 'ATTRACTION'
      });
    }
    days.push({
      dayIndex: d + 1,
      date,
      themeSummary: `Day ${d + 1} around ${cluster}`,
      activities: stops.map((s, i) => ({
        placeId: s.place.id,
        placeName: s.place.name,
        activityType: s.type,
        startTime: fmt(s.start),
        endTime: fmt(s.end),
        durationMinutes: s.end - s.start,
        travelTimeFromPreviousMinutes: i === 0 ? 0 : 20,
        transitModeFromPrevious: 'TRANSIT',
        reason: `Plain description of ${s.place.name}`
      }))
    });
  }
  return {
    schemaVersion: 'itinerary.schema.v1',
    tripTitle: `Dry-run plan for ${dest}`,
    destination: dest,
    summary: `Synthetic dry-run itinerary for ${dest}, ${nDays} days.`,
    currency: (/- Budget: .* (\w{3})$/m.exec(prompt) || [])[1] || 'USD',
    days
  };
}

/** Deliberate defects, each aimed at one validator rule. */
const FAULTS: Array<{ name: string; apply: (it: any, ctx: FakeCtx) => boolean }> = [
  { name: 'overlap', apply: (it) => { const a = it.days[0].activities; if (a.length < 2) return false; a[1].startTime = fmt(toMin(a[0].endTime) - 30); return true; } },
  { name: 'no_travel_gap', apply: (it) => { const a = it.days[0].activities; if (a.length < 2) return false; a[1].startTime = a[0].endTime; return true; } },
  { name: 'after_closing', apply: (it) => { const a = it.days[0].activities; const i = a.findIndex((x: any) => x.activityType !== 'RESTAURANT'); if (i < 0) return false; a[i].startTime = '19:00'; a[i].endTime = '20:00'; a[i].durationMinutes = 60; return true; } },
  { name: 'repeat_venue', apply: (it) => { if (it.days.length < 2) return false; const f = it.days[0].activities.find((x: any) => x.activityType !== 'RESTAURANT'); if (!f) return false; it.days[1].activities[0] = { ...it.days[1].activities[0], placeId: f.placeId, placeName: f.placeName, activityType: f.activityType }; return true; } },
  { name: 'sparse_day', apply: (it) => { it.days[it.days.length - 1].activities.splice(1); return true; } },
  { name: 'too_early', apply: (it) => { const x = it.days[0].activities[0]; x.startTime = '06:00'; x.endTime = '08:00'; x.durationMinutes = 120; return true; } },
  { name: 'overpacked', apply: (it) => { const a = it.days[0].activities; if (a.length < 3) return false; a[0].startTime = '09:00'; a[0].endTime = '12:00'; a[0].durationMinutes = 180; a[2].startTime = '14:30'; a[2].endTime = '18:00'; a[2].durationMinutes = 210; return true; } },
  { name: 'closed_weekday', apply: (it, ctx) => { const day = it.days[0]; const dow = new Date(day.date).getUTCDay(); const bad = ctx.catalogue.find((p) => !isFood(p) && !isOpen(p, dow, 570, 660) && p.openingHours?.periods?.length); if (!bad) return false; day.activities[0].placeId = bad.id; day.activities[0].placeName = bad.name; return true; } }
];

function fakeModelResponse(args: any, ctx: FakeCtx): string {
  const prompt: string = args.messages.find((m: any) => m.role === 'user').content;
  const isRepair = prompt.includes('CRITICAL FIXES REQUIRED');
  const r = ctx.rng();
  ctx.calls++;
  if (r < 0.02) throw Object.assign(new Error('dry-run: simulated 500 from model'), { status: 500 });
  if (r < 0.05) return '{"schemaVersion":"itinerary.schema.v1","tripTitle":"x"'; // truncated JSON
  const it = buildCleanItinerary(prompt, ctx);
  const pClean = ctx.stubborn ? 0 : isRepair ? 0.7 : 0.5;
  if (ctx.rng() >= pClean) {
    // Try faults in a random order until one actually applies to this itinerary.
    const order = FAULTS.map((_, i) => i).sort(() => ctx.rng() - 0.5);
    for (const i of order) if (FAULTS[i].apply(it, ctx)) break;
  }
  const body = JSON.stringify(it);
  return ctx.rng() < 0.1 ? '```json\n' + body + '\n```' : body;
}

// ---------------------------------------------------------------------------------------------
// Instrumentation (wraps instances only; production code is untouched)
// ---------------------------------------------------------------------------------------------
interface Attempt {
  index: number;
  source: 'llm' | 'llm-error' | 'deterministic-in-loop' | 'deterministic-fallback';
  schemaValid: boolean;
  valid: boolean;
  violationCodes: string[];
  error?: string;
}
interface Pending {
  source: Attempt['source'];
  consumed: boolean;
  /** The model call threw, so the planner swaps in the deterministic generator inside the loop. */
  threw?: boolean;
  /** Set for unparseable model output: a model failure, not an infrastructure error. */
  code?: string;
  error?: string;
}

class Recorder {
  attempts: Attempt[] = [];
  pending: Pending | null = null;
  reset() { this.attempts = []; this.pending = null; }
  flush() {
    const p = this.pending;
    if (!p || p.consumed) { this.pending = null; return; }
    this.attempts.push({
      index: this.attempts.length,
      source: p.source,
      schemaValid: false,
      valid: false,
      violationCodes: [p.source === 'llm-error' ? 'LLM_CALL_ERROR' : p.code || 'SCHEMA_INVALID'],
      error: p.error
    });
    this.pending = null;
  }
}

function redactor(secrets: string[]) {
  return (s: string) => {
    let out = String(s);
    for (const sec of secrets) if (sec && sec.length > 6) out = out.split(sec).join('[REDACTED]');
    return out.replace(/key=[^&\s"']+/gi, 'key=[REDACTED]').replace(/(sk|gsk|AIza)[A-Za-z0-9_-]{10,}/g, '[REDACTED]').slice(0, 300);
  };
}

function instrument(planner: any, validator: ItineraryValidator, rec: Recorder, redact: (s: string) => string) {
  for (const name of ['callOpenAI', 'callGeminiNative']) {
    const orig = planner[name].bind(planner);
    planner[name] = async (...a: any[]) => {
      rec.flush();
      try {
        const res = await orig(...a);
        rec.pending = { source: 'llm', consumed: false };
        return res;
      } catch (e) {
        // Unparseable output (truncated or non-JSON text) is the model's fault and counts against it.
        // Anything else (HTTP errors, rate limits, network) is infrastructure and excludes the run.
        const badOutput = e instanceof SyntaxError;
        rec.pending = {
          source: badOutput ? 'llm' : 'llm-error',
          consumed: false,
          threw: true,
          code: badOutput ? 'MALFORMED_JSON' : undefined,
          error: redact((e as Error).message)
        };
        throw e;
      }
    };
  }
  const origDet = planner.generateDeterministicCandidate.bind(planner);
  planner.generateDeterministicCandidate = (...a: any[]) => {
    const inLoop = Boolean(rec.pending?.threw);
    rec.flush();
    rec.pending = { source: inLoop ? 'deterministic-in-loop' : 'deterministic-fallback', consumed: false };
    return origDet(...a);
  };
  const origValidate = validator.validate.bind(validator);
  (validator as any).validate = async (...a: any[]) => {
    const res = await (origValidate as any)(...a);
    const src = rec.pending?.source ?? 'llm';
    if (rec.pending) rec.pending.consumed = true;
    rec.attempts.push({
      index: rec.attempts.length,
      source: src,
      schemaValid: true,
      valid: res.isValid,
      violationCodes: [...new Set<string>(res.violations.map((v: any) => v.code))]
    });
    return res;
  };
}

// ---------------------------------------------------------------------------------------------
// Aggregation
// ---------------------------------------------------------------------------------------------
interface RunRecord {
  scenario: string;
  runIndex: number;
  durationMs: number;
  finalStatus: 'VERIFIED' | 'DRAFT';
  modelUsed: string;
  repairIterationsRaw: number;
  repairRounds: number;
  firstPassVerified: boolean;
  verifiedByLlm: boolean;
  fallbackUsed: boolean;
  fallbackVerified: boolean;
  llmErrors: number;
  excluded: boolean;
  harnessError?: string;
  attempts: Attempt[];
}

function wilson(k: number, n: number): [number, number] {
  if (n === 0) return [0, 0];
  const z = 1.96;
  const p = k / n;
  const den = 1 + (z * z) / n;
  const centre = (p + (z * z) / (2 * n)) / den;
  const half = (z * Math.sqrt((p * (1 - p)) / n + (z * z) / (4 * n * n))) / den;
  return [Math.max(0, centre - half), Math.min(1, centre + half)];
}
const pct = (k: number, n: number) => (n === 0 ? 'n/a' : `${((100 * k) / n).toFixed(1)}% (${k}/${n})`);

function summarise(runs: RunRecord[]) {
  const clean = runs.filter((r) => !r.excluded);
  const n = clean.length;
  const count = (f: (r: RunRecord) => boolean) => clean.filter(f).length;
  const first: Record<string, number> = {};
  const all: Record<string, number> = {};
  for (const r of clean) {
    r.attempts.forEach((a, i) => {
      if (a.source !== 'llm' || a.valid) return;
      for (const c of a.violationCodes) {
        all[c] = (all[c] || 0) + 1;
        if (i === 0) first[c] = (first[c] || 0) + 1;
      }
    });
  }
  const top = (m: Record<string, number>) =>
    Object.entries(m).sort((a, b) => b[1] - a[1]).slice(0, 5).map(([code, count]) => ({ code, count }));
  const fp = count((r) => r.firstPassVerified);
  return {
    runs: runs.length,
    excludedRuns: runs.length - n,
    cleanRuns: n,
    firstPassVerified: fp,
    firstPassVerifiedRate: n ? fp / n : null,
    firstPassWilson95: wilson(fp, n),
    verifiedAfterRepair: count((r) => r.verifiedByLlm),
    verifiedAfterRepairRate: n ? count((r) => r.verifiedByLlm) / n : null,
    fallbackUsed: count((r) => r.fallbackUsed),
    fallbackRate: n ? count((r) => r.fallbackUsed) / n : null,
    fallbackVerified: count((r) => r.fallbackUsed && r.fallbackVerified),
    finalVerified: count((r) => r.finalStatus === 'VERIFIED'),
    finalVerifiedRate: n ? count((r) => r.finalStatus === 'VERIFIED') / n : null,
    avgRepairRounds: n ? clean.reduce((s, r) => s + r.repairRounds, 0) / n : null,
    topViolationsAllAttempts: top(all),
    topViolationsFirstPass: top(first)
  };
}

type Summary = ReturnType<typeof summarise>;
function markdown(meta: any, per: Record<string, Summary>, overall: Summary): string {
  const row = (name: string, s: Summary) =>
    `| ${name} | ${s.cleanRuns}/${s.runs} | ${pct(s.firstPassVerified, s.cleanRuns)} | ${pct(s.verifiedAfterRepair, s.cleanRuns)} | ${pct(s.fallbackUsed, s.cleanRuns)} | ${pct(s.finalVerified, s.cleanRuns)} | ${s.avgRepairRounds === null ? 'n/a' : s.avgRepairRounds.toFixed(2)} | ${s.topViolationsAllAttempts.slice(0, 3).map((v) => `${v.code} (${v.count})`).join(', ') || '-'} |`;
  const lines: string[] = [];
  if (meta.mode === 'dry-run') {
    lines.push('> **DRY RUN: synthetic fake model with injected faults. These numbers say nothing about any real LLM. Do not quote them.**', '');
  }
  lines.push(
    `# LLM evaluation (${meta.mode})`, '',
    `- Timestamp: ${meta.timestamp}`,
    `- Provider/model: ${meta.provider} / ${meta.model}`,
    `- Runs per scenario: ${meta.n}  |  commit: ${meta.gitCommit}`,
    '',
    '| Scenario | Clean runs | First-pass verified | Verified after repair | Fallback used | Final VERIFIED (incl. fallback) | Avg repair rounds | Top violations (all LLM attempts) |',
    '|---|---|---|---|---|---|---|---|'
  );
  for (const [id, s] of Object.entries(per)) lines.push(row(id, s));
  lines.push(row('**OVERALL**', overall), '');
  const [lo, hi] = overall.firstPassWilson95;
  lines.push(
    `Overall first-pass verified rate: ${pct(overall.firstPassVerified, overall.cleanRuns)}, Wilson 95% CI ${(lo * 100).toFixed(1)}% to ${(hi * 100).toFixed(1)}%.`,
    '',
    '**Definitions.** Clean run = no model-call errors during the run (runs with errors are excluded from every rate and counted in the first column). ' +
      'First-pass verified = the model\'s first itinerary passed schema + validator with no repair. ' +
      'Verified after repair = some model-written itinerary (first or a repaired one, max 3 repairs) passed. ' +
      'Fallback used = the deterministic generator ran (repair loop exhausted, or the model output was unparseable). ' +
      'Final VERIFIED = AIPlannerService returned success=true (what the app stores as VERIFIED), model or fallback. ' +
      'Avg repair rounds = repair re-prompts used (0 to 3).',
    ''
  );
  return lines.join('\n');
}

// ---------------------------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------------------------
async function main() {
  const args = parseArgs(process.argv.slice(2));

  if (args.envFile) {
    if (!fs.existsSync(args.envFile)) throw new Error(`--env-file not found: ${args.envFile}`);
    backendRequire('dotenv').config({ path: args.envFile });
  }

  const { Logger } = backendRequire('@nestjs/common');
  if (!args.verbose) Logger.overrideLogger(false);

  // Dry run: the planner sleeps 2s between repair rounds to respect free-tier limits. Skip that wait.
  if (args.dryRun) {
    const realSetTimeout = global.setTimeout;
    (global as any).setTimeout = (fn: any, _ms?: number, ...rest: any[]) => realSetTimeout(fn, 0, ...rest);
  }

  const env: Record<string, string | undefined> = args.dryRun
    ? { OPENAI_API_KEY: 'dry-run-fake-key', OPENAI_MODEL: 'dry-run-model' }
    : process.env;
  const config: any = { get: (k: string, d?: any) => (env[k] !== undefined && env[k] !== '' ? env[k] : d) };

  const placeProvider = new MockPlaceProvider();
  const routeProvider = new MockRouteProvider();
  let catalogue: PlaceModel[] = [];
  let currentRequirement: any = null;
  let currentScenarioId = '';

  // Mocked collaborators. Everything the planner and validator ask for is answered offline.
  const placeService: any = {
    geocodeDestination: async (dest: string) => geocode(dest),
    searchPlaces: async () => catalogue,
    attachEntryPrices: async (p: PlaceModel[]) => p,
    getPlaceDetails: async (id: string) => {
      const q = (id || '').toLowerCase().trim();
      return (
        catalogue.find((p) => p.id.toLowerCase() === q || p.googlePlaceId.toLowerCase() === q || p.name.toLowerCase() === q) ||
        (await placeProvider.details(id)) // same lookup the Paris mock venues use in run-eval.ts
      );
    }
  };
  const routeService: any = {
    estimateTravelTimeMinutes: (o: any, d: any, m: any) =>
      routeProvider.calculateRoute(o, d, m).then((r) => r.durationMinutes)
  };
  const weatherService: any = {
    getForecast: async () => mockForecast(currentRequirement.startDate, currentScenarioId.includes('rain'))
  };

  const validator = new ItineraryValidator(placeService, routeService);
  const rec = new Recorder();
  const secrets = [process.env.GEMINI_API_KEY, process.env.OPENAI_API_KEY, process.env.GROQ_API_KEY].filter(Boolean) as string[];
  const planner: any = new AIPlannerService(config, placeService, weatherService, validator);
  instrument(planner, validator, rec, redactor(secrets));

  const fakeCtx: FakeCtx = { rng: Math.random, stubborn: false, catalogue: [], calls: 0 };
  if (args.dryRun) {
    planner.openai = {
      chat: { completions: { create: async (a: any) => ({ choices: [{ message: { content: fakeModelResponse(a, fakeCtx) } }] }) } }
    };
  } else if (!planner.isNativeGemini && !planner.openai) {
    console.error(
      'No model configured. Set GEMINI_API_KEY, or OPENAI_API_KEY (+ optional OPENAI_BASE_URL, OPENAI_MODEL), or GROQ_API_KEY.\n' +
        'Without one the planner silently uses only the deterministic generator, which would measure nothing. Aborting.\n' +
        'Use --dry-run to exercise the pipeline with a fake model.'
    );
    process.exit(2);
  }

  const provider = args.dryRun ? 'fake (dry-run)' : planner.isNativeGemini ? 'google-gemini-native' : 'openai-compatible';
  const model = args.dryRun ? 'dry-run-model' : planner.model;

  let wanted = SCENARIO_FILES;
  if (args.scenarios) wanted = SCENARIO_FILES.filter((f) => args.scenarios!.includes(f.replace('.json', '')));
  if (!wanted.length) throw new Error('No matching scenarios');

  console.log(`LLM eval: mode=${args.dryRun ? 'DRY-RUN (fake model)' : 'REAL'} provider=${provider} model=${model} n=${args.n} scenarios=${wanted.length}`);

  const allRuns: RunRecord[] = [];
  const perScenario: Record<string, RunRecord[]> = {};

  for (const file of wanted) {
    const tc = JSON.parse(fs.readFileSync(path.join(__dirname, file), 'utf-8'));
    const req: TripRequirement = tc.requirements;
    catalogue = await buildCatalogue(req.destination);
    fakeCtx.catalogue = catalogue;
    currentRequirement = { ...req };
    currentScenarioId = tc.testCaseId;
    perScenario[tc.testCaseId] = [];
    process.stdout.write(`\n[${tc.testCaseId}] `);

    for (let i = 0; i < args.n; i++) {
      rec.reset();
      fakeCtx.rng = mulberry32(args.seed ^ hashString(`${tc.testCaseId}#${i}`));
      fakeCtx.calls = 0;
      fakeCtx.stubborn = fakeCtx.rng() < 0.15;
      const t0 = Date.now();
      let outcome: any = null;
      let harnessError: string | undefined;
      try {
        outcome = await planner.planItinerary({ ...req });
      } catch (e) {
        harnessError = redactor(secrets)((e as Error).message);
      }
      rec.flush();
      const attempts = rec.attempts;
      const llmAttempts = attempts.filter((a) => a.source === 'llm' || a.source === 'llm-error');
      const llmErrors = attempts.filter((a) => a.source === 'llm-error').length;
      const verifiedByLlm = attempts.some((a) => a.source === 'llm' && a.valid);
      const fallbackUsed = attempts.some((a) => a.source.startsWith('deterministic'));
      const run: RunRecord = {
        scenario: tc.testCaseId,
        runIndex: i,
        durationMs: Date.now() - t0,
        finalStatus: outcome?.success ? 'VERIFIED' : 'DRAFT',
        modelUsed: outcome?.modelUsed ?? 'n/a',
        repairIterationsRaw: outcome?.repairIterations ?? -1,
        repairRounds: Math.min(outcome?.repairIterations ?? 0, MAX_REPAIRS),
        firstPassVerified: llmAttempts.length > 0 && attempts[0]?.source === 'llm' && attempts[0].valid,
        verifiedByLlm,
        fallbackUsed,
        fallbackVerified: fallbackUsed && Boolean(outcome?.success) && !verifiedByLlm, // success came from the deterministic generator
        llmErrors,
        excluded: Boolean(harnessError) || llmErrors > 0,
        harnessError,
        attempts
      };
      perScenario[tc.testCaseId].push(run);
      allRuns.push(run);
      process.stdout.write(run.excluded ? 'E' : run.firstPassVerified ? '.' : run.verifiedByLlm ? 'r' : run.finalStatus === 'VERIFIED' ? 'f' : 'X');
      if (args.delayMs > 0 && i < args.n - 1) await new Promise((r) => setTimeout(r, args.delayMs));
    }
  }
  console.log('\n\nLegend: . first-pass verified | r verified after repair | f verified only via fallback | X DRAFT | E excluded (model-call error)\n');

  const per: Record<string, Summary> = {};
  for (const [id, runs] of Object.entries(perScenario)) per[id] = summarise(runs);
  const overall = summarise(allRuns);

  let gitCommit = 'unknown';
  try {
    gitCommit = require('child_process').execFileSync('git', ['rev-parse', '--short', 'HEAD'], { cwd: __dirname }).toString().trim();
  } catch { /* not a git checkout */ }
  const timestamp = new Date().toISOString();
  const meta = {
    mode: args.dryRun ? 'dry-run' : 'real',
    provider, model, n: args.n, seed: args.dryRun ? args.seed : null, timestamp, gitCommit,
    node: process.version,
    note: args.dryRun ? 'SYNTHETIC fake model. Not a measurement of any real LLM.' : undefined
  };

  fs.mkdirSync(args.outDir, { recursive: true });
  const stamp = timestamp.replace(/[:.]/g, '-');
  const base = path.join(args.outDir, `llm-eval-${args.dryRun ? 'dryrun-' : ''}${stamp}`);
  fs.writeFileSync(base + '.json', JSON.stringify({ meta, overall, perScenario: per, runs: allRuns }, null, 2));
  const md = markdown(meta, per, overall);
  fs.writeFileSync(base + '.md', md);

  console.log(md);
  console.log(`Wrote ${path.relative(process.cwd(), base)}.json and .md`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
