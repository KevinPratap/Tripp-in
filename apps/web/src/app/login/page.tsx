'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { requestMagicLink, verifyMagicLink } from '@/lib/auth-client';
import { sessionToken } from '@/lib/api-client';

/**
 * Passwordless sign in.
 *
 * No email is sent, because no mail provider is configured for this project. The API
 * writes the link to the server log and says so, and this page repeats that plainly
 * rather than pretending a message is on its way.
 */
export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<'idle' | 'sending' | 'sent' | 'verifying' | 'signed-in'>(
    'idle'
  );
  const [error, setError] = useState<string | null>(null);
  const [migratedTrips, setMigratedTrips] = useState(0);
  const [signedInEmail, setSignedInEmail] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    const linkEmail = params.get('email') || undefined;

    if (sessionToken()) {
      setStatus('signed-in');
      setSignedInEmail('this browser');
    }

    if (!token) return;

    setStatus('verifying');
    verifyMagicLink(token, linkEmail)
      .then((session) => {
        setMigratedTrips(session.migratedTrips);
        setSignedInEmail(session.user.email);
        setStatus('signed-in');
        window.history.replaceState({}, '', '/login');
      })
      .catch((err: Error) => {
        setError(err.message);
        setStatus('idle');
      });
  }, []);

  const handleRequest = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!email.trim()) return;
    setStatus('sending');
    setError(null);
    try {
      await requestMagicLink(email.trim());
      setStatus('sent');
    } catch (err) {
      setError((err as Error).message);
      setStatus('idle');
    }
  };

  return (
    <main className="min-h-screen bg-[#FAF8F5] px-4 py-10 flex items-start justify-center">
      <div className="w-full max-w-md">
        <Link
          href="/"
          className="inline-flex items-center gap-2 text-xs font-black uppercase tracking-widest text-[#52525B] hover:text-[#E11D48] min-h-11"
        >
          Back to planner
        </Link>

        <div className="comic-panel bg-white rounded-xl p-6 mt-4">
          <h1 className="font-display font-black text-2xl text-[#18181B]">Sign in</h1>
          <p className="text-sm font-medium text-[#52525B] mt-2">
            Trips are tied to a sign in link. No password is stored, and nothing is sent by
            email yet.
          </p>

          {error && (
            <p className="mt-4 text-sm font-bold text-[#E11D48] border-2 border-[#E11D48] rounded-lg p-3">
              {error}
            </p>
          )}

          {status === 'signed-in' ? (
            <div className="mt-6 space-y-4">
              <p className="text-sm font-bold text-[#18181B]">
                Signed in{migratedTrips > 0 ? `, and ${migratedTrips} trip(s) moved onto this account` : ''}.
              </p>
              <Link
                href="/trips"
                className="comic-btn-primary inline-flex items-center justify-center w-full min-h-11 rounded-lg text-xs font-black uppercase tracking-wider"
              >
                Open my trips
              </Link>
            </div>
          ) : status === 'sent' ? (
            <div className="mt-6 space-y-3">
              <p className="text-sm font-bold text-[#18181B]">Link created for {email}.</p>
              <p className="text-sm font-medium text-[#52525B]">
                No mail provider is connected, so the link is in the API log. In a terminal:
              </p>
              <code className="block text-xs font-mono bg-[#18181B] text-[#FAF8F5] rounded-lg p-3 overflow-x-auto">
                railway logs --service backend | grep &quot;Magic link&quot;
              </code>
            </div>
          ) : (
            <form onSubmit={handleRequest} className="mt-6 space-y-4">
              <label className="block">
                <span className="text-[10px] font-black uppercase tracking-widest text-[#52525B]">
                  Email
                </span>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                  placeholder="you@example.com"
                  className="mt-1 w-full h-12 px-3 bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg font-bold text-base sm:text-sm text-[#18181B] focus:bg-white focus:outline-none"
                />
              </label>
              <button
                type="submit"
                disabled={status === 'sending' || status === 'verifying'}
                className="comic-btn-primary w-full min-h-11 rounded-lg text-xs font-black uppercase tracking-wider disabled:opacity-60"
              >
                {status === 'sending' ? 'Creating link' : status === 'verifying' ? 'Signing in' : 'Send sign in link'}
              </button>
            </form>
          )}
        </div>
      </div>
    </main>
  );
}
