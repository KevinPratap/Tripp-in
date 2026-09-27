'use client';

import React, { useState, useEffect, use } from 'react';
import Link from 'next/link';
import {
  ArrowLeft,
  Users,
  Calendar,
  ThumbsUp,
  ThumbsDown,
  MessageSquare,
  Send,
  Share2,
  Check,
  CalendarPlus,
  Compass,
  AlertCircle,
  ExternalLink,
  ShieldCheck,
  Lock,
  Unlock
} from 'lucide-react';
import { downloadTripCalendar } from '@/lib/calendar-generator';
import { apiFetch } from '@/lib/api-client';
import SquadLedger from '@/components/SquadLedger';

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'https://backend-production-011e.up.railway.app';

export default function TripCollabPage({
  params
}: {
  params: Promise<{ id: string }>;
}) {
  const resolvedParams = use(params);
  const tripId = resolvedParams.id;

  const [tripData, setTripData] = useState<any>(null);
  const [collabData, setCollabData] = useState<Record<string, any>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [activeDayIndex, setActiveDayIndex] = useState(0);
  const [activeTab, setActiveTab] = useState<'voting' | 'ledger'>('voting');
  const [isLocked, setIsLocked] = useState(false);
  const [lockedAt, setLockedAt] = useState<string | null>(null);
  const [lockActionLoading, setLockActionLoading] = useState(false);

  // Companion identity
  const [agentName, setAgentName] = useState('Agent Fox');
  const [isEditingName, setIsEditingName] = useState(false);

  // New comment draft per activity
  const [commentDrafts, setCommentDrafts] = useState<Record<string, string>>({});
  const [submittingVote, setSubmittingVote] = useState<string | null>(null);
  const [copiedLink, setCopiedLink] = useState(false);

  useEffect(() => {
    async function loadTripAndCollab() {
      try {
        setIsLoading(true);
        const [tripRes, collabRes] = await Promise.all([
          fetch(`${API_BASE_URL}/api/v1/trips/${tripId}`),
          fetch(`${API_BASE_URL}/api/v1/trips/${tripId}/collab`)
        ]);

        if (tripRes.ok) {
          const tData = await tripRes.json();
          setTripData(tData);
          if (tData.trip?.isLocked || tData.itinerary?.isLocked) {
            setIsLocked(true);
            setLockedAt(tData.trip?.lockedAt || tData.itinerary?.lockedAt || null);
          }
        }

        if (collabRes.ok) {
          const cData = await collabRes.json();
          setCollabData(cData.activities || {});
          if (cData.isLocked) {
            setIsLocked(true);
            setLockedAt(cData.lockedAt || null);
          }
        }
      } catch (err) {
        console.error('Failed loading collab session:', err);
      } finally {
        setIsLoading(false);
      }
    }

    loadTripAndCollab();

    const pollInterval = setInterval(async () => {
      if (typeof document !== 'undefined' && document.hidden) return;
      try {
        const collabRes = await fetch(`${API_BASE_URL}/api/v1/trips/${tripId}/collab`);
        if (collabRes.ok) {
          const cData = await collabRes.json();
          setCollabData(cData.activities || {});
          if (cData.isLocked) {
            setIsLocked(true);
            setLockedAt(cData.lockedAt || null);
          } else {
            setIsLocked(false);
          }
        }
      } catch {
        // Silently ignore background polling failure
      }
    }, 6000);

    return () => clearInterval(pollInterval);
  }, [tripId]);

  const handleToggleLock = async () => {
    setLockActionLoading(true);
    try {
      const endpoint = isLocked ? `/api/v1/trips/${tripId}/unlock` : `/api/v1/trips/${tripId}/lock`;
      const res = await apiFetch(endpoint, { method: 'POST' });
      if (res.ok) {
        const data = await res.json();
        setIsLocked(Boolean(data.isLocked));
        setLockedAt(data.lockedAt || null);
      }
    } catch (err) {
      console.error('Lock toggle error:', err);
    } finally {
      setLockActionLoading(false);
    }
  };

  const handleVote = async (activityId: string, voteValue: number) => {
    if (isLocked) return;
    const voter = agentName.trim() || 'Companion';
    setSubmittingVote(activityId);

    // Optimistic update
    setCollabData((prev) => {
      const current = prev[activityId] || {
        activityId,
        upvotes: 0,
        downvotes: 0,
        voters: {},
        comments: []
      };
      const prevVote = current.voters[voter] || 0;
      let newUp = current.upvotes;
      let newDown = current.downvotes;
      const newVoters = { ...current.voters };

      if (prevVote === 1) newUp = Math.max(0, newUp - 1);
      if (prevVote === -1) newDown = Math.max(0, newDown - 1);

      if (prevVote !== voteValue) {
        newVoters[voter] = voteValue;
        if (voteValue === 1) newUp++;
        if (voteValue === -1) newDown++;
      } else {
        delete newVoters[voter];
      }

      return {
        ...prev,
        [activityId]: {
          ...current,
          upvotes: newUp,
          downvotes: newDown,
          voters: newVoters
        }
      };
    });

    try {
      const res = await apiFetch(`/api/v1/trips/${tripId}/vote`, {
        method: 'POST',
        body: JSON.stringify({
          activityId,
          voterName: voter,
          vote: voteValue
        })
      });

      if (res.ok) {
        const updated = await res.json();
        setCollabData(updated.activities || {});
      }
    } catch (err) {
      console.error('Vote submission error:', err);
    } finally {
      setSubmittingVote(null);
    }
  };

  const handleAddComment = async (activityId: string) => {
    const text = commentDrafts[activityId]?.trim();
    if (!text) return;

    const voter = agentName.trim() || 'Companion';

    try {
      const res = await apiFetch(`/api/v1/trips/${tripId}/vote`, {
        method: 'POST',
        body: JSON.stringify({
          activityId,
          voterName: voter,
          vote: collabData[activityId]?.voters?.[voter] || 1,
          comment: text
        })
      });

      if (res.ok) {
        const updated = await res.json();
        setCollabData(updated.activities || {});
        setCommentDrafts((prev) => ({ ...prev, [activityId]: '' }));
      }
    } catch (err) {
      console.error('Comment error:', err);
    }
  };

  const copyInviteLink = () => {
    if (typeof window !== 'undefined') {
      navigator.clipboard.writeText(window.location.href);
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2000);
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#FAF8F5] flex flex-col items-center justify-center p-6 text-center">
        <div className="w-12 h-12 border-4 border-[#18181B] border-t-[#E11D48] rounded-full animate-spin mb-4" />
        <p className="font-mono text-sm uppercase tracking-widest text-[#18181B] font-black">
          Decrypting Squad Dispatch...
        </p>
      </div>
    );
  }

  const trip = tripData?.trip;
  const itinerary = tripData?.itinerary;
  const days = itinerary?.days || [];
  const currentDay = days[activeDayIndex] || days[0];
  const allActivities = days.flatMap((d: any) => d.activities || []);

  return (
    <div className="min-h-screen bg-[#FAF8F5] text-[#18181B] flex flex-col selection:bg-[#E11D48] selection:text-white pb-20">
      {/* Top Issue Nav */}
      <header className="border-b-[2.5px] border-[#18181B] bg-white sticky top-0 z-40">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <Link
              href={`/trip/${tripId}`}
              className="w-10 h-10 border-[2.5px] border-[#18181B] rounded-xl flex items-center justify-center bg-white shadow-comic hover:-translate-x-0.5 transition-transform"
              title="Return to Master Ticket"
            >
              <ArrowLeft className="w-5 h-5 text-[#18181B]" />
            </Link>
            <div>
              <span className="text-[10px] font-mono font-black tracking-widest text-[#E11D48] uppercase block">
                MULTIPLAYER DISPATCH // VOTE ROOM
              </span>
              <h1 className="text-base sm:text-lg font-black tracking-tight uppercase leading-none font-display">
                {trip?.destination || 'Destination'} Squad Collab
              </h1>
            </div>
          </div>

          <div className="flex items-center space-x-2 sm:space-x-3">
            <button
              onClick={copyInviteLink}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 border-[2px] border-[#18181B] rounded-xl text-xs font-black uppercase bg-[#FAF8F5] shadow-comic hover:-translate-y-0.5 transition-transform"
            >
              {copiedLink ? <Check className="w-3.5 h-3.5 text-[#10B981]" /> : <Share2 className="w-3.5 h-3.5 text-[#18181B]" />}
              <span className="hidden sm:inline">{copiedLink ? 'Copied Link!' : 'Invite Squad'}</span>
            </button>

            <button
              onClick={handleToggleLock}
              disabled={lockActionLoading}
              className={`inline-flex items-center space-x-1.5 px-3 py-1.5 border-[2px] border-[#18181B] rounded-xl text-xs font-black uppercase shadow-comic hover:-translate-y-0.5 transition-transform ${
                isLocked ? 'bg-[#18181B] text-white' : 'bg-[#FAF8F5] text-[#18181B]'
              }`}
              title={isLocked ? 'Unlock schedule to resume voting' : 'Lock schedule to finalize decisions'}
            >
              {isLocked ? <Unlock className="w-3.5 h-3.5 text-[#E11D48]" /> : <Lock className="w-3.5 h-3.5 text-[#18181B]" />}
              <span className="hidden sm:inline">{isLocked ? 'Unlock Plan' : 'Lock Plan'}</span>
            </button>

            <button
              onClick={() => downloadTripCalendar(trip, itinerary)}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 border-[2px] border-[#18181B] rounded-xl text-xs font-black uppercase bg-[#E11D48] text-white shadow-comic hover:-translate-y-0.5 transition-transform"
              title="Download RFC-5545 iCalendar (.ics)"
            >
              <CalendarPlus className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Sync (.ics)</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-4xl mx-auto px-4 sm:px-6 pt-8 w-full">
        {/* Plan Locked Notification Banner */}
        {isLocked && (
          <div className="mb-6 comic-panel bg-[#18181B] text-white rounded-2xl p-5 border-[2.5px] border-[#18181B] flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-[#E11D48] flex items-center justify-center font-black shrink-0">
                <Lock className="w-5 h-5 text-white" />
              </div>
              <div>
                <div className="font-display font-black text-sm uppercase tracking-wide">
                  DECISION FINALIZED: SCHEDULE LOCKED
                </div>
                <p className="text-xs text-zinc-300 font-medium mt-0.5">
                  This itinerary has been locked by the group organizer. Voting, swaps, and route adjustments are frozen.
                </p>
              </div>
            </div>
            <button
              type="button"
              disabled={lockActionLoading}
              onClick={handleToggleLock}
              className="comic-btn-secondary px-4 min-h-11 py-2 rounded-xl text-xs font-black uppercase bg-white text-[#18181B] hover:bg-zinc-100 whitespace-nowrap shrink-0"
            >
              {lockActionLoading ? 'Updating...' : 'Unlock Plan'}
            </button>
          </div>
        )}

        {/* Companion Callout Banner */}
        <section className="comic-panel rounded-2xl p-5 mb-8 bg-white flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-[2.5px] border-[#18181B]">
          <div className="space-y-1">
            <div className="flex items-center space-x-2">
              <span className="inline-block w-2.5 h-2.5 rounded-full bg-[#10B981] animate-pulse" />
              <span className="text-xs font-mono font-black text-[#E11D48] tracking-widest uppercase">
                ACTIVE MULTIPLAYER BRIEFING
              </span>
            </div>
            <p className="text-sm font-bold text-[#52525B]">
              Vote on itinerary activities or propose swaps before the schedule is sealed.
            </p>
          </div>

          <div className="flex items-center space-x-2 bg-[#FAF8F5] border-[2px] border-[#18181B] rounded-xl p-1.5">
            <span className="text-xs font-mono font-bold text-[#52525B] pl-2 uppercase">Your Handle:</span>
            {isEditingName ? (
              <input
                type="text"
                value={agentName}
                onChange={(e) => setAgentName(e.target.value)}
                onBlur={() => setIsEditingName(false)}
                onKeyDown={(e) => e.key === 'Enter' && setIsEditingName(false)}
                autoFocus
                className="px-2 py-1 border-[1.5px] border-[#18181B] rounded-lg text-xs font-black uppercase bg-white w-28 focus:outline-none"
              />
            ) : (
              <button
                onClick={() => setIsEditingName(true)}
                className="px-2.5 py-1 bg-[#18181B] text-white rounded-lg text-xs font-black uppercase hover:bg-[#E11D48] transition-colors"
                title="Click to edit your moniker"
              >
                {agentName}
              </button>
            )}
          </div>
        </section>

        {/* Mode Switcher Tabs */}
        <nav className="flex items-center gap-2 mb-6 border-b-2 border-[#18181B] pb-3" aria-label="Collab View Modes">
          <button
            type="button"
            onClick={() => setActiveTab('voting')}
            className={`px-4 py-2 rounded-xl text-xs font-black uppercase tracking-wider border-2 border-[#18181B] transition-all ${
              activeTab === 'voting'
                ? 'bg-[#E11D48] text-white '
                : 'bg-white text-[#18181B] hover:bg-[#FAF8F5]'
            }`}
          >
            Activity Voting ({allActivities.length})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('ledger')}
            className={`px-4 py-2 rounded-xl text-xs font-black uppercase tracking-wider border-2 border-[#18181B] transition-all ${
              activeTab === 'ledger'
                ? 'bg-[#E11D48] text-white '
                : 'bg-white text-[#18181B] hover:bg-[#FAF8F5]'
            }`}
          >
            Squad Expense Splitter
          </button>
        </nav>

        {activeTab === 'ledger' ? (
          <SquadLedger
            tripId={tripId}
            defaultCurrency={trip?.currency || 'USD'}
            currentMember={agentName}
            apiUrl={API_BASE_URL}
          />
        ) : (
          <>
            {/* Day Switcher Tabs */}
            {days.length > 0 && (
              <nav className="flex space-x-2 overflow-x-auto pb-3 mb-6 no-scrollbar" aria-label="Trip Days">
            {days.map((day: any, idx: number) => {
              const isActive = activeDayIndex === idx;
              return (
                <button
                  key={day.id || idx}
                  onClick={() => setActiveDayIndex(idx)}
                  className={`px-4 py-2 border-[2.5px] border-[#18181B] rounded-xl text-xs font-black tracking-wider uppercase transition-all whitespace-nowrap shadow-comic ${
                    isActive
                      ? 'bg-[#E11D48] text-white -translate-y-0.5'
                      : 'bg-white text-[#18181B] hover:bg-[#FAF8F5]'
                  }`}
                >
                  DAY {String(day.dayIndex).padStart(2, '0')} · {day.date?.split('T')[0]}
                </button>
              );
            })}
          </nav>
        )}

        {/* Current Day Activities with Vote Controls */}
        <section className="space-y-6">
          <div className="flex items-center justify-between pb-2 border-b-[2px] border-[#18181B]">
            <h2 className="text-sm font-mono font-black uppercase tracking-widest text-[#E11D48]">
              DAY {String(currentDay?.dayIndex || 1).padStart(2, '0')} CANDIDATE LEGS
            </h2>
            <span className="text-xs font-mono font-bold text-[#52525B]">
              {currentDay?.activities?.length || 0} ACTIVITIES SCHEDULED
            </span>
          </div>

          {(currentDay?.activities || []).map((act: any, idx: number) => {
            const collab = collabData[act.id] || {
              upvotes: 0,
              downvotes: 0,
              voters: {},
              comments: []
            };

            const userVote = collab.voters?.[agentName.trim() || 'Companion'] || 0;
            const comments = collab.comments || [];
            const commentDraft = commentDrafts[act.id] || '';

            return (
              <article
                key={act.id || idx}
                className="comic-panel rounded-2xl p-5 bg-white border-[2.5px] border-[#18181B] space-y-4 shadow-comic"
              >
                {/* Header row: Pin Number, Timeslot, Cost, Nav Link */}
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center space-x-3">
                    <span className="w-8 h-8 rounded-lg bg-[#E11D48] text-white border-[2px] border-[#18181B] font-display font-black text-xs flex items-center justify-center">
                      {String(idx + 1).padStart(2, '0')}
                    </span>
                    <div>
                      <h3 className="text-base sm:text-lg font-black tracking-tight uppercase leading-snug font-display">
                        {act.title}
                      </h3>
                      <div className="flex items-center space-x-2 text-xs font-mono font-bold text-[#52525B]">
                        <span>{act.startTime} to {act.endTime}</span>
                        <span>•</span>
                        <span className="uppercase text-[#E11D48]">{act.activityType || 'Attraction'}</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center space-x-2">
                    <a
                      href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${act.title}, ${trip?.destination || ''}`)}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="p-1.5 border-[1.5px] border-[#18181B] rounded-lg hover:bg-[#FAF8F5] transition-colors"
                      title="Open Google Maps Route"
                    >
                      <ExternalLink className="w-4 h-4 text-[#52525B]" />
                    </a>
                  </div>
                </div>

                {/* Reason description */}
                {act.reason && (
                  <p className="text-xs sm:text-sm text-[#52525B] font-medium leading-relaxed bg-[#FAF8F5] p-3 rounded-xl border-[1.5px] border-[#18181B]/30">
                    {act.reason}
                  </p>
                )}

                {/* Voting Action Bar */}
                <div className="pt-2 flex items-center justify-between border-t-[1.5px] border-[#18181B]/20">
                  <div className="flex items-center space-x-3">
                    {/* Upvote */}
                    <button
                      onClick={() => handleVote(act.id, 1)}
                      disabled={isLocked || submittingVote === act.id}
                      title={isLocked ? 'Schedule is locked' : 'Vote up'}
                      className={`inline-flex items-center space-x-1.5 px-3 py-1.5 border-[2px] border-[#18181B] rounded-xl text-xs font-black uppercase transition-all shadow-comic ${
                        isLocked
                          ? 'opacity-50 cursor-not-allowed bg-zinc-100 text-zinc-500'
                          : 'hover:-translate-y-0.5'
                      } ${
                        userVote === 1
                          ? 'bg-[#10B981] text-white'
                          : 'bg-white text-[#18181B] hover:bg-[#FAF8F5]'
                      }`}
                    >
                      <ThumbsUp className="w-3.5 h-3.5" />
                      <span>{collab.upvotes}</span>
                    </button>

                    {/* Downvote */}
                    <button
                      onClick={() => handleVote(act.id, -1)}
                      disabled={isLocked || submittingVote === act.id}
                      title={isLocked ? 'Schedule is locked' : 'Vote down'}
                      className={`inline-flex items-center space-x-1.5 px-3 py-1.5 border-[2px] border-[#18181B] rounded-xl text-xs font-black uppercase transition-all shadow-comic ${
                        isLocked
                          ? 'opacity-50 cursor-not-allowed bg-zinc-100 text-zinc-500'
                          : 'hover:-translate-y-0.5'
                      } ${
                        userVote === -1
                          ? 'bg-[#E11D48] text-white'
                          : 'bg-white text-[#18181B] hover:bg-[#FAF8F5]'
                      }`}
                    >
                      <ThumbsDown className="w-3.5 h-3.5" />
                      <span>{collab.downvotes}</span>
                    </button>
                  </div>

                  <span className="text-[11px] font-mono text-[#52525B] font-bold">
                    {comments.length} {comments.length === 1 ? 'Dispatch Note' : 'Dispatch Notes'}
                  </span>
                </div>

                {/* Comments & Swap Suggestion Thread */}
                <div className="pt-2 space-y-2">
                  {comments.map((c: any) => (
                    <div
                      key={c.id}
                      className="bg-[#FAF8F5] border-[1.5px] border-[#18181B] p-2.5 rounded-xl text-xs space-y-1"
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-mono font-black text-[#E11D48] uppercase">{c.voterName}</span>
                        <span className="text-[10px] font-mono text-[#71717A]">
                          {c.createdAt ? new Date(c.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                        </span>
                      </div>
                      <p className="text-[#18181B] font-medium">{c.text}</p>
                    </div>
                  ))}

                  {/* Add note input */}
                  <div className="flex items-center space-x-2 pt-1">
                    <input
                      type="text"
                      disabled={isLocked}
                      placeholder={
                        isLocked
                          ? 'Schedule is locked by organizer'
                          : `Suggest swap or note for ${act.title}...`
                      }
                      value={commentDraft}
                      onChange={(e) =>
                        setCommentDrafts((prev) => ({ ...prev, [act.id]: e.target.value }))
                      }
                      onKeyDown={(e) => e.key === 'Enter' && handleAddComment(act.id)}
                      className="flex-1 px-3 py-1.5 border-[2px] border-[#18181B] rounded-xl text-xs font-medium focus:outline-none focus:border-[#E11D48] bg-white disabled:bg-zinc-100 disabled:cursor-not-allowed"
                    />
                    <button
                      onClick={() => handleAddComment(act.id)}
                      disabled={isLocked || !commentDraft.trim()}
                      className="px-3 py-1.5 border-[2px] border-[#18181B] rounded-xl bg-[#18181B] text-white text-xs font-black uppercase disabled:opacity-40 hover:bg-[#E11D48] transition-colors"
                    >
                      <Send className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </section>
        </>
        )}

        {/* Footer actions */}
        <div className="mt-12 pt-6 border-t-[2.5px] border-[#18181B] flex flex-col sm:flex-row items-center justify-between gap-4">
          <Link
            href={`/trip/${tripId}`}
            className="comic-btn-secondary w-full sm:w-auto text-center"
          >
            ← View Final Itinerary
          </Link>
          <button
            onClick={() => downloadTripCalendar(trip, itinerary)}
            className="comic-btn-primary w-full sm:w-auto text-center"
          >
            Sync to Phone Calendar (.ics)
          </button>
        </div>
      </main>
    </div>
  );
}
