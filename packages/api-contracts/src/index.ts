import {
  UserProfile,
  TripSummary,
  TripStatus,
  TripRequirement,
  ItineraryModel,
  PlaceModel,
  WeatherDayForecast,
  TravellerDto
} from '@trippin/shared-types';
import { ItineraryV1 } from '@trippin/itinerary-schema';

/**
 * Trippin' AI — Shared API Contracts & DTOs
 */

// 1. Home Dashboard API (GET /api/v1/home)
export interface DestinationCardDto {
  id: string;
  name: string;
  country: string;
  imageUrl: string;
  description: string;
  averageRating: number;
  tags: string[];
}

export interface HomeFeedResponse {
  user: UserProfile;
  recentTrips: TripSummary[];
  recommendedDestinations: DestinationCardDto[];
  popularDestinations: DestinationCardDto[];
}

// 2. Trip Creation (POST /api/v1/trips)
export interface CreateTripRequestDto {
  destination: string;
  /** Optional departure city, used to plan the arrival day realistically. */
  originCity?: string;
  startDate: string; // YYYY-MM-DD
  endDate: string; // YYYY-MM-DD
  travelersCount: number;
  budgetTotal?: number;
  currency?: string;
  travelStyles?: string[];
  interests?: string[];
  foodPreferences?: string[];
  transportPreference?: string;
  pace?: string;
  notes?: string;
}

export interface CreateTripResponse {
  tripId: string;
  status: TripStatus;
}

// 3. Trip Generation Trigger (POST /api/v1/trips/:id/generate)
export interface GenerateTripResponse {
  tripId: string;
  jobId: string;
  status: 'GENERATING';
  estimatedSeconds: number;
}

// 4. Trip Generation Status Poll (GET /api/v1/trips/:id/status)
export interface TripGenerationStatusResponse {
  tripId: string;
  status: TripStatus;
  progressPercentage: number;
  currentStepMessage: string;
  /**
   * Which real stage of the pipeline is running. The worker emits these as it reaches
   * each step, so the planner can show true progress rather than an invented animation.
   */
  currentStepKey?: GenerationStageKey;
  itineraryId?: string;
  errorMessage?: string;
}

/** Ordered stages of a generation run. Keep the order in sync with the worker. */
export type GenerationStageKey =
  | 'queued'
  | 'forecast'
  | 'geocoding'
  | 'venues'
  | 'planning'
  | 'validation'
  | 'persist'
  | 'ready'
  | 'failed';

// 5. Trip Details with Itinerary (GET /api/v1/trips/:id)
export interface TripDetailsResponse {
  trip: TripSummary;
  requirements: TripRequirement;
  itinerary?: ItineraryModel;
}

// 6. Conversational Itinerary Modification (POST /api/v1/itineraries/:id/modify)
export interface ModifyItineraryRequestDto {
  instruction: string; // e.g. "Move Musée d'Orsay to Day 3" or "Make Day 2 less busy"
}

export interface ModifyItineraryResponse {
  itineraryId: string;
  newVersion: number;
  appliedChangesSummary: string;
  updatedItinerary: ItineraryModel;
}

// 7. Places & Explore API (GET /api/v1/explore, GET /api/v1/places/search)
export interface SearchPlacesQueryDto {
  query?: string;
  latitude?: number;
  longitude?: number;
  radiusMeters?: number;
  category?: string;
}

export interface ExploreResponse {
  categories: Array<{ id: string; name: string; icon: string }>;
  nearbyPlaces: PlaceModel[];
  trendingPlaces: PlaceModel[];
  recommendedPlaces: PlaceModel[];
}

// 8. Server-Driven UI (SDUI) Specifications (Blinkit / Swiggy Pattern)
export type SDUIType =
  | 'HEADER_GREETING'
  | 'SEARCH_BAR'
  | 'HERO_BANNER'
  | 'QUICK_ACTIONS'
  | 'HORIZONTAL_CAROUSEL'
  | 'RECENT_TRIPS_LIST'
  | 'WEATHER_BANNER'
  | 'TIMELINE_DAY'
  | 'ACTIVITY_CARD'
  | 'CATEGORY_CHIPS'
  | 'CALL_TO_ACTION';

export interface SDUIAction {
  type: 'NAVIGATE' | 'DEEP_LINK' | 'API_CALL' | 'OPEN_MODAL';
  target: string;
  params?: Record<string, any>;
}

export interface SDUISection<T = any> {
  id: string;
  type: SDUIType;
  orderIndex: number;
  title?: string;
  subtitle?: string;
  action?: SDUIAction;
  payload: T;
}

export interface SDUIScreenResponse {
  screenId: string;
  title: string;
  sections: SDUISection[];
  metadata?: Record<string, any>;
}

export * from '@trippin/shared-types';

export interface JoinTripResponse {
  tripId: string;
  traveller: TravellerDto;
}
