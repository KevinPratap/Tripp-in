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
  /** Optional departure city. Lets the engine plan the arrival day around real travel time. */
  originCity?: string;
  startDate: string; // ISO 8601 YYYY-MM-DD
  endDate: string; // ISO 8601 YYYY-MM-DD
  travelersCount: number;
  budgetTotal?: number;
  /**
   * What the traveller says they spend per person for one night or one day. These are their own
   * numbers and are labelled as theirs. Absent means we do not know, so the line is left out of
   * the total rather than guessed.
   */
  stayPerNightMin?: number;
  stayPerNightMax?: number;
  foodPerDayMin?: number;
  foodPerDayMax?: number;
  localTransitPerDayMin?: number;
  localTransitPerDayMax?: number;
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
  /** Departure city the traveller starts from, when they told us. */
  originCity?: string;
  currency?: string;
  startDate: string;
  endDate: string;
  travelersCount: number;
  status: TripStatus;
  isLocked?: boolean;
  lockedAt?: string;
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

/**
 * Prices. The rule this product holds itself to: a number is only ever shown as a range,
 * and only when a named source supports it. Never a point price, never a guess, and an
 * unknown is displayed as unknown instead of defaulting to zero.
 */
export type PriceSource = 'OSM' | 'USER_INPUT';

export interface EntryPriceModel {
  /** FREE and CHARGED_KNOWN come from a real published tag. CHARGED_UNKNOWN means the venue
   *  charges an entry fee but no amount is published, so the app must not invent one. */
  status: 'FREE' | 'CHARGED_KNOWN' | 'CHARGED_UNKNOWN';
  amountMin?: number;
  amountMax?: number;
  currency?: string;
  source: PriceSource;
  /** The raw value as published, so a traveller can check the claim themselves. */
  sourceDetail?: string;
}

export interface DailyRateModel {
  unit: 'PER_NIGHT' | 'PER_DAY';
  units: number;
  /** What the traveller told us they spend, per person, for one unit. */
  perUnitMin?: number;
  perUnitMax?: number;
  /** NOT_SET means the traveller gave us no figure, so this line is excluded from the total. */
  source: 'USER_INPUT' | 'NOT_SET';
}

export interface EntryCostLineModel {
  activityId: string;
  title: string;
  status: 'FREE' | 'CHARGED_KNOWN' | 'CHARGED_UNKNOWN';
  amountMin?: number;
  amountMax?: number;
  currency?: string;
  sourceDetail?: string;
  /** True when the venue prices in another currency, so converting would need an FX rate we do not have. */
  excludedForeignCurrency?: boolean;
}

export interface TripCostModel {
  currency: string;
  basis: 'PER_PERSON';
  nights: number;
  days: number;
  stay: DailyRateModel;
  food: DailyRateModel;
  localTransit: DailyRateModel;
  entries: {
    lines: EntryCostLineModel[];
    knownMin: number;
    knownMax: number;
    freeCount: number;
    unknownCount: number;
    foreignCurrencyCount: number;
  };
  /** Sum of every line we could price. Both ends are real: low end and high end of what we know. */
  totalMin: number;
  totalMax: number;
  /** True when some stops charge an entry fee with no published amount, or a rate is missing:
   *  the total is then a floor rather than a ceiling, and the UI must say so. */
  isFloor: boolean;
  pricedStops: number;
  unpricedStops: number;
  /** Plain sentences explaining what the total does and does not include. */
  notes: string[];
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
  /** Entry price from a real published source. Absent means nothing is published, not that it is free. */
  price?: EntryPriceModel;
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
  isLocked?: boolean;
  lockedAt?: string;
  createdAt: string;
  title?: string;
  summary?: string;
  totalEstimatedCost?: number;
  currency?: string;
  days: ItineraryDayModel[];
  /** What the trip costs, per person, as ranges. Built from published entry fees and the
   *  traveller's own day rates. Absent when the plan carries no cost information at all. */
  cost?: TripCostModel;
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
  };
  activityChecks?: Record<string, VerificationCheck[]>;
}

