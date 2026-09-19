/**
 * Thin wrapper around fetch that gives this browser its own guest identity.
 *
 * The API accepts any caller for reads (so a shared trip link works) but wants an
 * identity for writes. Rather than a shared demo account, every browser keeps a
 * random session id and sends it as X-Guest-Session, which the backend maps to
 * that browser's own account.
 */

const SESSION_STORAGE_KEY = 'trippin.guest.session';

export function guestSessionId(): string {
  if (typeof window === 'undefined') return '';
  try {
    const existing = window.localStorage.getItem(SESSION_STORAGE_KEY);
    if (existing) return existing;
    const created =
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `g-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    window.localStorage.setItem(SESSION_STORAGE_KEY, created);
    return created;
  } catch {
    return '';
  }
}

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'https://backend-production-011e.up.railway.app';

export function apiUrl(path: string): string {
  return path.startsWith('http') ? path : `${API_BASE_URL}${path}`;
}

export async function apiFetch(
  path: string,
  init: RequestInit = {}
): Promise<Response> {
  const sessionId = guestSessionId();
  const headers = new Headers(init.headers || {});
  if (sessionId) headers.set('X-Guest-Session', sessionId);
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  return fetch(apiUrl(path), { ...init, headers });
}
