'use client';

import React, { useState } from 'react';
import {
  X,
  Sparkles,
  Send,
  AlertCircle,
  CheckCircle2,
  RefreshCw,
  Flame,
  MessageSquare
} from 'lucide-react';
import { apiFetch } from '@/lib/api-client';

interface RefineRouteModalProps {
  isOpen: boolean;
  onClose: () => void;
  itineraryId: string;
  tripId: string;
  destinationName: string;
  onItineraryUpdated: (newItinerary: any) => void;
}

export default function RefineRouteModal({
  isOpen,
  onClose,
  itineraryId,
  tripId,
  destinationName,
  onItineraryUpdated,
}: RefineRouteModalProps) {
  const [instruction, setInstruction] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  if (!isOpen) return null;

  const quickPrompts = [
    'Make the pace slower and add more rest time',
    'Focus more on authentic local food and street markets',
    'Prioritize scenic photography and outdoor viewpoints',
    'Day 2 has too much walking, condense transit distance',
  ];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!instruction.trim() || isSubmitting) return;

    setIsSubmitting(true);
    setErrorMsg(null);
    setFeedback(null);

    try {
      const res = await apiFetch(`/api/v1/itineraries/${itineraryId}/modify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ instruction: instruction.trim() }),
      });

      if (!res.ok) {
        throw new Error(`Modification failed (HTTP ${res.status})`);
      }

      const data = await res.json();
      setFeedback(data.appliedChangesSummary || 'Schedule updated and re-verified!');
      if (data.updatedItinerary) {
        onItineraryUpdated(data.updatedItinerary);
      }
      setInstruction('');
      setTimeout(() => {
        onClose();
        setFeedback(null);
      }, 1800);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to refine schedule. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div className="comic-panel w-full max-w-lg bg-white rounded-2xl p-6 sm:p-8 space-y-6 shadow-[6px_6px_0px_#18181B] relative">
        {/* Header */}
        <div className="flex items-center justify-between border-b-2 border-[#18181B] pb-4">
          <div className="flex items-center gap-2.5">
            <MessageSquare className="w-6 h-6 text-[#E11D48]" />
            <div>
              <span className="text-[10px] font-black uppercase tracking-widest text-[#E11D48]">
                RE-DISPATCH ENGINE
              </span>
              <h2 className="font-display font-black text-xl uppercase text-[#18181B]">
                Refine {destinationName} Route
              </h2>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="comic-btn-secondary p-1.5 rounded-lg"
            aria-label="Close"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Feedback Alert */}
        {feedback && (
          <div className="bg-emerald-50 border-2 border-[#18181B] rounded-xl p-3.5 flex items-start gap-2.5 text-xs font-bold text-emerald-900 shadow-[2px_2px_0px_#18181B]">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
            <div>
              <span className="font-black uppercase tracking-wider block text-[11px]">
                Schedule Re-Verified!
              </span>
              <span>{feedback}</span>
            </div>
          </div>
        )}

        {errorMsg && (
          <div className="bg-red-50 border-2 border-[#18181B] rounded-xl p-3 flex items-center gap-2 text-xs font-bold text-red-900">
            <AlertCircle className="w-4 h-4 text-[#E11D48] shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}

        {/* Quick Suggestion Chips */}
        <div className="space-y-1.5">
          <span className="text-[10px] font-black uppercase text-[#52525B] tracking-wider block">
            Quick Instructions:
          </span>
          <div className="flex flex-wrap gap-1.5">
            {quickPrompts.map((prompt, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => setInstruction(prompt)}
                className="text-left text-[11px] font-bold text-[#18181B] bg-[#FAF8F5] hover:bg-[#F4F2EE] border border-[#18181B] px-2.5 py-1 rounded-md transition-colors"
              >
                {prompt}
              </button>
            ))}
          </div>
        </div>

        {/* Input Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="text-xs font-black uppercase text-[#18181B] block mb-1.5">
              Natural Language Instruction
            </label>
            <textarea
              rows={3}
              placeholder="e.g. Day 2 is too packed. Swap the art museum for a street food tour and give us more rest in the afternoon."
              value={instruction}
              onChange={(e) => setInstruction(e.target.value)}
              required
              className="w-full bg-[#FAF8F5] border-2 border-[#18181B] rounded-xl p-3 text-xs font-bold text-[#18181B] focus:outline-none focus:ring-2 focus:ring-[#E11D48] resize-none"
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting || !instruction.trim()}
            className="comic-btn-primary w-full py-3 rounded-xl text-xs font-black uppercase tracking-wider flex items-center justify-center gap-2 disabled:opacity-50"
          >
            {isSubmitting ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin" />
                <span>Engine Re-Calculating Route...</span>
              </>
            ) : (
              <>
                <Send className="w-4 h-4" />
                <span>Apply Revisions to Schedule &rarr;</span>
              </>
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
