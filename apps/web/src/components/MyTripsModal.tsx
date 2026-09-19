'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  X,
  MapPin,
  Calendar,
  Users,
  ArrowRight,
  Trash2,
  Briefcase,
  Compass,
  Flame
} from 'lucide-react';
import { getSavedTrips, removeSavedTrip, SavedTrip } from '@/lib/saved-trips';

interface MyTripsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function MyTripsModal({ isOpen, onClose }: MyTripsModalProps) {
  const [trips, setTrips] = useState<SavedTrip[]>([]);

  useEffect(() => {
    if (isOpen) {
      setTrips(getSavedTrips());
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleDelete = (id: string, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    const updated = removeSavedTrip(id);
    setTrips(updated);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div className="comic-panel w-full max-w-xl bg-white rounded-2xl p-6 sm:p-8 space-y-6 max-h-[85vh] flex flex-col shadow-[6px_6px_0px_#18181B] relative">
        {/* Header */}
        <div className="flex items-center justify-between border-b-2 border-[#18181B] pb-4 shrink-0">
          <div className="flex items-center gap-2.5">
            <Briefcase className="w-6 h-6 text-[#E11D48]" />
            <div>
              <span className="text-[10px] font-black uppercase tracking-widest text-[#E11D48]">
                DISPATCH ARCHIVES
              </span>
              <h2 className="font-display font-black text-xl uppercase text-[#18181B]">
                Your Saved Field Runs ({trips.length})
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

        {/* Trips List */}
        <div className="overflow-y-auto space-y-3 flex-1 pr-1">
          {trips.length === 0 ? (
            <div className="text-center py-10 space-y-3">
              <Compass className="w-10 h-10 text-[#52525B] mx-auto opacity-50" />
              <p className="text-xs font-bold text-[#52525B] max-w-xs mx-auto">
                No archived itineraries found on this device. Draft your first run from the planner!
              </p>
              <Link
                href="/planner"
                onClick={onClose}
                className="comic-btn-primary px-5 py-2.5 rounded-lg text-xs font-black uppercase inline-flex items-center gap-1.5"
              >
                <Flame className="w-3.5 h-3.5" />
                <span>Open Planner</span>
              </Link>
            </div>
          ) : (
            trips.map((t) => (
              <Link
                key={t.id}
                href={`/trip/${t.id}`}
                onClick={onClose}
                className="block p-4 rounded-xl border-2 border-[#18181B] bg-[#FAF8F5] hover:bg-white shadow-[2px_2px_0px_#18181B] transition-all group"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="comic-tag bg-[#E11D48] text-white text-[9px] py-0.5 px-1.5">
                        ID: {t.id.slice(0, 8)}
                      </span>
                      <h3 className="font-display font-black text-base uppercase text-[#18181B] group-hover:text-[#E11D48] transition-colors">
                        {t.destination}
                      </h3>
                    </div>

                    <div className="flex flex-wrap items-center gap-3 text-[11px] font-bold text-[#52525B] mt-2">
                      {t.startDate && (
                        <span className="flex items-center gap-1">
                          <Calendar className="w-3 h-3 text-[#18181B]" />
                          {new Date(t.startDate).toLocaleDateString()}
                        </span>
                      )}
                      {t.travelersCount && (
                        <span className="flex items-center gap-1">
                          <Users className="w-3 h-3 text-[#18181B]" />
                          {t.travelersCount} Travelers
                        </span>
                      )}
                      {t.estimatedCost && (
                        <span className="font-black text-[#18181B]">
                          ~{t.estimatedCost} {t.currency}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={(e) => handleDelete(t.id, e)}
                      className="p-1.5 rounded text-[#52525B] hover:text-[#E11D48] hover:bg-red-50 transition-colors"
                      title="Remove from history"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                    <div className="w-7 h-7 rounded-lg bg-white border border-[#18181B] flex items-center justify-center group-hover:bg-[#E11D48] group-hover:text-white transition-colors">
                      <ArrowRight className="w-3.5 h-3.5" />
                    </div>
                  </div>
                </div>
              </Link>
            ))
          )}
        </div>

        {/* Footer */}
        {trips.length > 0 && (
          <div className="border-t-2 border-[#18181B] pt-4 flex items-center justify-between shrink-0">
            <span className="text-[11px] font-bold text-[#52525B]">
              Trips cached in device memory
            </span>
            <Link
              href="/planner"
              onClick={onClose}
              className="comic-btn-primary px-4 py-2 rounded-lg text-xs font-black uppercase"
            >
              Draft New Route &rarr;
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
