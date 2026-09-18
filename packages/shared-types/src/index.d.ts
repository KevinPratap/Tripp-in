export * from './design-tokens';
export type TravelStyle = 'LUXURY' | 'BUDGET' | 'BACKPACKER' | 'FAMILY' | 'SOLO' | 'COUPLE' | 'ADVENTURE' | 'RELAXATION' | 'CULTURAL';
export type Pace = 'RELAXED' | 'MODERATE' | 'FAST';
export type TransportPreference = 'WALKING' | 'PUBLIC_TRANSIT' | 'DRIVING' | 'CYCLING' | 'MIXED';
export type FoodPreference = 'VEGETARIAN' | 'VEGAN' | 'HALAL' | 'KOSHER' | 'GLUTEN_FREE' | 'LOCAL_CUISINE' | 'FINE_DINING' | 'STREET_FOOD' | 'NO_RESTRICTIONS';
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
export type TripStatus = 'DRAFT' | 'GENERATING' | 'READY' | 'FAILED' | 'ARCHIVED';
export interface TripRequirement {
    destination: string;
    startDate: string;
    endDate: string;
    travelersCount: number;
    budgetTotal?: number;
    currency?: string;
    travelStyles?: TravelStyle[];
    interests?: string[];
    foodPreferences?: FoodPreference[];
    transportPreference?: TransportPreference;
    pace?: Pace;
    notes?: string;
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
export interface GeoLocation {
    latitude: number;
    longitude: number;
}
export interface OpeningHourPeriod {
    open: {
        day: number;
        time: string;
    };
    close: {
        day: number;
        time: string;
    };
}
export interface PlaceOpeningHours {
    openNow?: boolean;
    periods?: OpeningHourPeriod[];
    weekdayDescriptions?: string[];
}
export interface PlaceModel {
    id: string;
    googlePlaceId: string;
    name: string;
    description?: string;
    formattedAddress: string;
    location: GeoLocation;
    types: string[];
    rating?: number;
    userRatingsTotal?: number;
    priceLevel?: number;
    photoUrls: string[];
    openingHours?: PlaceOpeningHours;
    websiteUrl?: string;
    phoneNumber?: string;
}
export type RouteMode = 'DRIVING' | 'WALKING' | 'TRANSIT' | 'BICYCLING';
export interface RouteSegment {
    originPlaceId: string;
    destinationPlaceId: string;
    mode: RouteMode;
    distanceMeters: number;
    durationMinutes: number;
    encodedPolyline?: string;
}
export type WeatherCondition = 'SUNNY' | 'PARTLY_CLOUDY' | 'CLOUDY' | 'RAIN' | 'HEAVY_RAIN' | 'SNOW' | 'WINDY' | 'THUNDERSTORM';
export interface WeatherDayForecast {
    date: string;
    temperatureCelsius: number;
    temperatureMinCelsius: number;
    temperatureMaxCelsius: number;
    condition: WeatherCondition;
    precipitationProbability: number;
    windSpeedKmh: number;
    iconCode?: string;
    advisoryNote?: string;
}
export type ActivityType = 'ATTRACTION' | 'MUSEUM' | 'RESTAURANT' | 'CAFE' | 'PARK' | 'TRANSIT' | 'HOTEL_CHECKIN' | 'FREE_TIME' | 'NIGHTLIFE';
export interface ActivityModel {
    id: string;
    placeId: string;
    title: string;
    type: ActivityType;
    startTime: string;
    endTime: string;
    durationMinutes: number;
    travelTimeFromPreviousMinutes?: number;
    transitModeFromPrevious?: RouteMode;
    estimatedCost?: number;
    currency?: string;
    reason?: string;
    tips?: string;
    bookingUrl?: string;
    place?: PlaceModel;
}
export interface ItineraryDayModel {
    id: string;
    date: string;
    dayIndex: number;
    summary?: string;
    weather?: WeatherDayForecast;
    activities: ActivityModel[];
}
export interface ItineraryModel {
    id: string;
    tripId: string;
    version: number;
    status: 'DRAFT' | 'VERIFIED' | 'ARCHIVED';
    createdAt: string;
    days: ItineraryDayModel[];
}
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
export interface ValidationViolation {
    code: 'TIME_OVERLAP' | 'PLACE_CLOSED' | 'INSUFFICIENT_TRAVEL_TIME' | 'INVALID_DURATION' | 'EXCEEDS_BUDGET' | 'EXCEEDS_PACE' | 'INVALID_TIME_BOUNDS';
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
}
