'use client';

import React, { useState } from 'react';
import {
  X,
  Shuffle,
  Search,
  CheckCircle2,
  RefreshCw,
  MapPin,
  Clock,
  ArrowRight
} from 'lucide-react';
import { apiFetch } from '@/lib/api-client';

interface SwapActivityModalProps {
  isOpen: boolean;
  onClose: () => void;
  activity: {
    id: string;
    title: string;
    dayIndex: number;
    startTime?: string;
    endTime?: string;
  } | null;
  itineraryId: string;
  destinationName: string;
  onItineraryUpdated: (newItinerary: any) => void;
}

export default function SwapActivityModal({
  isOpen,
  onClose,
  activity,
  itineraryId,
  destinationName,
  onItineraryUpdated,
}: SwapActivityModalProps) {
  const [replacementName, setReplacementName] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  if (!isOpen || !activity) return null;

  const quickPicks = [
    'Local Street Food Market',
    'Scenic City Viewpoint',
    'Contemporary Art Gallery',
    'Relaxed Park & Tea House',
    'Historic Neighborhood Walk',
  ];

  const handleSwap = async (chosenName: string) => {
    const targetVenue = chosenName.trim();
    if (!targetVenue || isSubmitting) return;

    setIsSubmitting(true);
    setErrorMsg(null);

    const prompt = `On Day ${activity.dayIndex}, replace "${activity.title}" with a visit to "${targetVenue}" in ${destinationName}. Keep the schedule conflict-free and physically reachable.`;

    try {
      const res = await apiFetch(`/api/v1/itineraries/${itineraryId}/modify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ instruction: prompt }),
      });

      if (!res.ok) {
        throw new Error(`Swap failed (HTTP ${res.status})`);
      }

      const data = await res.json();
      if (data.updatedItinerary) {
        onItineraryUpdated(data.updatedItinerary);
      }
      onClose();
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to swap activity. Try another venue.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div className="comic-panel w-full max-w-md bg-white rounded-2xl p-6 space-y-5 shadow-[6px_6px_0px_#18181B] relative">
        {/* Header */}
        <div className="flex items-center justify-between border-b-2 border-[#18181B] pb-3">
          <div className="flex items-center gap-2">
            <Shuffle className="w-5 h-5 text-[#E11D48]" />
            <h3 className="font-display font-black text-lg uppercase text-[#18181B]">
              Swap Venue Stop
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="comic-btn-secondary p-1 rounded-lg"
            aria-label="Close"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Current Activity Box */}
        <div className="bg-[#FAF8F5] p-3 border-2 border-[#18181B] rounded-xl text-xs">
          <span className="text-[10px] font-black uppercase text-[#E11D48] block mb-0.5">
            REPLACING ON STAGE 0{activity.dayIndex}
          </span>
          <span className="font-display font-black text-sm text-[#18181B] block">
            {activity.title}
          </span>
          {activity.startTime && (
            <span className="text-[11px] text-[#52525B] font-bold">
              Timeslot: {activity.startTime} – {activity.endTime}
            </span>
          )}
        </div>

        {errorMsg && (
          <div className="bg-red-50 border-2 border-[#18181B] rounded-lg p-2.5 text-xs font-bold text-red-900">
            {errorMsg}
          </div>
        )}

        {/* Custom Input */}
        <div className="space-y-1.5">
          <label className="text-[11px] font-black uppercase text-[#18181B] block">
            Enter New Venue / Spot Name
          </label>
          <div className="flex gap-2">
            <input
              type="text"
              placeholder="e.g. Meiji Shrine, Tsukiji Outer Market"
              value={replacementName}
              onChange={(e) => setReplacementName(e.target.value)}
              className="flex-1 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg px-3 py-2 text-xs font-bold text-[#18181B] focus:outline-none focus:ring-2 focus:ring-[#E11D48]"
            />
            <button
              type="button"
              disabled={isSubmitting || !replacementName.trim()}
              onClick={() => handleSwap(replacementName)}
              className="comic-btn-primary px-3 py-2 rounded-lg text-xs font-black uppercase disabled:opacity-50"
            >
              {isSubmitting ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : 'Swap'}
            </button>
          </div>
        </div>

        {/* Quick Alternative Picks */}
        <div className="space-y-1.5 pt-1">
          <span className="text-[10px] font-black uppercase text-[#52525B] tracking-wider block">
            Or pick an alternative category:
          </span>
          <div className="space-y-1.5">
            {quickPicks.map((pick, i) => (
              <button
                key={i}
                type="button"
                disabled={isSubmitting}
                onClick={() => handleSwap(pick)}
                className="w-full text-left p-2.5 rounded-lg border-2 border-[#18181B] bg-white hover:bg-[#FAF8F5] flex items-center justify-between text-xs font-black text-[#18181B] shadow-[2px_2px_0px_#18181B] transition-all disabled:opacity-50"
              >
                <span>{pick}</span>
                <ArrowRight className="w-3.5 h-3.5 text-[#E11D48]" />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
