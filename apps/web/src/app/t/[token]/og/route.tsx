import { ImageResponse } from 'next/og';
import { formatDateRange } from '@/lib/trip-contract';
import { loadSharedTrip } from '@/lib/share-loader';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const size = { width: 1200, height: 630 };

/**
 * Preview card for WhatsApp and iMessage.
 *
 * Shows only what the trip actually contains: destination, dates, day and stop counts and
 * whether the engine verified it. No stock imagery and no invented figures.
 */
export async function GET(
  _request: Request,
  context: { params: Promise<{ token: string }> }
) {
  const { token } = await context.params;
  const shared = await loadSharedTrip(token);

  const destination = shared?.flat.destinationName || "Trippin' AI";
  const title = shared?.itineraryTitle || destination;
  const dates = shared ? formatDateRange(shared.flat.startDate, shared.flat.endDate) : '';
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
        <div style={{ display: 'flex', fontSize: 26, letterSpacing: 4, color: '#E11D48' }}>
          SHARED TRIP
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', fontSize: 30, color: '#52525B' }}>{destination}</div>
          <div style={{ display: 'flex', fontSize: 68, color: '#18181B' }}>{title}</div>
          <div style={{ display: 'flex', fontSize: 30, color: '#52525B' }}>{dates}</div>
        </div>

        <div style={{ display: 'flex', gap: 18, fontSize: 26, color: '#18181B' }}>
          <div
            style={{
              display: 'flex',
              border: '3px solid #18181B',
              borderRadius: 8,
              padding: '8px 18px'
            }}
          >
            {days} days
          </div>
          <div
            style={{
              display: 'flex',
              border: '3px solid #18181B',
              borderRadius: 8,
              padding: '8px 18px'
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
    size
  );
}
