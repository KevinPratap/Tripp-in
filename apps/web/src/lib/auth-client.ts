import { apiFetch, apiUrl, clearSessionToken, guestSessionId, setSessionToken } from './api-client';

export interface SavedTripSummary {
  id: string;
  destinationName: string;
  startDate: string;
  endDate: string;
  status: string;
  itineraryStatus: string | null;
  dayCount: number;
  stopCount: number;
  currency: string | null;
  totalEstimatedCost: number | null;
  shareToken: string | null;
}

export interface RequestedMagicLink {
  email: string;
  expiresAt: string;
  delivery: 'console';
  loginUrl: string;
}

export interface VerifiedSession {
  sessionToken: string;
  expiresAt: string;
  user: { id: string; email: string; displayName: string };
  migratedTrips: number;
}

/** Asks for a magic link. The API logs the link because no mail provider is wired up. */
export async function requestMagicLink(email: string): Promise<RequestedMagicLink> {
  const res = await apiFetch('/api/v1/auth/request-link', {
    method: 'POST',
    body: JSON.stringify({ email })
  });
  if (!res.ok) {
    const detail = await res.json().catch(() => null);
    throw new Error(detail?.message || 'Could not create a sign in link.');
  }
  return res.json();
}

/**
 * Consumes a magic link and stores the session token. The guest session id this browser
 * was already using is sent along, so trips planned before signing in come with it.
 */
export async function verifyMagicLink(token: string, email?: string): Promise<VerifiedSession> {
  const res = await apiFetch('/api/v1/auth/verify', {
    method: 'POST',
    body: JSON.stringify({
      token,
      ...(email ? { email } : {}),
      guestSessionId: guestSessionId() || undefined
    })
  });
  if (!res.ok) {
    const detail = await res.json().catch(() => null);
    throw new Error(detail?.message || 'That sign in link could not be used.');
  }
  const session: VerifiedSession = await res.json();
  setSessionToken(session.sessionToken);
  return session;
}

export function signOut(): void {
  clearSessionToken();
}

/** The signed in account's trips. Returns an empty list when nobody is signed in. */
export async function fetchMyTrips(): Promise<SavedTripSummary[]> {
  const res = await apiFetch('/api/v1/me/trips');
  if (res.status === 401) return [];
  if (!res.ok) throw new Error('Could not load your trips.');
  const data = await res.json();
  return data.trips || [];
}

/** Creates (or returns) the public share link for a trip. */
export async function createShareLink(
  tripId: string
): Promise<{ token: string; url: string; createdAt: string }> {
  const res = await apiFetch(`/api/v1/trips/${tripId}/share`, { method: 'POST' });
  if (!res.ok) {
    const detail = await res.json().catch(() => null);
    throw new Error(detail?.message || 'Could not create a share link.');
  }
  return res.json();
}

/** Deletes a trip from the database. Requires organizer session. */
export async function deleteSavedTrip(
  tripId: string
): Promise<{ success: boolean; tripId: string }> {
  const res = await apiFetch(`/api/v1/trips/${tripId}`, { method: 'DELETE' });
  if (!res.ok) {
    const detail = await res.json().catch(() => null);
    throw new Error(detail?.message || 'Could not delete trip.');
  }
  return res.json();
}

export { apiUrl };
