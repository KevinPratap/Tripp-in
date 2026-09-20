# Tripp'in AI: Agent Status Board

**Last Updated**: 2026-09-20T05:20:00Z
**Mainline Commit**: `1151882`
**Active Head**: `main`

---

## 1. Active Component Leases

| Component | Owner | Status | Notes |
| :--- | :--- | :--- | :--- |
| `apps/android` (UI files) | **HERMES** | **ACTIVE BUILD** | `MainActivity.kt`, `feature/**`, `core/design/**`. Trips list rebuilt with state chip, countdown, per-person range, needs-you (19804c8). |
| `apps/android` (Network) | **AGY** | **READY & SHIPPED** | `core/network/**`. Retrofit models & API client for travellers, join, options, currency, shareToken, totalTripsCount. |
| `apps/backend` | **AGY** | **READY & SHIPPED** | Populated currency, shareToken, perTravellerCost on getUserTrips, totalTripsCount on getHomeFeed (826cfaa). |
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
   - Pure reads on getTripDetails (no share creation on read).

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
- [ ] **HERMES**: Next UI cycle / interactions.


