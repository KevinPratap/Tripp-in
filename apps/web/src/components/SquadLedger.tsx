'use client';

import React, { useState, useEffect } from 'react';
import { apiFetch } from '@/lib/api-client';
import {
  DollarSign,
  Plus,
  Users,
  Check,
  Copy,
  Receipt,
  ArrowRight,
  Sparkles,
  Wallet
} from 'lucide-react';

interface Expense {
  id: string;
  title: string;
  amount: number;
  currency: string;
  paidBy: string;
  splitBetween: string[];
  createdAt: string;
}

interface Settlement {
  from: string;
  to: string;
  amount: number;
  currency: string;
}

interface SquadLedgerProps {
  tripId: string;
  defaultCurrency?: string;
  squadMembers?: string[];
  currentMember: string;
  apiUrl: string;
}

export default function SquadLedger({
  tripId,
  defaultCurrency = 'USD',
  squadMembers = ['@Maverick', '@Scout', '@Cipher', '@Phoenix'],
  currentMember,
  apiUrl,
}: SquadLedgerProps) {
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [settlements, setSettlements] = useState<Settlement[]>([]);
  const [totalSpent, setTotalSpent] = useState(0);
  const [currency, setCurrency] = useState(defaultCurrency);
  const [loading, setLoading] = useState(true);

  // Form State
  const [title, setTitle] = useState('');
  const [amount, setAmount] = useState('');
  const [paidBy, setPaidBy] = useState(currentMember || squadMembers[0]);
  const [selectedSplit, setSelectedSplit] = useState<string[]>(squadMembers);
  const [copied, setCopied] = useState(false);
  const [totalsByCurrency, setTotalsByCurrency] = useState<
    Array<{ currency: string; amount: number }>
  >([]);
  const [mixedCurrencies, setMixedCurrencies] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    fetchExpenses();
  }, [tripId]);

  const fetchExpenses = async () => {
    try {
      const res = await apiFetch(`/api/v1/trips/${tripId}/expenses`);
      if (res.ok) {
        const data = await res.json();
        setExpenses(data.expenses || []);
        setSettlements(data.settlements || []);
        setTotalSpent(data.totalSpent || 0);
        setTotalsByCurrency(data.totalsByCurrency || []);
        setMixedCurrencies(Boolean(data.mixedCurrencies));
        if (data.currency) setCurrency(data.currency);
      }
    } catch (err) {
      console.warn('Expense fetch error:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleAddExpense = async (e: React.FormEvent) => {
    e.preventDefault();
    const numAmount = parseFloat(amount);
    if (!title.trim() || isNaN(numAmount) || numAmount <= 0) return;

    setSubmitting(true);
    try {
      const res = await apiFetch(`/api/v1/trips/${tripId}/expenses`, {
        method: 'POST',
        body: JSON.stringify({
          title: title.trim(),
          amount: numAmount,
          currency,
          paidBy: paidBy.trim(),
          splitBetween: selectedSplit.length > 0 ? selectedSplit : squadMembers,
        }),
      });

      if (res.ok) {
        const data = await res.json();
        setExpenses(data.expenses || []);
        setSettlements(data.settlements || []);
        setTotalSpent(data.totalSpent || 0);
        setTotalsByCurrency(data.totalsByCurrency || []);
        setMixedCurrencies(Boolean(data.mixedCurrencies));
        setTitle('');
        setAmount('');
      }
    } catch (err) {
      console.error('Failed to save expense:', err);
    } finally {
      setSubmitting(false);
    }
  };

  const toggleSplitMember = (member: string) => {
    setSelectedSplit((prev) =>
      prev.includes(member) ? prev.filter((m) => m !== member) : [...prev, member]
    );
  };

  const copySettlementSummary = () => {
    if (settlements.length === 0) return;
    const lines = [
"TRIPPIN' SQUAD SETTLEMENT REPORT",
      mixedCurrencies
        ? `Totals by currency: ${totalsByCurrency.map((t) => `${t.amount} ${t.currency}`).join(', ')}`
        : `Total group spend: ${totalSpent} ${currency}`,
      '---------------------------------',
      ...settlements.map((s) => `${s.from} owes ${s.to}: ${s.amount} ${s.currency}`),
      '---------------------------------',
"Generated via Tripp'in Collab Ledger",
    ];
    const text = lines.join('\n');

    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-8">
      {/* Top Banner Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="comic-panel p-4 bg-white rounded-xl">
          <span className="text-[10px] uppercase font-black tracking-widest text-[#52525B] block">
            Total Squad Spend
          </span>
          <span className="font-display font-black text-2xl text-[#E11D48]">
            {mixedCurrencies && totalsByCurrency.length > 0
              ? totalsByCurrency.map((t) => `${t.amount} ${t.currency}`).join(' + ')
              : `${totalSpent} ${currency}`}
          </span>
          {mixedCurrencies && (
            <span className="block text-[10px] font-bold text-[#52525B] mt-1">
              Separate currencies, not converted
            </span>
          )}
        </div>
        <div className="comic-panel p-4 bg-white rounded-xl">
          <span className="text-[10px] uppercase font-black tracking-widest text-[#52525B] block">
            Logged Receipts
          </span>
          <span className="font-display font-black text-2xl text-[#18181B]">
            {expenses.length} Entries
          </span>
        </div>
        <div className="comic-panel p-4 bg-white rounded-xl">
          <span className="text-[10px] uppercase font-black tracking-widest text-[#52525B] block">
            Settlement Status
          </span>
          <span className="font-display font-black text-lg text-emerald-700">
            {settlements.length === 0 ? 'All Settled Clean' : `${settlements.length} Transfers Due`}
          </span>
        </div>
      </div>

      {/* Log Expense Form */}
      <section className="comic-panel p-6 rounded-2xl bg-white space-y-4">
        <div className="flex items-center gap-2 border-b-2 border-[#18181B] pb-3">
          <Receipt className="w-5 h-5 text-[#E11D48]" />
          <h3 className="font-display font-black text-lg uppercase text-[#18181B]">
            Log Field Expense
          </h3>
        </div>

        <form onSubmit={handleAddExpense} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-[11px] font-black uppercase text-[#18181B] block mb-1">
                Description / Line Item
              </label>
              <input
                type="text"
                placeholder="e.g. Izakaya Dinner, Bullet Train, Airbnb"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
                className="w-full bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg px-3 py-2 text-xs font-bold text-[#18181B] focus:outline-none focus:ring-2 focus:ring-[#E11D48]"
              />
            </div>
            <div>
              <label className="text-[11px] font-black uppercase text-[#18181B] block mb-1">
                Amount ({currency})
              </label>
              <input
                type="number"
                step="any"
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
                className="w-full bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg px-3 py-2 text-xs font-bold text-[#18181B] focus:outline-none focus:ring-2 focus:ring-[#E11D48]"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-[11px] font-black uppercase text-[#18181B] block mb-1">
                Paid By
              </label>
              <select
                value={paidBy}
                onChange={(e) => setPaidBy(e.target.value)}
                className="w-full bg-[#FAF8F5] border-2 border-[#18181B] rounded-lg px-3 py-2 text-xs font-bold text-[#18181B] focus:outline-none focus:ring-2 focus:ring-[#E11D48]"
              >
                {squadMembers.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-[11px] font-black uppercase text-[#18181B] block mb-1">
                Split Amongst
              </label>
              <div className="flex flex-wrap gap-1.5 pt-0.5">
                {squadMembers.map((m) => {
                  const isSelected = selectedSplit.includes(m);
                  return (
                    <button
                      key={m}
                      type="button"
                      onClick={() => toggleSplitMember(m)}
                      className={`px-2.5 py-1 rounded text-[10px] font-black uppercase tracking-wider border-2 border-[#18181B] transition-all ${
                        isSelected
                          ? 'bg-[#E11D48] text-white'
                          : 'bg-white text-[#52525B] opacity-60 hover:opacity-100'
                      }`}
                    >
                      {m}
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          <button
            type="submit"
            disabled={submitting || !title.trim() || !amount}
            className="comic-btn-primary w-full py-2.5 rounded-lg text-xs font-black uppercase tracking-wider flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <Plus className="w-4 h-4" />
            <span>{submitting ? 'Recording...' : 'Add to Squad Ledger'}</span>
          </button>
        </form>
      </section>

      {/* Debt Settlements ("Who Owes What") */}
      <section className="comic-panel p-6 rounded-2xl bg-[#FAF8F5] space-y-4">
        <div className="flex items-center justify-between border-b-2 border-[#18181B] pb-3">
          <div className="flex items-center gap-2">
            <Wallet className="w-5 h-5 text-emerald-600" />
            <h3 className="font-display font-black text-lg uppercase text-[#18181B]">
              Settlement Plan // Who Owes What
            </h3>
          </div>

          {settlements.length > 0 && (
            <button
              type="button"
              onClick={copySettlementSummary}
              className="comic-btn-secondary px-3 py-1.5 rounded-md text-[11px] font-black uppercase flex items-center gap-1.5"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copied Report' : 'Copy Breakdown'}</span>
            </button>
          )}
        </div>

        {settlements.length === 0 ? (
          <div className="p-6 text-center text-xs font-bold text-[#52525B] bg-white border-2 border-[#18181B] rounded-xl">
            Everyone is squared away. No pending debts in the squad.
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {settlements.map((s, idx) => (
              <div
                key={idx}
                className="bg-white p-3.5 border-2 border-[#18181B] rounded-xl flex items-center justify-between"
              >
                <div className="flex items-center gap-2 text-xs font-black text-[#18181B]">
                  <span className="bg-red-100 text-[#E11D48] px-2 py-0.5 rounded border border-[#18181B]">
                    {s.from}
                  </span>
                  <ArrowRight className="w-3.5 h-3.5 text-[#52525B]" />
                  <span className="bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded border border-[#18181B]">
                    {s.to}
                  </span>
                </div>
                <span className="font-display font-black text-sm text-[#E11D48]">
                  {s.amount} {s.currency}
                </span>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Expense History List */}
      <section className="space-y-3">
        <h4 className="font-display font-black text-sm uppercase tracking-wider text-[#18181B]">
          Expense Audit Trail ({expenses.length})
        </h4>

        {expenses.length === 0 ? (
          <p className="text-xs text-[#52525B] italic">No expenses recorded yet.</p>
        ) : (
          <div className="space-y-2">
            {expenses.map((exp) => (
              <div
                key={exp.id}
                className="bg-white p-3 border-2 border-[#18181B] rounded-xl flex items-center justify-between"
              >
                <div>
                  <h5 className="font-display font-black text-xs uppercase text-[#18181B]">
                    {exp.title}
                  </h5>
                  <p className="text-[10px] text-[#52525B] font-bold mt-0.5">
                    Paid by <span className="text-[#E11D48] font-black">{exp.paidBy}</span> · Split with{' '}
                    {exp.splitBetween.join(', ')}
                  </p>
                </div>
                <span className="font-display font-black text-sm text-[#18181B]">
                  {exp.amount} {exp.currency}
                </span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
