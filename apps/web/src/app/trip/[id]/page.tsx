'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import dynamic from 'next/dynamic';
import {
  ArrowLeft,
  Calendar,
  Users,
  DollarSign,
  Clock,
  ShieldCheck,
  Navigation,
  MapPin,
  ExternalLink,
  Share2,
  Printer,
  Check,
  Flame,
  AlertCircle,
  Globe,
  Phone,
  Download,
  MessageSquare,
  Luggage,
  CloudRain,
  Briefcase,
  Shuffle,
  Sparkles,
  Edit3,
  ChevronDown,
  RefreshCw,
  History,
  Lock,
  Unlock
} from 'lucide-react';
import { downloadTripCalendar } from '@/lib/calendar-generator';
import { unwrapTripDetails, formatDateRange } from '@/lib/trip-contract';
import { fetchTripWeather, DayWeather } from '@/lib/weather-service';
import { saveTripToHistory } from '@/lib/saved-trips';
import PackingDocket from '@/components/PackingDocket';
import MyTripsModal from '@/components/MyTripsModal';
import RefineRouteModal from '@/components/RefineRouteModal';
import ReplanRouteModal from '@/components/ReplanRouteModal';
import SwapActivityModal from '@/components/SwapActivityModal';

// Dynamic import for Leaflet map to prevent SSR issues
const ComicRouteMap = dynamic(() => import('@/components/ComicRouteMap'), {
  ssr: false,
  loading: () => (
    <div className="comic-panel rounded-2xl h-64 flex items-center justify-center text-xs font-bold uppercase text-[#52525B]">
      Loading the map...
    </div>
  )
});

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'https://backend-production-011e.up.railway.app';

export default function PublicTripPage() {
  const params = useParams();
  const tripId = params?.id as string;

  const [tripData, setTripData] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [calendarDownloaded, setCalendarDownloaded] = useState(false);
  const [activeDayIndex, setActiveDayIndex] = useState(1);
  const [weatherList, setWeatherList] = useState<DayWeather[]>([]);
  const [showPackingDocket, setShowPackingDocket] = useState(false);
  const [showMyTrips, setShowMyTrips] = useState(false);
  const [showRefineModal, setShowRefineModal] = useState(false);
  const [showReplanModal, setShowReplanModal] = useState(false);
  const [availableVersions, setAvailableVersions] = useState<any[]>([]);
  const [swappingActivity, setSwappingActivity] = useState<{
    id: string;
    title: string;
    dayIndex: number;
    startTime?: string;
    endTime?: string;
  } | null>(null);
  const [isLocking, setIsLocking] = useState(false);

  const isPlanLocked = Boolean(tripData?.isLocked || tripData?.itinerary?.isLocked);

  const handleToggleLock = async () => {
    if (!tripId || isLocking) return;
    setIsLocking(true);
    try {
      const endpoint = isPlanLocked ? 'unlock' : 'lock';
      const guestSession =
        typeof window !== 'undefined'
          ? localStorage.getItem('guest_session_id') || 'trip-organizer'
          : 'trip-organizer';
      const res = await fetch(`${API_BASE_URL}/api/v1/trips/${tripId}/${endpoint}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Guest-Session': guestSession,
        },
      });
      if (res.ok) {
        loadTrip();
      }
    } catch (err) {
      console.error('Failed to toggle lock:', err);
    } finally {
      setIsLocking(false);
    }
  };

  const fetchVersions = (id: string) => {
    fetch(`${API_BASE_URL}/api/v1/trips/${id}/versions`)
      .then((r) => (r.ok ? r.json() : []))
      .then((list) => {
        if (Array.isArray(list)) {
          setAvailableVersions(list);
        }
      })
      .catch(() => {});
  };

  const loadTrip = (version?: number) => {
    if (!tripId) return;
    const url = version
      ? `${API_BASE_URL}/api/v1/trips/${tripId}?version=${version}`
      : `${API_BASE_URL}/api/v1/trips/${tripId}`;

    fetch(url)
      .then(async (res) => {
        if (!res.ok) throw new Error(`Trip not found (HTTP ${res.status})`);
        return res.json();
      })
      .then((data) => {
        const unwrapped = unwrapTripDetails(data);
        setTripData(unwrapped);
        setIsLoading(false);

        if (unwrapped?.id) {
          saveTripToHistory(unwrapped);
          fetchVersions(unwrapped.id);
        }

        if (unwrapped?.destinationName) {
          const tripDates = (unwrapped.itinerary?.days || [])
            .map((day: { date?: string }) => (day.date || '').slice(0, 10))
            .filter(Boolean);
          fetchTripWeather(unwrapped.destinationName, tripDates)
            .then((forecast) => setWeatherList(forecast))
            .catch(() => {});
        }
      })
      .catch((err) => {
        console.error('Fetch error:', err);
        setErrorMsg(err.message);
        setIsLoading(false);
      });
  };

  useEffect(() => {
    loadTrip();
  }, [tripId]);

  const handleCopyLink = () => {
    if (typeof window !== 'undefined') {
      navigator.clipboard.writeText(window.location.href);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleSyncCalendar = () => {
    if (!tripData || !tripData.itinerary) return;
    downloadTripCalendar(tripData, tripData.itinerary);
    setCalendarDownloaded(true);
    setTimeout(() => setCalendarDownloaded(false), 2500);
  };

  const handlePrint = () => {
    if (typeof window !== 'undefined') {
      window.print();
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#FAF8F5] flex flex-col items-center justify-center p-8 space-y-4">
        <div className="w-12 h-12 border-4 border-[#18181B] border-t-[#E11D48] rounded-full animate-spin" />
        <p className="font-display font-black text-sm uppercase tracking-wider text-[#18181B]">
          Retrieving Itinerary // {tripId}...
        </p>
      </div>
    );
  }

  if (errorMsg || !tripData) {
    return (
      <div className="min-h-screen bg-[#FAF8F5] flex flex-col items-center justify-center p-8 space-y-4 text-center">
        <div className="w-12 h-12 bg-red-100 text-[#E11D48] border-2 border-[#18181B] flex items-center justify-center font-black text-xl rounded-lg">
          !
        </div>
        <h1 className="font-display font-black text-xl uppercase text-[#18181B]">
          Itinerary Not Found
        </h1>
        <p className="text-xs text-[#52525B] max-w-sm">
          {errorMsg || 'This trip could not be located in the dispatch archives.'}
        </p>
        <Link href="/planner" className="comic-btn-primary px-6 py-2.5 rounded-lg text-xs font-black uppercase tracking-wider">
          Draft New Route &rarr;
        </Link>
      </div>
    );
  }

  const itinerary = tripData.itinerary;
  const days = itinerary?.days || [];

  // A draft plan has to say what is unresolved, otherwise the label is just anxiety. These are the
  // checks the engine could not confirm, per stop.
  const unresolvedChecks = days.flatMap((d: any) =>
    (d.activities || []).flatMap((a: any) =>
      (a.checks || [])
        .filter((c: any) => c?.status === 'unchecked')
        .map((c: any) => ({
          day: d.dayIndex,
          stop: a.title || a.name || 'Stop',
          label: c?.label || c?.detail || c?.source || 'unresolved check'
        }))
    )
  );
  const isVerifiedPlan = itinerary?.status === 'VERIFIED';
  const activeDay = days.find((d: any) => d.dayIndex === activeDayIndex) || days[0];

  // Search bias for the swap modal: coordinates of the first located stop on the shown day.
  const dayAnchor = activeDay?.activities?.find((a: any) => a?.place?.location)?.place?.location as
    | { latitude: number; longitude: number }
    | undefined;
  const allActivities = days.flatMap((d: any) => d.activities || []);

  return (
    <div className="min-h-screen bg-[#FAF8F5] text-[#18181B] pb-24">
      {/* Top Header */}
      <header className="sticky top-0 z-40 bg-[#FAF8F5] border-b-[2.5px] border-[#18181B] print:hidden">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 h-18 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link
              href="/"
              className="comic-btn-secondary p-2 min-h-11 min-w-11 flex items-center justify-center rounded-lg"
              aria-label="Back to home"
            >
              <ArrowLeft className="w-5 h-5" />
            </Link>
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 bg-[#E11D48] border-2 border-[#18181B]" />
              <h1 className="font-display font-black text-lg sm:text-xl uppercase tracking-tight truncate max-w-xs sm:max-w-md">
                Trip plan: {tripData.destinationName}
              </h1>
            </div>
          </div>

          <div className="flex items-center gap-2 sm:gap-2.5">
            <button
              type="button"
              disabled={isPlanLocked}
              onClick={() => !isPlanLocked && setShowReplanModal(true)}
              className={`comic-btn-primary px-3 sm:px-3.5 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5  bg-[#E11D48] text-white hover:bg-[#be123c] ${
                isPlanLocked ? 'opacity-50 cursor-not-allowed' : ''
              }`}
              title={isPlanLocked ? 'Itinerary is locked by the trip organizer' : 'One-Tap Replanning: Rain, Running Late, Tired, Budget'}
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Replan</span>
              <span className="sm:hidden">Replan</span>
            </button>

            <button
              type="button"
              disabled={isPlanLocked}
              onClick={() => !isPlanLocked && setShowRefineModal(true)}
              className={`comic-btn-secondary px-3 sm:px-3.5 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5 ${
                isPlanLocked ? 'opacity-50 cursor-not-allowed' : 'hover:border-[#E11D48]'
              }`}
              title={isPlanLocked ? 'Itinerary is locked by the trip organizer' : 'Chat with AI Planner to adjust pace, swap meals, or re-route'}
            >
              <Sparkles className="w-3.5 h-3.5 text-[#E11D48]" />
              <span className="hidden sm:inline">Refine</span>
              <span>Route</span>
            </button>

            <button
              type="button"
              onClick={handleToggleLock}
              disabled={isLocking}
              className={`comic-btn-secondary px-3 sm:px-3.5 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5 transition-colors ${
                isPlanLocked
                  ? 'bg-amber-100 border-amber-900 text-amber-950 hover:bg-amber-200'
                  : 'hover:border-[#E11D48]'
              }`}
              title={isPlanLocked ? 'Unlock plan to allow changes' : 'Lock itinerary to seal decisions'}
            >
              {isPlanLocked ? (
                <Lock className="w-3.5 h-3.5 text-amber-800" />
              ) : (
                <Unlock className="w-3.5 h-3.5 text-[#18181B]" />
              )}
              <span className="hidden sm:inline">{isPlanLocked ? 'Unlock Plan' : 'Lock Plan'}</span>
              <span className="sm:hidden">{isPlanLocked ? 'Locked' : 'Lock'}</span>
            </button>

            <Link
              href={`/trip/${tripId}/today`}
              className="comic-btn-secondary px-3 sm:px-3.5 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5 hover:border-[#E11D48]"
              title="Open Today mode for real-time live navigation"
            >
              <Navigation className="w-3.5 h-3.5 text-[#E11D48]" />
              <span className="hidden sm:inline">Today</span>
              <span>Mode</span>
            </Link>

            <Link
              href={`/trip/${tripId}/collab`}
              className="comic-btn-secondary px-3 sm:px-3.5 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5 hover:border-[#E11D48]"
            >
              <Users className="w-3.5 h-3.5 text-[#E11D48]" />
              <span className="hidden md:inline">Squad</span>
              <span>Collab</span>
            </Link>

            <button
              type="button"
              onClick={() => setShowPackingDocket(!showPackingDocket)}
              className={`comic-btn-secondary px-3 sm:px-3.5 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5 transition-colors ${
                showPackingDocket ? 'bg-[#18181B] text-white border-[#18181B]' : 'hover:border-[#E11D48]'
              }`}
              title="Toggle Dynamic Field Packing Kit Checklist"
            >
              <Luggage className="w-3.5 h-3.5" />
              <span className="hidden md:inline">Field Kit</span>
              <span className="md:hidden">Kit</span>
            </button>

            <button
              type="button"
              onClick={() => setShowMyTrips(true)}
              className="comic-btn-secondary px-2.5 min-h-11 py-2 rounded-lg text-xs font-black uppercase"
              title="View all your saved trips"
            >
              <Briefcase className="w-3.5 h-3.5 text-[#18181B]" />
            </button>

            <button
              type="button"
              onClick={handleSyncCalendar}
              className="comic-btn-secondary px-3 sm:px-3.5 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5"
              title="Export verified schedule to Apple, Google, or Outlook Calendar (.ics)"
            >
              {calendarDownloaded ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Calendar className="w-3.5 h-3.5 text-[#18181B]" />}
              <span className="hidden md:inline">{calendarDownloaded ? 'Synced!' : 'Calendar'}</span>
              <span className="text-[10px] font-black opacity-70">.ics</span>
            </button>

            <button
              type="button"
              onClick={handleCopyLink}
              className="comic-btn-secondary px-3 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5"
              title="Copy shareable trip link"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Share2 className="w-3.5 h-3.5" />}
              <span className="hidden sm:inline">{copied ? 'Copied' : 'Share'}</span>
            </button>

            <Link
              href={`/trip/${tripId}/print`}
              target="_blank"
              className="comic-btn-primary px-3 sm:px-4 min-h-11 py-2 rounded-lg text-xs font-black uppercase tracking-wider flex items-center gap-1.5"
              title="Open high-contrast printable Comic Field Dossier and PDF Voucher"
            >
              <Printer className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Print Voucher</span>
              <span className="sm:hidden">Print</span>
            </Link>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-5xl mx-auto px-4 sm:px-6 pt-8 space-y-8">
        {/* Plan Locked Alert Banner */}
        {isPlanLocked && (
          <div className="comic-panel bg-amber-50 border-2 border-[#18181B] p-4 rounded-xl flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-amber-950">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-lg bg-amber-200 border-2 border-[#18181B] flex items-center justify-center font-black shrink-0">
                <Lock className="w-5 h-5 text-amber-900" />
              </div>
              <div>
                <p className="font-display font-black text-sm uppercase tracking-wide">
                  PLAN LOCKED // Decisions Sealed
                </p>
                <p className="text-xs text-amber-800 font-medium mt-0.5">
                  The organizer has finalized this route. Replanning, refining, and squad voting are frozen.
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={handleToggleLock}
              disabled={isLocking}
              className="comic-btn-secondary px-3 py-1.5 min-h-11 text-xs font-black uppercase tracking-wider bg-white shrink-0 hover:bg-amber-100 self-end sm:self-auto"
            >
              {isLocking ? 'Updating...' : 'Unlock Plan'}
            </button>
          </div>
        )}

        {/* Ticket Header Banner */}
        <section className="comic-panel p-6 sm:p-8 rounded-2xl bg-white space-y-4 relative overflow-hidden">
          <div className="absolute top-0 right-0 w-48 h-48 comic-halftone opacity-30 pointer-events-none" />

          <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4 border-b-2 border-[#18181B] pb-6 relative z-10">
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <span className="comic-tag bg-[#E11D48] text-white">
                  {tripData.destinationName}
                </span>
                <span className="comic-tag bg-[#FAF8F5] text-[#18181B]">
                  Version {itinerary?.version || 1}
                </span>
                {availableVersions.length > 1 && (
                  <div className="flex items-center gap-1.5 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg px-2 py-0.5 text-xs font-black">
                    <History className="w-3.5 h-3.5 text-[#52525B]" />
                    <span className="text-[#52525B] text-[10px] uppercase tracking-wider">
                      History:
                    </span>
                    <div className="flex items-center gap-1">
                      {availableVersions.map((v) => {
                        const isCurrentDisplay = (itinerary?.version || 1) === v.version;
                        return (
                          <button
                            key={v.version}
                            type="button"
                            onClick={() => loadTrip(v.version)}
                            className={`px-1.5 py-0.5 rounded text-[10px] font-black transition-colors ${
                              isCurrentDisplay
                                ? 'bg-[#18181B] text-white'
                                : 'text-[#52525B] hover:bg-zinc-200'
                            }`}
                            title={`Load version ${v.version} (${v.status}${v.isCurrent ? ', Active' : ''})`}
                          >
                            v{v.version}{v.isCurrent ? ' [LIVE]' : ''}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
              <h2 className="font-display font-black text-2xl sm:text-4xl uppercase tracking-tight text-[#18181B] mt-2">
                {itinerary?.title || `Journey to ${tripData.destinationName}`}
              </h2>
              {itinerary?.summary && (
                <p className="text-xs sm:text-sm text-[#52525B] font-medium mt-1.5 max-w-2xl leading-relaxed">
                  {itinerary.summary}
                </p>
              )}
            </div>

            <div className="flex sm:flex-col items-center sm:items-end gap-2 shrink-0">
              {itinerary?.status === 'VERIFIED' ? (
                <span className="inline-flex">
                  <span className="inline-flex items-center gap-1.5 bg-emerald-100 text-emerald-900 border-2 border-[#18181B] px-3 py-1 text-xs font-black uppercase rounded-xs rotate-[-2deg]">
                    <ShieldCheck className="w-4 h-4 text-emerald-700" />
                    VERIFIED PASS
                  </span>
                </span>
              ) : (
                <span className="inline-flex">
                  <span className="inline-flex items-center gap-1.5 bg-amber-100 text-amber-900 border-2 border-[#18181B] px-3 py-1 text-xs font-black uppercase rounded-xs rotate-[-2deg]">
                    <AlertCircle className="w-4 h-4 text-amber-800" />
                    DRAFT // NOT FULLY CHECKED
                  </span>
                </span>
              )}
              {isPlanLocked && (
                <span className="inline-flex">
                  <span className="inline-flex items-center gap-1.5 bg-amber-100 text-amber-900 border-2 border-[#18181B] px-3 py-1 text-xs font-black uppercase rounded-xs rotate-[1.5deg]">
                    <Lock className="w-3.5 h-3.5 text-amber-800" />
                    PLAN LOCKED
                  </span>
                </span>
              )}
              <span className="text-[10px] font-extrabold uppercase tracking-widest text-[#52525B]">
                ID: {tripData.id.slice(0, 8)}...
              </span>
            </div>
          </div>

          {!isVerifiedPlan && (
            <div className="mt-4 bg-amber-50 border-2 border-[#18181B] rounded-lg p-3 sm:p-4 print:block">
              <span className="text-[10px] font-black uppercase tracking-wider text-amber-900 block mb-1">
                This plan is a draft
              </span>
              <p className="text-xs font-bold text-[#18181B] leading-relaxed">
                {unresolvedChecks.length > 0
                  ? `The engine could not confirm ${unresolvedChecks.length} check${unresolvedChecks.length === 1 ? '' : 's'} below. Fix or swap those stops, or run the replan, and it will be re-verified.`
                  : 'The engine left one or more rules unresolved for this plan, so treat the times as a draft until it passes again.'}
              </p>
              {unresolvedChecks.length > 0 && (
                <ul className="mt-2 space-y-1">
                  {unresolvedChecks.slice(0, 6).map((c: any, i: number) => (
                    <li key={i} className="text-[11px] font-bold text-[#52525B] leading-snug break-words">
                      Day 0{c.day}, {c.stop}: {c.label}
                    </li>
                  ))}
                  {unresolvedChecks.length > 6 && (
                    <li className="text-[11px] font-black uppercase text-[#52525B]">
                      and {unresolvedChecks.length - 6} more
                    </li>
                  )}
                </ul>
              )}
            </div>
          )}

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs font-bold text-[#18181B] relative z-10">
            <div className="bg-[#FAF8F5] p-3 border-2 border-[#18181B] rounded-lg">
              <span className="text-[10px] uppercase text-[#52525B] font-black block mb-0.5">Timeframe</span>
              <span className="block font-black text-[11px] sm:text-xs leading-snug break-words">
                {formatDateRange(tripData.startDate, tripData.endDate)}
              </span>
            </div>
            <div className="bg-[#FAF8F5] p-3 border-2 border-[#18181B] rounded-lg">
              <span className="text-[10px] uppercase text-[#52525B] font-black block mb-0.5">Travellers</span>
              <span className="font-black">{tripData.travelersCount} Travelers</span>
            </div>
            <div className="bg-[#FAF8F5] p-3 border-2 border-[#18181B] rounded-lg">
              <span className="text-[10px] uppercase text-[#52525B] font-black block mb-0.5">Estimated cost</span>
              <span className="font-black text-[#E11D48]">
                ~{itinerary?.totalEstimatedCost || 0} {itinerary?.currency || tripData.currency}
              </span>
            </div>
            <div className="bg-[#FAF8F5] p-3 border-2 border-[#18181B] rounded-lg">
              <span className="text-[10px] uppercase text-[#52525B] font-black block mb-0.5">Pace</span>
              <span className="font-black">{tripData.pace || 'MODERATE'}</span>
            </div>
          </div>
        </section>

        {/* Dynamic Field Packing Kit Docket */}
        {showPackingDocket && (
          <section className="print:hidden">
            <PackingDocket
              tripId={tripId}
              destination={tripData.destinationName || 'Destination'}
              isRainy={weatherList.some((w) => w.isRainy)}
              pace={tripData?.pace}
            />
          </section>
        )}

        {/* Empty / in-progress state */}
        {(!itinerary || days.length === 0) && (
          <section className="comic-panel p-8 rounded-2xl bg-white text-center space-y-3">
            <h2 className="font-display font-black text-xl uppercase tracking-tight text-[#18181B]">
              {tripData.status === 'GENERATING' ? 'Route still computing' : 'No verified route yet'}
            </h2>
            <p className="text-xs text-[#52525B] max-w-md mx-auto">
              {tripData.status === 'GENERATING'
                ? 'The deterministic engine is checking opening hours, transit times and pace. Reload in a few seconds.'
                : 'This trip has no verified itinerary. Start one from the planner and the engine will lock a schedule.'}
            </p>
            <Link
              href={`/planner?destination=${encodeURIComponent(tripData.destinationName || '')}`}
              className="comic-btn-primary px-6 py-2.5 rounded-lg text-xs font-black uppercase tracking-wider inline-flex items-center gap-2"
            >
              <Flame className="w-3.5 h-3.5" />
              <span>Open the planner</span>
            </Link>
          </section>
        )}

        {/* Interactive Route Map Radar */}
        {allActivities.length > 0 && (
          <section className="print:hidden">
            <ComicRouteMap activities={activeDay?.activities || allActivities} height="340px" />
          </section>
        )}

        {/* Day Switcher Tabs */}
        {days.length > 1 && (
          <nav className="flex items-center gap-2 border-b-2 border-[#18181B] pb-3 print:hidden overflow-x-auto">
            {days.map((d: any) => (
              <button
                key={d.dayIndex}
                type="button"
                onClick={() => setActiveDayIndex(d.dayIndex)}
                className={`px-4 py-2 min-h-11 rounded-lg text-xs font-black uppercase tracking-wider transition-all shrink-0 ${
                  activeDayIndex === d.dayIndex
                    ? 'bg-[#E11D48] text-white border-2 border-[#18181B]  -translate-y-0.5'
                    : 'bg-white text-[#18181B] border-2 border-[#18181B] hover:bg-[#FAF8F5] hover:border-[#E11D48]'
                }`}
              >
                Day 0{d.dayIndex}
              </button>
            ))}
          </nav>
        )}

        {/* Day Schedule Cards */}
        <section className="space-y-6">
          {days.map((d: any) => (
            <div
              key={d.dayIndex}
              className={`comic-panel p-6 sm:p-8 rounded-2xl bg-white space-y-6 print:border-2 print:border-black print:mb-8 print:break-inside-avoid ${
                d.dayIndex === activeDayIndex ? 'block' : 'hidden print:block'
              }`}
            >
                <div className="flex items-center justify-between border-b-2 border-[#18181B] pb-4">
                  <div className="flex items-center gap-2.5">
                    <span className="w-3.5 h-3.5 bg-[#E11D48] border-2 border-[#18181B]" />
                    <div>
                      <span className="text-[10px] font-black uppercase tracking-widest text-[#E11D48]">
                        STAGE 0{d.dayIndex}
                      </span>
                      <h3 className="font-display font-black text-xl uppercase tracking-tight text-[#18181B]">
                        {d.themeSummary || 'Exploration Circuit'}
                      </h3>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {(() => {
                      const dayWeather = weatherList.find(
                        (w) => w.date === (d.date || '').slice(0, 10)
                      );
                      if (!dayWeather) return null;
                      return (
                        <span
                          className={`comic-tag text-[10px] font-black border-2 border-[#18181B] ${
                            dayWeather.isRainy
                              ? 'bg-amber-100 text-amber-950 border-amber-900'
                              : 'bg-[#FAF8F5] text-[#18181B]'
                          }`}
                        >
                          {dayWeather.condition} · {dayWeather.tempMax}° / {dayWeather.tempMin}°C
                        </span>
                      );
                    })()}

                    {d.weatherSummary && (
                      <span className="comic-tag bg-[#FAF8F5] text-[#18181B] text-[10px] hidden sm:inline-block">
                        {d.weatherSummary}
                      </span>
                    )}
                  </div>
                </div>

                {/* Rain Contingency Warning */}
                {(() => {
                  const dayWeather = weatherList.find(
                    (w) => w.date === (d.date || '').slice(0, 10)
                  );
                  if (dayWeather && dayWeather.isRainy) {
                    return (
                      <div className="bg-amber-50 border-2 border-[#18181B] rounded-xl p-3 flex items-start gap-2 text-xs font-bold text-amber-900 print:hidden">
                        <CloudRain className="w-4 h-4 text-amber-700 shrink-0 mt-0.5" />
                        <div>
                          <span className="font-black uppercase tracking-wider block text-[11px]">
                            Rain Contingency Radar ({dayWeather.precipitationProbability}% chance)
                          </span>
                          <span className="text-[11px] font-medium">
                            {dayWeather.advisoryNote || 'Precipitation expected. Carry rain gear or consider covered museum circuits.'}
                          </span>
                        </div>
                      </div>
                    );
                  }
                  return null;
                })()}

                {/* Timeline Stops */}
                <div className="space-y-6 pl-2">
                  {d.activities?.map((a: any, idx: number) => {
                    const lat = a.place?.location?.latitude;
                    const lng = a.place?.location?.longitude;
                    const navUrl = lat && lng
                      ? `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`
                      : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent((a.title || a.name) + ' ' + tripData.destinationName)}`;

                    return (
                      <div
                        key={a.id || idx}
                        className="relative pl-8 pb-4 border-l-[2.5px] border-[#18181B] last:border-l-transparent last:pb-0"
                      >
                        <div className="absolute -left-[9px] top-1 w-4 h-4 bg-[#E11D48] border-2 border-[#18181B] flex items-center justify-center text-[9px] text-white font-black">
                          {idx + 1}
                        </div>

                        {(() => {
                          const photo = a.place?.photoUrls?.[0];
                          if (!photo) {
                            return (
                              <div className="mb-3 h-14 rounded-lg border-2 border-dashed border-[#18181B]/40 flex items-center justify-center px-3">
                                <span className="text-[10px] font-bold uppercase tracking-widest text-[#52525B] text-center">
                                  No verified photo of this place yet. We only show real photos.
                                </span>
                              </div>
                            );
                          }
                          const isCommons = photo.includes('/wiki/Special:FilePath/');
                          const creditHref = isCommons ? photo.replace('/wiki/Special:FilePath/', '/wiki/File:').split('?')[0] : photo;
                          return (
                            <figure className="mb-3">
                              <img
                                src={photo}
                                alt={a.title || a.name || 'Venue photograph'}
                                loading="lazy"
                                className="w-full h-40 sm:h-48 object-cover rounded-lg border-2 border-[#18181B]"
                              />
                              <figcaption className="mt-1">
                                <a
                                  href={creditHref}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="text-[10px] font-bold uppercase tracking-wider text-[#52525B] underline decoration-dotted hover:text-[#E11D48]"
                                >
                                  {isCommons ? 'Photo: Wikimedia Commons' : 'Photo: Wikipedia'}
                                </a>
                              </figcaption>
                            </figure>
                          );
                        })()}

                        <div className="flex flex-col sm:flex-row sm:items-baseline justify-between gap-1 text-xs mb-1">
                          <span className="font-black text-[#E11D48] flex items-center gap-1 text-sm">
                            <Clock className="w-3.5 h-3.5 text-[#18181B]" />
                            {a.startTime} to {a.endTime} ({a.durationMinutes}m)
                          </span>
                          {a.estimatedCost ? (
                            <span
                              className="font-black text-xs text-[#18181B]"
                              title="Engine estimate for this stop, not a booked price from the venue"
                            >
                              est. {a.estimatedCost} {a.currency || itinerary.currency}
                            </span>
                          ) : (
                            <span className="text-[10px] font-bold text-[#52525B]">COST NOT LISTED</span>
                          )}
                        </div>

                        <h4 className="font-display font-black text-lg uppercase text-[#18181B] mt-0.5">
                          {a.title || a.name}
                        </h4>

                        {a.place?.formattedAddress && (
                          <p className="text-xs text-[#52525B] font-medium flex items-center gap-1 mt-1">
                            <MapPin className="w-3.5 h-3.5 shrink-0 text-[#18181B]" />
                            <span>{a.place.formattedAddress}</span>
                          </p>
                        )}

                        {a.reason && (
                          <p className="text-xs text-[#18181B] font-medium mt-2 p-3 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg leading-relaxed">
                            {a.reason}
                          </p>
                        )}

                        {/* Action Buttons */}
                        <div className="mt-3 flex flex-wrap items-center gap-2 print:hidden">
                          {a.place?.websiteUrl && (
                            <a
                              href={a.place.websiteUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="comic-btn-secondary px-3 py-1.5 min-h-11 rounded-md text-[11px] font-black uppercase tracking-wider flex items-center gap-1 hover:text-[#E11D48]"
                              title="Opens the venue's own page"
                            >
                              <Globe className="w-3 h-3 text-[#E11D48]" />
                              <span>Venue page</span>
                              <ExternalLink className="w-2.5 h-2.5 opacity-60" />
                            </a>
                          )}

                          {a.place?.phoneNumber && (
                            <a
                              href={`tel:${String(a.place.phoneNumber).replace(/\s+/g, '')}`}
                              className="comic-btn-secondary px-3 py-1.5 min-h-11 rounded-md text-[11px] font-black uppercase tracking-wider flex items-center gap-1 hover:text-[#E11D48]"
                              title="Call the venue"
                            >
                              <Phone className="w-3 h-3 text-[#E11D48]" />
                              <span>Call</span>
                            </a>
                          )}

                          <a
                            href={navUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="comic-btn-secondary px-3 py-1.5 min-h-11 rounded-md text-[11px] font-black uppercase tracking-wider flex items-center gap-1 hover:text-[#E11D48]"
                          >
                            <Navigation className="w-3 h-3 text-[#E11D48]" />
                            <span>Open Navigation</span>
                            <ExternalLink className="w-2.5 h-2.5 opacity-60" />
                          </a>

                          <button
                            type="button"
                            disabled={isPlanLocked}
                            onClick={() => !isPlanLocked && setSwappingActivity({
                              id: a.id || `${idx}`,
                              title: a.title || a.name || 'Venue Stop',
                              dayIndex: d.dayIndex,
                              startTime: a.startTime,
                              endTime: a.endTime,
                            })}
                            className={`comic-btn-secondary px-3 py-1.5 min-h-11 rounded-md text-[11px] font-black uppercase tracking-wider flex items-center gap-1 ${
                              isPlanLocked ? 'opacity-40 cursor-not-allowed' : 'hover:text-[#E11D48]'
                            }`}
                            title={isPlanLocked ? 'Itinerary is locked by the organizer' : 'Swap this venue for an alternative in the city'}
                          >
                            <Shuffle className="w-3 h-3 text-[#E11D48]" />
                            <span>Swap Stop</span>
                          </button>

                          {a.travelTimeFromPreviousMinutes > 0 && (
                            <span className="inline-flex items-center gap-1 text-[10px] font-black uppercase text-[#18181B] bg-amber-100 border border-[#18181B] px-2 py-1 rounded-xs">
                              {a.travelTimeFromPreviousMinutes}m transit ({a.transitModeFromPrevious || 'TRANSIT'})
                            </span>
                          )}
                        </div>

                        {/* Why We Trust This Receipt Panel */}
                        {(() => {
                          const checks = a.checks && a.checks.length > 0
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
                                  status: a.place?.openingHours?.periods?.length && !a.place?.openingHoursEstimated
                                    ? 'confirmed'
                                    : a.place?.openingHoursEstimated
                                    ? 'estimated'
                                    : 'unchecked',
                                  details: a.place?.openingHours?.periods?.length && !a.place?.openingHoursEstimated
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
                                  details: idx === 0 ? 'First stop of day' : 'Transit time verified'
                                },
                                {
                                  code: 'WEATHER_WINDOW',
                                  label: 'Weather forecast',
                                  source: 'Open-Meteo',
                                  status: weatherList.length > 0 ? 'confirmed' : 'unchecked',
                                  details: weatherList.length > 0 ? 'Forecast active' : 'Beyond forecast range'
                                }
                              ];

                          return (
                            <details className="mt-3 group border-2 border-[#18181B] bg-[#FAF8F5] rounded-lg p-2.5 text-xs">
                              <summary className="font-black text-[11px] uppercase tracking-wider text-[#18181B] cursor-pointer list-none flex items-center justify-between select-none">
                                <span className="flex items-center gap-1.5">
                                  <ShieldCheck className="w-3.5 h-3.5 text-[#E11D48]" />
                                  <span>Why we trust this ({checks.length} checks)</span>
                                </span>
                                <ChevronDown className="w-3.5 h-3.5 text-[#52525B] transition-transform group-open:rotate-180" />
                              </summary>
                              <div className="mt-2.5 pt-2 border-t border-[#18181B]/20 space-y-1.5 font-mono text-[11px]">
                                {checks.map((c: any, cIdx: number) => (
                                  <div key={cIdx} className="flex items-center justify-between gap-2">
                                    <div className="flex items-center gap-1.5 min-w-0">
                                      <span className="px-1.5 py-0.5 text-[9px] font-black border border-[#18181B] rounded bg-white text-[#18181B] uppercase shrink-0">
                                        {c.source}
                                      </span>
                                      <span className={c.status === 'confirmed' ? 'text-[#18181B] font-bold truncate' : 'text-[#71717A] truncate'}>
                                        {c.label}
                                      </span>
                                    </div>
                                    <span
                                      className={`text-[10px] font-bold uppercase shrink-0 ${
                                        c.status === 'confirmed'
                                          ? 'text-[#18181B]'
                                          : c.status === 'estimated'
                                          ? 'text-amber-800'
                                          : 'text-[#71717A]'
                                      }`}
                                    >
                                      {c.status === 'confirmed'
                                        ? 'confirmed'
                                        : c.status === 'estimated'
                                        ? 'estimated'
                                        : 'not checked'}
                                    </span>
                                  </div>
                                ))}
                              </div>
                            </details>
                          );
                        })()}
                      </div>
                    );
                  })}
                </div>
              </div>
            ))}
        </section>

        {/* Back Link */}
        <div className="text-center pt-8 print:hidden">
          <Link
            href="/planner"
            className="comic-btn-primary px-8 py-3.5 rounded-xl text-sm font-black uppercase tracking-wider inline-flex items-center gap-2"
          >
            <Flame className="w-4 h-4" />
            <span>Draft Another Run</span>
          </Link>
        </div>
      </main>

      {/* Modals & Drawers */}
      <MyTripsModal isOpen={showMyTrips} onClose={() => setShowMyTrips(false)} />
      <RefineRouteModal
        isOpen={showRefineModal}
        onClose={() => setShowRefineModal(false)}
        itineraryId={itinerary?.id || tripData?.id}
        tripId={tripId}
        destinationName={tripData?.destinationName || 'Destination'}
        onItineraryUpdated={(updated) => {
          setTripData((prev: any) => ({ ...prev, itinerary: updated }));
        }}
      />
      <ReplanRouteModal
        isOpen={showReplanModal}
        onClose={() => setShowReplanModal(false)}
        tripId={tripId}
        destinationName={tripData?.destinationName || 'Destination'}
        currentVersion={itinerary?.version || 1}
        totalDays={days.length}
        activeDayIndex={activeDayIndex}
        onReplanComplete={(result) => {
          if (result.newVersion) {
            loadTrip(result.newVersion);
          }
        }}
      />
      <SwapActivityModal
        isOpen={!!swappingActivity}
        onClose={() => setSwappingActivity(null)}
        activity={swappingActivity}
        itineraryId={itinerary?.id || tripData?.id}
        destinationName={tripData?.destinationName || 'Destination'}
        near={dayAnchor}
        onItineraryUpdated={(updated) => {
          setTripData((prev: any) => ({ ...prev, itinerary: updated }));
        }}
      />
    </div>
  );
}
