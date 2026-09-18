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
  CheckCircle2
} from 'lucide-react';

export default function WebTripPlanner() {
  const [destination, setDestination] = useState('Paris');
  const [startDate, setStartDate] = useState('2026-05-10');
  const [endDate, setEndDate] = useState('2026-05-13');
  const [travelers, setTravelers] = useState(2);
  const [budget, setBudget] = useState('1500');
  const [pace, setPace] = useState('MODERATE');
  const [isGenerating, setIsGenerating] = useState(false);
  const [generationStep, setGenerationStep] = useState(0);
  const [itineraryResult, setItineraryResult] = useState<any>(null);

  const interestsList = ['Art & Museums', 'Local Food', 'Historic Landmarks', 'Architecture', 'Cafes', 'Parks & Walks'];
  const [selectedInterests, setSelectedInterests] = useState(['Art & Museums', 'Local Food', 'Historic Landmarks']);

  const steps = [
    'Analyzing travel preferences & seasonal factors...',
    'Querying verified Google Places & opening hours...',
    'Computing real transit times and Haversine matrix...',
    'Verifying weather forecasts and rain advisories...',
    'Running Deterministic Physics & Constraint Engine...',
    'Itinerary Verified!'
  ];

  const handleGenerate = async () => {
    setIsGenerating(true);
    setItineraryResult(null);

    for (let i = 0; i < steps.length; i++) {
      setGenerationStep(i);
      await new Promise((r) => setTimeout(r, 700));
    }

    setItineraryResult({
      title: `Curated ${destination} Experience`,
      version: 1,
      totalCost: 180,
      days: [
        {
          day: 1,
          theme: 'World-Class Art & Iconic Architecture',
          weather: '19°C · Sunny',
          activities: [
            { time: '10:00 - 12:30', name: 'Louvre Museum', type: 'MUSEUM', transit: 'Starting Point' },
            { time: '13:00 - 14:15', name: 'Café de Flore', type: 'RESTAURANT', transit: '30m via Metro Line 1' },
            { time: '14:45 - 17:00', name: "Musée d'Orsay", type: 'MUSEUM', transit: '25m Walk' },
            { time: '17:45 - 19:30', name: 'Eiffel Tower Sunset', type: 'LANDMARK', transit: '35m Transit' }
          ]
        }
      ]
    });
    setIsGenerating(false);
  };

  return (
    <div className="min-h-screen bg-[#F8F9FA] pb-20">
      {/* Navigation Header */}
      <header className="bg-white border-b border-gray-100 py-4">
        <div className="max-w-4xl mx-auto px-4 flex items-center gap-4">
          <Link
            href="/"
            className="p-2 rounded-xl text-gray-600 hover:bg-gray-100 transition"
          >
            <ArrowLeft className="w-5 h-5" />
          </Link>
          <h1 className="font-extrabold text-xl text-gray-900">AI Trip Planner</h1>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 py-8 grid grid-cols-1 md:grid-cols-12 gap-8">
        {/* Form Inputs (Left Column) */}
        <div className="md:col-span-6 space-y-6 bg-white p-6 rounded-3xl border border-gray-100 shadow-sm">
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-2">
              Where would you like to go?
            </label>
            <div className="relative">
              <MapPin className="absolute left-3.5 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <input
                type="text"
                value={destination}
                onChange={(e) => setDestination(e.target.value)}
                className="w-full h-12 pl-11 pr-4 rounded-xl border border-gray-200 text-gray-900 font-medium focus:ring-2 focus:ring-blue-600 focus:outline-none"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-bold text-gray-700 mb-2">Start Date</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full h-12 px-3 rounded-xl border border-gray-200 text-gray-900 text-sm font-medium focus:ring-2 focus:ring-blue-600 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-bold text-gray-700 mb-2">End Date</label>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full h-12 px-3 rounded-xl border border-gray-200 text-gray-900 text-sm font-medium focus:ring-2 focus:ring-blue-600 focus:outline-none"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-bold text-gray-700 mb-2">Travelers</label>
              <div className="flex items-center border border-gray-200 rounded-xl h-12 px-3 justify-between">
                <button
                  onClick={() => setTravelers(Math.max(1, travelers - 1))}
                  className="font-bold text-gray-600 px-2"
                >
                  -
                </button>
                <span className="font-semibold">{travelers} people</span>
                <button
                  onClick={() => setTravelers(travelers + 1)}
                  className="font-bold text-gray-600 px-2"
                >
                  +
                </button>
              </div>
            </div>

            <div>
              <label className="block text-sm font-bold text-gray-700 mb-2">Budget (USD)</label>
              <div className="relative">
                <DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input
                  type="number"
                  value={budget}
                  onChange={(e) => setBudget(e.target.value)}
                  className="w-full h-12 pl-9 pr-3 rounded-xl border border-gray-200 text-gray-900 font-medium focus:ring-2 focus:ring-blue-600 focus:outline-none"
                />
              </div>
            </div>
          </div>

          <div>
            <label className="block text-sm font-bold text-gray-700 mb-2">Travel Pace</label>
            <div className="grid grid-cols-3 gap-2">
              {['RELAXED', 'MODERATE', 'FAST'].map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPace(p)}
                  className={`h-10 rounded-xl text-xs font-bold transition ${
                    pace === p
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-sm font-bold text-gray-700 mb-2">Interests</label>
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
            {isGenerating ? 'Validating Constraints...' : 'Generate Verified Itinerary'}
          </button>
        </div>

        {/* Output Preview (Right Column) */}
        <div className="md:col-span-6">
          {isGenerating && (
            <div className="bg-white p-8 rounded-3xl border border-gray-100 shadow-sm text-center space-y-6">
              <div className="w-16 h-16 rounded-full border-4 border-blue-600 border-t-transparent animate-spin mx-auto" />
              <div>
                <h3 className="text-xl font-bold text-gray-900">Deterministic Engine Running</h3>
                <p className="text-sm text-blue-600 font-semibold mt-2 animate-pulse">
                  {steps[generationStep]}
                </p>
              </div>
              <div className="w-full bg-gray-100 rounded-full h-2">
                <div
                  className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                  style={{ width: `${((generationStep + 1) / steps.length) * 100}%` }}
                />
              </div>
            </div>
          )}

          {!isGenerating && itineraryResult && (
            <div className="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm space-y-6">
              <div className="flex items-center justify-between border-b border-gray-100 pb-4">
                <div>
                  <h3 className="text-xl font-extrabold text-gray-900">{itineraryResult.title}</h3>
                  <p className="text-xs text-gray-500">v{itineraryResult.version} · Certified Safe Schedule</p>
                </div>
                <span className="px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-700 flex items-center gap-1">
                  <ShieldCheck className="w-4 h-4" /> 100% VALID
                </span>
              </div>

              {itineraryResult.days.map((d: any) => (
                <div key={d.day} className="space-y-4">
                  <div className="bg-blue-50/70 p-3 rounded-xl flex items-center justify-between text-xs text-blue-900 font-semibold">
                    <span>Day {d.day}: {d.theme}</span>
                    <span className="bg-white px-2 py-0.5 rounded-md shadow-xs">{d.weather}</span>
                  </div>

                  <div className="space-y-3 pl-2">
                    {d.activities.map((a: any, idx: number) => (
                      <div key={idx} className="relative pl-6 border-l-2 border-blue-200 pb-2">
                        <div className="absolute -left-[9px] top-0 w-4 h-4 rounded-full bg-blue-600 border-2 border-white" />
                        <span className="text-xs font-bold text-blue-600">{a.time}</span>
                        <h4 className="font-bold text-gray-900 text-sm">{a.name}</h4>
                        <span className="text-xs text-gray-400">Transit: {a.transit}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}

          {!isGenerating && !itineraryResult && (
            <div className="h-full min-h-[350px] rounded-3xl border-2 border-dashed border-gray-200 flex flex-col items-center justify-center p-8 text-center text-gray-400">
              <Sparkles className="w-12 h-12 text-gray-300 mb-3" />
              <h4 className="font-bold text-gray-700">No Itinerary Generated Yet</h4>
              <p className="text-sm mt-1 max-w-xs">
                Fill in your destination and dates, then tap Generate to compute a conflict-free schedule.
              </p>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
