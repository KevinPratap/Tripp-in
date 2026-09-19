# Tripp'in AI: Agent Rules and Engineering Invariants

You are working on Tripp'in AI, an honest, verified travel planning engine. Read and adhere to these guidelines on every task.

---

## 1. Product Invariants (Non-Negotiable)

1. **Never invent data**: No generated ratings, no generated review counts, no invented opening hours, no stock photos presented as venue photos. If a value is unknown, state that it is unknown.
2. **Deterministic verification**: Every generated itinerary must pass `ItineraryValidator` before storage. Nothing bypasses `validate()`.
3. **Status honesty**: `VERIFIED` versus `DRAFT` must reflect reality. If constraints were relaxed or warnings remain, the status is `DRAFT`.
4. **Data provenance**:
   - Travel times and routing come from OSRM.
   - Venue coordinates and opening hours come from OpenStreetMap (OSM) or are explicitly flagged with `openingHoursEstimated: true`.
   - Forecasts come from Open-Meteo and only for dates inside the forecast window.
5. **Currency honesty**: Currency must follow the destination (e.g. EUR for Lisbon, JPY for Tokyo). Never mix currencies across stops.
6. **Auth & identity**: Writes require `X-Guest-Session` header. Reads are public so shared trips resolve.
7. **DTO safety**: Every request DTO declared as a class must have `class-validator` decorators (due to `whitelist: true`). Every new class DTO must be registered in `apps/backend/src/common/validation/dto-decorators.spec.ts`.
8. **Copy standards**: Zero em dashes (`—`) or en dashes (`–`) in user-facing copy (use 'to' or plain hyphens). Zero emojis anywhere in code, user copy, or system logs.
9. **No marketing buzzwords**: Never use `CERTIFIED`, `FACTORY-GRADED`, or `FIELD TESTED & CERTIFIED`. State verified facts plainly.
10. **Zero-cost stack & model guardrails**:
    - The product's runtime providers stay free tier: Google Gemini 2.5 Flash, Open-Meteo, OSRM, OpenStreetMap Photon / Nominatim.
    - Do not introduce a paid model dependency into the app's code, config or deployment. In particular, DeepSeek must not appear anywhere in this product's stack.
    - Scope note: this rule governs the product. It does not govern the configuration of the assistants that edit this repo. Their own model and provider settings are dev tooling, not part of the shipped stack, and are out of scope for this rule.
11. **Git discipline**:
    - Commit with explicit file paths only.
    - Never run `git add -A` or `git add .` (prevents sweeping untracked artifacts).
    - Use `git --literal-pathspecs add` when paths contain brackets like `[id]`.
    - Never force push.

---

## 2. Design System & Accessibility

- **Palette**: Action Crimson Red (`#E11D48`), Carbon Ink (`#18181B`), Newsprint Cream (`#FAF8F5`).
- **Borders & Shadows**: 2.5px solid ink borders, `shadow-[4px_4px_0px_#18181B]`, 8px grid system.
- **Accessibility**:
  - All interactive buttons and touch targets must be at least 44px (`min-h-11`).
  - Text input font size must be at least 16px on mobile to prevent iOS auto-zoom.
  - Respect `prefers-reduced-motion`.
  - Use semantic HTML5 elements (`<header>`, `<main>`, `<section>`, `<article>`, `<nav>`).

---

## 3. Shared Wire Contract

- `GET /api/v1/trips/:id` returns envelope `{ "trip": ..., "requirements": ..., "itinerary": ... }`.
- Web client must unwrap with `unwrapTripDetails()` in `apps/web/src/lib/trip-contract.ts`.
- Itinerary statuses: `DRAFT`, `VERIFIED`, `ARCHIVED`.
- Activity receipts: Every activity carries `checks: VerificationCheck[]` detailing provenance (`OSM`, `OSRM`, `Open-Meteo`, `engine`) and status (`confirmed`, `estimated`, `unchecked`).

---

## 4. Verification Check Before Claiming Done

Always run:
1. `pnpm --filter @trippin/backend run test` (must pass 100%).
2. `pnpm --filter @trippin/backend run build`.
3. `pnpm --filter @trippin/web run build`.
4. `.\gradlew.bat compileDebugKotlin` in `apps/android` if mobile code was touched.
5. Live smoke test on deployed endpoints.
