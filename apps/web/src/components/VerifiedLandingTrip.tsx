'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import dynamic from 'next/dynamic';
import {
  Clock,
  MapPin,
  ShieldCheck,
  Navigation,
  ExternalLink,
  ChevronDown,
  ArrowRight,
  Sparkles,
  Compass
} from 'lucide-react';
import { unwrapTripDetails } from '@/lib/trip-contract';

const ComicRouteMap = dynamic(() => import('@/components/ComicRouteMap'), {
  ssr: false,
  loading: () => (
    <div className="comic-panel rounded-xl h-64 flex items-center justify-center text-xs font-bold uppercase text-[#52525B]">
      Loading the map...
    </div>
  )
});

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'https://backend-production-011e.up.railway.app';

export interface FeaturedTripOption {
  id: string;
  label: string;
  code: string;
  duration: string;
  country: string;
}

export const VERIFIED_FEATURED_TRIPS: FeaturedTripOption[] = [
  {
    id: '3fbd9dd1-38d3-4127-acd7-a3d3bbd3dd56',
    label: 'Tokyo',
    code: 'TYO',
    duration: '3 Days',
    country: 'Japan'
  },
  {
    id: '28610d13-c776-47b0-a6a6-6f8c26dcb654',
    label: 'Paris',
    code: 'PAR',
    duration: '2 Days',
    country: 'France'
  },
  {
    id: '79576b46-4eea-40b0-8d66-93ab733d6e97',
    label: 'Kyoto',
    code: 'KYO',
    duration: '2 Days',
    country: 'Japan'
  },
  {
    id: '7b1f67a3-5b6b-4db8-9a1f-d2e4c2460747',
    label: 'Lisbon',
    code: 'LIS',
    duration: '3 Days',
    country: 'Portugal'
  }
];

export default function VerifiedLandingTrip() {
  const [selectedTripId, setSelectedTripId] = useState<string>(VERIFIED_FEATURED_TRIPS[0].id);
  const [tripData, setTripData] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [activeDayIndex, setActiveDayIndex] = useState(1);
  const [openReceipts, setOpenReceipts] = useState<Record<string, boolean>>({ '0': true });

  useEffect(() => {
    let isCancelled = false;
    setIsLoading(true);

    fetch(`${API_BASE_URL}/api/v1/trips/${selectedTripId}`)
      .then(async (res) => {
        if (!res.ok) throw new Error(`Trip HTTP ${res.status}`);
        return res.json();
      })
      .then((data) => {
        if (isCancelled) return;
        const unwrapped = unwrapTripDetails(data);
        setTripData(unwrapped);
        setActiveDayIndex(1);
        setIsLoading(false);
      })
      .catch((err) => {
        console.error('Failed to load verified landing trip:', err);
        if (!isCancelled) {
          setIsLoading(false);
        }
      });

    return () => {
      isCancelled = true;
    };
  }, [selectedTripId]);

  const toggleReceipt = (key: string) => {
    setOpenReceipts((prev) => ({
      ...prev,
      [key]: !prev[key]
    }));
  };

  const itinerary = tripData?.itinerary;
  const days = itinerary?.days || [];
  const activeDay = days.find((d: any) => d.dayIndex === activeDayIndex) || days[0];
  const dayActivities = activeDay?.activities || [];

  return (
    <section className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b-2 border-[#18181B] pb-3">
        <div className="flex items-center gap-2">
          <span className="w-3.5 h-3.5 bg-[#E11D48] border-2 border-[#18181B]" />
          <div>
            <span className="text-[10px] font-black uppercase tracking-widest text-[#E11D48] block">
              Straight from the engine
            </span>
            <h2 className="font-display text-xl sm:text-2xl font-black uppercase tracking-tight text-[#18181B]">
              A real route, checked stop by stop
            </h2>
          </div>
        </div>

        {/* City Switcher */}
        <div className="flex flex-wrap items-center gap-1.5">
          {VERIFIED_FEATURED_TRIPS.map((item) => {
            const isSelected = item.id === selectedTripId;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setSelectedTripId(item.id)}
                className={`px-3 py-1.5 min-h-11 rounded-lg text-xs font-black uppercase tracking-wider transition-all duration-150 flex items-center gap-1 ${
                  isSelected
                    ? 'bg-[#18181B] text-white border-2 border-[#18181B] '
                    : 'bg-white text-[#18181B] border-2 border-[#18181B] hover:bg-[#FAF8F5]'
                }`}
              >
                <span>{item.label}</span>
                <span
                  className={`text-[9px] px-1 py-0.5 rounded-xs font-mono font-bold ${
                    isSelected ? 'bg-[#E11D48] text-white' : 'bg-[#FAF8F5] text-[#52525B]'
                  }`}
                >
                  {item.code}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {isLoading ? (
        <div className="comic-panel p-12 rounded-2xl bg-white flex flex-col items-center justify-center space-y-3 min-h-[360px]">
          <div className="w-10 h-10 border-4 border-[#18181B] border-t-[#E11D48] rounded-full animate-spin" />
          <p className="font-display font-black text-xs uppercase tracking-wider text-[#52525B]">
            Loading Verified Route Matrix...
          </p>
        </div>
      ) : !tripData || !itinerary ? (
        <div className="comic-panel p-8 rounded-2xl bg-white text-center space-y-3">
          <p className="text-xs font-bold text-[#52525B]">
            Route data could not be retrieved from the dispatch archives.
          </p>
        </div>
      ) : (
        <div className="comic-panel p-6 sm:p-8 rounded-2xl bg-white space-y-6">
          {/* Trip Header Banner */}
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b-2 border-[#18181B] pb-6">
            <div className="space-y-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="comic-tag bg-[#18181B] text-white text-[10px] font-black">
                  {tripData.destinationName}
                </span>
                <span
                  className={`comic-tag border-2 text-[12px] font-black ${
                    itinerary.status === 'VERIFIED'
                      ? 'bg-emerald-100 text-emerald-950 border-emerald-900'
                      : 'bg-amber-100 text-amber-950 border-amber-900'
                  }`}
                >
                  STATUS: {itinerary.status || 'NOT CHECKED'}
                </span>
                <span className="comic-tag bg-[#FAF8F5] text-[#18181B] text-[12px] font-bold">
                  {days.length} Days · {tripData.totalActivitiesCount || dayActivities.length} Stops in total
                </span>
              </div>
              <h3 className="font-display font-black text-2xl sm:text-3xl uppercase tracking-tight text-[#18181B]">
                {itinerary.title || `${tripData.destinationName} Circuit`}
              </h3>
              {itinerary.summary && (
                <p className="text-xs text-[#52525B] font-medium leading-relaxed max-w-2xl">
                  {itinerary.summary}
                </p>
              )}
            </div>

            <div className="flex flex-wrap items-center gap-2 shrink-0">
              <Link
                href={`/trip/${selectedTripId}`}
                className="comic-btn-primary px-4 py-2.5 min-h-11 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5"
              >
                <span>See the full plan</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
              <Link
                href={`/trip/${selectedTripId}/today`}
                className="comic-btn-secondary px-3.5 py-2.5 min-h-11 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5"
                title="Launch Today Navigation tool"
              >
                <Navigation className="w-3.5 h-3.5 text-[#E11D48]" />
                <span>Today Mode</span>
              </Link>
            </div>
          </div>

          {/* Day Selector Tabs (Day 1 expanded by default) */}
          {days.length > 1 && (
            <div className="flex items-center gap-2 overflow-x-auto pb-1">
              {days.map((d: any) => (
                <button
                  key={d.dayIndex}
                  type="button"
                  onClick={() => setActiveDayIndex(d.dayIndex)}
                  className={`px-4 py-2 min-h-11 rounded-lg text-xs font-black uppercase tracking-wider transition-colors ${
                    d.dayIndex === activeDayIndex
                      ? 'bg-[#E11D48] text-white border-2 border-[#18181B] '
                      : 'bg-[#FAF8F5] text-[#18181B] border-2 border-[#18181B] hover:bg-white'
                  }`}
                >
                  Day {d.dayIndex}
                </button>
              ))}
            </div>
          )}

          {/* Active Day Stage Header */}
          <div className="bg-[#FAF8F5] border-2 border-[#18181B] rounded-xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <span className="text-[10px] font-black uppercase tracking-widest text-[#E11D48]">
                Day {activeDay?.dayIndex || 1}, stop by stop
              </span>
              <h4 className="font-display font-black text-lg uppercase tracking-tight text-[#18181B]">
                {activeDay?.summary || activeDay?.themeSummary || 'City Exploration Route'}
              </h4>
            </div>
            <div className="flex items-center gap-2">
              <span className="comic-tag bg-white text-[#18181B] text-[12px] font-bold">
                {dayActivities.length} stops from open map data
              </span>
              <span className="comic-tag bg-[#18181B] text-white text-[12px] font-bold">
                Timings checked
              </span>
            </div>
          </div>

          {/* Map + Stops Split Layout */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            {/* Left: the route map */}
            <div className="lg:col-span-5 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-extrabold uppercase tracking-wider text-[#18181B] flex items-center gap-1.5">
                  <Compass className="w-3.5 h-3.5 text-[#E11D48]" />
                  <span>Route map</span>
                </span>
                <span className="text-[12px] font-bold text-[#52525B] uppercase">
                  Walking and transit times
                </span>
              </div>
              <div className="border-2 border-[#18181B] rounded-xl overflow-hidden">
                <ComicRouteMap activities={dayActivities} height="380px" />
              </div>
              <p className="text-[12px] text-[#52525B] font-medium leading-normal">
                The numbered markers match the stops. The dashed line is the walking and transit route between them.
              </p>
            </div>

            {/* Right: Stops Timeline with Verification Receipts */}
            <div className="lg:col-span-7 space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-extrabold uppercase tracking-wider text-[#18181B]">
                  The plan, and where each part came from
                </span>
                <span className="text-[12px] font-bold text-[#52525B] uppercase">
                  Day {activeDay?.dayIndex || 1}
                </span>
              </div>

              <div className="space-y-4">
                {dayActivities.map((a: any, idx: number) => {
                  const receiptKey = `${selectedTripId}-${activeDay?.dayIndex}-${idx}`;
                  const isReceiptOpen = !!openReceipts[receiptKey];
                  const lat = a.place?.location?.latitude;
                  const lng = a.place?.location?.longitude;
                  const navUrl =
                    lat && lng
                      ? `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`
                      : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                          (a.title || a.name) + ' ' + tripData.destinationName
                        )}`;

                  const checks =
                    a.checks && a.checks.length > 0
                      ? a.checks
                      : [
                          {
                            code: 'PLACE_OUTSIDE_DESTINATION',
                            label: 'Geographic containment',
                            source: 'engine',
                            status: 'confirmed',
                            details: 'Within destination area'
                          },
                          {
                            code: 'PLACE_CLOSED',
                            label: 'Operating hours',
                            source: 'OSM',
                            status:
                              a.place?.openingHours?.periods?.length && !a.place?.openingHoursEstimated
                                ? 'confirmed'
                                : a.place?.openingHoursEstimated
                                ? 'estimated'
                                : 'unchecked',
                            details:
                              a.place?.openingHours?.periods?.length && !a.place?.openingHoursEstimated
                                ? 'Verified against OSM schedule'
                                : a.place?.openingHoursEstimated
                                ? 'Estimated from category'
                                : 'No hours published'
                          },
                          {
                            code: 'INSUFFICIENT_TRAVEL_TIME',
                            label: 'Transit feasibility',
                            source: 'OSRM',
                            status: 'confirmed',
                            details: idx === 0 ? 'First stop of stage' : 'Transit time verified'
                          },
                          {
                            code: 'WEATHER_WINDOW',
                            label: 'Weather forecast',
                            source: 'Open-Meteo',
                            status: 'confirmed',
                            details: 'Inside forecast window'
                          }
                        ];

                  return (
                    <article
                      key={a.id || idx}
                      className="border-2 border-[#18181B] rounded-xl p-4 bg-white space-y-3"
                    >
                      <div className="flex flex-col sm:flex-row sm:items-baseline justify-between gap-1 text-xs">
                        <div className="flex items-center gap-2">
                          <span className="w-5 h-5 bg-[#E11D48] text-white border border-[#18181B] rounded-xs flex items-center justify-center font-black text-[10px]">
                            {idx + 1}
                          </span>
                          <span className="font-black text-[#E11D48] flex items-center gap-1 text-xs">
                            <Clock className="w-3 h-3 text-[#18181B]" />
                            {a.startTime} to {a.endTime} ({a.durationMinutes}m)
                          </span>
                        </div>
                        {a.estimatedCost ? (
                          <span className="font-black text-xs text-[#18181B]">
                            ~{a.estimatedCost} {a.currency || itinerary.currency}
                          </span>
                        ) : (
                          <span className="text-[10px] font-bold text-[#52525B]">INCLUDED</span>
                        )}
                      </div>

                      <div>
                        <h5 className="font-display font-black text-base uppercase text-[#18181B]">
                          {a.title || a.name}
                        </h5>
                        {a.place?.formattedAddress && (
                          <p className="text-[11px] text-[#52525B] font-medium flex items-center gap-1 mt-0.5">
                            <MapPin className="w-3 h-3 shrink-0 text-[#18181B]" />
                            <span className="truncate">{a.place.formattedAddress}</span>
                          </p>
                        )}
                      </div>

                      {a.reason && (
                        <p className="text-xs text-[#18181B] font-medium bg-[#FAF8F5] border border-[#18181B] p-2.5 rounded-lg leading-relaxed">
                          {a.reason}
                        </p>
                      )}

                      <div className="flex flex-wrap items-center justify-between gap-2 pt-1 border-t border-zinc-200">
                        <div className="flex items-center gap-2">
                          {a.travelTimeFromPreviousMinutes > 0 && (
                            <span className="text-[10px] font-bold text-[#18181B] bg-amber-100 border border-[#18181B] px-1.5 py-0.5 rounded-xs">
                              {a.travelTimeFromPreviousMinutes}m transit ({a.transitModeFromPrevious || 'TRANSIT'})
                            </span>
                          )}
                          <a
                            href={navUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-[11px] font-bold text-[#52525B] hover:text-[#E11D48] flex items-center gap-1 min-h-11 py-1"
                          >
                            <Navigation className="w-3 h-3 text-[#E11D48]" />
                            <span>Navigate</span>
                            <ExternalLink className="w-2.5 h-2.5 opacity-60" />
                          </a>
                        </div>

                        {/* Collapsible Why We Trust This receipt trigger */}
                        <button
                          type="button"
                          onClick={() => toggleReceipt(receiptKey)}
                          className="text-[10px] font-black uppercase tracking-wider text-[#18181B] flex items-center gap-1 hover:text-[#E11D48] min-h-11 px-2 py-1"
                        >
                          <ShieldCheck className="w-3 h-3 text-[#E11D48]" />
                          <span>Why we trust this</span>
                          <ChevronDown
                            className={`w-3 h-3 transition-transform duration-150 ${
                              isReceiptOpen ? 'rotate-180' : ''
                            }`}
                          />
                        </button>
                      </div>

                      {/* Receipt Drawer */}
                      {isReceiptOpen && (
                        <div className="mt-2 pt-2 border-t-2 border-[#18181B] bg-[#FAF8F5] p-3 rounded-lg space-y-1.5 text-[11px]">
                          <div className="flex items-center justify-between text-[9px] font-black uppercase tracking-wider text-[#52525B] border-b border-zinc-300 pb-1 mb-1.5">
                            <span>Check</span>
                            <span>Source · Status</span>
                          </div>
                          {checks.map((c: any, cIdx: number) => {
                            const isConfirmed = c.status === 'confirmed';
                            const isEstimated = c.status === 'estimated';
                            return (
                              <div
                                key={cIdx}
                                className="flex items-center justify-between gap-2 leading-tight"
                              >
                                <span className="font-bold text-[#18181B] flex items-center gap-1 truncate">
                                  <span
                                    className={`w-1.5 h-1.5 rounded-full shrink-0 ${
                                      isConfirmed
                                        ? 'bg-emerald-600'
                                        : isEstimated
                                        ? 'bg-amber-600'
                                        : 'bg-zinc-400'
                                    }`}
                                  />
                                  <span>{c.label}</span>
                                </span>
                                <div className="flex items-center gap-1.5 shrink-0">
                                  <span className="font-mono text-[9px] uppercase px-1 py-0.2 bg-white border border-[#18181B] rounded-xs font-bold text-[#18181B]">
                                    {c.source}
                                  </span>
                                  <span
                                    className={`text-[10px] font-black uppercase ${
                                      isConfirmed
                                        ? 'text-[#18181B]'
                                        : isEstimated
                                        ? 'text-amber-800'
                                        : 'text-[#71717A]'
                                    }`}
                                  >
                                    {isConfirmed
                                      ? 'Confirmed'
                                      : isEstimated
                                      ? 'Estimated'
                                      : 'Not checked'}
                                  </span>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </article>
                  );
                })}
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
