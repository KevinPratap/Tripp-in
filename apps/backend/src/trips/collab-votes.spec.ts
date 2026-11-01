import { ConflictException, NotFoundException } from '@nestjs/common';
import { CollabService } from './collab.service';

interface VoteRow { id: string; tripId: string; activityId: string; voterName: string; vote: number }
interface CommentRow { id: string; tripId: string; activityId: string; voterName: string; text: string; createdAt: Date }

/**
 * Votes are the group deciding together. These pin down that one person has one vote per stop,
 * that pressing the same vote takes it back, that votes survive the move off the old cache once,
 * and that a locked plan or a stop from another trip cannot be voted on.
 */
function setup(options: { locked?: boolean; legacy?: unknown; stopExists?: boolean } = {}) {
  const votes: VoteRow[] = [];
  const comments: CommentRow[] = [];
  let seq = 0;
  const cache = new Map<string, unknown>();
  if (options.legacy) cache.set('trip:collab:trip-1', options.legacy);
  const find = (w: { activityId: string; voterName: string }) =>
    votes.find((v) => v.activityId === w.activityId && v.voterName === w.voterName);

  const prisma = {
    trip: { findUnique: jest.fn(async () => ({ isLocked: Boolean(options.locked), lockedAt: null })) },
    activity: { findFirst: jest.fn(async () => (options.stopExists === false ? null : { id: 'stop-1' })) },
    activityVote: {
      findMany: jest.fn(async ({ where }: { where: { tripId: string } }) => votes.filter((v) => v.tripId === where.tripId)),
      findUnique: jest.fn(async ({ where }: { where: { activityId_voterName: { activityId: string; voterName: string } } }) =>
        find(where.activityId_voterName) ?? null
      ),
      delete: jest.fn(async ({ where }: { where: { activityId_voterName: { activityId: string; voterName: string } } }) => {
        const row = find(where.activityId_voterName)!;
        votes.splice(votes.indexOf(row), 1);
        return row;
      }),
      upsert: jest.fn(async ({ where, create, update }: {
        where: { activityId_voterName: { activityId: string; voterName: string } };
        create: Omit<VoteRow, 'id'>;
        update: { vote: number };
      }) => {
        const row = find(where.activityId_voterName);
        if (row) {
          row.vote = update.vote;
          return row;
        }
        const created = { ...create, id: `v${++seq}` };
        votes.push(created);
        return created;
      }),
      createMany: jest.fn(async ({ data }: { data: Array<Omit<VoteRow, 'id'>> }) => {
        data.forEach((d) => { if (!find(d)) votes.push({ ...d, id: `v${++seq}` }); });
        return { count: data.length };
      })
    },
    activityComment: {
      findMany: jest.fn(async ({ where }: { where: { tripId: string } }) => comments.filter((c) => c.tripId === where.tripId)),
      create: jest.fn(async ({ data }: { data: Omit<CommentRow, 'id' | 'createdAt'> }) => {
        const row = { ...data, id: `c${++seq}`, createdAt: new Date('2026-09-28T10:00:00Z') };
        comments.push(row);
        return row;
      }),
      createMany: jest.fn(async ({ data }: { data: Array<Omit<CommentRow, 'id'>> }) => {
        data.forEach((d) => comments.push({ ...d, id: `c${++seq}` }));
        return { count: data.length };
      })
    }
  };
  const redis = {
    get: jest.fn(async (key: string) => cache.get(key) ?? null),
    set: jest.fn(),
    del: jest.fn(async (key: string) => { cache.delete(key); })
  };
  return { service: new CollabService(prisma as never, redis as never), prisma, redis };
}

describe('CollabService votes', () => {
  it('counts one vote per person and lets a person change it', async () => {
    const { service } = setup();
    await service.vote('trip-1', 'stop-1', 'Sam', 1);
    await service.vote('trip-1', 'stop-1', 'Kevin', 1);
    const after = await service.vote('trip-1', 'stop-1', 'Sam', -1);

    expect(after.activities['stop-1']).toMatchObject({ upvotes: 1, downvotes: 1, voters: { Kevin: 1, Sam: -1 } });
  });

  it('takes a vote back when the same vote is pressed again', async () => {
    const { service } = setup();
    await service.vote('trip-1', 'stop-1', 'Sam', 1);
    const after = await service.vote('trip-1', 'stop-1', 'Sam', 1);

    expect(after.activities['stop-1']).toBeUndefined();
  });

  it('keeps a comment with the vote', async () => {
    const { service } = setup();
    const after = await service.vote('trip-1', 'stop-1', 'Sam', -1, '  MAAT is closer  ');
    expect(after.activities['stop-1'].comments).toEqual([
      expect.objectContaining({ voterName: 'Sam', text: 'MAAT is closer' })
    ]);
  });

  it('refuses votes on a locked plan and on a stop from another trip', async () => {
    await expect(setup({ locked: true }).service.vote('trip-1', 'stop-1', 'Sam', 1)).rejects.toBeInstanceOf(ConflictException);
    await expect(setup({ stopExists: false }).service.vote('trip-1', 'stop-1', 'Sam', 1)).rejects.toBeInstanceOf(NotFoundException);
  });

  it('moves votes and comments left in the old cache into the database once', async () => {
    const legacy = {
      'stop-1': {
        activityId: 'stop-1', upvotes: 1, downvotes: 1,
        voters: { Kevin: 1, Sam: -1, Ghost: 7 },
        comments: [{ id: 'x', voterName: 'Sam', text: 'Too far', createdAt: '2026-09-27T09:00:00Z' }]
      }
    };
    const { service, prisma, redis } = setup({ legacy });

    const first = await service.getCollabData('trip-1');
    await service.getCollabData('trip-1');

    expect(prisma.activityVote.createMany).toHaveBeenCalledTimes(1);
    expect(redis.del).toHaveBeenCalledWith('trip:collab:trip-1');
    expect(first.activities['stop-1']).toMatchObject({ upvotes: 1, downvotes: 1, voters: { Kevin: 1, Sam: -1 } });
    expect(first.activities['stop-1'].comments.map((c) => c.text)).toEqual(['Too far']);
  });
});
