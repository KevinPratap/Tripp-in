/**
 * Client-side persistent trip history manager.
 * Allows travelers to retain access to all drafted and verified itineraries.
 */

export interface SavedTrip {
  id: string;
  destination: string;
  startDate?: string;
  endDate?: string;
  travelersCount?: number;
  estimatedCost?: number;
  currency?: string;
  savedAt: string;
}

const STORAGE_KEY = 'trippin_saved_field_runs';

export function saveTripToHistory(trip: {
  id: string;
  destinationName?: string;
  destination?: string;
  startDate?: string;
  endDate?: string;
  travelersCount?: number;
  totalEstimatedCost?: number;
  currency?: string;
}): void {
  if (typeof window === 'undefined' || !trip?.id) return;

  try {
    const existing = getSavedTrips();
    const dest = trip.destinationName || trip.destination || 'Destination';
    const filtered = existing.filter((t) => t.id !== trip.id);

    const newEntry: SavedTrip = {
      id: trip.id,
      destination: dest,
      startDate: trip.startDate,
      endDate: trip.endDate,
      travelersCount: trip.travelersCount,
      estimatedCost: trip.totalEstimatedCost,
      currency: trip.currency || 'USD',
      savedAt: new Date().toISOString(),
    };

    filtered.unshift(newEntry);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(filtered.slice(0, 30)));
  } catch (err) {
    console.warn('Failed to save trip to localStorage:', err);
  }
}

export function getSavedTrips(): SavedTrip[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    return JSON.parse(raw);
  } catch {
    return [];
  }
}

export function removeSavedTrip(id: string): SavedTrip[] {
  if (typeof window === 'undefined') return [];
  try {
    const existing = getSavedTrips();
    const updated = existing.filter((t) => t.id !== id);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
    return updated;
  } catch {
    return [];
  }
}
