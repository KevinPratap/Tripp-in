'use client';

import React, { useEffect, useState, useMemo } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import {
  ArrowLeft,
  Navigation,
  CheckCircle2,
  Clock,
  MapPin,
  Calendar,
  AlertTriangle,
  RotateCcw,
  WifiOff,
  ExternalLink,
  ChevronRight,
  ShieldCheck
} from 'lucide-react';
import { unwrapTripDetails } from '@/lib/trip-contract';
import { apiFetch } from '@/lib/api-client';

interface ActivityStop {
  id: string;
  placeId: string;
  title: string;
  type: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  travelTimeFromPreviousMinutes?: number;
  transitModeFromPrevious?: string;
  estimatedCost?: number;
  currency?: string;
  reason?: string;
  checks?: any[];
  place?: {
    id: string;
    name: string;
    formattedAddress: string;
    location?: { latitude: number; longitude: number };
  };
}

interface DayPlan {
  id: string;
  date: string;
  dayIndex: number;
  themeSummary?: string;
  weatherSummary?: string;
  activities: ActivityStop[];
}

function parseTimeToMinutes(timeStr: string): number {
  if (!timeStr) return 0;
  const [h, m] = timeStr.split(':').map(Number);
  return (h || 0) * 60 + (m || 0);
}

function formatMinutesToTime(mins: number): string {
  const normalized = Math.max(0, Math.min(1439, mins));
  const h = Math.floor(normalized / 60);
  const m = normalized % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

export default function TodayModePage() {
  const params = useParams();
  const tripId = Array.isArray(params?.id) ? params.id[0] : (params?.id as string);

  const [tripData, setTripData] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isOfflineCopy, setIsOfflineCopy] = useState(false);
  const [activeDayIndex, setActiveDayIndex] = useState(1);
  const [activeStopIndex, setActiveStopIndex] = useState(0);
  const [currentTimeMinutes, setCurrentTimeMinutes] = useState(() => {
    const now = new Date();
    return now.getHours() * 60 + now.getMinutes();
  });
  const [isAppleDevice, setIsAppleDevice] = useState(false);

  // Detect iOS for Apple Maps deep links
  useEffect(() => {
    if (typeof navigator !== 'undefined') {
      const isIOS = /iPad|iPhone|iPod|Macintosh/.test(navigator.userAgent);
      setIsAppleDevice(isIOS);
    }
  }, []);

  // Update clock every minute
  useEffect(() => {
    const interval = setInterval(() => {
      const now = new Date();
      setCurrentTimeMinutes(now.getHours() * 60 + now.getMinutes());
    }, 60000);
    return () => clearInterval(interval);
  }, []);

  // Fetch or load from offline cache
  useEffect(() => {
    if (!tripId) return;

    const cacheKey = `trippin_offline_trip_${tripId}`;

    const loadData = async () => {
      try {
        const res = await apiFetch(`/api/v1/trips/${tripId}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        const unwrapped = unwrapTripDetails(data);
        if (unwrapped) {
          setTripData(unwrapped);
          setIsOfflineCopy(false);
          // Update offline cache
          try {
            localStorage.setItem(cacheKey, JSON.stringify(unwrapped));
          } catch {
            // storage quota fallback
          }
        }
      } catch {
        // Fallback to offline cache
        try {
          const cached = localStorage.getItem(cacheKey);
          if (cached) {
            setTripData(JSON.parse(cached));
            setIsOfflineCopy(true);
          }
        } catch {
          // offline parse error
        }
      } finally {
        setIsLoading(false);
      }
    };

    loadData();
  }, [tripId]);

  const days: DayPlan[] = useMemo(() => {
    return tripData?.itinerary?.days || [];
  }, [tripData]);

  // Determine initial day based on today's calendar date
  useEffect(() => {
    if (days.length === 0) return;
    const todayStr = new Date().toISOString().split('T')[0];
    const match = days.find((d) => d.date === todayStr);
    if (match) {
      setActiveDayIndex(match.dayIndex);
    } else {
      setActiveDayIndex(1);
    }
  }, [days]);

  // Load persisted progress pointer from localStorage for this day
  useEffect(() => {
    if (!tripId) return;
    const pointerKey = `trippin_pointer_${tripId}_day_${activeDayIndex}`;
    try {
      const saved = localStorage.getItem(pointerKey);
      if (saved !== null) {
        const parsed = parseInt(saved, 10);
        if (!Number.isNaN(parsed) && parsed >= 0) {
          setActiveStopIndex(parsed);
          return;
        }
      }
    } catch {
      // storage disabled
    }

    // Default auto-calculation based on system time if no pointer saved
    const activeDay = days.find((d) => d.dayIndex === activeDayIndex);
    if (activeDay && activeDay.activities.length > 0) {
      let candidate = 0;
      for (let i = 0; i < activeDay.activities.length; i++) {
        const act = activeDay.activities[i];
        const end = parseTimeToMinutes(act.endTime);
        if (currentTimeMinutes >= end && i < activeDay.activities.length - 1) {
          candidate = i + 1;
        }
      }
      setActiveStopIndex(candidate);
    }
  }, [tripId, activeDayIndex, days, currentTimeMinutes]);

  const activeDay = useMemo(() => {
    return days.find((d) => d.dayIndex === activeDayIndex) || days[0];
  }, [days, activeDayIndex]);

  const activities = useMemo(() => {
    return activeDay?.activities || [];
  }, [activeDay]);

  const currentStop: ActivityStop | undefined = activities[activeStopIndex];
  const nextStop: ActivityStop | undefined = activities[activeStopIndex + 1];

  // Save pointer changes to localStorage
  const handleSetStopIndex = (newIndex: number) => {
    const clamped = Math.max(0, Math.min(activities.length - 1, newIndex));
    setActiveStopIndex(clamped);
    if (tripId) {
      const pointerKey = `trippin_pointer_${tripId}_day_${activeDayIndex}`;
      try {
        localStorage.setItem(pointerKey, clamped.toString());
      } catch {
        // storage disabled
      }
    }
  };

  const handleResetProgress = () => {
    handleSetStopIndex(0);
  };

  const handleAdvanceNext = () => {
    if (activeStopIndex < activities.length - 1) {
      handleSetStopIndex(activeStopIndex + 1);
    }
  };

  // Leave-by calculation for next stop
  const leaveByTime = useMemo(() => {
    if (!nextStop) return null;
    const nextStart = parseTimeToMinutes(nextStop.startTime);
    const transitMins = nextStop.travelTimeFromPreviousMinutes || 15;
    const leaveMins = nextStart - transitMins;
    return formatMinutesToTime(leaveMins);
  }, [nextStop]);

  // Compute navigation URL
  const getNavUrl = (stop: ActivityStop | undefined) => {
    if (!stop) return '#';
    const lat = stop.place?.location?.latitude;
    const lng = stop.place?.location?.longitude;
    if (lat && lng) {
      if (isAppleDevice) {
        return `https://maps.apple.com/?daddr=${lat},${lng}&dirflg=r`;
      }
      return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;
    }
    const query = encodeURIComponent(`${stop.title} ${tripData?.destinationName || ''}`);
    if (isAppleDevice) {
      return `https://maps.apple.com/?q=${query}`;
    }
    return `https://www.google.com/maps/search/?api=1&query=${query}`;
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#FAF8F5] flex flex-col items-center justify-center p-6 text-center space-y-3">
        <div className="w-10 h-10 border-4 border-[#18181B] border-t-[#E11D48] rounded-full animate-spin" />
        <p className="font-display font-black text-sm uppercase tracking-wider text-[#18181B]">
          Syncing Field Coordinates...
        </p>
      </div>
    );
  }

  if (!tripData || days.length === 0) {
    return (
      <div className="min-h-screen bg-[#FAF8F5] p-6 max-w-lg mx-auto flex flex-col items-center justify-center text-center space-y-4">
        <AlertTriangle className="w-12 h-12 text-[#E11D48]" />
        <h1 className="font-display font-black text-xl uppercase tracking-tight">
          No Route Schedule Loaded
        </h1>
        <p className="text-xs text-[#52525B]">
          Today Mode requires an active verified itinerary.
        </p>
        <Link
          href={`/trip/${tripId}`}
          className="comic-btn-primary px-6 py-2.5 rounded-lg text-xs font-black uppercase tracking-wider"
        >
          Return to Itinerary
        </Link>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#FAF8F5] text-[#18181B] pb-24">
      {/* Top Header */}
      <header className="sticky top-0 z-40 bg-[#FAF8F5] border-b-[2.5px] border-[#18181B]">
        <div className="max-w-2xl mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link
              href={`/trip/${tripId}`}
              className="comic-btn-secondary p-2 min-h-11 min-w-11 flex items-center justify-center rounded-lg"
              aria-label="Back to ticket"
            >
              <ArrowLeft className="w-5 h-5" />
            </Link>
            <div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 bg-[#E11D48] border border-[#18181B]" />
                <h1 className="font-display font-black text-base uppercase tracking-tight truncate max-w-[200px] sm:max-w-xs">
                  TODAY // {tripData.destinationName}
                </h1>
              </div>
              <span className="text-[10px] font-mono font-bold text-[#52525B] uppercase block">
                Local Time: {formatMinutesToTime(currentTimeMinutes)}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {isOfflineCopy && (
              <span className="px-2 py-1 text-[10px] font-black uppercase bg-amber-100 border border-[#18181B] rounded flex items-center gap-1 text-amber-950">
                <WifiOff className="w-3 h-3 text-amber-700" />
                <span>OFFLINE COPY</span>
              </span>
            )}
            <button
              type="button"
              onClick={handleResetProgress}
              className="comic-btn-secondary p-2 min-h-11 min-w-11 flex items-center justify-center rounded-lg"
              title="Reset progress to first stop"
              aria-label="Reset progress"
            >
              <RotateCcw className="w-4 h-4 text-[#18181B]" />
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-4 pt-4 space-y-5">
        {/* Day Override Bar */}
        <section className="bg-white border-2 border-[#18181B] rounded-xl p-3 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 min-w-0">
            <Calendar className="w-4 h-4 text-[#E11D48] shrink-0" />
            <div className="truncate">
              <span className="text-[10px] font-black uppercase tracking-wider text-[#52525B] block">
                Active Stage
              </span>
              <span className="font-display font-black text-xs uppercase text-[#18181B] truncate block">
                Day 0{activeDay.dayIndex} ({activeDay.date})
              </span>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0 overflow-x-auto">
            {days.map((d) => (
              <button
                key={d.dayIndex}
                type="button"
                onClick={() => {
                  setActiveDayIndex(d.dayIndex);
                  setActiveStopIndex(0);
                }}
                className={`px-3 py-1.5 min-h-11 rounded-md text-xs font-black uppercase transition-all ${
                  activeDayIndex === d.dayIndex
                    ? 'bg-[#E11D48] text-white border-2 border-[#18181B] '
                    : 'bg-[#FAF8F5] text-[#18181B] border border-[#18181B] hover:bg-white'
                }`}
              >
                D0{d.dayIndex}
              </button>
            ))}
          </div>
        </section>

        {/* Current Active Stop Hero Card */}
        {currentStop ? (
          <article className="comic-panel p-5 sm:p-6 rounded-2xl bg-white border-2 border-[#18181B] space-y-4">
            <div className="flex items-center justify-between border-b-2 border-[#18181B] pb-3">
              <div className="flex items-center gap-2">
                <span className="px-2 py-0.5 bg-[#E11D48] text-white text-[10px] font-black uppercase tracking-wider border border-[#18181B]">
                  CURRENT STOP
                </span>
                <span className="text-xs font-black text-[#52525B]">
                  {activeStopIndex + 1} of {activities.length}
                </span>
              </div>
              <span className="font-mono text-xs font-black text-[#E11D48]">
                {currentStop.startTime} to {currentStop.endTime}
              </span>
            </div>

            <div>
              <h2 className="font-display font-black text-2xl uppercase tracking-tight text-[#18181B]">
                {currentStop.title}
              </h2>
              {currentStop.place?.formattedAddress && (
                <p className="text-xs text-[#52525B] font-medium flex items-center gap-1.5 mt-1.5">
                  <MapPin className="w-3.5 h-3.5 shrink-0 text-[#18181B]" />
                  <span>{currentStop.place.formattedAddress}</span>
                </p>
              )}
            </div>

            {currentStop.reason && (
              <p className="text-xs text-[#18181B] p-3 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg font-medium leading-relaxed">
                {currentStop.reason}
              </p>
            )}

            {/* Action Bar */}
            <div className="pt-2 grid grid-cols-2 gap-3">
              <a
                href={getNavUrl(currentStop)}
                target="_blank"
                rel="noopener noreferrer"
                className="comic-btn-primary min-h-12 py-3 rounded-xl text-xs font-black uppercase tracking-wider flex items-center justify-center gap-2"
              >
                <Navigation className="w-4 h-4 text-white" />
                <span>Navigate</span>
                <ExternalLink className="w-3 h-3 opacity-80" />
              </a>

              <button
                type="button"
                onClick={handleAdvanceNext}
                disabled={activeStopIndex >= activities.length - 1}
                className="comic-btn-secondary min-h-12 py-3 rounded-xl text-xs font-black uppercase tracking-wider flex items-center justify-center gap-2 disabled:opacity-50"
              >
                <CheckCircle2 className="w-4 h-4 text-[#E11D48]" />
                <span>Done, next</span>
              </button>
            </div>
          </article>
        ) : (
          <div className="comic-panel p-8 rounded-2xl bg-white text-center space-y-2">
            <CheckCircle2 className="w-10 h-10 text-[#E11D48] mx-auto" />
            <h3 className="font-display font-black text-lg uppercase">
              Circuit Completed for Day 0{activeDay.dayIndex}
            </h3>
            <p className="text-xs text-[#52525B]">
              All stops on this stage have been marked done.
            </p>
          </div>
        )}

        {/* Up Next & Leave By Countdown */}
        {nextStop && (
          <section className="bg-amber-50 border-2 border-[#18181B] rounded-xl p-4 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-black uppercase tracking-widest text-[#E11D48] flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5" />
                <span>UP NEXT</span>
              </span>
              {leaveByTime && (
                <span className="px-2 py-0.5 bg-[#18181B] text-white text-[10px] font-black uppercase font-mono rounded">
                  Leave by {leaveByTime}
                </span>
              )}
            </div>

            <div className="flex items-center justify-between gap-3">
              <div>
                <h4 className="font-display font-black text-base uppercase text-[#18181B]">
                  {nextStop.title}
                </h4>
                <p className="text-[11px] font-mono text-[#52525B]">
                  Scheduled: {nextStop.startTime} to {nextStop.endTime} · ~{nextStop.travelTimeFromPreviousMinutes || 15}m transit
                </p>
              </div>

              <a
                href={getNavUrl(nextStop)}
                target="_blank"
                rel="noopener noreferrer"
                className="comic-btn-secondary p-2.5 min-h-11 min-w-11 rounded-lg shrink-0 flex items-center justify-center hover:text-[#E11D48]"
                title="Preview route to next stop"
              >
                <ChevronRight className="w-5 h-5 text-[#18181B]" />
              </a>
            </div>
          </section>
        )}

        {/* Full Day Itinerary Mini Checklist */}
        <section className="bg-white border-2 border-[#18181B] rounded-2xl p-4 space-y-3">
          <div className="border-b-2 border-[#18181B] pb-2 flex items-center justify-between">
            <span className="text-[10px] font-black uppercase tracking-wider text-[#18181B]">
              Day Schedule Checklist ({activities.length} stops)
            </span>
            <span className="text-[10px] font-mono text-[#52525B]">
              Tap to jump
            </span>
          </div>

          <div className="space-y-2">
            {activities.map((act, idx) => {
              const isPast = idx < activeStopIndex;
              const isCurrent = idx === activeStopIndex;

              return (
                <button
                  key={act.id || idx}
                  type="button"
                  onClick={() => handleSetStopIndex(idx)}
                  className={`w-full p-2.5 min-h-11 rounded-lg border-2 text-left flex items-center justify-between gap-3 transition-colors ${
                    isCurrent
                      ? 'border-[#E11D48] bg-rose-50/50 '
                      : isPast
                      ? 'border-[#18181B]/30 bg-[#FAF8F5] opacity-60'
                      : 'border-[#18181B] bg-white hover:bg-[#FAF8F5]'
                  }`}
                >
                  <div className="flex items-center gap-2.5 min-w-0">
                    <span
                      className={`w-5 h-5 rounded-sm border border-[#18181B] flex items-center justify-center text-[10px] font-black shrink-0 ${
                        isCurrent
                          ? 'bg-[#E11D48] text-white'
                          : isPast
                          ? 'bg-[#18181B] text-white'
                          : 'bg-[#FAF8F5] text-[#18181B]'
                      }`}
                    >
                      {idx + 1}
                    </span>
                    <span className="text-xs font-bold text-[#18181B] truncate">
                      {act.title}
                    </span>
                  </div>

                  <span className="text-[10px] font-mono font-bold text-[#52525B] shrink-0">
                    {act.startTime}
                  </span>
                </button>
              );
            })}
          </div>
        </section>
      </main>
    </div>
  );
}
