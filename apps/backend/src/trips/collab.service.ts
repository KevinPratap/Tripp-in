import { Injectable, NotFoundException, ConflictException, Logger } from '@nestjs/common';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';

export interface ActivityComment {
  id: string;
  voterName: string;
  text: string;
  createdAt: string;
}

export interface ActivityCollabData {
  activityId: string;
  upvotes: number;
  downvotes: number;
  voters: Record<string, number>; // voterName -> 1 or -1
  comments: ActivityComment[];
}

export interface TripCollabResponse {
  tripId: string;
  isLocked?: boolean;
  lockedAt?: string;
  activities: Record<string, ActivityCollabData>;
}

export interface TripExpense {
  id: string;
  title: string;
  amount: number;
  currency: string;
  paidBy: string;
  splitBetween: string[];
  createdAt: string;
}

export interface DebtSettlement {
  from: string;
  to: string;
  amount: number;
  currency: string;
}

export interface ExpenseOverview {
  tripId: string;
  expenses: TripExpense[];
  /** Total for the dominant currency only. Never a sum across currencies. */
  totalSpent: number;
  /** The currency the totals and settlements below are expressed in. */
  currency: string;
  settlements: DebtSettlement[];
  /** Every currency present in the ledger, with its own total. */
  totalsByCurrency: Array<{ currency: string; amount: number }>;
  /** True when the ledger holds more than one currency, so totals are not comparable. */
  mixedCurrencies: boolean;
}

@Injectable()
export class CollabService {
  private readonly logger = new Logger(CollabService.name);
  // In-memory fallback if Redis is offline
  private readonly inMemoryCollab = new Map<string, Record<string, ActivityCollabData>>();

  constructor(
    private readonly prisma: PrismaService,
    private readonly redis: RedisService
  ) {}

  async getCollabData(tripId: string): Promise<TripCollabResponse> {
    const trip = await this.prisma.trip.findUnique({
      where: { id: tripId },
      select: { isLocked: true, lockedAt: true }
    });

    const isLocked = Boolean(trip?.isLocked);
    const lockedAt = trip?.lockedAt ? trip.lockedAt.toISOString() : undefined;

    const cacheKey = `trip:collab:${tripId}`;
    const cached = await this.redis.get<Record<string, ActivityCollabData>>(cacheKey);
    if (cached) {
      return { tripId, isLocked, lockedAt, activities: cached };
    }
    const mem = this.inMemoryCollab.get(tripId) || {};
    return { tripId, isLocked, lockedAt, activities: mem };
  }

  async vote(
    tripId: string,
    activityId: string,
    voterName: string,
    vote: number,
    comment?: string
  ): Promise<TripCollabResponse> {
    const trip = await this.prisma.trip.findUnique({
      where: { id: tripId },
      select: { isLocked: true }
    });
    if (trip?.isLocked) {
      throw new ConflictException(
        'Voting is closed: This itinerary has been locked by the trip organizer.'
      );
    }

    // A vote must point at a real stop on this trip, otherwise anyone can create
    // phantom entries in the collaboration payload.
    const activity = await this.prisma.activity.findFirst({
      where: { id: activityId, day: { itinerary: { tripId } } },
      select: { id: true }
    });
    if (!activity) {
      throw new NotFoundException(`Activity ${activityId} is not part of trip ${tripId}`);
    }

    const data = await this.getCollabData(tripId);
    const activities = data.activities;

    if (!activities[activityId]) {
      activities[activityId] = {
        activityId,
        upvotes: 0,
        downvotes: 0,
        voters: {},
        comments: []
      };
    }

    const act = activities[activityId];
    const prevVote = act.voters[voterName] || 0;

    // Remove previous vote impact
    if (prevVote === 1) act.upvotes = Math.max(0, act.upvotes - 1);
    if (prevVote === -1) act.downvotes = Math.max(0, act.downvotes - 1);

    // Apply new vote if different
    if (prevVote !== vote) {
      act.voters[voterName] = vote;
      if (vote === 1) act.upvotes++;
      if (vote === -1) act.downvotes++;
    } else {
      // Toggle off if same vote pressed again
      delete act.voters[voterName];
    }

    // Add optional comment / swap suggestion
    if (comment && comment.trim().length > 0) {
      act.comments.push({
        id: `c_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
        voterName,
        text: comment.trim(),
        createdAt: new Date().toISOString()
      });
    }

    // Persist in Redis and in-memory
    const cacheKey = `trip:collab:${tripId}`;
    await this.redis.set(cacheKey, activities, 86400 * 30); // 30 days retention
    this.inMemoryCollab.set(tripId, activities);

    return { tripId, activities };
  }

  async generateIcs(tripId: string): Promise<string> {
    const trip = await this.prisma.trip.findUnique({
      where: { id: tripId },
      include: {
        itineraries: {
          where: { isCurrent: true },
          include: {
            days: {
              orderBy: { dayIndex: 'asc' },
              include: {
                activities: {
                  orderBy: { orderIndex: 'asc' },
                  include: { place: true }
                }
              }
            }
          }
        }
      }
    });

    if (!trip) {
      throw new NotFoundException(`Trip ${tripId} not found`);
    }

    const currentItinerary = trip.itineraries[0];
    const nowStamp = new Date().toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z';

    const events: string[] = [];

    if (currentItinerary && currentItinerary.days) {
      for (const day of currentItinerary.days) {
        const dateStr = day.date.toISOString().split('T')[0]; // YYYY-MM-DD
        const [year, month, dayNum] = dateStr.split('-');

        for (const act of day.activities) {
          const [startH, startM] = (act.startTime || '09:00').split(':');
          const [endH, endM] = (act.endTime || '11:00').split(':');

          const dtStart = `${year}${month}${dayNum}T${startH.padStart(2, '0')}${startM.padStart(2, '0')}00`;
          const dtEnd = `${year}${month}${dayNum}T${endH.padStart(2, '0')}${endM.padStart(2, '0')}00`;

          const cleanTitle = (act.title || 'Trip Activity').replace(/[,;\\]/g, ' ');
          const address = act.place?.formattedAddress || trip.destinationName;
          const navUrl = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${act.title}, ${trip.destinationName}`)}`;
          const description = `${act.reason || 'Planned itinerary stop'}\\nPrices are not estimated\\nTransit leg: ${act.travelTimeToNextMin || 0}m\\nNavigation: ${navUrl}`;

          events.push([
            'BEGIN:VEVENT',
            `UID:${act.id}@trippin.ai`,
            `DTSTAMP:${nowStamp}`,
            `DTSTART:${dtStart}`,
            `DTEND:${dtEnd}`,
            `SUMMARY:${cleanTitle}`,
            `DESCRIPTION:${description}`,
            `LOCATION:${address.replace(/[,;\\]/g, ' ')}`,
            'STATUS:CONFIRMED',
            'END:VEVENT'
          ].join('\r\n'));
        }
      }
    }

    return [
      'BEGIN:VCALENDAR',
      'VERSION:2.0',
      'PRODID:-//Trippin AI//NONSGML Field Itinerary v1.0//EN',
      'CALSCALE:GREGORIAN',
      'METHOD:PUBLISH',
      `X-WR-CALNAME:${trip.destinationName} Field Issue`,
      'X-WR-TIMEZONE:UTC',
      ...events,
      'END:VCALENDAR'
    ].join('\r\n');
  }

  /**
   * Records a new group expense for a trip and recalculates debt settlements. An expense with no
   * currency of its own is in the trip's currency, which follows the destination.
   */
  async addExpense(
    tripId: string,
    expenseData: {
      title: string;
      amount: number;
      currency?: string;
      paidBy: string;
      splitBetween?: string[];
    }
  ): Promise<ExpenseOverview> {
    const trip = await this.prisma.trip.findUnique({ where: { id: tripId }, select: { currency: true } });
    if (!trip) throw new NotFoundException('Trip not found');
    await this.importLegacyExpenses(tripId);

    const paidBy = expenseData.paidBy.trim();
    const split = (expenseData.splitBetween ?? []).map((s) => s.trim()).filter((s) => s.length > 0);
    await this.prisma.tripExpense.create({
      data: {
        tripId,
        title: expenseData.title.trim(),
        amount: Math.round(Math.max(0, expenseData.amount) * 100) / 100,
        currency: (expenseData.currency || trip.currency || '').trim().toUpperCase(),
        paidBy,
        splitBetween: split.length > 0 ? split : [paidBy]
      }
    });
    return this.getExpenses(tripId);
  }

  /** Removes one logged expense, for the entry that was typed wrong. */
  async deleteExpense(tripId: string, expenseId: string): Promise<ExpenseOverview> {
    const { count } = await this.prisma.tripExpense.deleteMany({ where: { id: expenseId, tripId } });
    if (count === 0) throw new NotFoundException('Expense not found');
    return this.getExpenses(tripId);
  }

  /**
   * Retrieves the current expense ledger and simplified debt settlements.
   */
  async getExpenses(tripId: string): Promise<ExpenseOverview> {
    await this.importLegacyExpenses(tripId);
    const rows = await this.prisma.tripExpense.findMany({
      where: { tripId },
      orderBy: { createdAt: 'desc' }
    });
    const expenses: TripExpense[] = rows.map((row) => ({
      id: row.id,
      title: row.title,
      amount: row.amount,
      currency: row.currency,
      paidBy: row.paidBy,
      splitBetween: row.splitBetween,
      createdAt: row.createdAt.toISOString()
    }));
    return this.buildExpenseOverview(tripId, expenses);
  }

  /**
   * Expenses used to live only in Redis with a 30 day expiry. The first read of a trip's ledger
   * copies whatever is still there into the database, then drops the cache key so nothing is
   * copied twice.
   */
  private async importLegacyExpenses(tripId: string): Promise<void> {
    const cacheKey = `trip:expenses:${tripId}`;
    let legacy: TripExpense[] | null = null;
    try {
      legacy = await this.redis.get<TripExpense[]>(cacheKey);
    } catch {
      return;
    }
    if (!legacy || !Array.isArray(legacy) || legacy.length === 0) return;

    const valid = legacy.filter((e) => e && typeof e.amount === 'number' && e.amount > 0 && e.title && e.paidBy);
    if (valid.length > 0) {
      await this.prisma.tripExpense.createMany({
        data: valid.map((e) => ({
          tripId,
          title: String(e.title),
          amount: e.amount,
          currency: String(e.currency || '').toUpperCase(),
          paidBy: String(e.paidBy),
          splitBetween: Array.isArray(e.splitBetween) && e.splitBetween.length > 0 ? e.splitBetween : [String(e.paidBy)],
          createdAt: e.createdAt ? new Date(e.createdAt) : new Date()
        }))
      });
    }
    await this.redis.del(cacheKey).catch(() => undefined);
    this.logger.log(`Moved ${valid.length} expense(s) for trip ${tripId} from cache into the database`);
  }

  private buildExpenseOverview(tripId: string, expenses: TripExpense[]): ExpenseOverview {
    // Amounts in different currencies cannot be added together, so totals and
    // settlements are computed per currency and the dominant one is reported as
    // the headline figure.
    const totals = new Map<string, number>();
    for (const expense of expenses) {
      const code = (expense.currency || '').toUpperCase() || 'UNSPECIFIED';
      totals.set(code, (totals.get(code) || 0) + (expense.amount || 0));
    }

    const totalsByCurrency = Array.from(totals.entries())
      .map(([currency, amount]) => ({ currency, amount: Math.round(amount * 100) / 100 }))
      .sort((a, b) => b.amount - a.amount);

    const dominant = totalsByCurrency[0];
    const defaultCurrency = dominant?.currency ?? 'UNSPECIFIED';

    const sameCurrency = expenses.filter(
      (expense) => ((expense.currency || '').toUpperCase() || 'UNSPECIFIED') === defaultCurrency
    );

    const settlements = this.calculateSettlements(sameCurrency, defaultCurrency);

    return {
      tripId,
      expenses,
      totalSpent: dominant?.amount ?? 0,
      currency: defaultCurrency,
      settlements,
      totalsByCurrency,
      mixedCurrencies: totalsByCurrency.length > 1,
    };
  }

  /**
   * Greedy debt minimization algorithm: reduces N-way group debts to the minimum transactions.
   */
  private calculateSettlements(expenses: TripExpense[], currency: string): DebtSettlement[] {
    const netBalances: Record<string, number> = {};

    for (const exp of expenses) {
      const splitList = exp.splitBetween.length > 0 ? exp.splitBetween : [exp.paidBy];
      const perPersonShare = exp.amount / splitList.length;

      // Payer gets credit
      netBalances[exp.paidBy] = (netBalances[exp.paidBy] || 0) + exp.amount;

      // Each beneficiary owes their share
      for (const person of splitList) {
        netBalances[person] = (netBalances[person] || 0) - perPersonShare;
      }
    }

    const creditors: { name: string; amount: number }[] = [];
    const debtors: { name: string; amount: number }[] = [];

    for (const [person, balance] of Object.entries(netBalances)) {
      const rounded = Math.round(balance * 100) / 100;
      if (rounded > 0.01) {
        creditors.push({ name: person, amount: rounded });
      } else if (rounded < -0.01) {
        debtors.push({ name: person, amount: -rounded });
      }
    }

    // Sort descending
    creditors.sort((a, b) => b.amount - a.amount);
    debtors.sort((a, b) => b.amount - a.amount);

    const settlements: DebtSettlement[] = [];
    let i = 0;
    let j = 0;

    while (i < debtors.length && j < creditors.length) {
      const debtor = debtors[i];
      const creditor = creditors[j];
      const settledAmount = Math.min(debtor.amount, creditor.amount);

      if (settledAmount > 0.01) {
        settlements.push({
          from: debtor.name,
          to: creditor.name,
          amount: Math.round(settledAmount * 100) / 100,
          currency,
        });
      }

      debtor.amount -= settledAmount;
      creditor.amount -= settledAmount;

      if (debtor.amount <= 0.01) i++;
      if (creditor.amount <= 0.01) j++;
    }

    return settlements;
  }
}

