'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import {
  ArrowLeft,
  Sparkles,
  MapPin,
  Calendar,
  Users,
  DollarSign,
  Clock,
  ShieldCheck,
  CheckCircle2,
  Navigation,
  Star,
  RefreshCw,
  Send,
  AlertCircle
} from 'lucide-react';

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'https://backend-production-011e.up.railway.app';

export default function WebTripPlanner() {
  const [destination, setDestination] = useState('Tokyo, Japan');
  const [startDate, setStartDate] = useState('2026-11-10');
  const [endDate, setEndDate] = useState('2026-11-12');
  const [travelers, setTravelers] = useState(2);
  const [budget, setBudget] = useState('2500');
  const [pace, setPace] = useState('MODERATE');
  const [currency, setCurrency] = useState('USD');

  // Generation & Status state
  const [isGenerating, setIsGenerating] = useState(false);
  const [statusMessage, setStatusMessage] = useState('Initializing AI Planner...');
  const [progressPercent, setProgressPercent] = useState(0);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Results state
  const [tripData, setTripData] = useState<any>(null);

  // Refine / Chat modification state
  const [refineInstruction, setRefineInstruction] = useState('');
  const [isRefining, setIsRefining] = useState(false);
  const [refineFeedback, setRefineFeedback] = useState<string | null>(null);

  const interestsList = [
    'Art & Museums',
    'Local Food',
    'Historic Landmarks',
    'Architecture',
    'Cafes',
    'Parks & Walks',
    'Shopping',
    'Nightlife'
  ];
  const [selectedInterests, setSelectedInterests] = useState([
    'Art & Museums',
    'Local Food',
    'Historic Landmarks'
  ]);

  const handleGenerate = async () => {
    setIsGenerating(true);
    setErrorMsg(null);
    setTripData(null);
    setRefineFeedback(null);
    setProgressPercent(10);
    setStatusMessage('Creating trip parameters...');

    try {
      // 1. Create Trip record
      const createRes = await fetch(`${API_BASE_URL}/api/v1/trips`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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
        throw new Error(`Failed to create trip (HTTP ${createRes.status})`);
      }

      const { tripId } = await createRes.json();
      setProgressPercent(25);
      setStatusMessage('Querying verified places and real-time weather...');

      // 2. Trigger Generation
      const genRes = await fetch(`${API_BASE_URL}/api/v1/trips/${tripId}/generate`, {
        method: 'POST'
      });

      if (!genRes.ok) {
        throw new Error(`Failed to start generation (HTTP ${genRes.status})`);
      }

      // 3. Poll generation status
      let attempts = 0;
      const maxAttempts = 30;
      let completed = false;

      while (attempts < maxAttempts && !completed) {
        await new Promise((r) => setTimeout(r, 2000));
        attempts++;

        const statusRes = await fetch(`${API_BASE_URL}/api/v1/trips/${tripId}/status`);
        if (!statusRes.ok) continue;

        const statusData = await statusRes.json();
        if (statusData.progressPercentage) {
          setProgressPercent(Math.max(30, statusData.progressPercentage));
        }
        if (statusData.currentStepMessage) {
          setStatusMessage(statusData.currentStepMessage);
        }

        if (statusData.status === 'READY') {
          completed = true;
          setProgressPercent(100);
          setStatusMessage('Itinerary verified & ready!');
          break;
        } else if (statusData.status === 'FAILED') {
          throw new Error(statusData.errorMessage || 'AI generation failed constraints');
        }
      }

      // 4. Fetch full trip details
      const detailsRes = await fetch(`${API_BASE_URL}/api/v1/trips/${tripId}`);
      if (!detailsRes.ok) {
        throw new Error('Failed to load trip details');
      }

      const fullTrip = await detailsRes.json();
      setTripData(fullTrip);
    } catch (err: any) {
      console.error('Generation error:', err);
      setErrorMsg(err.message || 'Something went wrong during generation');
    } finally {
      setIsGenerating(false);
    }
  };

  const handleRefine = async () => {
    if (!refineInstruction.trim() || !tripData?.itinerary?.id) return;

    setIsRefining(true);
    setRefineFeedback(null);

    try {
      const res = await fetch(
        `${API_BASE_URL}/api/v1/itineraries/${tripData.itinerary.id}/modify`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ instruction: refineInstruction })
        }
      );

      if (!res.ok) {
        throw new Error(`Modification failed (HTTP ${res.status})`);
      }

      const data = await res.json();
      setRefineFeedback(data.appliedChangesSummary || 'Itinerary successfully adjusted!');
      setTripData({
        ...tripData,
        itinerary: data.updatedItinerary
      });
      setRefineInstruction('');
    } catch (err: any) {
      console.error('Refinement error:', err);
      setRefineFeedback(`Error: ${err.message}`);
    } finally {
      setIsRefining(false);
    }
  };

  const itinerary = tripData?.itinerary;

  return (
    <div className="min-h-screen bg-[#F8F9FA] pb-24 text-gray-900">
      {/* Top Header */}
      <header className="bg-white border-b border-gray-100 py-4 sticky top-0 z-30 shadow-xs">
        <div className="max-w-5xl mx-auto px-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Link
              href="/"
              className="p-2 rounded-xl text-gray-600 hover:bg-gray-100 transition"
              aria-label="Back to home"
            >
              <ArrowLeft className="w-5 h-5" />
            </Link>
            <div>
              <h1 className="font-extrabold text-xl text-gray-900 flex items-center gap-2">
                <span>Trippin&apos; AI Planner</span>
                <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-blue-50 text-blue-600 border border-blue-200">
                  Live Production
                </span>
              </h1>
            </div>
          </div>
          <div className="text-xs text-gray-500 font-medium hidden sm:block">
            Powered by Gemini 2.5 Flash Free Tier
          </div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 py-8 grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Form Inputs (Left Column) */}
        <section className="lg:col-span-5 space-y-6 bg-white p-6 rounded-3xl border border-gray-100 shadow-sm self-start">
          <div>
            <label htmlFor="destination-input" className="block text-sm font-bold text-gray-800 mb-2">
              Destination City &amp; Country
            </label>
            <div className="relative">
              <MapPin className="absolute left-3.5 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <input
                id="destination-input"
                type="text"
                value={destination}
                onChange={(e) => setDestination(e.target.value)}
                placeholder="e.g. Tokyo, Japan or Paris, France"
                className="w-full h-12 pl-11 pr-4 rounded-xl border border-gray-200 text-gray-900 font-medium focus:ring-2 focus:ring-blue-600 focus:outline-none"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="start-date-input" className="block text-sm font-bold text-gray-800 mb-2">Start Date</label>
              <input
                id="start-date-input"
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full h-12 px-3 rounded-xl border border-gray-200 text-gray-900 text-sm font-medium focus:ring-2 focus:ring-blue-600 focus:outline-none"
              />
            </div>
            <div>
              <label htmlFor="end-date-input" className="block text-sm font-bold text-gray-800 mb-2">End Date</label>
              <input
                id="end-date-input"
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full h-12 px-3 rounded-xl border border-gray-200 text-gray-900 text-sm font-medium focus:ring-2 focus:ring-blue-600 focus:outline-none"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-bold text-gray-800 mb-2">Travelers</label>
              <div className="flex items-center border border-gray-200 rounded-xl h-12 px-3 justify-between">
                <button
                  type="button"
                  onClick={() => setTravelers(Math.max(1, travelers - 1))}
                  className="font-bold text-gray-600 hover:text-gray-900 px-2 text-lg"
                  aria-label="Decrease travelers"
                >
                  -
                </button>
                <span className="font-semibold text-sm">{travelers} {travelers === 1 ? 'person' : 'people'}</span>
                <button
                  type="button"
                  onClick={() => setTravelers(travelers + 1)}
                  className="font-bold text-gray-600 hover:text-gray-900 px-2 text-lg"
                  aria-label="Increase travelers"
                >
                  +
                </button>
              </div>
            </div>

            <div>
              <label htmlFor="budget-input" className="block text-sm font-bold text-gray-800 mb-2">Budget ({currency})</label>
              <div className="relative">
                <DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input
                  id="budget-input"
                  type="number"
                  value={budget}
                  onChange={(e) => setBudget(e.target.value)}
                  className="w-full h-12 pl-9 pr-3 rounded-xl border border-gray-200 text-gray-900 font-medium focus:ring-2 focus:ring-blue-600 focus:outline-none"
                />
              </div>
            </div>
          </div>

          <div>
            <label className="block text-sm font-bold text-gray-800 mb-2">Travel Pace</label>
            <div className="grid grid-cols-3 gap-2">
              {['RELAXED', 'MODERATE', 'FAST'].map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPace(p)}
                  className={`h-10 rounded-xl text-xs font-bold transition ${
                    pace === p
                      ? 'bg-blue-600 text-white shadow-xs'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-sm font-bold text-gray-800 mb-2">Interests &amp; Vibe</label>
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
                    className={`px-3 py-1.5 rounded-full text-xs font-semibold transition ${
                      isSelected
                        ? 'bg-blue-100 text-blue-800 border border-blue-300'
                        : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                    }`}
                  >
                    {interest}
                  </button>
                );
              })}
            </div>
          </div>

          <button
            type="button"
            onClick={handleGenerate}
            disabled={isGenerating}
            className="w-full h-14 rounded-2xl bg-blue-600 text-white font-bold text-base shadow-lg shadow-blue-500/25 hover:bg-blue-700 transition flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <Sparkles className="w-5 h-5 text-yellow-300" />
            {isGenerating ? 'Computing Mathematical Schedule...' : 'Generate Verified Itinerary'}
          </button>

          {errorMsg && (
            <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-start gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{errorMsg}</span>
            </div>
          )}
        </section>

        {/* Output Preview (Right Column) */}
        <section className="lg:col-span-7">
          {isGenerating && (
            <div className="bg-white p-8 rounded-3xl border border-gray-100 shadow-sm text-center space-y-6">
              <div className="w-16 h-16 rounded-full border-4 border-blue-600 border-t-transparent animate-spin mx-auto" />
              <div>
                <h2 className="text-xl font-bold text-gray-900">Deterministic Engine Active</h2>
                <p className="text-sm text-blue-600 font-semibold mt-2 animate-pulse">
                  {statusMessage}
                </p>
              </div>
              <div className="w-full bg-gray-100 rounded-full h-2.5 overflow-hidden">
                <div
                  className="bg-blue-600 h-2.5 rounded-full transition-all duration-500"
                  style={{ width: `${progressPercent}%` }}
                />
              </div>
              <p className="text-xs text-gray-400">
                Evaluating physical transit feasibility, opening hours, and weather constraints...
              </p>
            </div>
          )}

          {!isGenerating && itinerary && (
            <div className="space-y-6">
              {/* Itinerary Header Card */}
              <div className="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm space-y-4">
                <div className="flex items-start justify-between gap-4 border-b border-gray-100 pb-4">
                  <div>
                    <span className="text-xs font-bold text-blue-600 uppercase tracking-wider">
                      {destination}
                    </span>
                    <h2 className="text-2xl font-black text-gray-900 mt-1">{itinerary.title}</h2>
                    {itinerary.summary && (
                      <p className="text-xs text-gray-500 mt-1 max-w-lg leading-relaxed">
                        {itinerary.summary}
                      </p>
                    )}
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <span className="px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-700 flex items-center gap-1">
                      <ShieldCheck className="w-4 h-4" /> 100% VERIFIED
                    </span>
                    <span className="text-[11px] font-semibold text-gray-400">
                      Version v{itinerary.version}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-6 text-xs text-gray-600">
                  <div className="flex items-center gap-1.5">
                    <Calendar className="w-4 h-4 text-gray-400" />
                    <span>{startDate} to {endDate}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Users className="w-4 h-4 text-gray-400" />
                    <span>{travelers} Travelers</span>
                  </div>
                  {itinerary.totalEstimatedCost && (
                    <div className="flex items-center gap-1.5 font-bold text-gray-900">
                      <DollarSign className="w-4 h-4 text-emerald-600" />
                      <span>~{itinerary.totalEstimatedCost} {itinerary.currency}</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Day-by-Day Cards */}
              {itinerary.days?.map((d: any) => (
                <div
                  key={d.id || d.dayIndex}
                  className="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm space-y-4"
                >
                  <div className="bg-blue-50/70 p-3.5 rounded-2xl flex items-center justify-between">
                    <div>
                      <span className="text-xs font-bold text-blue-600 uppercase tracking-wider">
                        Day {d.dayIndex} · {d.date?.split('T')[0] || d.date}
                      </span>
                      <h3 className="text-sm font-bold text-blue-950 mt-0.5">
                        {d.themeSummary || d.summary || 'Curated Exploration'}
                      </h3>
                    </div>
                    {d.weatherSummary && (
                      <span className="bg-white px-2.5 py-1 rounded-xl text-xs font-semibold text-gray-700 shadow-2xs border border-blue-100">
                        {d.weatherSummary}
                      </span>
                    )}
                  </div>

                  <div className="space-y-4 pt-2">
                    {d.activities?.map((a: any, idx: number) => (
                      <div key={a.id || idx} className="relative pl-7 pb-2 border-l-2 border-blue-100">
                        <div className="absolute -left-[9px] top-1 w-4 h-4 rounded-full bg-blue-600 border-2 border-white shadow-xs" />

                        <div className="flex items-center justify-between text-xs">
                          <span className="font-bold text-blue-600 flex items-center gap-1">
                            <Clock className="w-3.5 h-3.5" />
                            {a.startTime} – {a.endTime} ({a.durationMinutes}m)
                          </span>
                          {a.estimatedCost ? (
                            <span className="font-semibold text-gray-700">
                              ~{a.estimatedCost} {a.currency || itinerary.currency}
                            </span>
                          ) : (
                            <span className="text-gray-400 font-medium">Free / Included</span>
                          )}
                        </div>

                        <h4 className="font-extrabold text-gray-900 text-base mt-1">
                          {a.title || a.name}
                        </h4>

                        {a.place?.formattedAddress && (
                          <p className="text-xs text-gray-400 flex items-center gap-1 mt-0.5">
                            <MapPin className="w-3 h-3 shrink-0" />
                            <span className="truncate">{a.place.formattedAddress}</span>
                          </p>
                        )}

                        {a.reason && (
                          <p className="text-xs text-gray-600 mt-2 bg-gray-50 p-2.5 rounded-xl border border-gray-100">
                            {a.reason}
                          </p>
                        )}

                        {a.travelTimeFromPreviousMinutes > 0 && (
                          <div className="mt-2 inline-flex items-center gap-1 text-[11px] font-semibold text-amber-800 bg-amber-50 px-2 py-0.5 rounded-md border border-amber-200">
                            <Navigation className="w-3 h-3" />
                            <span>
                              {a.travelTimeFromPreviousMinutes}m transit ({a.transitModeFromPrevious || 'TRANSIT'})
                            </span>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ))}

              {/* Conversational Refinement ("Chat to Edit") */}
              <div className="bg-gradient-to-br from-blue-50 to-indigo-50 p-6 rounded-3xl border border-blue-100 shadow-sm space-y-4">
                <div className="flex items-center gap-2 text-blue-900 font-bold text-sm">
                  <RefreshCw className={`w-4 h-4 ${isRefining ? 'animate-spin' : ''}`} />
                  <span>Refine Schedule with AI (Auto-Repair Loop)</span>
                </div>
                <p className="text-xs text-blue-800/80">
                  Want adjustments? Tell the AI planner what to change (e.g. &quot;Add dinner at 19:30 on day 1&quot; or &quot;Make afternoon more relaxed&quot;). The deterministic engine will re-verify all transit times and save a new version.
                </p>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={refineInstruction}
                    onChange={(e) => setRefineInstruction(e.target.value)}
                    placeholder="e.g. Swap day 2 afternoon for a scenic walk"
                    onKeyDown={(e) => e.key === 'Enter' && handleRefine()}
                    className="flex-1 h-12 px-4 rounded-xl border border-blue-200 bg-white text-sm text-gray-900 focus:ring-2 focus:ring-blue-600 focus:outline-none"
                  />
                  <button
                    type="button"
                    onClick={handleRefine}
                    disabled={isRefining || !refineInstruction.trim()}
                    className="h-12 px-5 rounded-xl bg-blue-600 text-white font-bold text-xs hover:bg-blue-700 transition flex items-center gap-1.5 disabled:opacity-50"
                  >
                    <Send className="w-3.5 h-3.5" />
                    {isRefining ? 'Re-verifying...' : 'Update'}
                  </button>
                </div>
                {refineFeedback && (
                  <div className="p-3 rounded-xl bg-white text-xs text-blue-900 border border-blue-100 font-medium">
                    {refineFeedback}
                  </div>
                )}
              </div>
            </div>
          )}

          {!isGenerating && !itinerary && (
            <div className="h-full min-h-[420px] rounded-3xl border-2 border-dashed border-gray-200 bg-white/50 flex flex-col items-center justify-center p-8 text-center text-gray-400">
              <div className="w-16 h-16 rounded-2xl bg-blue-50 flex items-center justify-center text-blue-600 mb-4">
                <Sparkles className="w-8 h-8" />
              </div>
              <h3 className="font-extrabold text-gray-800 text-lg">No Itinerary Generated Yet</h3>
              <p className="text-xs text-gray-500 mt-2 max-w-sm leading-relaxed">
                Choose your destination and preferred pace on the left, then click &quot;Generate Verified Itinerary&quot; to compute a conflict-free schedule.
              </p>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
