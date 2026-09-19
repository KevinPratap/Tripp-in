/**
 * Trip details contract adapter.
 *
 * The backend returns a nested envelope:
 *   { trip, requirements, itinerary }
 * while older clients (and older deployments of this web app) assumed a flat
 * trip object. Keeping the unwrapping in one place means a future envelope
 * change breaks in exactly one spot instead of scattering `.slice()` crashes
 * across pages.
 */

export interface TripDetailsEnvelope {
  trip: Record<string, any>;
  requirements?: Record<string, any> | null;
  itinerary?: Record<string, any> | null;
}

export interface FlatTripDetails {
  id: string;
  destinationName: string;
  startDate: string;
  endDate: string;
  travelersCount: number;
  status: string;
  heroImageUrl?: string;
  totalActivitiesCount?: number;
  currentVersion?: number;
  currency?: string;
  pace?: string;
  budgetTotal?: number;
  requirements: Record<string, any>;
  itinerary: Record<string, any> | null;
}

/** Accepts either the nested envelope or an already-flat trip object. */
export function unwrapTripDetails(raw: any): FlatTripDetails | null {
  if (!raw) return null;

  if (raw.trip) {
    const trip = raw.trip as Record<string, any>;
    const requirements = (raw.requirements || {}) as Record<string, any>;
    return {
      id: trip.id,
      destinationName: trip.destination || requirements.destination || 'Unknown destination',
      startDate: trip.startDate || requirements.startDate || '',
      endDate: trip.endDate || requirements.endDate || '',
      travelersCount: trip.travelersCount ?? requirements.travelersCount ?? 1,
      status: trip.status || 'DRAFT',
      heroImageUrl: trip.heroImageUrl,
      totalActivitiesCount: trip.totalActivitiesCount,
      currentVersion: trip.currentVersion,
      currency: requirements.currency || trip.currency,
      pace: requirements.pace,
      budgetTotal: requirements.budgetTotal,
      requirements,
      itinerary: raw.itinerary || null,
    };
  }

  // Already flat (defensive: tolerate a rollback to the old response shape)
  return {
    id: raw.id,
    destinationName: raw.destinationName || raw.destination || 'Unknown destination',
    startDate: raw.startDate || '',
    endDate: raw.endDate || '',
    travelersCount: raw.travelersCount ?? 1,
    status: raw.status || 'DRAFT',
    heroImageUrl: raw.heroImageUrl,
    totalActivitiesCount: raw.totalActivitiesCount,
    currentVersion: raw.currentVersion,
    currency: raw.currency,
    pace: raw.pace,
    budgetTotal: raw.budgetTotal,
    requirements: raw.requirements || {},
    itinerary: raw.itinerary || null,
  };
}

export function itineraryOf(raw: any): Record<string, any> | null {
  if (!raw) return null;
  return raw.itinerary || raw;
}

/** Human readable date range, safe against missing dates. */
export function formatDateRange(startDate?: string, endDate?: string): string {
  if (!startDate) return 'Dates to be confirmed';
  const start = new Date(startDate);
  const end = endDate ? new Date(endDate) : null;
  if (Number.isNaN(start.getTime())) return 'Dates to be confirmed';
  const fmt = (d: Date) => d.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
  return end && !Number.isNaN(end.getTime()) ? `${fmt(start)} to ${fmt(end)}` : fmt(start);
}
