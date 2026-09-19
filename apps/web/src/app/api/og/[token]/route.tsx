import { ImageResponse } from 'next/og';
import { formatDateRange } from '@/lib/trip-contract';
import { loadSharedTrip } from '@/lib/share-loader';

/**
 * Preview card for a shared trip, used by WhatsApp, iMessage and the rest.
 *
 * This is a plain route handler rather than the app/opengraph-image.tsx file convention:
 * that convention goes through a loader which breaks when the project path contains an
 * apostrophe, which is the case for this repo. A route handler compiles normally.
 *
 * The card shows only what the trip contains: destination, dates, day and stop counts and
 * whether the engine verified it. No stock imagery, no invented figures.
 */
export const runtime = 'nodejs';

const SIZE = { width: 1200, height: 630 };

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ token: string }> }
) {
  const { token } = await params;
  const shared = await loadSharedTrip(token);

  const destination = shared?.flat.destinationName || "Trippin' AI";
  const title = shared?.itineraryTitle || destination;
  const dates = shared ? formatDateRange(shared.flat.startDate, shared.flat.endDate) : 'No dates yet';
  const days = shared?.days.length ?? 0;
  const stops = shared?.stopCount ?? 0;
  const verified = shared?.itineraryStatus === 'VERIFIED';

  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          backgroundColor: '#FAF8F5',
          padding: '64px',
          fontFamily: 'sans-serif'
        }}
      >
        <div style={{ display: 'flex', fontSize: 26, letterSpacing: 6, color: '#E11D48' }}>
          SHARED TRIP
        </div>

        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', fontSize: 30, color: '#52525B' }}>{destination}</div>
          <div style={{ display: 'flex', fontSize: 66, color: '#18181B', marginTop: 10 }}>
            {title}
          </div>
          <div style={{ display: 'flex', fontSize: 30, color: '#52525B', marginTop: 14 }}>
            {dates}
          </div>
        </div>

        <div style={{ display: 'flex', fontSize: 26, color: '#18181B' }}>
          <div
            style={{
              display: 'flex',
              border: '3px solid #18181B',
              borderRadius: 8,
              padding: '8px 18px',
              marginRight: 18
            }}
          >
            {days} days
          </div>
          <div
            style={{
              display: 'flex',
              border: '3px solid #18181B',
              borderRadius: 8,
              padding: '8px 18px',
              marginRight: 18
            }}
          >
            {stops} stops
          </div>
          <div
            style={{
              display: 'flex',
              border: `3px solid ${verified ? '#047857' : '#E11D48'}`,
              color: verified ? '#047857' : '#E11D48',
              borderRadius: 8,
              padding: '8px 18px'
            }}
          >
            {verified ? 'Verified' : 'Draft'}
          </div>
        </div>
      </div>
    ),
    SIZE
  );
}
