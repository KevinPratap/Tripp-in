/**
 * Thin wrapper around fetch that gives this browser its own guest identity.
 *
 * The API accepts any caller for reads (so a shared trip link works) but wants an
 * identity for writes. Rather than a shared demo account, every browser keeps a
 * random session id and sends it as X-Guest-Session, which the backend maps to
 * that browser's own account.
 */

const SESSION_STORAGE_KEY = 'trippin.guest.session';
const AUTH_TOKEN_KEY = 'trippin.auth.token';

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

/**
 * The signed in session token, when the browser has one. Kept in localStorage because
 * there is no cookie session on the API: the token is sent as a Bearer header.
 */
export function sessionToken(): string {
  if (typeof window === 'undefined') return '';
  try {
    return window.localStorage.getItem(AUTH_TOKEN_KEY) || '';
  } catch {
    return '';
  }
}

export function setSessionToken(token: string): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(AUTH_TOKEN_KEY, token);
  } catch {
    /* storage unavailable, the request still goes out anonymously */
  }
}

export function clearSessionToken(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(AUTH_TOKEN_KEY);
  } catch {
    /* nothing to clear */
  }
}

export function apiUrl(path: string): string {
  return path.startsWith('http') ? path : `${API_BASE_URL}${path}`;
}

export async function apiFetch(
  path: string,
  init: RequestInit = {}
): Promise<Response> {
  const sessionId = guestSessionId();
  const token = sessionToken();
  const headers = new Headers(init.headers || {});
  if (sessionId) headers.set('X-Guest-Session', sessionId);
  // A signed in session takes precedence over the guest identity.
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  return fetch(apiUrl(path), { ...init, headers });
}
