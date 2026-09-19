'use client';

import React, { useState } from 'react';
import {
  X,
  RefreshCw,
  CloudRain,
  Clock,
  Coffee,
  DollarSign,
  PlusCircle,
  Sparkles,
  AlertCircle,
  CheckCircle2,
  ArrowRight,
  ShieldCheck
} from 'lucide-react';
import { apiFetch } from '@/lib/api-client';

interface ReplanRouteModalProps {
  isOpen: boolean;
  onClose: () => void;
  tripId: string;
  destinationName: string;
  currentVersion: number;
  totalDays: number;
  activeDayIndex: number;
  onReplanComplete: (result: any) => void;
}

interface IntentOption {
  id: string;
  label: string;
  subtext: string;
  icon: React.ComponentType<{ className?: string }>;
}

const INTENT_OPTIONS: IntentOption[] = [
  {
    id: 'rain',
    label: 'Rain Advisory',
    subtext: 'Swap outdoor parks for indoor museums and galleries',
    icon: CloudRain,
  },
  {
    id: 'running-late',
    label: 'Running Late',
    subtext: 'Shift remaining stops forward by 45 minutes',
    icon: Clock,
  },
  {
    id: 'tired',
    label: 'Feeling Tired',
    subtext: 'Trim low-priority stops and add breathing room',
    icon: Coffee,
  },
  {
    id: 'budget-cut',
    label: 'Trim Budget',
    subtext: 'Zero out paid admissions for free public attractions',
    icon: DollarSign,
  },
  {
    id: 'add-stop',
    label: 'Add Bonus Stop',
    subtext: 'Inject a nearby verified stop or scenic café',
    icon: PlusCircle,
  },
];

export default function ReplanRouteModal({
  isOpen,
  onClose,
  tripId,
  destinationName,
  currentVersion,
  totalDays,
  activeDayIndex,
  onReplanComplete,
}: ReplanRouteModalProps) {
  const [selectedIntent, setSelectedIntent] = useState<string>('rain');
  const [selectedDay, setSelectedDay] = useState<number>(activeDayIndex || 1);
  const [applyToEntireTrip, setApplyToEntireTrip] = useState<boolean>(false);
  const [freeText, setFreeText] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [replanResult, setReplanResult] = useState<any | null>(null);

  if (!isOpen) return null;

  const handleExecuteReplan = async () => {
    setIsSubmitting(true);
    setErrorMsg(null);
    setReplanResult(null);

    try {
      const payload: Record<string, any> = {
        intent: selectedIntent,
        freeText: freeText.trim() || undefined,
      };

      if (!applyToEntireTrip) {
        payload.dayIndex = selectedDay;
      }

      const res = await apiFetch(`/api/v1/trips/${tripId}/replan`, {
        method: 'POST',
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => null);
        throw new Error(
          errorData?.message || `Replanning failed with HTTP ${res.status}`
        );
      }

      const data = await res.json();
      setReplanResult(data);
    } catch (err: any) {
      console.error('Replan error:', err);
      setErrorMsg(err.message || 'Unable to replan schedule. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleApplyNewVersion = () => {
    if (replanResult) {
      onReplanComplete(replanResult);
      handleClose();
    }
  };

  const handleClose = () => {
    setReplanResult(null);
    setErrorMsg(null);
    setFreeText('');
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div className="comic-panel w-full max-w-xl bg-white rounded-2xl p-6 sm:p-8 space-y-6 shadow-[6px_6px_0px_#18181B] relative max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b-2 border-[#18181B] pb-4">
          <div className="flex items-center gap-2.5">
            <RefreshCw className="w-6 h-6 text-[#E11D48]" />
            <div>
              <span className="text-[10px] font-black uppercase tracking-widest text-[#E11D48]">
                DISPATCH ADAPTATION ENGINE
              </span>
              <h2 className="font-display font-black text-xl uppercase text-[#18181B]">
                One-Tap Replan Route
              </h2>
            </div>
          </div>

          <button
            type="button"
            onClick={handleClose}
            className="comic-btn-secondary p-2 min-h-11 min-w-11 rounded-lg text-xs font-black flex items-center justify-center"
            aria-label="Close replan modal"
          >
            <X className="w-5 h-5 text-[#18181B]" />
          </button>
        </div>

        {/* Current status bar */}
        <div className="flex flex-wrap items-center justify-between gap-2 p-3 bg-[#FAF8F5] border-2 border-[#18181B] rounded-xl text-xs">
          <div className="flex items-center gap-2">
            <span className="font-bold text-[#52525B]">Destination:</span>
            <span className="font-black text-[#18181B] uppercase">{destinationName}</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="font-bold text-[#52525B]">Current Version:</span>
            <span className="px-2 py-0.5 bg-[#18181B] text-white font-black text-[11px] rounded">
              v{currentVersion}
            </span>
          </div>
        </div>

        {/* Replan Success View */}
        {replanResult ? (
          <div className="space-y-4 py-2">
            <div className="p-4 bg-emerald-50 border-2 border-emerald-700 rounded-xl space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-5 h-5 text-emerald-700" />
                  <span className="font-display font-black text-sm uppercase text-emerald-900">
                    Replan Generated Successfully
                  </span>
                </div>
                <span
                  className={`px-2.5 py-0.5 text-xs font-black rounded border-2 border-[#18181B] ${
                    replanResult.status === 'VERIFIED'
                      ? 'bg-emerald-200 text-emerald-950'
                      : 'bg-amber-200 text-amber-950'
                  }`}
                >
                  {replanResult.status}
                </span>
              </div>

              <p className="text-xs text-emerald-950 leading-relaxed font-medium">
                {replanResult.summary}
              </p>

              <div className="grid grid-cols-2 gap-3 pt-2 text-xs border-t border-emerald-200">
                <div>
                  <span className="text-[#52525B] block font-bold">Version Created:</span>
                  <span className="font-black text-[#18181B]">
                    v{replanResult.newVersion} (from v{replanResult.previousVersion})
                  </span>
                </div>
                <div>
                  <span className="text-[#52525B] block font-bold">Modified Stops:</span>
                  <span className="font-black text-[#18181B]">
                    {replanResult.changedActivitiesCount} activity slot(s)
                  </span>
                </div>
              </div>
            </div>

            <div className="flex items-center gap-3 pt-2">
              <button
                type="button"
                onClick={handleApplyNewVersion}
                className="comic-btn-primary flex-1 min-h-11 py-3 rounded-xl text-xs font-black uppercase tracking-wider flex items-center justify-center gap-2 shadow-[3px_3px_0px_#18181B]"
              >
                <span>Switch to Itinerary v{replanResult.newVersion}</span>
                <ArrowRight className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={() => setReplanResult(null)}
                className="comic-btn-secondary min-h-11 px-4 py-3 rounded-xl text-xs font-black uppercase"
              >
                Adjust Options
              </button>
            </div>
          </div>
        ) : (
          /* Configuration View */
          <div className="space-y-5">
            {/* Scope / Day selector */}
            <div className="space-y-2">
              <label className="text-xs font-black uppercase tracking-wider text-[#18181B] block">
                1. Select Replan Scope
              </label>
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => setApplyToEntireTrip(true)}
                  className={`min-h-11 px-3 py-2 rounded-lg text-xs font-black uppercase border-2 border-[#18181B] transition-colors ${
                    applyToEntireTrip
                      ? 'bg-[#18181B] text-white'
                      : 'bg-[#FAF8F5] text-[#18181B] hover:bg-zinc-100'
                  }`}
                >
                  All Days
                </button>
                {Array.from({ length: totalDays || 1 }, (_, i) => i + 1).map((dayNum) => (
                  <button
                    key={dayNum}
                    type="button"
                    onClick={() => {
                      setApplyToEntireTrip(false);
                      setSelectedDay(dayNum);
                    }}
                    className={`min-h-11 px-3 py-2 rounded-lg text-xs font-black uppercase border-2 border-[#18181B] transition-colors ${
                      !applyToEntireTrip && selectedDay === dayNum
                        ? 'bg-[#E11D48] text-white'
                        : 'bg-[#FAF8F5] text-[#18181B] hover:bg-zinc-100'
                    }`}
                  >
                    Day {dayNum}
                  </button>
                ))}
              </div>
            </div>

            {/* 1-Tap Intent Selection */}
            <div className="space-y-2">
              <label className="text-xs font-black uppercase tracking-wider text-[#18181B] block">
                2. Choose Adaptive Intent
              </label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {INTENT_OPTIONS.map((opt) => {
                  const Icon = opt.icon;
                  const isSelected = selectedIntent === opt.id;
                  return (
                    <button
                      key={opt.id}
                      type="button"
                      onClick={() => setSelectedIntent(opt.id)}
                      className={`min-h-11 p-3 rounded-xl border-2 border-[#18181B] text-left transition-all flex items-start gap-2.5 ${
                        isSelected
                          ? 'bg-[#18181B] text-white shadow-[2px_2px_0px_#E11D48]'
                          : 'bg-[#FAF8F5] text-[#18181B] hover:bg-zinc-100'
                      }`}
                    >
                      <Icon
                        className={`w-5 h-5 shrink-0 mt-0.5 ${
                          isSelected ? 'text-[#E11D48]' : 'text-[#52525B]'
                        }`}
                      />
                      <div>
                        <div className="font-display font-black text-xs uppercase tracking-tight">
                          {opt.label}
                        </div>
                        <div
                          className={`text-[11px] leading-snug mt-0.5 ${
                            isSelected ? 'text-zinc-300' : 'text-[#52525B]'
                          }`}
                        >
                          {opt.subtext}
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Optional Custom Instructions */}
            <div className="space-y-2">
              <label className="text-xs font-black uppercase tracking-wider text-[#18181B] block">
                3. Additional Dispatch Instructions (Optional)
              </label>
              <textarea
                value={freeText}
                onChange={(e) => setFreeText(e.target.value)}
                placeholder="Example: Need wheelchair accessibility for afternoon stop, or prefer quiet tea houses over noisy markets."
                rows={2}
                className="w-full text-xs p-3 rounded-xl border-2 border-[#18181B] bg-[#FAF8F5] focus:bg-white focus:outline-hidden focus:border-[#E11D48] text-[#18181B] placeholder:text-zinc-400 resize-none font-medium"
              />
            </div>

            {/* Error banner */}
            {errorMsg && (
              <div className="p-3 bg-red-50 border-2 border-red-600 rounded-xl flex items-center gap-2 text-xs text-red-700 font-bold">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            {/* Action buttons */}
            <div className="pt-2">
              <button
                type="button"
                disabled={isSubmitting}
                onClick={handleExecuteReplan}
                className="comic-btn-primary w-full min-h-11 py-3 rounded-xl text-xs font-black uppercase tracking-wider flex items-center justify-center gap-2 shadow-[3px_3px_0px_#18181B] disabled:opacity-50"
              >
                {isSubmitting ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    <span>Calculating OSRM Routes and Verifying...</span>
                  </>
                ) : (
                  <>
                    <Sparkles className="w-4 h-4" />
                    <span>Generate Replan (Creates v{currentVersion + 1})</span>
                  </>
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
