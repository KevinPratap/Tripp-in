import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

/**
 * Runs on every request before the CDN edge, which is the only reliable place for
 * these headers on a Next.js app behind Railway (next.config headers() are skipped
 * for prerendered pages).
 */
export function middleware(request: NextRequest) {
  const response = NextResponse.next();

  response.headers.set('X-Content-Type-Options', 'nosniff');
  response.headers.set('X-Frame-Options', 'DENY');
  response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
  response.headers.set('Permissions-Policy', 'geolocation=(self), camera=(), microphone=()');
  response.headers.set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');

  // Next.js serves prerendered HTML with a one year CDN cache. Five minutes keeps
  // the site fast without pinning stale itineraries or copy in front of users.
  // The content-type is not available yet at this point in the pipeline, so key off
  // the request's Accept header instead.
  const wantsHtml = (request.headers.get('accept') || '').includes('text/html');
  if (wantsHtml) {
    response.headers.set(
      'Cache-Control',
      'public, max-age=0, s-maxage=300, stale-while-revalidate=600'
    );
  }

  return response;
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|icon.svg).*)']
};
