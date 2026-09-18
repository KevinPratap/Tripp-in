# Trippin' AI — System Architecture & Core Product Loop

## Overview
Trippin' AI is an AI-powered travel planning mobile application (Android) and backend service (NestJS) that generates, validates, and refines travel itineraries with strict physical and business constraints.

```
USER
 ↓
Trip requirements (Destination, Dates, Travelers, Pace, Budget, Interests)
 ↓
TRIPPIN' AI BACKEND (NestJS)
 ↓
Data Retrieval (Google Places / Routes Matrix / Weather Normalization)
 ↓
AI Planning (OpenAI Structured Outputs via itinerary.schema.v1)
 ↓
Deterministic Constraint Engine (Overlap, Hours, Transit, Pace, Budget)
 ├── FAILED ──> Feedback Auto-Repair Loop (Re-prompt AI up to 3 times)
 └── PASSED ──> Verified Itinerary (v1, v2...)
 ↓
PostgreSQL + PostGIS Persistence
 ↓
Android App (Jetpack Compose UI + Room Offline Cache)
```

## Golden Architectural Contract
The AI model never writes directly to the production database or user-facing itinerary. Every schedule is deterministically validated by `ItineraryValidator`.

## Monorepo Layout
- `apps/backend`: NestJS TypeScript REST API, BullMQ workers, Prisma ORM, PostGIS.
- `apps/android`: Kotlin, Jetpack Compose Material 3, Hilt, Room, Retrofit.
- `packages/shared-types`: Canonical domain models, enums, design tokens.
- `packages/itinerary-schema`: JSON Schema & Zod validator for OpenAI structured outputs.
- `packages/api-contracts`: DTOs and API response models.
- `test-cases/`: 6 canonical evaluation test cases.
- `infrastructure/`: Docker Compose (PostGIS + Redis), Dockerfiles, CI/CD.
