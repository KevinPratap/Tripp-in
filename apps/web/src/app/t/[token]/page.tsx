import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { formatDateRange } from '@/lib/trip-contract';
import { loadSharedTrip } from '@/lib/share-loader';

interface PageProps {
  params: Promise<{ token: string }>;
}

/**
 * Public share page. Reads one trip through its share token and renders a static summary,
 * so a link pasted into a chat opens something real without an account.
 *
 * The interactive version lives at /trip/[id], which is also readable anonymously.
 */
export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { token } = await params;
  const shared = await loadSharedTrip(token);

  if (!shared) {
    return {
      title: 'Share link not found',
      description: 'That share link is not valid or was revoked.'
    };
  }

  const { flat, days, itineraryTitle, itineraryStatus, stopCount } = shared;
  const title = `${itineraryTitle || flat.destinationName} | Trippin' AI`;
  const verified = itineraryStatus === 'VERIFIED' ? 'Verified' : 'Draft';
  const description = `${verified} ${days.length} day trip to ${flat.destinationName} with ${stopCount} stops, ${formatDateRange(
    flat.startDate,
    flat.endDate
  )}. Built from OpenStreetMap venues with real transit times.`;

  return {
    title,
    description,
    openGraph: {
      title,
      description,
      type: 'article',
      url: `/t/${token}`,
      siteName: "Trippin' AI",
      images: [`/t/${token}/og`]
    },
    twitter: {
      card: 'summary_large_image',
      title,
      description,
      images: [`/t/${token}/og`]
    }
  };
}

export default async function SharedTripPage({ params }: PageProps) {
  const { token } = await params;
  const shared = await loadSharedTrip(token);

  if (!shared) {
    notFound();
  }

  const { flat, days, itineraryTitle, itineraryStatus, stopCount } = shared;
  const verified = itineraryStatus === 'VERIFIED';

  return (
    <main className="min-h-screen bg-[#FAF8F5] px-4 py-8">
      <div className="mx-auto w-full max-w-2xl space-y-5">
        <div className="flex items-center justify-between gap-3">
          <span className="text-[10px] font-black uppercase tracking-widest text-[#52525B]">
            Shared trip
          </span>
          <Link
            href={`/trip/${flat.id}`}
            className="comic-btn-secondary px-3 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider inline-flex items-center"
          >
            Open interactive view
          </Link>
        </div>

        <section className="comic-panel bg-white rounded-xl p-5">
          <p className="text-[10px] font-black uppercase tracking-widest text-[#E11D48]">
            {flat.destinationName}
          </p>
          <h1 className="font-display font-black text-2xl sm:text-3xl text-[#18181B] mt-1">
            {itineraryTitle || flat.destinationName}
          </h1>
          <div className="mt-3 flex flex-wrap gap-3 text-[11px] font-black uppercase tracking-wider text-[#52525B]">
            <span className="border-2 border-[#18181B] rounded px-2 py-1">
              {formatDateRange(flat.startDate, flat.endDate)}
            </span>
            <span className="border-2 border-[#18181B] rounded px-2 py-1">
              {days.length} day{days.length === 1 ? '' : 's'}
            </span>
            <span className="border-2 border-[#18181B] rounded px-2 py-1">{stopCount} stops</span>
            <span
              className={`border-2 rounded px-2 py-1 ${
                verified
                  ? 'border-emerald-700 text-emerald-700'
                  : 'border-[#E11D48] text-[#E11D48]'
              }`}
            >
              {verified ? 'Verified by the engine' : 'Draft, not verified'}
            </span>
          </div>
          <p className="mt-3 text-xs font-medium text-[#52525B]">
            Every stop below comes from OpenStreetMap venue data, with transit times from OSRM.
            Opening hours are only stated when a source published them.
          </p>
        </section>

        {days.length === 0 ? (
          <section className="comic-panel bg-white rounded-xl p-5">
            <p className="text-sm font-bold text-[#18181B]">
              This trip has no generated itinerary yet.
            </p>
            <p className="text-sm font-medium text-[#52525B] mt-1">
              The share link works, there is simply nothing scheduled to show.
            </p>
          </section>
        ) : (
          days.map((day) => (
            <section key={day.dayIndex} className="comic-panel bg-white rounded-xl p-5">
              <div className="flex items-baseline justify-between gap-3">
                <h2 className="font-display font-black text-lg text-[#18181B]">
                  Day {String(day.dayIndex).padStart(2, '0')}
                </h2>
                <span className="text-[10px] font-black uppercase tracking-widest text-[#52525B]">
                  {day.date ? new Date(day.date).toLocaleDateString() : ''}
                </span>
              </div>
              {day.weatherSummary && (
                <p className="mt-1 text-xs font-bold text-[#52525B]">{day.weatherSummary}</p>
              )}
              <ul className="mt-4 space-y-3">
                {day.activities.map((activity, index) => (
                  <li
                    key={activity.id || index}
                    className="border-2 border-[#18181B] rounded-lg p-3"
                  >
                    <div className="flex items-baseline justify-between gap-2">
                      <span className="font-bold text-sm text-[#18181B]">{activity.title}</span>
                      {(activity.startTime || activity.endTime) && (
                        <span className="text-[10px] font-black uppercase tracking-wider text-[#52525B] whitespace-nowrap">
                          {activity.startTime} to {activity.endTime}
                        </span>
                      )}
                    </div>
                    {activity.address && (
                      <p className="text-xs font-medium text-[#52525B] mt-1">{activity.address}</p>
                    )}
                    <div className="mt-2 flex flex-wrap gap-2 text-[10px] font-black uppercase tracking-wider text-[#52525B]">
                      {typeof activity.travelTimeFromPreviousMinutes === 'number' &&
                        activity.travelTimeFromPreviousMinutes > 0 && (
                          <span className="border border-[#18181B] rounded px-1.5 py-0.5">
                            {activity.travelTimeFromPreviousMinutes}m transit
                          </span>
                        )}
                      <span className="border border-[#18181B] rounded px-1.5 py-0.5">
                        {activity.estimatedCost
                          ? `${activity.estimatedCost} ${activity.currency || ''}`.trim()
                          : 'not priced'}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          ))
        )}
      </div>
    </main>
  );
}
