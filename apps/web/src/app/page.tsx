'use client';

import React from 'react';
import Link from 'next/link';
import {
  Compass,
  MapPin,
  Search,
  Calendar,
  ArrowRight,
  ShieldCheck,
  Navigation,
  Clock,
  Zap,
  Flame
} from 'lucide-react';

export default function WebHomePage() {
  const destinations = [
    {
      code: 'TYO',
      name: 'Tokyo',
      country: 'Japan',
      tagline: 'Cyberpunk Temples & Alley Ramen',
      issue: 'VOL. 01',
      rating: 4.9,
      image: 'https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=800',
      tag: 'MEGA-CITY RUN'
    },
    {
      code: 'PAR',
      name: 'Paris',
      country: 'France',
      tagline: 'Impressionist Vaults & Sidewalk Cafes',
      issue: 'VOL. 02',
      rating: 4.9,
      image: 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=800',
      tag: 'CULTURE CIRCUIT'
    },
    {
      code: 'ROM',
      name: 'Rome',
      country: 'Italy',
      tagline: 'Ancient Amphitheaters & Sunset Piazzas',
      issue: 'VOL. 03',
      rating: 4.8,
      image: 'https://images.unsplash.com/photo-1552832230-c0197dd311b5?w=800',
      tag: 'HISTORIC CORE'
    },
    {
      code: 'LDN',
      name: 'London',
      country: 'United Kingdom',
      tagline: 'West End Lights & Riverbank Fog',
      issue: 'VOL. 04',
      rating: 4.8,
      image: 'https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?w=800',
      tag: 'METROPOLIS'
    }
  ];

  return (
    <div className="min-h-screen bg-[#FAF8F5] text-[#18181B] pb-16">
      {/* 1. Graphic Novel Header */}
      <header className="sticky top-0 z-40 bg-[#FAF8F5] border-b-[2.5px] border-[#18181B]">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 h-18 flex items-center justify-between">
          <Link href="/" className="flex items-center gap-2 group">
            <div className="w-10 h-10 bg-[#E11D48] text-white border-2 border-[#18181B] shadow-[2px_2px_0px_#18181B] flex items-center justify-center font-black text-xl tracking-tighter">
              T!
            </div>
            <div className="flex flex-col leading-none">
              <span className="font-display font-black text-2xl tracking-tighter uppercase text-[#18181B]">
                TRIPPIN<span className="text-[#E11D48]">&apos;</span>
              </span>
              <span className="text-[9px] font-extrabold uppercase tracking-widest text-[#52525B]">
                Field Guide // v1.0
              </span>
            </div>
          </Link>

          <nav className="flex items-center gap-3 sm:gap-6">
            <Link
              href="/planner"
              className="comic-btn-primary px-4 sm:px-6 py-2.5 rounded-lg text-xs sm:text-sm font-black uppercase tracking-wider flex items-center gap-2"
            >
              <span>Build Route</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </nav>
        </div>
      </header>

      {/* 2. Hero Comic Panel */}
      <main className="max-w-5xl mx-auto px-4 sm:px-6 pt-10 space-y-12">
        <section className="relative comic-panel p-6 sm:p-12 rounded-2xl bg-white overflow-hidden">
          {/* Halftone backdrop accent */}
          <div className="absolute top-0 right-0 w-64 h-64 comic-halftone opacity-40 pointer-events-none" />

          {/* Top Label Tag */}
          <div className="inline-flex items-center gap-2 bg-[#E11D48] text-white comic-tag px-3 py-1 rounded-sm text-xs font-black uppercase mb-6">
            <Flame className="w-3.5 h-3.5" />
            <span>Issue #01 — Real-World Transit Engine</span>
          </div>

          <div className="max-w-3xl space-y-6 relative z-10">
            <h1 className="font-display text-4xl sm:text-6xl font-black tracking-tight leading-[1.05] uppercase">
              NO HALLUCINATIONS. <br />
              <span className="text-[#E11D48] underline decoration-[#18181B] decoration-4 underline-offset-4">
                NO DEAD ENDS.
              </span>{' '}
              <br />
              JUST THE ROUTE.
            </h1>

            <p className="text-base sm:text-lg text-[#52525B] font-medium leading-relaxed max-w-xl">
              Most travel apps schedule impossible schedules and closed museums. Trippin&apos; checks
              real physical walking times, live operating hours, and local weather before you take a single step.
            </p>

            <div className="pt-2 flex flex-wrap items-center gap-4">
              <Link
                href="/planner"
                className="comic-btn-primary px-8 py-4 rounded-xl text-sm sm:text-base font-black uppercase tracking-wider flex items-center gap-2.5"
              >
                <span>Draft Your Journey</span>
                <ArrowRight className="w-5 h-5" />
              </Link>
              <div className="flex items-center gap-2 text-xs font-bold text-[#52525B] px-3 py-2 border-2 border-[#18181B] rounded-lg bg-[#F4F2EE]">
                <ShieldCheck className="w-4 h-4 text-[#E11D48]" />
                <span>100% Conflict-Free Guarantee</span>
              </div>
            </div>
          </div>
        </section>

        {/* 3. The Three Laws (Comic Panels Grid) */}
        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 bg-[#E11D48] border-2 border-[#18181B]" />
              <h2 className="font-display text-lg font-black uppercase tracking-tight">
                The Ground Rules
              </h2>
            </div>
            <span className="text-xs font-bold uppercase tracking-widest text-[#52525B]">
              Physics-Checked
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <article className="comic-panel p-6 rounded-xl bg-white space-y-3">
              <div className="w-10 h-10 rounded-lg bg-[#FAF8F5] border-2 border-[#18181B] flex items-center justify-center font-black text-sm">
                01
              </div>
              <h3 className="font-display font-black text-base uppercase text-[#18181B]">
                Zero Overlaps
              </h3>
              <p className="text-xs text-[#52525B] leading-relaxed font-medium">
                Every reservation and stop has strict timestamp boundaries. No scheduling a 2-hour lunch in a 30-minute window.
              </p>
            </article>

            <article className="comic-panel p-6 rounded-xl bg-white space-y-3">
              <div className="w-10 h-10 rounded-lg bg-[#FAF8F5] border-2 border-[#18181B] flex items-center justify-center font-black text-sm">
                02
              </div>
              <h3 className="font-display font-black text-base uppercase text-[#18181B]">
                Verified Openings
              </h3>
              <p className="text-xs text-[#52525B] leading-relaxed font-medium">
                The Louvre is closed on Tuesdays. Tokyo fish markets shut early. Our engine validates venue hours against calendar days.
              </p>
            </article>

            <article className="comic-panel p-6 rounded-xl bg-white space-y-3">
              <div className="w-10 h-10 rounded-lg bg-[#FAF8F5] border-2 border-[#18181B] flex items-center justify-center font-black text-sm">
                03
              </div>
              <h3 className="font-display font-black text-base uppercase text-[#18181B]">
                Physical Transit
              </h3>
              <p className="text-xs text-[#52525B] leading-relaxed font-medium">
                Walking and metro travel times are calculated using real road networks, giving you enough buffer to actually explore.
              </p>
            </article>
          </div>
        </section>

        {/* 4. Curated Field Runs (Manga Issue Cards) */}
        <section className="space-y-6">
          <div className="flex items-center justify-between border-b-2 border-[#18181B] pb-3">
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 bg-[#E11D48] border-2 border-[#18181B]" />
              <h2 className="font-display text-xl font-black uppercase tracking-tight">
                Featured Field Runs
              </h2>
            </div>
            <span className="text-xs font-bold uppercase tracking-wider text-[#52525B]">
              Ready to Load
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {destinations.map((d) => (
              <div
                key={d.code}
                className="comic-panel rounded-xl overflow-hidden bg-white group hover:-translate-y-1 transition-transform duration-150 flex flex-col justify-between"
              >
                <div>
                  <div className="relative h-44 border-b-2 border-[#18181B] overflow-hidden">
                    <img
                      src={d.image}
                      alt={d.name}
                      className="w-full h-full object-cover grayscale contrast-125 group-hover:grayscale-0 group-hover:scale-105 transition-all duration-300"
                    />
                    <span className="absolute top-3 left-3 bg-[#E11D48] text-white comic-tag text-[10px] rounded-xs font-black">
                      {d.issue}
                    </span>
                    <span className="absolute bottom-3 right-3 bg-white text-[#18181B] comic-tag text-[10px] rounded-xs font-black">
                      {d.code}
                    </span>
                  </div>

                  <div className="p-4 space-y-2">
                    <div className="flex items-baseline justify-between">
                      <h3 className="font-display font-black text-xl uppercase tracking-tight">
                        {d.name}
                      </h3>
                      <span className="text-xs font-bold text-[#52525B]">{d.country}</span>
                    </div>
                    <p className="text-xs text-[#52525B] font-medium leading-normal">
                      {d.tagline}
                    </p>
                  </div>
                </div>

                <div className="p-4 pt-0">
                  <Link
                    href={`/planner?destination=${encodeURIComponent(d.name + ', ' + d.country)}`}
                    className="comic-btn-secondary w-full py-2.5 rounded-lg text-xs font-black uppercase tracking-wider flex items-center justify-center gap-1.5"
                  >
                    <span>Inspect Route</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* 5. Bottom Callout Banner */}
        <section className="comic-panel-red p-8 sm:p-10 rounded-2xl bg-[#18181B] text-white flex flex-col sm:flex-row items-center justify-between gap-6">
          <div className="space-y-2 text-center sm:text-left">
            <span className="text-xs font-extrabold tracking-widest text-[#E11D48] uppercase">
              Field Tested &amp; Certified
            </span>
            <h2 className="font-display text-2xl sm:text-3xl font-black uppercase tracking-tight">
              Ready to map your next run?
            </h2>
            <p className="text-xs sm:text-sm text-[#A1A1AA] max-w-md font-medium">
              Plug in your city, pick your dates, and get an immutable, conflict-free travel log in seconds.
            </p>
          </div>
          <Link
            href="/planner"
            className="comic-btn-primary px-8 py-4 rounded-xl text-sm font-black uppercase tracking-wider shrink-0"
          >
            Launch Planner &rarr;
          </Link>
        </section>
      </main>

      {/* 6. Colophon Footer */}
      <footer className="max-w-5xl mx-auto px-4 sm:px-6 pt-16 text-center sm:text-left border-t-2 border-[#18181B] mt-16 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs font-bold text-[#52525B] uppercase">
        <div>
          <span>TRIPPIN&apos; GRAPHIC ITINERARY ENGINE</span> // &copy; {new Date().getFullYear()}
        </div>
        <div className="flex items-center gap-4">
          <Link href="/planner" className="hover:text-[#E11D48] transition">
            Planner
          </Link>
          <span>·</span>
          <a
            href="https://backend-production-011e.up.railway.app/api/docs"
            target="_blank"
            rel="noopener noreferrer"
            className="hover:text-[#E11D48] transition"
          >
            API Protocol
          </a>
        </div>
      </footer>
    </div>
  );
}
