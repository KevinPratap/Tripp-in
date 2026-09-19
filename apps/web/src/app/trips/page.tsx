'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Trash2, Share2 } from 'lucide-react';
import { createShareLink, deleteSavedTrip, fetchMyTrips, SavedTripSummary } from '@/lib/auth-client';
import { sessionToken } from '@/lib/api-client';
import { removeSavedTrip } from '@/lib/saved-trips';

/**
 * Saved trips.
 *
 * Reads /me/trips with the browser's session token. Trips planned while signed out are
 * moved onto the account at sign in, so this list is the whole picture rather than only
 * what was created after signing up.
 */
export default function TripsPage() {
  const [trips, setTrips] = useState<SavedTripSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [signedIn, setSignedIn] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [shareFor, setShareFor] = useState<Record<string, string>>({});

  useEffect(() => {
    setSignedIn(Boolean(sessionToken()));
    fetchMyTrips()
      .then((list) => {
        setTrips(list);
        setSignedIn(Boolean(sessionToken()));
      })
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const [deletingId, setDeletingId] = useState<string | null>(null);

  const handleShare = async (tripId: string) => {
    try {
      const share = await createShareLink(tripId);
      setShareFor((prev) => ({ ...prev, [tripId]: share.url }));
      if (typeof navigator !== 'undefined' && navigator.clipboard) {
        await navigator.clipboard.writeText(share.url);
      }
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const handleDeleteTrip = async (tripId: string) => {
    if (!confirm('Delete this trip? This removes the itinerary and everything saved with it.')) {
      return;
    }
    setDeletingId(tripId);
    try {
      await deleteSavedTrip(tripId);
      removeSavedTrip(tripId);
      setTrips((prev) => prev.filter((t) => t.id !== tripId));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <main className="min-h-screen bg-[#FAF8F5] px-4 py-8">
      <div className="mx-auto w-full max-w-3xl space-y-5">
        <div className="flex items-center justify-between gap-3">
          <h1 className="font-display font-black text-2xl sm:text-3xl text-[#18181B]">My trips</h1>
          <Link
            href="/"
            className="comic-btn-secondary px-3 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider inline-flex items-center"
          >
            Plan a trip
          </Link>
        </div>

        {error && (
          <p className="text-sm font-bold text-[#E11D48] border-2 border-[#E11D48] rounded-lg p-3">
            {error}
          </p>
        )}

        {loading ? (
          <section className="comic-panel bg-white rounded-xl p-5">
            <p className="text-sm font-bold text-[#52525B]">Loading your trips</p>
          </section>
        ) : !signedIn ? (
          <section className="comic-panel bg-white rounded-xl p-5">
            <p className="text-sm font-bold text-[#18181B]">You are not signed in.</p>
            <p className="text-sm font-medium text-[#52525B] mt-1">
              Trips you planned on this browser stay on this browser until you sign in. The
              sign in link moves them onto the account.
            </p>
            <Link
              href="/login"
              className="comic-btn-primary inline-flex items-center justify-center min-h-11 px-4 rounded-lg text-xs font-black uppercase tracking-wider mt-4"
            >
              Sign in
            </Link>
          </section>
        ) : trips.length === 0 ? (
          <section className="comic-panel bg-white rounded-xl p-5">
            <p className="text-sm font-bold text-[#18181B]">No trips on this account yet.</p>
            <p className="text-sm font-medium text-[#52525B] mt-1">
              Anything planned on this browser before signing in is moved here automatically.
            </p>
          </section>
        ) : (
          trips.map((trip) => {
            const verified = trip.itineraryStatus === 'VERIFIED';
            return (
              <section key={trip.id} className="comic-panel bg-white rounded-xl p-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="text-[10px] font-black uppercase tracking-widest text-[#E11D48]">
                      {trip.destinationName}
                    </p>
                    <p className="text-[11px] font-bold text-[#52525B] mt-1">
                      {new Date(trip.startDate).toLocaleDateString()} to{' '}
                      {new Date(trip.endDate).toLocaleDateString()}
                    </p>
                  </div>
                  <span
                    className={`text-[10px] font-black uppercase tracking-wider border-2 rounded px-2 py-1 ${
                      verified
                        ? 'border-emerald-700 text-emerald-700'
                        : 'border-[#E11D48] text-[#E11D48]'
                    }`}
                  >
                    {trip.itineraryStatus || trip.status}
                  </span>
                </div>

                <div className="mt-3 flex flex-wrap gap-2 text-[10px] font-black uppercase tracking-wider text-[#52525B]">
                  <span className="border border-[#18181B] rounded px-1.5 py-0.5">
                    {trip.dayCount} day{trip.dayCount === 1 ? '' : 's'}
                  </span>
                  <span className="border border-[#18181B] rounded px-1.5 py-0.5">
                    {trip.stopCount} stops
                  </span>
                </div>

                <div className="mt-4 flex flex-wrap gap-2">
                  <Link
                    href={`/trip/${trip.id}`}
                    className="comic-btn-secondary px-3 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider inline-flex items-center"
                  >
                    Open trip
                  </Link>
                  <Link
                    href={`/trip/${trip.id}/today`}
                    className="comic-btn-secondary px-3 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider inline-flex items-center"
                  >
                    Today
                  </Link>
                  <button
                    type="button"
                    onClick={() => handleShare(trip.id)}
                    className="comic-btn-primary px-3 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider inline-flex items-center gap-1.5"
                  >
                    <Share2 className="w-3.5 h-3.5" />
                    <span>{trip.shareToken ? 'Copy share link' : 'Create share link'}</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDeleteTrip(trip.id)}
                    disabled={deletingId === trip.id}
                    className="comic-btn-secondary px-3 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider inline-flex items-center gap-1.5 text-zinc-500 hover:text-[#E11D48] hover:border-[#E11D48] transition-colors"
                    title="Permanently scrap and delete this trip"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>{deletingId === trip.id ? 'Scrapping...' : 'Scrap Ticket'}</span>
                  </button>
                </div>

                {(shareFor[trip.id] || trip.shareToken) && (
                  <p className="mt-3 text-[11px] font-mono text-[#52525B] break-all">
                    {shareFor[trip.id] || `/t/${trip.shareToken}`}
                  </p>
                )}
              </section>
            );
          })
        )}
      </div>
    </main>
  );
}
