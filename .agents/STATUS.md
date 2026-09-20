# Tripp'in AI: Agent Status Board

**Last Updated**: 2026-09-20T09:45:00Z
**Mainline Commit**: `696dc94`
**Active Head**: `main`

**Read this before you commit.** agy's `eefd056` ("populate checks array on every activity") also
contains part of a HERMES change set that was uncommitted in the tree at the time: the deletion of
`HomeScreen.kt`, `ExploreScreen.kt` and `TrippinNavHost.kt`, and eight new DTOs in
`core/network/NetworkModels.kt`. Nothing was lost, but that commit is not only a backend change and
`git show eefd056 --stat` is worth reading before either of us reasons about it. Stage explicit file
paths, never a directory.

---

## 1. Active Component Leases

| Component | Owner | Status | Notes |
| :--- | :--- | :--- | :--- |
| `apps/android` (UI files) | **HERMES** | **DEPLOYED TO DEVICE** | `MainActivity.kt`, `feature/**`, `core/design/**`. Flat surfaces, semantic colour tokens, seven-role type scale across every feature screen (`696dc94`). Accounts, invite and join flows live on emulator-5554 (`56ead62`). |
| `apps/android` (Network) | **AGY** | **CHANGED BY HERMES** | `core/network/**`. Guest identity removed; `SessionStore` holds the session token and the client sends `Authorization: Bearer`. Auth, share and my-trips calls added (`56ead62`). |
| `apps/backend` | **AGY** | **DEPLOYED & VERIFIED** | Price Provenance Guard (`4fd2965`), photo honesty (`cdf14bb`), reason normalisation (`6a65224`), checks backfill (`eefd056`), venue photo backfill on read (`0959974`, `231f0c8`). |
| `packages/shared-types` | **AGY** | **READY & SHIPPED** | `TravellerDto`, `TripOptionDto`, `StopSupportDto`, `TripSummary.currency`, `TripSummary.shareToken`, `HomeFeedResponse.totalTripsCount`. |

---

## 2. Recent Decisions & Alignments

1. **Frozen Interface (`.agents/INTERFACE.md`)**:
   - `TravellerDto` fields: `id`, `name`, `budgetCap`, `interests`, `dislikes`, `pace`, `joinedAt`.
   - Fixed vocabulary: `culture`, `food`, `nightlife`, `nature`, `adventure`, `shopping`, `museums`, `history`, `photography`, `wellness`, `relaxation`, `landmark`.
   - Endpoints:
     - `GET /api/v1/trips/:id/travellers`
     - `POST /api/v1/trips/:id/travellers`
     - `PATCH /api/v1/trips/:id/travellers/:tid`
     - `DELETE /api/v1/trips/:id/travellers/:tid`
     - `POST /api/v1/trips/join`
   - Trip additions: `trip.currency`, `trip.shareToken`, `trip.travellers`, `trip.perTravellerCost`, `trip.options`, `stop.support`.
   - Home Feed: `totalTripsCount?: number`.

2. **Non-Negotiable Truth Invariants**:
   - Zero invented counts: `support.want` and `support.total` count real travellers only.
   - `overCap` is honest: computed against that traveller's own budgetCap.
   - An option may only exist if the engine actually produced it under that objective.
   - Costs remain honest ranges with sources.
   - Price Provenance Guard: The API serializer must omit `estimatedCost` and `currency` on a stop unless a named source and checks accompany them.
   - Pure reads on getTripDetails (no share creation on read).
   - An address is navigable or it is not printed as one. A ward with no street is replaced by
     coordinates, and no button may offer an action the app cannot take.

3. **Accounts replaced the guest model (Kevin's call, 2026-09-20)**: no `X-Guest-Session`, no
   `guest:<id>` identity, no `migrateGuestTrips`. Sign-in is the magic link flow that already existed:
   `POST /auth/request-link` -> `{ email, expiresAt, delivery, loginUrl }`,
   `POST /auth/verify` -> `{ sessionToken, expiresAt, user, migratedTrips }`, and
   `GET /me/trips` with `Authorization: Bearer` doubles as the session check (401 means signed out).
   No mail provider is configured, so `delivery` is `console` and the app says so instead of claiming
   an email was sent. The backend half is still agy's: `firebase-auth.guard.ts` still mints guest
   identities and still falls back to the demo user for anonymous reads.

4. **The images trap, so nobody pays for it twice**: Coil builds on OkHttp, OkHttp's default
   User-Agent is `okhttp/<version>`, and Wikimedia answers that with **403**. The photograph was on the
   wire the whole time. Coil now gets its own client from an `ImageLoaderFactory` with a descriptive
   agent carrying a contact (`19eb5b5`), kept separate from the API client so an image request never
   carries the session token.

---

## 3. Active Task List

- [x] **AGY**: Commit Android overhaul files to `main` (`c00859c`).
- [x] **AGY**: Multi-agent collaboration protocol & bus (`ae1361c`).
- [x] **HERMES**: Step 1 4-tab shell landed on `main` (`28259d8`).
- [x] **AGY**: Boot emulator-5554, install Step 1 APK, smoke-test and report UI findings to Hermes.
- [x] **AGY**: Implement backend Traveller model, CRUD, `/trips/join`, `perTravellerCost`, `stop.support`, and multi-objective options (`589cfbb`).
- [x] **AGY**: Implement Android network DTOs & `ApiService` endpoints in `core/network/**` (`589cfbb`).
- [x] **HERMES**: Rebuild Group screen with real travellers, caps, and conflicts (`ba56f34`).
- [x] **AGY**: Add currency & shareToken to TripSummaryDto and trips.service (`8140cbb`, `92bb21d`, `645e493`).
- [x] **HERMES**: Rebuild Trips list cards with state chip, countdown, per-person range, needs-you (`19804c8`).
- [x] **AGY**: Populate perTravellerCost, currency, shareToken, totalTripsCount on list and home feed (`826cfaa`).
- [x] **HERMES**: Section 7 copy pass across Plan, You, Today, Planner, Generating, Map (`1151882`).
- [x] **AGY**: Visual smoke test of 12sp labels and expanded You/Plan cards on emulator-5554.
- [x] **HERMES**: Real currency on Plan day card & stop prices, removed hardcoded dollar signs (`c6559dd`).
- [x] **AGY**: Visual verification of currency formatting (JPY/¥) on emulator-5554 & Tokyo costJson data audit.
- [x] **HERMES**: Raise last sub-12sp labels on reachable screens to 12sp (`dec4013`).
- [x] **AGY**: Visual inspection of 12sp Trips filter row on emulator-5554 (confirmed 38dp pill inner height fits 12sp cleanly).
- [x] **AGY**: Land and deploy Price Provenance Guard to Railway backend (`4fd2965`, deployment `ff5eb114`).
- [x] **AGY**: Live production endpoint verification & emulator-5554 visual verification on Kyoto without unverified prices.
- [x] **AGY**: Photo honesty & Paris fallback closed (`cdf14bb`, deployment `9828bed8`).
- [x] **AGY**: Section 4.2 Reason Normalization deployed & live on wire (`6a65224`, deployment `bd8f0bbe`).
- [x] **AGY**: Checks array populated on every activity via baseline backfill on read (`eefd056`). Contains part of a HERMES change set, see the note at the top of this file.
- [x] **HERMES**: Flatten every offset shadow, delete the three dead screens, add semantic colour tokens and the seven-role type scale (`56ead62`, `696dc94`).
- [x] **HERMES**: Real accounts end to end, with invite and join reachable from the Trips card and the fake device id gone from You (`56ead62`).
- [x] **HERMES**: Venue photographs filled in on read, matched to the name's own Wikipedia script, deployed and verified live (`0959974`, `231f0c8`).
- [x] **HERMES**: Addresses navigable or replaced by coordinates, and attribution corrected for `thumb.wikimedia.org` (`9a6ec57`).
- [x] **HERMES**: Coil given a descriptive User-Agent, which is what actually made the photographs appear (`19eb5b5`).
- [ ] **AGY**: Remove the guest identity and the demo-user read fallback from `firebase-auth.guard.ts`, as Kevin asked. The client half is already done, so the backend is now the only place a device-scoped identity can still be minted.
- [ ] **AGY**: Regenerate or re-verify the Lisbon plan, which still carries no photographs, and decide the fate of the seeded `Destination.imageUrl` Unsplash stand-ins.
- [ ] **HERMES**: The demo shortcut on the sign-in screen opens the seeded `traveler@trippin.ai` account and is a demo affordance to remove before any real release.
