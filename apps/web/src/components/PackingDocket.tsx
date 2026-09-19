'use client';

import React, { useState, useEffect } from 'react';
import {
  CheckSquare,
  Square,
  Luggage,
  ShieldAlert,
  Zap,
  RotateCcw,
  CheckCircle2,
  Sparkles
} from 'lucide-react';

interface PackingDocketProps {
  tripId: string;
  destination: string;
  isRainy?: boolean;
  /** Trip pace. High pace adds hydration gear because the days run long. */
  pace?: string;
}

interface PackingItem {
  id: string;
  category: 'DOCS' | 'CLIMATE' | 'TRANSIT' | 'HEALTH';
  text: string;
  recommendedReason?: string;
}

/**
 * Plug and voltage by destination. Defaults to a universal adapter when the destination
 * is not recognised rather than guessing a standard.
 */
function adapterFor(destination: string): { text: string; region: string | null } {
  const d = destination.toLowerCase();
  const match = (pattern: RegExp) => pattern.test(d);

  if (match(/japan|tokyo|kyoto|osaka|sapporo/)) {
    return { text: 'Type A Power Adapter (100V, 50/60Hz)', region: 'Japan' };
  }
  if (match(/united kingdom|england|london|scotland|edinburgh|wales|ireland|dublin/)) {
    return { text: 'Type G Power Adapter (230V)', region: 'UK and Ireland' };
  }
  if (match(/india|delhi|mumbai|bangalore|bengaluru|jaipur|goa|chennai/)) {
    return { text: 'Type C or D Power Adapter (230V)', region: 'India' };
  }
  if (match(/australia|sydney|melbourne|brisbane|new zealand|auckland/)) {
    return { text: 'Type I Power Adapter (230V)', region: 'Australia and New Zealand' };
  }
  if (match(/united states|usa|new york|san francisco|chicago|boston|canada|toronto|vancouver/)) {
    return { text: 'Type A Power Adapter (120V)', region: 'North America' };
  }
  if (match(/united arab emirates|dubai|abu dhabi|qatar|doha|saudi/)) {
    return { text: 'Type G Power Adapter (230V)', region: 'Gulf' };
  }
  if (match(/china|beijing|shanghai|hong kong|singapore|malaysia|kuala lumpur/)) {
    return { text: 'Type A or I Power Adapter (220V)', region: 'East and Southeast Asia' };
  }
  if (
    match(
      /france|paris|italy|rome|spain|madrid|barcelona|germany|berlin|netherlands|amsterdam|portugal|lisbon|porto|greece|athens|austria|vienna|belgium|brussels|croatia|split|finland|helsinki|czech|prague|hungary|budapest|poland|warsaw|norway|oslo|sweden|stockholm|denmark|copenhagen|switzerland|zurich/
    )
  ) {
    return { text: 'Type C or E Power Adapter (230V)', region: 'Europe' };
  }
  return { text: 'Universal Travel Power Adapter', region: null };
}

export default function PackingDocket({
  tripId,
  destination,
  isRainy = false,
  pace,
}: PackingDocketProps) {
  const isJapan = /japan|tokyo|kyoto|osaka/i.test(destination);
  const adapter = adapterFor(destination);
  const isHighPace = String(pace || '').trim().toUpperCase() === 'FAST';

  const defaultItems: PackingItem[] = [
    // Documents & Tech
    {
      id: 'doc-pass',
      category: 'DOCS',
      text: 'Passport / Travel ID (valid 6+ months)',
      recommendedReason: 'Essential border clearance',
    },
    {
      id: 'doc-esim',
      category: 'DOCS',
      text: 'eSIM or International Data Roaming',
      recommendedReason: 'For real-time maps & navigation',
    },
    {
      id: 'doc-plug',
      category: 'DOCS',
      text: adapter.text,
      recommendedReason: adapter.region
        ? `Mains standard for ${adapter.region}`
        : 'Destination not recognised, bring the universal adapter',
    },
    {
      id: 'doc-bank',
      category: 'DOCS',
      text: '2x Travel Debit/Credit Cards (No Foreign Tx Fees)',
      recommendedReason: 'Cash reserve backup',
    },

    // Climate Gear
    {
      id: 'clim-rain',
      category: 'CLIMATE',
      text: isRainy ? 'Windproof Umbrella and Rain Shell' : 'Packable Lightweight Umbrella',
      recommendedReason: isRainy
        ? 'Rain is in the forecast for these dates'
        : 'Precautionary weather defense',
    },
    ...(isRainy
      ? [
          {
            id: 'clim-poncho',
            category: 'CLIMATE' as const,
            text: 'Compact Rain Poncho',
            recommendedReason: 'Packs flat for wet walking days, keeps the daypack dry',
          },
        ]
      : []),
    {
      id: 'clim-sun',
      category: 'CLIMATE',
      text: 'UV Sunglasses & Broad-Spectrum Sunscreen',
      recommendedReason: 'All-day outdoor foot circuits',
    },

    // Transit & Urban Gear
    {
      id: 'tran-shoes',
      category: 'TRANSIT',
      text: 'Broken-in Walk Shoes (15,000+ daily steps)',
      recommendedReason: 'Dense itinerary walking defense',
    },
    {
      id: 'tran-pbank',
      category: 'TRANSIT',
      text: '10,000mAh Portable Power Bank',
      recommendedReason: 'For continuous navigation & camera use',
    },
    {
      id: 'tran-pack',
      category: 'TRANSIT',
      text: 'Compact Daypack or Crossbody Bag',
      recommendedReason: 'Daily excursions & field purchases',
    },
    ...(isHighPace
      ? [
          {
            id: 'tran-hydro',
            category: 'TRANSIT' as const,
            text: '1L Insulated Water Bottle',
            recommendedReason: 'All-out pace means long stretches between refills',
          },
          {
            id: 'hlth-hydro',
            category: 'HEALTH' as const,
            text: 'Electrolyte Sachets (2 per active day)',
            recommendedReason: 'Hydration for back-to-back high pace days',
          },
        ]
      : []),

    // Health & Essentials
    {
      id: 'hlth-blist',
      category: 'HEALTH',
      text: 'Hydrocolloid Blister Bandages & First Aid',
      recommendedReason: 'Life-saver on long walking days',
    },
    {
      id: 'hlth-cash',
      category: 'HEALTH',
      text: isJapan
        ? '10,000 - 20,000 JPY in Cash Notes'
        : 'Emergency Local Currency Notes',
      recommendedReason: 'Local ticket machines & small shops',
    },
    {
      id: 'hlth-meds',
      category: 'HEALTH',
      text: 'Personal Medications & Electrolyte Packs',
      recommendedReason: 'Maintain peak energy',
    },
  ];

  const [checkedIds, setCheckedIds] = useState<string[]>([]);

  useEffect(() => {
    try {
      const stored = localStorage.getItem(`trippin_packing_${tripId}`);
      if (stored) {
        setCheckedIds(JSON.parse(stored));
      }
    } catch {
      // Storage access failover
    }
  }, [tripId]);

  const toggleItem = (id: string) => {
    setCheckedIds((prev) => {
      const next = prev.includes(id) ? prev.filter((i) => i !== id) : [...prev, id];
      try {
        localStorage.setItem(`trippin_packing_${tripId}`, JSON.stringify(next));
      } catch {}
      return next;
    });
  };

  const handleReset = () => {
    setCheckedIds([]);
    try {
      localStorage.removeItem(`trippin_packing_${tripId}`);
    } catch {}
  };

  const total = defaultItems.length;
  const completed = checkedIds.length;
  const progressPct = Math.round((completed / total) * 100);

  const categories = [
    { key: 'DOCS', label: 'Dispatch Documents & Tech' },
    { key: 'CLIMATE', label: 'Climate & Weather Defense' },
    { key: 'TRANSIT', label: 'Urban Walking & Transit' },
    { key: 'HEALTH', label: 'Field Essentials & Cash' },
  ];

  return (
    <div className="comic-panel p-6 sm:p-8 rounded-2xl bg-white space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b-2 border-[#18181B] pb-4">
        <div className="flex items-center gap-2.5">
          <Luggage className="w-6 h-6 text-[#E11D48]" />
          <div>
            <span className="text-[10px] font-black uppercase tracking-widest text-[#E11D48]">
              EQUIPMENT BRIEFING
            </span>
            <h3 className="font-display font-black text-xl uppercase text-[#18181B]">
              Field Kit Docket // {destination}
            </h3>
            {(isRainy || isHighPace) && (
              <div className="flex flex-wrap gap-1.5 mt-2">
                {isRainy && (
                  <span className="text-[10px] font-black uppercase tracking-wider bg-[#E11D48] text-white border-2 border-[#18181B] px-2 py-0.5 rounded-xs">
                    Rain gear added
                  </span>
                )}
                {isHighPace && (
                  <span className="text-[10px] font-black uppercase tracking-wider bg-[#18181B] text-white border-2 border-[#18181B] px-2 py-0.5 rounded-xs">
                    Hydration added
                  </span>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="flex items-center gap-3">
          <span className="text-xs font-black text-[#18181B]">
            {completed}/{total} Packed ({progressPct}%)
          </span>
          <button
            type="button"
            onClick={handleReset}
            className="comic-btn-secondary p-1.5 rounded-lg text-xs"
            title="Reset Checklist"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="w-full bg-[#FAF8F5] border-2 border-[#18181B] rounded-full h-3.5 overflow-hidden p-0.5">
        <div
          className="bg-[#E11D48] h-full rounded-full transition-all duration-300"
          style={{ width: `${progressPct}%` }}
        />
      </div>

      {/* Items Grouped by Category */}
      <div className="space-y-6">
        {categories.map((cat) => {
          const items = defaultItems.filter((i) => i.category === cat.key);
          return (
            <div key={cat.key} className="space-y-2">
              <h4 className="font-display font-black text-xs uppercase tracking-wider text-[#52525B]">
                {cat.label}
              </h4>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {items.map((item) => {
                  const isChecked = checkedIds.includes(item.id);
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => toggleItem(item.id)}
                      className={`text-left p-3 rounded-xl border-2 border-[#18181B] flex items-start gap-2.5 transition-all ${
                        isChecked
                          ? 'bg-emerald-50 opacity-70 shadow-none'
                          : 'bg-[#FAF8F5]  hover:bg-white'
                      }`}
                    >
                      {isChecked ? (
                        <CheckSquare className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                      ) : (
                        <Square className="w-4 h-4 text-[#18181B] shrink-0 mt-0.5" />
                      )}
                      <div>
                        <span
                          className={`text-xs font-black block text-[#18181B] ${
                            isChecked ? 'line-through text-[#52525B]' : ''
                          }`}
                        >
                          {item.text}
                        </span>
                        {item.recommendedReason && (
                          <span className="text-[10px] text-[#52525B] font-medium block mt-0.5">
                            {item.recommendedReason}
                          </span>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
