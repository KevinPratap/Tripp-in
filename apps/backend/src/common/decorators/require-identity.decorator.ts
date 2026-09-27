import { SetMetadata } from '@nestjs/common';

export const REQUIRE_IDENTITY_KEY = 'requireIdentity';

/**
 * Marks a route that must have a real caller identity.
 *
 * The auth guard deliberately lets anonymous reads through, so a shared trip link works
 * without an account. Routes that expose one person's own data have to opt out of that
 * fallback, otherwise an anonymous request would be answered as the demo account. This
 * decorator is how a route says "not you" instead of quietly returning someone else.
 */
export const RequireIdentity = () => SetMetadata(REQUIRE_IDENTITY_KEY, true);
