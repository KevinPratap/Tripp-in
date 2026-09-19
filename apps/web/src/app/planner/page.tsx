'use client';

import React, { useState, useEffect, Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import dynamic from 'next/dynamic';
import {
  ArrowLeft,
  MapPin,
  Calendar,
  Users,
  DollarSign,
  Clock,
  ShieldCheck,
  Navigation,
  Send,
  AlertCircle,
  Flame,
  CheckCircle2,
  RefreshCw,
  Share2,
  ExternalLink,
  Check,
  Bookmark
} from 'lucide-react';
import { unwrapTripDetails } from '@/lib/trip-contract';
import { apiFetch } from '@/lib/api-client';
import { saveTripToHistory } from '@/lib/saved-trips';
import MyTripsModal from '@/components/MyTripsModal';

const ComicRouteMap = dynamic(() => import('@/components/ComicRouteMap'), {
  ssr: false,
  loading: () => (
    <div className="comic-panel rounded-2xl h-56 flex items-center justify-center text-xs font-bold uppercase text-[#52525B]">
      Loading Route Radar...
    </div>
  )
});

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'https://backend-production-011e.up.railway.app';

function PlannerContent() {
  const searchParams = useSearchParams();
  const initialDest = searchParams.get('destination') || 'Tokyo, Japan';

  const [destination, setDestination] = useState(initialDest);
  const [startDate, setStartDate] = useState('2026-11-10');
  const [endDate, setEndDate] = useState('2026-11-12');
  const [travelers, setTravelers] = useState(2);
  const [budget, setBudget] = useState('2500');
  const [pace, setPace] = useState('MODERATE');
  const [currency, setCurrency] = useState('USD');

  // Generation & Status state
  const [isGenerating, setIsGenerating] = useState(false);
  const [statusMessage, setStatusMessage] = useState('Locking in coordinates...');
  const [progressPercent, setProgressPercent] = useState(0);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Results state
  const [tripData, setTripData] = useState<any>(null);
  const [isMyTripsOpen, setIsMyTripsOpen] = useState(false);

  // Refine / Modification state
  const [refineInstruction, setRefineInstruction] = useState('');
  const [isRefining, setIsRefining] = useState(false);
  const [refineFeedback, setRefineFeedback] = useState<string | null>(null);

  const interestsList = [
    'Art & Museums',
    'Local Food',
    'Historic Landmarks',
    'Architecture',
    'Cafes & Roasters',
    'Parks & Walks',
    'Nightlife',
    'Shopping'
  ];
  const [selectedInterests, setSelectedInterests] = useState([
    'Local Food',
    'Historic Landmarks',
    'Architecture'
  ]);

  useEffect(() => {
    const destParam = searchParams.get('destination');
    if (destParam) {
      setDestination(destParam);
    }
  }, [searchParams]);

  const handleGenerate = async () => {
    setIsGenerating(true);
    setErrorMsg(null);
    setTripData(null);
    setRefineFeedback(null);
    setProgressPercent(15);
    setStatusMessage('Creating route parameter log...');

    try {
      // 1. Create Trip
      const createRes = await apiFetch(`/api/v1/trips`, {
        method: 'POST',
        body: JSON.stringify({
          destination,
          startDate,
          endDate,
          travelersCount: Number(travelers),
          budgetTotal: budget ? Number(budget) : undefined,
          currency,
          pace,
          interests: selectedInterests
        })
      });

      if (!createRes.ok) {
        throw new Error(`Route initialization failed (HTTP ${createRes.status})`);
      }

      const { tripId } = await createRes.json();
      setProgressPercent(30);
      setStatusMessage('Querying venue hours and road matrix...');

      // 2. Trigger Generation
      const genRes = await apiFetch(`/api/v1/trips/${tripId}/generate`, {
        method: 'POST'
      });

      if (!genRes.ok) {
        throw new Error(`Failed to execute route engine (HTTP ${genRes.status})`);
      }

      // 3. Poll status
      let attempts = 0;
      const maxAttempts = 30;
      let completed = false;

      while (attempts < maxAttempts && !completed) {
        await new Promise((r) => setTimeout(r, 2000));
        attempts++;

        const statusRes = await apiFetch(`/api/v1/trips/${tripId}/status`);
        if (!statusRes.ok) continue;

        const statusData = await statusRes.json();
        if (statusData.progressPercentage) {
          setProgressPercent(Math.max(35, statusData.progressPercentage));
        }
        if (statusData.currentStepMessage) {
          setStatusMessage(statusData.currentStepMessage);
        }

        if (statusData.status === 'READY') {
          completed = true;
          setProgressPercent(100);
          setStatusMessage('Route locked & verified!');
          break;
        } else if (statusData.status === 'FAILED') {
          throw new Error(statusData.errorMessage || 'Route generation failed verification');
        }
      }

      // 4. Fetch Full Trip
      const detailsRes = await apiFetch(`/api/v1/trips/${tripId}`);
      if (!detailsRes.ok) {
        throw new Error('Failed to load final itinerary');
      }

      const fullTrip = await detailsRes.json();
      const unwrapped = unwrapTripDetails(fullTrip);
      setTripData(unwrapped);
      if (unwrapped?.id) {
        saveTripToHistory(unwrapped);
      }
    } catch (err: any) {
      console.error('Route error:', err);
      setErrorMsg(err.message || 'Error occurred while computing route');
    } finally {
      setIsGenerating(false);
    }
  };

  const handleRefine = async () => {
    if (!refineInstruction.trim() || !tripData?.itinerary?.id) return;

    setIsRefining(true);
    setRefineFeedback(null);

    try {
      const res = await apiFetch(
        `/api/v1/itineraries/${tripData.itinerary.id}/modify`,
        {
          method: 'POST',
          body: JSON.stringify({ instruction: refineInstruction })
        }
      );

      if (!res.ok) {
        throw new Error(`Revision rejected (HTTP ${res.status})`);
      }

      const data = await res.json();
      setRefineFeedback(data.appliedChangesSummary || 'Route updated and re-verified!');
      setTripData({
        ...tripData,
        itinerary: data.updatedItinerary
      });
      setRefineInstruction('');
    } catch (err: any) {
      console.error('Refine error:', err);
      setRefineFeedback(`Error: ${err.message}`);
    } finally {
      setIsRefining(false);
    }
  };

  const itinerary = tripData?.itinerary;

  return (
    <div className="min-h-screen bg-[#FAF8F5] text-[#18181B] pb-20">
      {/* Header */}
      <header className="sticky top-0 z-40 bg-[#FAF8F5] border-b-[2.5px] border-[#18181B]">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 h-18 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link
              href="/"
              className="comic-btn-secondary p-2 rounded-lg"
              aria-label="Back to home"
            >
              <ArrowLeft className="w-5 h-5" />
            </Link>
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 bg-[#E11D48] border-2 border-[#18181B]" />
              <h1 className="font-display font-black text-xl uppercase tracking-tight">
                MISSION BRIEFING // ROUTE PLANNER
              </h1>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setIsMyTripsOpen(true)}
              className="comic-btn-secondary px-3 py-1.5 text-xs font-black uppercase flex items-center gap-1.5 rounded-lg"
              title="View saved missions"
            >
              <Bookmark className="w-3.5 h-3.5 text-[#E11D48]" />
              <span className="hidden sm:inline">Saved Trips</span>
            </button>
            <div className="hidden sm:inline-flex items-center gap-1.5 text-xs font-black uppercase text-[#52525B]">
              <ShieldCheck className="w-4 h-4 text-[#E11D48]" />
              <span>PHYSICS CHECKED</span>
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 sm:px-6 pt-8 grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Left Panel: Inputs */}
        <section className="lg:col-span-5 space-y-6">
          <div className="comic-panel p-6 rounded-2xl bg-white space-y-6">
            <div className="border-b-2 border-[#18181B] pb-3 flex items-center justify-between">
              <span className="text-xs font-black uppercase tracking-widest text-[#E11D48]">
                STEP 01 // PARAMETERS
              </span>
              <span className="text-[10px] font-bold text-[#52525B] uppercase">FORM 40-A</span>
            </div>

            {/* Destination */}
            <div>
              <label htmlFor="destination-input" className="block text-xs font-black uppercase tracking-wider text-[#18181B] mb-2">
                Target Destination
              </label>
              <div className="relative">
                <MapPin className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[#18181B]" />
                <input
                  id="destination-input"
                  type="text"
                  value={destination}
                  onChange={(e) => setDestination(e.target.value)}
                  placeholder="e.g. Tokyo, Japan"
                  className="w-full h-12 pl-10 pr-4 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg font-bold text-base sm:text-sm text-[#18181B] focus:bg-white focus:outline-none focus:ring-2 focus:ring-[#E11D48]"
                />
              </div>
            </div>

            {/* Dates */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor="start-date-input" className="block text-xs font-black uppercase tracking-wider text-[#18181B] mb-2">
                  Launch Date
                </label>
                <input
                  id="start-date-input"
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="w-full h-12 px-3 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg font-bold text-base sm:text-sm text-[#18181B] focus:bg-white focus:outline-none"
                />
              </div>
              <div>
                <label htmlFor="end-date-input" className="block text-xs font-black uppercase tracking-wider text-[#18181B] mb-2">
                  Return Date
                </label>
                <input
                  id="end-date-input"
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  className="w-full h-12 px-3 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg font-bold text-base sm:text-sm text-[#18181B] focus:bg-white focus:outline-none"
                />
              </div>
            </div>

            {/* Travelers & Budget */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-black uppercase tracking-wider text-[#18181B] mb-2">
                  Party Size
                </label>
                <div className="flex items-center justify-between h-12 px-1 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg">
                  <button
                    type="button"
                    aria-label="Fewer travellers"
                    onClick={() => setTravelers(Math.max(1, travelers - 1))}
                    className="h-11 w-11 flex items-center justify-center font-black text-base hover:text-[#E11D48]"
                  >
                    -
                  </button>
                  <span className="font-black text-xs uppercase">{travelers} Crew</span>
                  <button
                    type="button"
                    aria-label="More travellers"
                    onClick={() => setTravelers(travelers + 1)}
                    className="h-11 w-11 flex items-center justify-center font-black text-base hover:text-[#E11D48]"
                  >
                    +
                  </button>
                </div>
              </div>

              <div>
                <label htmlFor="budget-input" className="block text-xs font-black uppercase tracking-wider text-[#18181B] mb-2">
                  Budget ({currency})
                </label>
                <div className="relative">
                  <DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#18181B]" />
                  <input
                    id="budget-input"
                    type="number"
                    value={budget}
                    onChange={(e) => setBudget(e.target.value)}
                    className="w-full h-12 pl-9 pr-3 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg font-bold text-base sm:text-sm text-[#18181B] focus:bg-white focus:outline-none"
                  />
                </div>
              </div>
            </div>

            {/* Pace Selector */}
            <div>
              <label className="block text-xs font-black uppercase tracking-wider text-[#18181B] mb-2">
                Intensity Level
              </label>
              <div className="grid grid-cols-3 gap-2">
                {[
                  { key: 'RELAXED', label: 'Cruising' },
                  { key: 'MODERATE', label: 'Standard' },
                  { key: 'FAST', label: 'All-Out' }
                ].map((p) => (
                  <button
                    key={p.key}
                    type="button"
                    onClick={() => setPace(p.key)}
                    className={`h-11 rounded-lg text-xs font-black uppercase tracking-wider transition-all ${
                      pace === p.key
                        ? 'bg-[#E11D48] text-white border-2 border-[#18181B] shadow-[2px_2px_0px_#18181B]'
                        : 'bg-[#FAF8F5] text-[#52525B] border-2 border-[#18181B] hover:bg-[#F4F2EE]'
                    }`}
                  >
                    {p.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Interests Tag Cloud */}
            <div>
              <label className="block text-xs font-black uppercase tracking-wider text-[#18181B] mb-2">
                Focus Areas
              </label>
              <div className="flex flex-wrap gap-2">
                {interestsList.map((interest) => {
                  const isSelected = selectedInterests.includes(interest);
                  return (
                    <button
                      key={interest}
                      type="button"
                      onClick={() => {
                        if (isSelected) {
                          setSelectedInterests(selectedInterests.filter((i) => i !== interest));
                        } else {
                          setSelectedInterests([...selectedInterests, interest]);
                        }
                      }}
                      className={`comic-tag rounded-xs transition-all min-h-11 px-3 inline-flex items-center ${
                        isSelected
                          ? 'bg-[#E11D48] text-white'
                          : 'bg-white text-[#52525B] hover:bg-[#F4F2EE]'
                      }`}
                    >
                      {interest}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Submit Action */}
            <button
              type="button"
              onClick={handleGenerate}
              disabled={isGenerating}
              className="comic-btn-primary w-full h-14 rounded-xl font-black text-sm uppercase tracking-wider flex items-center justify-center gap-2 disabled:opacity-50"
            >
              <Flame className="w-5 h-5" />
              <span>{isGenerating ? 'Compiling Route...' : 'Lock In Route'}</span>
            </button>

            {errorMsg && (
              <div className="p-3 bg-red-50 border-2 border-red-500 rounded-lg text-red-900 text-xs font-bold flex items-start gap-2">
                <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                <span>{errorMsg}</span>
              </div>
            )}
          </div>
        </section>

        {/* Right Panel: Output */}
        <section className="lg:col-span-7">
          {/* Active Generation State */}
          {isGenerating && (
            <div className="comic-panel p-8 sm:p-12 rounded-2xl bg-white text-center space-y-6">
              <div className="w-16 h-16 border-4 border-[#18181B] border-t-[#E11D48] rounded-full animate-spin mx-auto" />
              <div className="space-y-2">
                <span className="comic-tag bg-[#E11D48] text-white">ACTIVE ROUTING</span>
                <h2 className="font-display font-black text-xl uppercase tracking-tight text-[#18181B]">
                  {statusMessage}
                </h2>
                <p className="text-xs text-[#52525B] font-medium">
                  Validating venue opening days, computing walking buffers, and testing temporal limits.
                </p>
              </div>
              <div className="w-full bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg h-4 overflow-hidden p-0.5">
                <div
                  className="bg-[#E11D48] h-full rounded-xs transition-all duration-300"
                  style={{ width: `${progressPercent}%` }}
                />
              </div>
            </div>
          )}

          {/* Generated Result */}
          {!isGenerating && itinerary && (
            <div className="space-y-6">
              {/* Route Summary Header */}
              <div className="comic-panel p-6 rounded-2xl bg-white space-y-4">
                <div className="flex items-start justify-between gap-4 border-b-2 border-[#18181B] pb-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="comic-tag bg-[#E11D48] text-white">
                        {destination}
                      </span>
                      <span className="comic-tag bg-[#FAF8F5] text-[#18181B]">
                        VOL. {itinerary.version}
                      </span>
                    </div>
                    <h2 className="font-display font-black text-2xl uppercase tracking-tight mt-2 text-[#18181B]">
                      {itinerary.title}
                    </h2>
                    {itinerary.summary && (
                      <p className="text-xs font-medium text-[#52525B] mt-1 leading-relaxed">
                        {itinerary.summary}
                      </p>
                    )}
                  </div>

                  <div className="flex flex-col sm:flex-row items-end gap-2 shrink-0">
                    <span className="inline-flex items-center gap-1 bg-emerald-100 text-emerald-900 border-2 border-[#18181B] px-2 py-0.5 text-[10px] font-black uppercase rounded-xs">
                      <ShieldCheck className="w-3.5 h-3.5 text-emerald-700" />
                      VERIFIED
                    </span>
                    <Link
                      href={`/trip/${tripData.id}`}
                      className="comic-btn-primary px-2.5 py-1 rounded-md text-[10px] font-black uppercase tracking-wider inline-flex items-center gap-1"
                    >
                      <span>Field Ticket</span>
                      <ExternalLink className="w-2.5 h-2.5" />
                    </Link>
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-2 text-xs font-bold text-[#18181B]">
                  <div className="bg-[#FAF8F5] p-2.5 border-2 border-[#18181B] rounded-lg flex items-center gap-2">
                    <Calendar className="w-4 h-4 text-[#E11D48]" />
                    <span className="truncate">{startDate}</span>
                  </div>
                  <div className="bg-[#FAF8F5] p-2.5 border-2 border-[#18181B] rounded-lg flex items-center gap-2">
                    <Users className="w-4 h-4 text-[#E11D48]" />
                    <span>{travelers} Travelers</span>
                  </div>
                  <div className="bg-[#FAF8F5] p-2.5 border-2 border-[#18181B] rounded-lg flex items-center gap-2">
                    <DollarSign className="w-4 h-4 text-[#E11D48]" />
                    <span>~{itinerary.totalEstimatedCost} {itinerary.currency}</span>
                  </div>
                </div>
              </div>

              {/* Interactive Route Radar Map */}
              <ComicRouteMap activities={itinerary.days?.flatMap((d: any) => d.activities || []) || []} height="300px" />

              {/* Day Panels */}
              {itinerary.days?.map((d: any) => (
                <div
                  key={d.id || d.dayIndex}
                  className="comic-panel p-6 rounded-2xl bg-white space-y-4"
                >
                  <div className="flex items-center justify-between border-b-2 border-[#18181B] pb-3">
                    <div className="flex items-center gap-2">
                      <span className="w-3 h-3 bg-[#E11D48] border-2 border-[#18181B]" />
                      <h3 className="font-display font-black text-lg uppercase tracking-tight">
                        DAY {d.dayIndex} // {d.themeSummary || d.summary || 'Field Run'}
                      </h3>
                    </div>
                    {d.weatherSummary && (
                      <span className="comic-tag bg-[#FAF8F5] text-[#18181B] text-[10px]">
                        {d.weatherSummary}
                      </span>
                    )}
                  </div>

                  <div className="space-y-4 pt-1">
                    {d.activities?.map((a: any, idx: number) => (
                      <div
                        key={a.id || idx}
                        className="relative pl-7 pb-3 border-l-2 border-[#18181B]"
                      >
                        <div className="absolute -left-[7px] top-1.5 w-3 h-3 bg-[#E11D48] border-2 border-[#18181B]" />

                        <div className="flex items-center justify-between text-xs mb-1">
                          <span className="font-black text-[#E11D48] flex items-center gap-1">
                            <Clock className="w-3.5 h-3.5 text-[#18181B]" />
                            {a.startTime} – {a.endTime} ({a.durationMinutes}m)
                          </span>
                          {a.estimatedCost ? (
                            <span className="font-black text-xs text-[#18181B]">
                              ~{a.estimatedCost} {a.currency || itinerary.currency}
                            </span>
                          ) : (
                            <span className="text-[10px] font-bold text-[#52525B]">INCLUDED</span>
                          )}
                        </div>

                        <h4 className="font-display font-black text-base uppercase text-[#18181B]">
                          {a.title || a.name}
                        </h4>

                        {a.place?.formattedAddress && (
                          <p className="text-xs text-[#52525B] font-medium flex items-center gap-1 mt-0.5">
                            <MapPin className="w-3.5 h-3.5 shrink-0 text-[#18181B]" />
                            <span className="truncate">{a.place.formattedAddress}</span>
                          </p>
                        )}

                        {a.reason && (
                          <p className="text-xs text-[#18181B] font-medium mt-2 p-2.5 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg">
                            {a.reason}
                          </p>
                        )}

                        {/* Activity Action Buttons */}
                        <div className="mt-3 flex flex-wrap items-center gap-2">
                          <a
                            href={
                              a.place?.location?.latitude && a.place?.location?.longitude
                                ? `https://www.google.com/maps/dir/?api=1&destination=${a.place.location.latitude},${a.place.location.longitude}`
                                : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent((a.title || a.name) + ' ' + destination)}`
                            }
                            target="_blank"
                            rel="noopener noreferrer"
                            className="comic-btn-secondary px-2.5 py-1 rounded-md text-[10px] font-black uppercase tracking-wider inline-flex items-center gap-1 hover:text-[#E11D48]"
                          >
                            <Navigation className="w-3 h-3 text-[#E11D48]" />
                            <span>Navigate</span>
                            <ExternalLink className="w-2.5 h-2.5 opacity-60" />
                          </a>

                          {a.travelTimeFromPreviousMinutes > 0 && (
                            <div className="inline-flex items-center gap-1 text-[10px] font-black uppercase text-[#18181B] bg-amber-100 border border-[#18181B] px-2 py-1 rounded-xs">
                              <Navigation className="w-3 h-3 text-[#E11D48]" />
                              <span>
                                {a.travelTimeFromPreviousMinutes}m transit ({a.transitModeFromPrevious || 'TRANSIT'})
                              </span>
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}

              {/* Conversational Revision Panel */}
              <div className="comic-panel-red p-6 rounded-2xl bg-white space-y-4">
                <div className="flex items-center gap-2">
                  <span className="w-3 h-3 bg-[#E11D48] border-2 border-[#18181B]" />
                  <h3 className="font-display font-black text-sm uppercase tracking-tight text-[#18181B]">
                    DISPATCH REVISION // AUTO-REPAIR LOOP
                  </h3>
                </div>
                <p className="text-xs text-[#52525B] font-medium">
                  Need to swap an afternoon spot or push dinner later? Enter your instruction below. The deterministic engine will re-calculate transit physics and generate an updated edition.
                </p>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={refineInstruction}
                    onChange={(e) => setRefineInstruction(e.target.value)}
                    placeholder="e.g. Swap day 2 afternoon for vintage shopping in Shibuya"
                    onKeyDown={(e) => e.key === 'Enter' && handleRefine()}
                    className="flex-1 h-12 px-3.5 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg font-bold text-base sm:text-xs text-[#18181B] focus:bg-white focus:outline-none"
                  />
                  <button
                    type="button"
                    onClick={handleRefine}
                    disabled={isRefining || !refineInstruction.trim()}
                    className="comic-btn-primary px-5 rounded-lg font-black text-xs uppercase tracking-wider flex items-center gap-1.5 disabled:opacity-50"
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>{isRefining ? 'Re-Checking...' : 'Transmit'}</span>
                  </button>
                </div>
                {refineFeedback && (
                  <div className="p-3 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg text-xs font-bold text-[#18181B]">
                    {refineFeedback}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Empty Placeholder State */}
          {!isGenerating && !itinerary && (
            <div className="comic-panel rounded-2xl bg-white p-12 text-center space-y-4 h-full min-h-[460px] flex flex-col items-center justify-center">
              <div className="w-14 h-14 bg-[#E11D48] text-white border-2 border-[#18181B] shadow-[3px_3px_0px_#18181B] flex items-center justify-center font-black text-2xl mb-2">
                !
              </div>
              <h3 className="font-display font-black text-xl uppercase tracking-tight text-[#18181B]">
                Awaiting Target Coordinates
              </h3>
              <p className="text-xs font-medium text-[#52525B] max-w-sm leading-relaxed">
                Enter your city and travel window on the left. Tap &quot;Lock In Route&quot; to compile your certified conflict-free travel log.
              </p>
            </div>
          )}
        </section>
      </main>

      <MyTripsModal
        isOpen={isMyTripsOpen}
        onClose={() => setIsMyTripsOpen(false)}
      />
    </div>
  );
}

export default function WebTripPlanner() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-[#FAF8F5] p-8 text-center font-bold">Loading route engine...</div>}>
      <PlannerContent />
    </Suspense>
  );
}
