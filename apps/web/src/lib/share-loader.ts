import { FlatTripDetails, unwrapTripDetails } from './trip-contract';

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'https://backend-production-011e.up.railway.app';

export interface SharedTrip {
  flat: FlatTripDetails;
  days: Array<{
    dayIndex: number;
    date: string;
    summary?: string;
    weatherSummary?: string;
    activities: Array<{
      id: string;
      title: string;
      startTime?: string;
      endTime?: string;
      estimatedCost?: number;
      currency?: string;
      address?: string;
      travelTimeFromPreviousMinutes?: number;
    }>;
  }>;
  itineraryTitle: string | null;
  itineraryStatus: string | null;
  stopCount: number;
}

/**
 * Resolves a public share token through the API. Returns null for an unknown or revoked
 * token, so callers can render a real not found state.
 */
export async function loadSharedTrip(token: string): Promise<SharedTrip | null> {
  const clean = String(token || '').trim();
  if (!clean) return null;

  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/t/${encodeURIComponent(clean)}`, {
      cache: 'no-store'
    });
    if (!res.ok) return null;

    const raw = await res.json();
    const flat = unwrapTripDetails(raw);
    if (!flat) return null;

    const itinerary = (flat.itinerary || {}) as Record<string, any>;
    const days = Array.isArray(itinerary.days)
      ? itinerary.days.map((day: Record<string, any>) => ({
          dayIndex: Number(day.dayIndex ?? 0),
          date: String(day.date ?? ''),
          summary: day.summary,
          weatherSummary: day.weatherSummary,
          activities: Array.isArray(day.activities)
            ? day.activities.map((activity: Record<string, any>) => ({
                id: String(activity.id ?? activity.placeId ?? activity.title ?? ''),
                title: String(activity.title ?? activity.place?.name ?? 'Stop'),
                startTime: activity.startTime,
                endTime: activity.endTime,
                estimatedCost: activity.estimatedCost,
                currency: activity.currency,
                address: activity.place?.formattedAddress || activity.address,
                travelTimeFromPreviousMinutes: activity.travelTimeFromPreviousMinutes
              }))
            : []
        }))
      : [];

    return {
      flat,
      days,
      itineraryTitle: itinerary.title ?? null,
      itineraryStatus: itinerary.status ?? null,
      stopCount: days.reduce((total, day) => total + day.activities.length, 0)
    };
  } catch {
    return null;
  }
}
