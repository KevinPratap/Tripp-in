# Tripp'in AI: Agent Status Board

**Last Updated**: 2026-09-20T03:45:00Z
**Mainline Commit**: `ae1361c`
**Active Head**: `main`

---

## 1. Active Component Leases

| Component | Owner | Status | Notes |
| :--- | :--- | :--- | :--- |
| `apps/android` (UI files) | **HERMES** | **ACTIVE BUILD** | `MainActivity.kt`, `feature/**`, `core/design/**`. Building 4-tab IA (`Trips`, `Plan`, `Group`, `You`). |
| `apps/android` (Network) | **AGY** | **ACTIVE BUILD** | `core/network/**`. Retrofit models & API client for travellers, join, and options. |
| `apps/backend` | **AGY** | **ACTIVE BUILD** | Traveller CRUD, `/trips/join`, `perTravellerCost`, `stop.support`, multi-objective options. |
| `packages/shared-types` | **AGY** | **ACTIVE BUILD** | `TravellerDto`, `TripOptionDto`, `StopSupportDto`. |

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
   - Trip additions: `trip.travellers`, `trip.perTravellerCost`, `trip.options`, `stop.support`.

2. **Non-Negotiable Truth Invariants**:
   - Zero invented counts: `support.want` and `support.total` count real travellers only.
   - `overCap` is honest: computed against that traveller's own budgetCap.
   - An option may only exist if the engine actually produced it under that objective.
   - Costs remain honest ranges with sources.

---

## 3. Active Task List

- [x] **AGY**: Commit Android overhaul files to `main` (`c00859c`).
- [x] **AGY**: Multi-agent collaboration protocol & bus (`ae1361c`).
- [ ] **AGY**: Implement backend Traveller model, CRUD, `/trips/join`, `perTravellerCost`, `stop.support`, and multi-objective options.
- [ ] **AGY**: Implement Android network DTOs & `ApiService` endpoints in `core/network/**`.
- [ ] **HERMES**: Implement 4-tab Android UI (`Trips`, `Plan`, `Group`, `You`) on `flat-android-identity`.
- [ ] **AGY / HERMES**: Integration test on emulator.

