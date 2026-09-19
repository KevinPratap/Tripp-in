import { createHash, randomBytes } from 'node:crypto';

/**
 * Token helpers shared by the auth service and the auth guard.
 *
 * Rules:
 * - A raw token is only ever returned to the caller. The database stores its SHA-256
 *   hash, so a leaked row cannot be replayed against the API.
 * - Both the magic link and the session token go through these two functions, so the
 *   hash used at issue time and the hash used at lookup time can never drift.
 */

/** 32 random bytes, url safe. Used for magic links and session tokens. */
export function newToken(): string {
  return randomBytes(32).toString('base64url');
}

/** The value that is stored and looked up. Never store the raw token. */
export function hashToken(raw: string): string {
  return createHash('sha256').update(String(raw)).digest('hex');
}
