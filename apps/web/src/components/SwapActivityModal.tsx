'use client';

import React, { useEffect, useRef, useState } from 'react';
import {
  X,
  Shuffle,
  Search,
  RefreshCw,
  MapPin,
  ArrowRight,
  CircleSlash
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
  /** Optional bias so the search prefers venues near the stop being replaced. */
  near?: { latitude: number; longitude: number };
  onItineraryUpdated: (newItinerary: any) => void;
}

interface PlaceResult {
  id: string;
  name: string;
  formattedAddress?: string;
  location?: { latitude: number; longitude: number };
  types?: string[];
  openingHoursEstimated?: boolean;
}

/** Does the rebuilt day actually contain the venue the traveller picked? */
function dayContainsVenue(updatedItinerary: any, dayIndex: number, venueName: string): boolean {
  const days = updatedItinerary?.days;
  if (!Array.isArray(days)) return false;
  const day = days.find((d: any) => d?.dayIndex === dayIndex) || days[0];
  const activities = day?.activities || [];
  const wanted = venueName.trim().toLowerCase();
  return activities.some((a: any) =>
    String(a?.title || a?.name || '')
      .toLowerCase()
      .includes(wanted)
  );
}

/** The address usually repeats the venue name, so show the street part only. */
function streetPart(place: PlaceResult): string {
  const address = place.formattedAddress || '';
  if (!address) return '';
  const trimmed = address.replace(new RegExp(`^${place.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*,?\\s*`), '');
  return trimmed || address;
}

export default function SwapActivityModal({
  isOpen,
  onClose,
  activity,
  itineraryId,
  destinationName,
  near,
  onItineraryUpdated,
}: SwapActivityModalProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PlaceResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [submittingId, setSubmittingId] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [substituted, setSubstituted] = useState<{ asked: string; landed: string[] } | null>(null);
  const requestRef = useRef(0);

  // Reset the search every time the modal opens for a new stop.
  useEffect(() => {
    if (!isOpen) return;
    setQuery('');
    setResults([]);
    setSearchError(null);
    setErrorMsg(null);
    setSubmittingId(null);
    setSubstituted(null);
  }, [isOpen, activity?.id]);

  // Live OpenStreetMap search, debounced. Nothing here is prefilled or invented: an empty
  // result means OpenStreetMap has no match for what was typed.
  useEffect(() => {
    if (!isOpen) return;
    const term = query.trim();
    if (term.length < 2) {
      setResults([]);
      setSearchError(null);
      setIsSearching(false);
      return;
    }

    const requestId = requestRef.current + 1;
    requestRef.current = requestId;
    setIsSearching(true);
    setSearchError(null);

    const timer = setTimeout(async () => {
      try {
        const params = new URLSearchParams({ q: `${term} ${destinationName}`.trim() });
        if (near) {
          params.set('lat', String(near.latitude));
          params.set('lng', String(near.longitude));
        }
        const res = await apiFetch(`/api/v1/places/search?${params.toString()}`);
        if (requestRef.current !== requestId) return;
        if (!res.ok) {
          throw new Error(`Search failed (HTTP ${res.status})`);
        }
        const data = await res.json();
        if (requestRef.current !== requestId) return;
        setResults(Array.isArray(data) ? data.slice(0, 8) : []);
      } catch (err: any) {
        if (requestRef.current !== requestId) return;
        setResults([]);
        setSearchError(err.message || 'Could not reach the venue index.');
      } finally {
        if (requestRef.current === requestId) setIsSearching(false);
      }
    }, 350);

    return () => clearTimeout(timer);
  }, [query, isOpen, destinationName, near]);

  if (!isOpen || !activity) return null;

  const handleSwap = async (chosenName: string, result?: PlaceResult) => {
    const targetVenue = chosenName.trim();
    if (!targetVenue || submittingId) return;

    setSubmittingId(result?.id || 'manual');
    setErrorMsg(null);
    setSubstituted(null);

    // Name the venue exactly and hand over its coordinates, so the planner is not guessing which
    // place was meant. The engine may still refuse to use it, which is reported rather than hidden.
    const where = result?.formattedAddress ? ` at ${result.formattedAddress}` : '';
    const coords = result?.location
      ? ` Its OpenStreetMap coordinates are ${result.location.latitude}, ${result.location.longitude}.`
      : '';
    const prompt =
      `On Day ${activity.dayIndex}, replace"${activity.title}" with a visit to the exact venue ` +
      `"${targetVenue}"${where} in ${destinationName}.${coords} Use that exact venue by name. ` +
      `Do not substitute a different venue. If it cannot be used, leave the day as it is and say so. ` +
      `Keep the schedule conflict-free and physically reachable.`;

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
      const updated = data.updatedItinerary;
      const applied = updated ? dayContainsVenue(updated, activity.dayIndex, targetVenue) : false;

      if (updated) {
        onItineraryUpdated(updated);
      }

      if (applied) {
        onClose();
        return;
      }

      // The engine rebuilt the day without the chosen venue. Say that plainly instead of closing
      // the modal as if the swap succeeded.
      const days = updated?.days || [];
      const day = days.find((d: any) => d?.dayIndex === activity.dayIndex) || days[0];
      const landed = (day?.activities || []).map((a: any) => a?.title || a?.name).filter(Boolean);
      setSubstituted({ asked: targetVenue, landed });
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to swap activity. Try another venue.');
    } finally {
      setSubmittingId(null);
    }
  };

  const isBusy = submittingId !== null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div className="comic-panel w-full max-w-md bg-white rounded-2xl p-6 space-y-5 relative max-h-[92vh] overflow-y-auto">
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
            className="comic-btn-secondary p-1 min-h-11 min-w-11 flex items-center justify-center rounded-lg"
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
              Timeslot: {activity.startTime} to {activity.endTime}
            </span>
          )}
        </div>

        {errorMsg && (
          <div className="bg-red-50 border-2 border-[#18181B] rounded-lg p-2.5 text-xs font-bold text-red-900">
            {errorMsg}
          </div>
        )}

        {substituted && (
          <div className="bg-amber-50 border-2 border-[#18181B] rounded-lg p-3 space-y-1.5">
            <span className="text-[10px] font-black uppercase tracking-wider text-amber-900 block">
              Venue not used
            </span>
            <p className="text-xs font-bold text-[#18181B] leading-snug">
              The engine rebuilt the day but did not include {substituted.asked}. The new version is
              saved, and you can restore the previous one from the trip's version history.
            </p>
            {substituted.landed.length > 0 && (
              <p className="text-[11px] text-[#52525B] font-medium leading-snug">
                Day {activity.dayIndex} now holds: {substituted.landed.join(', ')}
              </p>
            )}
          </div>
        )}

        {/* Live OpenStreetMap search */}
        <div className="space-y-2">
          <label
            htmlFor="swap-search"
            className="text-[11px] font-black uppercase text-[#18181B] block"
          >
            Search venues in {destinationName}
          </label>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#18181B]" />
            <input
              id="swap-search"
              type="text"
              autoComplete="off"
              placeholder="e.g. market, museum, viewpoint"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="w-full min-h-12 pl-9 pr-9 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg text-base sm:text-sm font-bold text-[#18181B] focus:outline-none focus:ring-2 focus:ring-[#E11D48] focus:bg-white"
            />
            {isSearching && (
              <RefreshCw className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#E11D48] animate-spin" />
            )}
          </div>

          {searchError && (
            <p className="text-[11px] font-bold text-red-800">{searchError}</p>
          )}

          {!isSearching && !searchError && query.trim().length >= 2 && results.length === 0 && (
            <div className="flex items-center gap-2 p-2.5 border-2 border-[#18181B] rounded-lg bg-[#FAF8F5]">
              <CircleSlash className="w-4 h-4 text-[#52525B] shrink-0" />
              <span className="text-[11px] font-bold text-[#52525B]">
                No OpenStreetMap venue matches that search. Try a different word, or type the
                name in full below.
              </span>
            </div>
          )}

          {results.length > 0 && (
            <ul className="space-y-2 max-h-72 overflow-y-auto pr-0.5">
              {results.map((place) => {
                const street = streetPart(place);
                const isSubmitting = submittingId === place.id;
                return (
                  <li key={place.id}>
                    <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => handleSwap(place.name, place)}
                      className="w-full min-h-11 text-left p-3 rounded-lg border-2 border-[#18181B] bg-white hover:bg-[#FAF8F5] flex items-start justify-between gap-3 transition-all disabled:opacity-50"
                    >
                      <span className="flex-1 min-w-0">
                        <span className="font-black text-xs text-[#18181B] block leading-snug break-words">
                          {place.name}
                        </span>
                        {street && (
                          <span className="text-[11px] text-[#52525B] font-medium block mt-0.5 leading-snug break-words">
                            {street}
                          </span>
                        )}
                      </span>
                      {isSubmitting ? (
                        <RefreshCw className="w-4 h-4 text-[#E11D48] animate-spin shrink-0 mt-0.5" />
                      ) : (
                        <ArrowRight className="w-4 h-4 text-[#E11D48] shrink-0 mt-0.5" />
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}

          <p className="text-[10px] font-bold uppercase tracking-wider text-[#52525B]">
            Source: OpenStreetMap // coordinates and hours only, no stock imagery
          </p>
        </div>

        {/* Manual entry fallback */}
        <div className="space-y-1.5 pt-1 border-t-2 border-[#18181B]">
          <label className="text-[11px] font-black uppercase text-[#18181B] block pt-3">
            Or name the venue yourself
          </label>
          <form
            className="flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              handleSwap(query);
            }}
          >
            <input
              type="text"
              placeholder="exact venue name"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="flex-1 min-h-12 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg px-3 text-base sm:text-sm font-bold text-[#18181B] focus:outline-none focus:ring-2 focus:ring-[#E11D48] focus:bg-white"
            />
            <button
              type="submit"
              disabled={isBusy || query.trim().length < 2}
              className="comic-btn-primary px-4 min-h-12 rounded-lg text-xs font-black uppercase disabled:opacity-50 flex items-center gap-2"
            >
              {submittingId === 'manual' ? (
                <RefreshCw className="w-4 h-4 animate-spin" />
              ) : (
                <MapPin className="w-4 h-4" />
              )}
              Swap
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
