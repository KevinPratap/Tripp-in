'use client';

import React, { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { apiFetch } from '@/lib/api-client';
import { unwrapTripDetails, FlatTripDetails, formatDateRange } from '@/lib/trip-contract';
import { Printer, ArrowLeft, ShieldCheck, MapPin, Calendar, Users, DollarSign, ExternalLink } from 'lucide-react';

export default function TripPrintPage() {
  const params = useParams();
  const tripId = params?.id as string;
  const [tripData, setTripData] = useState<FlatTripDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!tripId) return;
    setLoading(true);
    apiFetch(`/api/v1/trips/${tripId}`)
      .then(async (res) => {
        if (!res.ok) throw new Error(`Could not load trip (${res.status})`);
        return res.json();
      })
      .then((raw) => {
        const unwrapped = unwrapTripDetails(raw);
        if (!unwrapped) throw new Error('Invalid trip format');
        setTripData(unwrapped);
        setLoading(false);
      })
      .catch((err) => {
        setError(err.message || 'Failed to load field ticket');
        setLoading(false);
      });
  }, [tripId]);

  if (loading) {
    return (
      <main className="min-h-screen bg-white text-[#18181B] flex flex-col items-center justify-center p-8 space-y-4">
        <div className="w-10 h-10 border-4 border-[#18181B] border-t-[#E11D48] rounded-full animate-spin" />
        <p className="font-display font-black text-sm uppercase tracking-wider">
          Rendering Printable Field Manifest...
        </p>
      </main>
    );
  }

  if (error || !tripData) {
    return (
      <main className="min-h-screen bg-white text-[#18181B] flex flex-col items-center justify-center p-8 space-y-4 text-center">
        <h1 className="font-display font-black text-xl uppercase text-[#E11D48]">
          Manifest Not Available
        </h1>
        <p className="text-sm text-zinc-600 max-w-sm">
          {error || 'Unable to retrieve itinerary data for printing.'}
        </p>
        <Link
          href={`/trip/${tripId}`}
          className="comic-btn-secondary px-6 py-2.5 rounded-lg text-xs font-black uppercase tracking-wider"
        >
          Return to Itinerary
        </Link>
      </main>
    );
  }

  const days = tripData.itinerary?.days || [];
  const liveUrl = `https://web-production-a9ec6.up.railway.app/trip/${tripId}`;
  const qrCodeUrl = `https://api.qrserver.com/v1/create-qr-code/?size=160x160&data=${encodeURIComponent(liveUrl)}&margin=4`;

  return (
    <div className="min-h-screen bg-white text-[#18181B] p-4 sm:p-8 selection:bg-[#E11D48] selection:text-white print:p-0">
      {/* Action Toolbar (Screen Only) */}
      <nav className="max-w-4xl mx-auto mb-8 flex items-center justify-between gap-4 print:hidden bg-[#FAF8F5] border-[2.5px] border-[#18181B] p-4 rounded-xl shadow-[4px_4px_0px_#18181B]">
        <div className="flex items-center gap-3">
          <Link
            href={`/trip/${tripId}`}
            className="comic-btn-secondary px-4 py-2 min-h-11 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-2"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to Trip</span>
          </Link>
          <span className="text-xs font-bold text-zinc-600">
            High-contrast print docket optimized for physical paper and PDF save
          </span>
        </div>

        <button
          type="button"
          onClick={() => window.print()}
          className="comic-btn-primary px-5 py-2.5 min-h-11 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-2 shadow-[2px_2px_0px_#18181B]"
        >
          <Printer className="w-4 h-4" />
          <span>Print Voucher / Save PDF</span>
        </button>
      </nav>

      {/* Official Field Voucher Dossier */}
      <article className="max-w-4xl mx-auto border-[2.5px] border-[#18181B] p-6 sm:p-10 rounded-2xl bg-white shadow-[6px_6px_0px_#18181B] print:border-none print:shadow-none print:p-0">
        {/* Header Block with Live QR */}
        <header className="border-b-[2.5px] border-[#18181B] pb-6 mb-6">
          <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <span className="w-3.5 h-3.5 bg-[#E11D48] border-2 border-[#18181B]" />
                <span className="text-[11px] font-black uppercase tracking-widest text-[#E11D48]">
                  Tripp&apos;in AI Field Voucher &amp; Transit Manifest
                </span>
              </div>
              <h1 className="font-display font-black text-2xl sm:text-3xl uppercase tracking-tight text-[#18181B]">
                {tripData.destinationName}
              </h1>
              <p className="text-xs font-bold text-zinc-600 uppercase tracking-wide">
                Dispatch Reference: {tripId} // Version {tripData.currentVersion || 1}
              </p>
            </div>

            {/* Verification QR Box */}
            <div className="flex flex-col items-center text-center p-3 border-2 border-[#18181B] rounded-xl bg-[#FAF8F5] shrink-0 self-start">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={qrCodeUrl}
                alt="Scan to verify field itinerary online"
                width={120}
                height={120}
                className="w-28 h-28 border border-[#18181B] rounded"
              />
              <span className="text-[10px] font-black uppercase tracking-wider mt-2 text-[#18181B]">
                Scan to Verify Live
              </span>
              <span className="text-[9px] font-mono text-zinc-500 max-w-[120px] truncate">
                {liveUrl}
              </span>
            </div>
          </div>

          {/* Quick Stats Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-6">
            <div className="border-2 border-[#18181B] p-3 rounded-lg bg-[#FAF8F5]">
              <div className="flex items-center gap-1.5 text-[10px] font-black uppercase text-zinc-600">
                <Calendar className="w-3 h-3 text-[#E11D48]" />
                <span>Travel Dates</span>
              </div>
              <p className="font-display font-black text-xs uppercase mt-1">
                {formatDateRange(tripData.startDate, tripData.endDate)}
              </p>
            </div>

            <div className="border-2 border-[#18181B] p-3 rounded-lg bg-[#FAF8F5]">
              <div className="flex items-center gap-1.5 text-[10px] font-black uppercase text-zinc-600">
                <Users className="w-3 h-3 text-[#E11D48]" />
                <span>Party Size</span>
              </div>
              <p className="font-display font-black text-xs uppercase mt-1">
                {tripData.travelersCount} {tripData.travelersCount === 1 ? 'Traveler' : 'Travelers'}
              </p>
            </div>

            <div className="border-2 border-[#18181B] p-3 rounded-lg bg-[#FAF8F5]">
              <div className="flex items-center gap-1.5 text-[10px] font-black uppercase text-zinc-600">
                <DollarSign className="w-3 h-3 text-[#E11D48]" />
                <span>Local Currency</span>
              </div>
              <p className="font-display font-black text-xs uppercase mt-1">
                {tripData.currency || 'EUR'}
              </p>
            </div>

            <div className="border-2 border-[#18181B] p-3 rounded-lg bg-[#FAF8F5]">
              <div className="flex items-center gap-1.5 text-[10px] font-black uppercase text-zinc-600">
                <ShieldCheck className="w-3 h-3 text-emerald-600" />
                <span>Audit Status</span>
              </div>
              <p className="font-display font-black text-xs uppercase mt-1 text-[#E11D48]">
                {tripData.status}
              </p>
            </div>
          </div>
        </header>

        {/* Provenance Guarantee Statement */}
        <section className="mb-8 p-4 border-2 border-[#18181B] rounded-xl bg-zinc-50">
          <h2 className="text-xs font-black uppercase tracking-wider text-[#18181B] flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-[#E11D48]" />
            Verified Provenance Certificate
          </h2>
          <p className="text-xs text-zinc-700 mt-1 leading-relaxed">
            All stops and opening windows verified via OpenStreetMap. Travel durations computed via
            OSRM navigation engine. Zero stock imagery and zero fabricated reviews are permitted on
            this ticket.
          </p>
        </section>

        {/* Day-by-Day Field Schedule */}
        <section className="space-y-8">
          <h2 className="font-display font-black text-lg uppercase tracking-tight border-b-2 border-[#18181B] pb-2">
            Field Itinerary Manifest
          </h2>

          {days.length === 0 ? (
            <p className="text-xs text-zinc-600 italic">No scheduled days recorded.</p>
          ) : (
            days.map((day: any) => {
              const activities = day.activities || [];
              return (
                <article
                  key={day.id || day.dayIndex}
                  className="border-2 border-[#18181B] rounded-xl overflow-hidden print:break-inside-avoid mb-6"
                >
                  {/* Day Header Banner */}
                  <div className="bg-[#FAF8F5] border-b-2 border-[#18181B] p-3.5 flex items-center justify-between">
                    <div>
                      <span className="font-display font-black text-sm uppercase text-[#18181B]">
                        Day {day.dayIndex} - {day.date}
                      </span>
                      {day.summary && (
                        <p className="text-xs text-zinc-600 font-medium mt-0.5">{day.summary}</p>
                      )}
                    </div>
                    {day.weatherSummary && (
                      <span className="text-[11px] font-bold text-zinc-700 bg-white border border-[#18181B] px-2.5 py-1 rounded">
                        {day.weatherSummary}
                      </span>
                    )}
                  </div>

                  {/* Schedule Table */}
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead>
                        <tr className="bg-zinc-100 border-b-2 border-[#18181B] text-[10px] font-black uppercase tracking-wider text-zinc-600">
                          <th className="p-2.5 w-12 text-center">Stop</th>
                          <th className="p-2.5 w-28">Time</th>
                          <th className="p-2.5">Activity &amp; Location</th>
                          <th className="p-2.5 w-24 text-right">Transit Trail</th>
                          <th className="p-2.5 w-20 text-right">Est. Cost</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-zinc-200">
                        {activities.map((act: any, actIdx: number) => (
                          <tr key={act.id || actIdx} className="hover:bg-zinc-50">
                            <td className="p-2.5 text-center font-black text-zinc-500">
                              {String(actIdx + 1).padStart(2, '0')}
                            </td>
                            <td className="p-2.5 font-bold font-mono text-zinc-800">
                              {act.startTime} - {act.endTime}
                            </td>
                            <td className="p-2.5">
                              <p className="font-black text-zinc-900">{act.title}</p>
                              {act.reason && (
                                <p className="text-[11px] text-zinc-500 mt-0.5">{act.reason}</p>
                              )}
                            </td>
                            <td className="p-2.5 text-right font-bold text-zinc-600">
                              {act.travelTimeFromPreviousMinutes > 0
                                ? `${act.travelTimeFromPreviousMinutes} min`
                                : '-'}
                            </td>
                            <td className="p-2.5 text-right font-black text-[#E11D48]">
                              {act.estimatedCost ? `${tripData.currency || '$'}${act.estimatedCost}` : 'Free'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </article>
              );
            })
          )}
        </section>

        {/* Footer Docket */}
        <footer className="mt-8 pt-6 border-t-[2.5px] border-[#18181B] flex flex-col sm:flex-row items-center justify-between gap-4 text-xs font-bold text-zinc-500">
          <div>
            Official Travel Manifest generated by Tripp&apos;in AI. Verified via OSRM &amp; OpenStreetMap.
          </div>
          <div className="font-mono text-[11px] text-zinc-600">
            {liveUrl}
          </div>
        </footer>
      </article>
    </div>
  );
}
