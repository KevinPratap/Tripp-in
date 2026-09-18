# Trippin' AI — API Specification

Base URL: `/api/v1`

## Endpoints

### 1. Home Feed
- **`GET /api/v1/home`**
  - Delivers profile, recent trips, recommended destinations, and popular destinations in a single request.

### 2. Trips
- **`POST /api/v1/trips`**
  - Creates a new trip in `DRAFT` status.
  - Returns `{ "tripId": "uuid", "status": "DRAFT" }`.
- **`GET /api/v1/trips/:id`**
  - Retrieves full trip details and current verified itinerary.
- **`POST /api/v1/trips/:id/generate`**
  - Enqueues BullMQ AI generation job.
  - Returns `202 Accepted` with `{ "jobId": "...", "status": "GENERATING" }`.
- **`GET /api/v1/trips/:id/status`**
  - Polls job progress percentage and current step message.

### 3. Itineraries
- **`POST /api/v1/itineraries/:id/modify`**
  - Conversational itinerary modification.
  - Request: `{ "instruction": "Make day 2 less busy" }`.
  - Re-plans affected days, verifies with deterministic validator, and persists as `version: N+1`.

### 4. Places & Explore
- **`GET /api/v1/places/search?q=museums&lat=48.85&lng=2.35`**
- **`GET /api/v1/places/:id`**
