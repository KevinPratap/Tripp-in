'use client';

import React from 'react';
import Link from 'next/link';
import {
  Compass,
  MapPin,
  Search,
  Sparkles,
  Sun,
  Calendar,
  ArrowRight,
  ShieldCheck,
  Plane
} from 'lucide-react';

export default function WebHomePage() {
  const destinations = [
    {
      name: 'Paris',
      country: 'France',
      rating: 4.9,
      image: 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=600',
      tag: 'Art & Cuisine'
    },
    {
      name: 'Tokyo',
      country: 'Japan',
      rating: 4.9,
      image: 'https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=600',
      tag: 'Futuristic & Temples'
    },
    {
      name: 'Rome',
      country: 'Italy',
      rating: 4.8,
      image: 'https://images.unsplash.com/photo-1552832230-c0197dd311b5?w=600',
      tag: 'History & Architecture'
    },
    {
      name: 'London',
      country: 'United Kingdom',
      rating: 4.8,
      image: 'https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?w=600',
      tag: 'Museums & Theater'
    }
  ];

  return (
    <div className="min-h-screen pb-20 md:pb-10">
      {/* Top Navigation Bar */}
      <header className="sticky top-0 z-50 bg-white/90 backdrop-blur-md border-b border-gray-100">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white font-bold text-xl shadow-md shadow-blue-500/20">
              🧭
            </div>
            <div>
              <span className="font-extrabold text-xl tracking-tight text-gray-900">
                Trippin<span className="text-blue-600">' AI</span>
              </span>
              <span className="hidden sm:inline-block ml-2 px-2 py-0.5 text-xs font-semibold bg-emerald-100 text-emerald-700 rounded-full">
                Blinkit-Style SDUI
              </span>
            </div>
          </div>

          <div className="flex items-center gap-4">
            <Link
              href="/planner"
              className="px-4 py-2 bg-blue-600 text-white rounded-xl font-semibold text-sm shadow-sm hover:bg-blue-700 transition"
            >
              Plan a Trip
            </Link>
            <div className="w-9 h-9 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center font-bold text-sm">
              AR
            </div>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-5xl mx-auto px-4 sm:px-6 pt-6 space-y-8">
        {/* 1. Header Greeting & Location */}
        <section className="flex items-center justify-between">
          <div>
            <p className="text-gray-500 text-sm font-medium">Welcome back, Alex 👋</p>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-gray-900">
              Where will you explore next?
            </h1>
          </div>
        </section>

        {/* 2. Interactive Search Bar */}
        <section className="relative">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="text"
            placeholder="Search landmarks, cities, museums, cafes..."
            className="w-full h-14 pl-12 pr-4 rounded-2xl bg-white border border-gray-200 text-gray-800 placeholder-gray-400 shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent transition"
          />
        </section>

        {/* 3. Hero AI Planner CTA Card (Blinkit Signature Banner) */}
        <section className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-blue-600 via-indigo-600 to-blue-700 text-white p-6 sm:p-8 shadow-xl shadow-blue-600/15">
          <div className="relative z-10 max-w-xl space-y-4">
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/20 backdrop-blur-md text-xs font-semibold uppercase tracking-wider">
              <Sparkles className="w-3.5 h-3.5 text-yellow-300" /> AI Itinerary Engine
            </div>
            <h2 className="text-2xl sm:text-3xl font-black leading-tight">
              Create a physically verified travel schedule in seconds.
            </h2>
            <p className="text-blue-100 text-sm sm:text-base leading-relaxed">
              No overlapping slots. Every activity is cross-referenced with real opening hours, transit buffers, and weather forecasts.
            </p>
            <div className="pt-2">
              <Link
                href="/planner"
                className="inline-flex items-center gap-2 px-6 py-3 bg-white text-blue-700 rounded-xl font-bold text-sm hover:bg-blue-50 transition shadow-md"
              >
                Start AI Planning <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>

          <div className="absolute right-4 bottom-4 opacity-15 hidden sm:block pointer-events-none">
            <Compass className="w-64 h-64 text-white" />
          </div>
        </section>

        {/* 4. Quick Action Grid */}
        <section className="grid grid-cols-3 gap-4">
          <div className="bg-white p-4 rounded-2xl border border-gray-100 shadow-sm flex flex-col items-center text-center cursor-pointer hover:border-blue-200 transition">
            <div className="w-12 h-12 rounded-xl bg-orange-100 text-orange-600 flex items-center justify-center mb-2">
              <Sun className="w-6 h-6" />
            </div>
            <span className="text-sm font-semibold text-gray-800">Weather</span>
            <span className="text-xs text-gray-400">Live Forecasts</span>
          </div>

          <div className="bg-white p-4 rounded-2xl border border-gray-100 shadow-sm flex flex-col items-center text-center cursor-pointer hover:border-blue-200 transition">
            <div className="w-12 h-12 rounded-xl bg-emerald-100 text-emerald-600 flex items-center justify-center mb-2">
              <Compass className="w-6 h-6" />
            </div>
            <span className="text-sm font-semibold text-gray-800">Explore</span>
            <span className="text-xs text-gray-400">PostGIS Places</span>
          </div>

          <div className="bg-white p-4 rounded-2xl border border-gray-100 shadow-sm flex flex-col items-center text-center cursor-pointer hover:border-blue-200 transition">
            <div className="w-12 h-12 rounded-xl bg-blue-100 text-blue-600 flex items-center justify-center mb-2">
              <Plane className="w-6 h-6" />
            </div>
            <span className="text-sm font-semibold text-gray-800">My Trips</span>
            <span className="text-xs text-gray-400">Saved Schedules</span>
          </div>
        </section>

        {/* 5. Popular Destinations Horizontal Carousel */}
        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-xl font-bold text-gray-900">Popular Destinations</h3>
              <p className="text-sm text-gray-500">Trending global hotspots verified for 2026</p>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {destinations.map((d) => (
              <div
                key={d.name}
                className="group relative rounded-2xl overflow-hidden shadow-sm hover:shadow-md transition cursor-pointer bg-white border border-gray-100"
              >
                <div className="h-44 w-full relative overflow-hidden">
                  <img
                    src={d.image}
                    alt={d.name}
                    className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent" />
                  <span className="absolute top-3 right-3 px-2 py-0.5 bg-white/90 backdrop-blur-md rounded-full text-xs font-bold text-gray-900">
                    ★ {d.rating}
                  </span>
                  <div className="absolute bottom-3 left-3 text-white">
                    <h4 className="font-bold text-lg leading-tight">{d.name}</h4>
                    <p className="text-xs text-white/80">{d.country}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* 6. Recent Active Trip Section */}
        <section className="space-y-3">
          <h3 className="text-xl font-bold text-gray-900">Active Trip</h3>
          <div className="bg-white p-5 rounded-2xl border border-gray-100 shadow-sm flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 rounded-2xl bg-emerald-500/10 text-emerald-600 flex items-center justify-center font-bold text-2xl">
                🇫🇷
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h4 className="font-bold text-lg text-gray-900">Paris Cultural Escape</h4>
                  <span className="px-2 py-0.5 text-xs font-bold bg-emerald-100 text-emerald-700 rounded-full flex items-center gap-1">
                    <ShieldCheck className="w-3 h-3" /> VERIFIED v1
                  </span>
                </div>
                <p className="text-sm text-gray-500">
                  10 Apr - 14 Apr · 3 Days · 9 Places · 18°C Sunny
                </p>
              </div>
            </div>
            <Link
              href="/planner"
              className="px-4 py-2 rounded-xl text-sm font-semibold text-blue-600 hover:bg-blue-50 transition"
            >
              View Itinerary →
            </Link>
          </div>
        </section>
      </main>
    </div>
  );
}
