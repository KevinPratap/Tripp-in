import { z } from 'zod';
export declare const TimeRegex: RegExp;
export declare const DateRegex: RegExp;
export declare const ActivitySchemaV1: z.ZodObject<{
    placeId: z.ZodString;
    placeName: z.ZodString;
    activityType: z.ZodEnum<["ATTRACTION", "MUSEUM", "RESTAURANT", "CAFE", "PARK", "TRANSIT", "HOTEL_CHECKIN", "FREE_TIME", "NIGHTLIFE"]>;
    startTime: z.ZodString;
    endTime: z.ZodString;
    durationMinutes: z.ZodNumber;
    travelTimeFromPreviousMinutes: z.ZodDefault<z.ZodNumber>;
    transitModeFromPrevious: z.ZodDefault<z.ZodEnum<["DRIVING", "WALKING", "TRANSIT", "BICYCLING"]>>;
    estimatedCost: z.ZodOptional<z.ZodNumber>;
    reason: z.ZodString;
    tips: z.ZodOptional<z.ZodString>;
}, "strip", z.ZodTypeAny, {
    placeId: string;
    placeName: string;
    activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
    startTime: string;
    endTime: string;
    durationMinutes: number;
    travelTimeFromPreviousMinutes: number;
    transitModeFromPrevious: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING";
    reason: string;
    estimatedCost?: number | undefined;
    tips?: string | undefined;
}, {
    placeId: string;
    placeName: string;
    activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
    startTime: string;
    endTime: string;
    durationMinutes: number;
    reason: string;
    travelTimeFromPreviousMinutes?: number | undefined;
    transitModeFromPrevious?: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING" | undefined;
    estimatedCost?: number | undefined;
    tips?: string | undefined;
}>;
export type ActivityV1 = z.infer<typeof ActivitySchemaV1>;
export declare const ItineraryDaySchemaV1: z.ZodObject<{
    dayIndex: z.ZodNumber;
    date: z.ZodString;
    themeSummary: z.ZodString;
    activities: z.ZodArray<z.ZodObject<{
        placeId: z.ZodString;
        placeName: z.ZodString;
        activityType: z.ZodEnum<["ATTRACTION", "MUSEUM", "RESTAURANT", "CAFE", "PARK", "TRANSIT", "HOTEL_CHECKIN", "FREE_TIME", "NIGHTLIFE"]>;
        startTime: z.ZodString;
        endTime: z.ZodString;
        durationMinutes: z.ZodNumber;
        travelTimeFromPreviousMinutes: z.ZodDefault<z.ZodNumber>;
        transitModeFromPrevious: z.ZodDefault<z.ZodEnum<["DRIVING", "WALKING", "TRANSIT", "BICYCLING"]>>;
        estimatedCost: z.ZodOptional<z.ZodNumber>;
        reason: z.ZodString;
        tips: z.ZodOptional<z.ZodString>;
    }, "strip", z.ZodTypeAny, {
        placeId: string;
        placeName: string;
        activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
        startTime: string;
        endTime: string;
        durationMinutes: number;
        travelTimeFromPreviousMinutes: number;
        transitModeFromPrevious: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING";
        reason: string;
        estimatedCost?: number | undefined;
        tips?: string | undefined;
    }, {
        placeId: string;
        placeName: string;
        activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
        startTime: string;
        endTime: string;
        durationMinutes: number;
        reason: string;
        travelTimeFromPreviousMinutes?: number | undefined;
        transitModeFromPrevious?: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING" | undefined;
        estimatedCost?: number | undefined;
        tips?: string | undefined;
    }>, "many">;
}, "strip", z.ZodTypeAny, {
    date: string;
    dayIndex: number;
    themeSummary: string;
    activities: {
        placeId: string;
        placeName: string;
        activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
        startTime: string;
        endTime: string;
        durationMinutes: number;
        travelTimeFromPreviousMinutes: number;
        transitModeFromPrevious: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING";
        reason: string;
        estimatedCost?: number | undefined;
        tips?: string | undefined;
    }[];
}, {
    date: string;
    dayIndex: number;
    themeSummary: string;
    activities: {
        placeId: string;
        placeName: string;
        activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
        startTime: string;
        endTime: string;
        durationMinutes: number;
        reason: string;
        travelTimeFromPreviousMinutes?: number | undefined;
        transitModeFromPrevious?: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING" | undefined;
        estimatedCost?: number | undefined;
        tips?: string | undefined;
    }[];
}>;
export type ItineraryDayV1 = z.infer<typeof ItineraryDaySchemaV1>;
export declare const ItinerarySchemaV1: z.ZodObject<{
    schemaVersion: z.ZodDefault<z.ZodLiteral<"itinerary.schema.v1">>;
    tripTitle: z.ZodString;
    destination: z.ZodString;
    summary: z.ZodString;
    totalEstimatedCost: z.ZodOptional<z.ZodNumber>;
    currency: z.ZodDefault<z.ZodString>;
    days: z.ZodArray<z.ZodObject<{
        dayIndex: z.ZodNumber;
        date: z.ZodString;
        themeSummary: z.ZodString;
        activities: z.ZodArray<z.ZodObject<{
            placeId: z.ZodString;
            placeName: z.ZodString;
            activityType: z.ZodEnum<["ATTRACTION", "MUSEUM", "RESTAURANT", "CAFE", "PARK", "TRANSIT", "HOTEL_CHECKIN", "FREE_TIME", "NIGHTLIFE"]>;
            startTime: z.ZodString;
            endTime: z.ZodString;
            durationMinutes: z.ZodNumber;
            travelTimeFromPreviousMinutes: z.ZodDefault<z.ZodNumber>;
            transitModeFromPrevious: z.ZodDefault<z.ZodEnum<["DRIVING", "WALKING", "TRANSIT", "BICYCLING"]>>;
            estimatedCost: z.ZodOptional<z.ZodNumber>;
            reason: z.ZodString;
            tips: z.ZodOptional<z.ZodString>;
        }, "strip", z.ZodTypeAny, {
            placeId: string;
            placeName: string;
            activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
            startTime: string;
            endTime: string;
            durationMinutes: number;
            travelTimeFromPreviousMinutes: number;
            transitModeFromPrevious: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING";
            reason: string;
            estimatedCost?: number | undefined;
            tips?: string | undefined;
        }, {
            placeId: string;
            placeName: string;
            activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
            startTime: string;
            endTime: string;
            durationMinutes: number;
            reason: string;
            travelTimeFromPreviousMinutes?: number | undefined;
            transitModeFromPrevious?: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING" | undefined;
            estimatedCost?: number | undefined;
            tips?: string | undefined;
        }>, "many">;
    }, "strip", z.ZodTypeAny, {
        date: string;
        dayIndex: number;
        themeSummary: string;
        activities: {
            placeId: string;
            placeName: string;
            activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
            startTime: string;
            endTime: string;
            durationMinutes: number;
            travelTimeFromPreviousMinutes: number;
            transitModeFromPrevious: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING";
            reason: string;
            estimatedCost?: number | undefined;
            tips?: string | undefined;
        }[];
    }, {
        date: string;
        dayIndex: number;
        themeSummary: string;
        activities: {
            placeId: string;
            placeName: string;
            activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
            startTime: string;
            endTime: string;
            durationMinutes: number;
            reason: string;
            travelTimeFromPreviousMinutes?: number | undefined;
            transitModeFromPrevious?: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING" | undefined;
            estimatedCost?: number | undefined;
            tips?: string | undefined;
        }[];
    }>, "many">;
}, "strip", z.ZodTypeAny, {
    schemaVersion: "itinerary.schema.v1";
    tripTitle: string;
    destination: string;
    summary: string;
    currency: string;
    days: {
        date: string;
        dayIndex: number;
        themeSummary: string;
        activities: {
            placeId: string;
            placeName: string;
            activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
            startTime: string;
            endTime: string;
            durationMinutes: number;
            travelTimeFromPreviousMinutes: number;
            transitModeFromPrevious: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING";
            reason: string;
            estimatedCost?: number | undefined;
            tips?: string | undefined;
        }[];
    }[];
    totalEstimatedCost?: number | undefined;
}, {
    tripTitle: string;
    destination: string;
    summary: string;
    days: {
        date: string;
        dayIndex: number;
        themeSummary: string;
        activities: {
            placeId: string;
            placeName: string;
            activityType: "TRANSIT" | "ATTRACTION" | "MUSEUM" | "RESTAURANT" | "CAFE" | "PARK" | "HOTEL_CHECKIN" | "FREE_TIME" | "NIGHTLIFE";
            startTime: string;
            endTime: string;
            durationMinutes: number;
            reason: string;
            travelTimeFromPreviousMinutes?: number | undefined;
            transitModeFromPrevious?: "WALKING" | "DRIVING" | "TRANSIT" | "BICYCLING" | undefined;
            estimatedCost?: number | undefined;
            tips?: string | undefined;
        }[];
    }[];
    schemaVersion?: "itinerary.schema.v1" | undefined;
    totalEstimatedCost?: number | undefined;
    currency?: string | undefined;
}>;
export type ItineraryV1 = z.infer<typeof ItinerarySchemaV1>;
export declare const ItineraryJsonSchemaV1: import("zod-to-json-schema").JsonSchema7Type & {
    $schema?: string | undefined;
    definitions?: {
        [key: string]: import("zod-to-json-schema").JsonSchema7Type;
    } | undefined;
};
export declare function validateItineraryV1(input: unknown): {
    success: boolean;
    data?: ItineraryV1;
    errors?: string[];
};
