"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ItineraryJsonSchemaV1 = exports.ItinerarySchemaV1 = exports.ItineraryDaySchemaV1 = exports.ActivitySchemaV1 = exports.DateRegex = exports.TimeRegex = void 0;
exports.validateItineraryV1 = validateItineraryV1;
const zod_1 = require("zod");
const zod_to_json_schema_1 = require("zod-to-json-schema");
exports.TimeRegex = /^([01]\d|2[0-3]):[0-5]\d$/;
exports.DateRegex = /^\d{4}-\d{2}-\d{2}$/;
exports.ActivitySchemaV1 = zod_1.z.object({
    placeId: zod_1.z.string().min(1, 'Place ID is required'),
    placeName: zod_1.z.string().min(1, 'Place name is required'),
    activityType: zod_1.z.enum([
        'ATTRACTION',
        'MUSEUM',
        'RESTAURANT',
        'CAFE',
        'PARK',
        'TRANSIT',
        'HOTEL_CHECKIN',
        'FREE_TIME',
        'NIGHTLIFE'
    ]),
    startTime: zod_1.z.string().regex(exports.TimeRegex, 'Start time must be formatted as HH:mm (24h)'),
    endTime: zod_1.z.string().regex(exports.TimeRegex, 'End time must be formatted as HH:mm (24h)'),
    durationMinutes: zod_1.z.number().int().positive('Duration must be positive minutes'),
    travelTimeFromPreviousMinutes: zod_1.z.number().int().nonnegative().default(0),
    transitModeFromPrevious: zod_1.z.enum(['DRIVING', 'WALKING', 'TRANSIT', 'BICYCLING']).default('TRANSIT'),
    estimatedCost: zod_1.z.number().nonnegative().optional(),
    reason: zod_1.z.string().min(3, 'Reason for including this activity must be provided'),
    tips: zod_1.z.string().optional()
});
exports.ItineraryDaySchemaV1 = zod_1.z.object({
    dayIndex: zod_1.z.number().int().positive('Day index must be 1-based positive integer'),
    date: zod_1.z.string().regex(exports.DateRegex, 'Date must be formatted as YYYY-MM-DD'),
    themeSummary: zod_1.z.string().min(5, 'Theme summary for the day is required'),
    activities: zod_1.z.array(exports.ActivitySchemaV1).min(1, 'Each day must have at least one scheduled activity')
});
exports.ItinerarySchemaV1 = zod_1.z.object({
    schemaVersion: zod_1.z.literal('itinerary.schema.v1').default('itinerary.schema.v1'),
    tripTitle: zod_1.z.string().min(3, 'Trip title is required'),
    destination: zod_1.z.string().min(2, 'Destination name is required'),
    summary: zod_1.z.string().min(10, 'Trip summary is required'),
    totalEstimatedCost: zod_1.z.number().nonnegative().optional(),
    currency: zod_1.z.string().length(3).default('USD'),
    days: zod_1.z.array(exports.ItineraryDaySchemaV1).min(1, 'At least one day must be present')
});
exports.ItineraryJsonSchemaV1 = (0, zod_to_json_schema_1.zodToJsonSchema)(exports.ItinerarySchemaV1, {
    name: 'ItineraryV1'
});
function validateItineraryV1(input) {
    const result = exports.ItinerarySchemaV1.safeParse(input);
    if (result.success) {
        return { success: true, data: result.data };
    }
    return {
        success: false,
        errors: result.error.errors.map((err) => `${err.path.join('.')}: ${err.message}`)
    };
}
//# sourceMappingURL=index.js.map