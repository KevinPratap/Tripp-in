import { NotFoundException } from '@nestjs/common';
import { CollabService } from './collab.service';

interface Row {
  id: string;
  tripId: string;
  title: string;
  amount: number;
  currency: string;
  paidBy: string;
  splitBetween: string[];
  createdAt: Date;
}

/**
 * The expense ledger is money people spent. These pin down that it lives in the database, keeps the
 * trip's own currency, survives the move off the old cache exactly once, and settles debts correctly.
 */
function setup(options: { tripCurrency?: string | null; legacy?: unknown } = {}) {
  const rows: Row[] = [];
  let seq = 0;
  let clock = Date.parse('2026-09-28T09:00:00Z');
  const cache = new Map<string, unknown>();
  if (options.legacy) cache.set('trip:expenses:trip-1', options.legacy);

  const prisma = {
    trip: {
      findUnique: jest.fn(async () =>
        options.tripCurrency === null ? null : { currency: options.tripCurrency ?? 'EUR' }
      )
    },
    tripExpense: {
      create: jest.fn(async ({ data }: { data: Omit<Row, 'id' | 'createdAt'> }) => {
        const row = { ...data, id: `exp-${++seq}`, createdAt: new Date((clock += 1000)) };
        rows.push(row);
        return row;
      }),
      createMany: jest.fn(async ({ data }: { data: Array<Omit<Row, 'id'>> }) => {
        data.forEach((d) => rows.push({ ...d, id: `exp-${++seq}` }));
        return { count: data.length };
      }),
      findMany: jest.fn(async ({ where }: { where: { tripId: string } }) =>
        rows
          .filter((r) => r.tripId === where.tripId)
          .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
      ),
      deleteMany: jest.fn(async ({ where }: { where: { id: string; tripId: string } }) => {
        const index = rows.findIndex((r) => r.id === where.id && r.tripId === where.tripId);
        if (index < 0) return { count: 0 };
        rows.splice(index, 1);
        return { count: 1 };
      })
    }
  };
  const redis = {
    get: jest.fn(async (key: string) => cache.get(key) ?? null),
    set: jest.fn(),
    del: jest.fn(async (key: string) => {
      cache.delete(key);
    })
  };

  const service = new CollabService(prisma as never, redis as never);
  return { service, prisma, redis, rows };
}

describe('CollabService expenses', () => {
  it('stores an expense in the database in the trip currency when none is given', async () => {
    const { service, rows } = setup({ tripCurrency: 'EUR' });

    const overview = await service.addExpense('trip-1', { title: 'Tram tickets', amount: 12, paidBy: 'Sam' });

    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ currency: 'EUR', splitBetween: ['Sam'] });
    expect(overview.currency).toBe('EUR');
    expect(overview.totalSpent).toBe(12);
  });

  it('refuses an expense on a trip that does not exist', async () => {
    const { service } = setup({ tripCurrency: null });
    await expect(
      service.addExpense('trip-1', { title: 'Dinner', amount: 40, paidBy: 'Kevin' })
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('settles an even split as one payment from the one who owes', async () => {
    const { service } = setup();
    await service.addExpense('trip-1', { title: 'Fado night', amount: 70, paidBy: 'Kevin', splitBetween: ['Kevin', 'Sam'] });
    const overview = await service.addExpense('trip-1', {
      title: 'Dinner',
      amount: 84,
      paidBy: 'Sam',
      splitBetween: ['Kevin', 'Sam']
    });

    expect(overview.totalSpent).toBe(154);
    expect(overview.settlements).toEqual([{ from: 'Kevin', to: 'Sam', amount: 7, currency: 'EUR' }]);
    expect(overview.expenses.map((e) => e.title)).toEqual(['Dinner', 'Fado night']);
  });

  it('moves a ledger left in the old cache into the database once', async () => {
    const legacy = [
      { id: 'a', title: 'Castle tickets', amount: 30, currency: 'eur', paidBy: 'Kevin', splitBetween: [], createdAt: '2026-09-27T10:00:00Z' },
      { id: 'b', title: '', amount: 5, currency: 'EUR', paidBy: 'Sam', splitBetween: [], createdAt: '2026-09-27T11:00:00Z' }
    ];
    const { service, prisma, redis } = setup({ legacy });

    const first = await service.getExpenses('trip-1');
    const second = await service.getExpenses('trip-1');

    expect(prisma.tripExpense.createMany).toHaveBeenCalledTimes(1);
    expect(redis.del).toHaveBeenCalledWith('trip:expenses:trip-1');
    expect(first.expenses).toHaveLength(1);
    expect(first.expenses[0]).toMatchObject({ title: 'Castle tickets', currency: 'EUR', splitBetween: ['Kevin'] });
    expect(second.expenses).toHaveLength(1);
  });

  it('deletes one expense and reports a missing one as not found', async () => {
    const { service } = setup();
    const added = await service.addExpense('trip-1', { title: 'Coffee', amount: 4, paidBy: 'Sam' });

    const after = await service.deleteExpense('trip-1', added.expenses[0].id);
    expect(after.expenses).toHaveLength(0);
    await expect(service.deleteExpense('trip-1', 'nope')).rejects.toBeInstanceOf(NotFoundException);
  });
});
