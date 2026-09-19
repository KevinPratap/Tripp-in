/**
 * Trippin' AI — Shared Types & Domain Models
 * Canonical enums and interfaces across backend, mobile contracts, and AI engine.
 */

export * from './design-tokens';

// User & Preferences
export type TravelStyle =
  | 'LUXURY'
  | 'BUDGET'
  | 'BACKPACKER'
  | 'FAMILY'
  | 'SOLO'
  | 'COUPLE'
  | 'ADVENTURE'
  | 'RELAXATION'
  | 'CULTURAL';

export type Pace = 'RELAXED' | 'MODERATE' | 'FAST';

export type TransportPreference = 'WALKING' | 'PUBLIC_TRANSIT' | 'DRIVING' | 'CYCLING' | 'MIXED';

export type FoodPreference =
  | 'VEGETARIAN'
  | 'VEGAN'
  | 'HALAL'
  | 'KOSHER'
  | 'GLUTEN_FREE'
  | 'LOCAL_CUISINE'
  | 'FINE_DINING'
  | 'STREET_FOOD'
  | 'NO_RESTRICTIONS';

export interface UserPreferences {
  travelStyles: TravelStyle[];
  pace: Pace;
  foodPreferences: FoodPreference[];
  transportPreference: TransportPreference;
  currency: string;
  interests: string[];
  accessibilityRequirements?: string[];
}

export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  photoUrl?: string;
  createdAt: string;
  preferences?: UserPreferences;
}

// Trip Domain
export type TripStatus = 'DRAFT' | 'GENERATING' | 'READY' | 'FAILED' | 'ARCHIVED';

export interface TripRequirement {
  destination: string;
  startDate: string; // ISO 8601 YYYY-MM-DD
  endDate: string; // ISO 8601 YYYY-MM-DD
  travelersCount: number;
  budgetTotal?: number;
  currency?: string;
  travelStyles?: TravelStyle[];
  interests?: string[];
  foodPreferences?: FoodPreference[];
  transportPreference?: TransportPreference;
  pace?: Pace;
  notes?: string;
  /** Geocoded destination centre, used for geographic sanity checks */
  destinationLocation?: GeoLocation;
  /** Maximum distance (km) a venue may sit from the destination cluster */
  maxVenueRadiusKm?: number;
}

export interface TripSummary {
  id: string;
  userId: string;
  destination: string;
  startDate: string;
  endDate: string;
  travelersCount: number;
  status: TripStatus;
  heroImageUrl?: string;
  totalActivitiesCount: number;
  currentVersion: number;
  createdAt: string;
  updatedAt: string;
}

// Places & Locations
export interface GeoLocation {
  latitude: number;
  longitude: number;
}

export interface OpeningHourPeriod {
  open: { day: number; time: string }; // day: 0=Sun, 6=Sat, time: "09:00"
  close: { day: number; time: string };
}

export interface PlaceOpeningHours {
  openNow?: boolean;
  periods?: OpeningHourPeriod[];
  weekdayDescriptions?: string[];
}

export interface PlaceModel {
  id: string; // Internal UUID or normalized placeId
  googlePlaceId: string;
  name: string;
  description?: string;
  formattedAddress: string;
  location: GeoLocation;
  types: string[];
  rating?: number;
  userRatingsTotal?: number;
  priceLevel?: number; // 0 to 4
  photoUrls: string[];
  openingHours?: PlaceOpeningHours;
  /** True when openingHours were derived from the venue category instead of a real source */
  openingHoursEstimated?: boolean;
  websiteUrl?: string;
  phoneNumber?: string;
}

// Routes & Travel Feasibility
export type RouteMode = 'DRIVING' | 'WALKING' | 'TRANSIT' | 'BICYCLING';

export interface RouteSegment {
  originPlaceId: string;
  destinationPlaceId: string;
  mode: RouteMode;
  distanceMeters: number;
  durationMinutes: number;
  encodedPolyline?: string;
}

// Weather Forecast
export type WeatherCondition =
  | 'SUNNY'
  | 'PARTLY_CLOUDY'
  | 'CLOUDY'
  | 'RAIN'
  | 'HEAVY_RAIN'
  | 'SNOW'
  | 'WINDY'
  | 'THUNDERSTORM';

export interface WeatherDayForecast {
  date: string; // YYYY-MM-DD
  temperatureCelsius: number;
  temperatureMinCelsius: number;
  temperatureMaxCelsius: number;
  condition: WeatherCondition;
  precipitationProbability: number; // 0-100%
  windSpeedKmh: number;
  iconCode?: string;
  advisoryNote?: string;
}

// Activities & Itinerary
export type ActivityType =
  | 'ATTRACTION'
  | 'MUSEUM'
  | 'RESTAURANT'
  | 'CAFE'
  | 'PARK'
  | 'TRANSIT'
  | 'HOTEL_CHECKIN'
  | 'FREE_TIME'
  | 'NIGHTLIFE';

export interface ActivityModel {
  id: string;
  placeId: string;
  title: string;
  type: ActivityType;
  startTime: string; // HH:mm
  endTime: string; // HH:mm
  durationMinutes: number;
  travelTimeFromPreviousMinutes?: number;
  transitModeFromPrevious?: RouteMode;
  estimatedCost?: number;
  currency?: string;
  reason?: string;
  tips?: string;
  bookingUrl?: string;
  place?: PlaceModel;
  checks?: VerificationCheck[];
}

export interface ItineraryDayModel {
  id: string;
  date: string; // YYYY-MM-DD
  dayIndex: number; // 1, 2, 3...
  summary?: string;
  weather?: WeatherDayForecast;
  weatherSummary?: string;
  activities: ActivityModel[];
}

export interface ItineraryModel {
  id: string;
  tripId: string;
  version: number;
  status: 'DRAFT' | 'VERIFIED' | 'ARCHIVED';
  createdAt: string;
  title?: string;
  summary?: string;
  totalEstimatedCost?: number;
  currency?: string;
  days: ItineraryDayModel[];
}

// Bookings
export type BookingCategory = 'FLIGHT' | 'HOTEL' | 'ACTIVITY' | 'RESTAURANT';
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'FAILED';

export interface BookingReference {
  id: string;
  tripId: string;
  activityId?: string;
  category: BookingCategory;
  provider: string;
  providerProductId?: string;
  title: string;
  confirmationUrl?: string;
  price?: number;
  currency?: string;
  status: BookingStatus;
  bookedAt?: string;
}

// Verification Receipts
export type VerificationCheckSource = 'OSM' | 'OSRM' | 'Open-Meteo' | 'engine';
export type VerificationCheckStatus = 'confirmed' | 'estimated' | 'unchecked';

export interface VerificationCheck {
  code: string;
  label: string;
  source: VerificationCheckSource;
  status: VerificationCheckStatus;
  details?: string;
}

// Deterministic Validation Results
export interface ValidationViolation {
  code:
    | 'TIME_OVERLAP'
    | 'PLACE_CLOSED'
    | 'INSUFFICIENT_TRAVEL_TIME'
    | 'INVALID_DURATION'
    | 'EXCEEDS_BUDGET'
    | 'EXCEEDS_PACE'
    | 'INVALID_TIME_BOUNDS'
    | 'PLACE_OUTSIDE_DESTINATION'
    | 'CROSS_DAY_HOP_UNREALISTIC'
    | 'REPEATED_VENUE'
    | 'DAY_TOO_SPARSE'
    | 'CURRENCY_MISMATCH';
  message: string;
  dayIndex: number;
  activityIndices: number[];
  details?: Record<string, any>;
}

export interface ValidationResult {
  isValid: boolean;
  violations: ValidationViolation[];
  metrics: {
    totalActivities: number;
    totalActiveMinutes: number;
    totalTransitMinutes: number;
    estimatedCostTotal: number;
  };
  activityChecks?: Record<string, VerificationCheck[]>;
}

