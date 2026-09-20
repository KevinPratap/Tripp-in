# Frozen interface: travellers and plan options

Owner: AGY (backend, shared-types, Android network client). Consumer: HERMES (all Android UI).
Frozen 2026-09-20 so both agents can build in parallel without waiting on each other.
Do not change a field name, type or path here without posting to the bus first.

## Ownership split

- HERMES owns: apps/android/app/src/main/java/com/trippin/MainActivity.kt, feature/**, core/design/**.
  That is every Android UI file. AGY should not edit these without a bus message first.
- AGY owns: apps/backend/**, packages/shared-types/**, apps/android/.../core/network/**,
  and emulator verification of the whole flow.

## Traveller

    TravellerDto {
      id: string
      name: string
      budgetCap: number | null      // this person's cap, trip currency
      interests: string[]           // vocabulary below
      dislikes: string[]
      pace: 'relaxed' | 'balanced' | 'packed' | null
      joinedAt: string              // ISO
    }

Interest / dislike vocabulary (fixed strings, both sides must use these exact values):
  culture, food, nightlife, nature, adventure, shopping, museums, history,
  photography, wellness, relaxation, landmark

## Endpoints

    GET    /api/v1/trips/:id/travellers           -> TravellerDto[]
    POST   /api/v1/trips/:id/travellers           -> body { name, budgetCap, interests, dislikes, pace } -> TravellerDto
    PATCH  /api/v1/trips/:id/travellers/:tid      -> partial body -> TravellerDto
    DELETE /api/v1/trips/:id/travellers/:tid      -> 204
    POST   /api/v1/trips/join                     -> body { token, name, budgetCap, interests, dislikes, pace } -> { tripId, traveller }

`travelersCount` stays in the response, derived from the traveller list, so nothing else breaks.

## Additions to the trip payload the UI reads

    trip.currency: string                                // e.g. "JPY", "USD", "EUR"
    trip.shareToken?: string                             // active unrevoked share token, read-only
    trip.travellers: TravellerDto[]
    trip.perTravellerCost?: [{ travellerId, shareMin, shareMax, overCap: boolean }]
    trip.options?: [{ id, objective: 'cheapest'|'balanced'|'experience', totalMin, totalMax,
                      currency, isFloor, headline }]     // headline = one plain sentence of what differs
    stop.support?: { want: number, total: number, against: string[] }   // real counts, never invented

## Rules that are not negotiable

1. No field may be populated with a guess. If it is unknown, omit it or send null, and the UI states it as unknown.
2. `support.want` and `support.total` are counts of real travellers with real interests. Never estimate them.
3. `overCap` is true only when the computed share exceeds that traveller's own budgetCap.
4. An option may only be listed if the engine actually produced it under that objective. No simulated options.
5. Costs stay ranges with a source. No single invented number, ever.
6. `shareToken` is read-only on trip details. Read paths never mint new shares; shares are created explicitly via POST /api/v1/trips/:id/share and terminated on DELETE /api/v1/trips/:id/share.
