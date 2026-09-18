import {
  UserProfile,
  TripSummary,
  TripStatus,
  TripRequirement,
  ItineraryModel,
  PlaceModel,
  WeatherDayForecast
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
  itineraryId?: string;
  errorMessage?: string;
}

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
