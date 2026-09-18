import { Injectable, NotFoundException, Logger } from '@nestjs/common';
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
  activities: Record<string, ActivityCollabData>;
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
    const cacheKey = `trip:collab:${tripId}`;
    const cached = await this.redis.get<Record<string, ActivityCollabData>>(cacheKey);
    if (cached) {
      return { tripId, activities: cached };
    }
    const mem = this.inMemoryCollab.get(tripId) || {};
    return { tripId, activities: mem };
  }

  async vote(
    tripId: string,
    activityId: string,
    voterName: string,
    vote: number,
    comment?: string
  ): Promise<TripCollabResponse> {
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
          const description = `${act.reason || 'Verified Field Leg'}\\nCost: $${act.estimatedCost || 0} ${act.currency || 'USD'}\\nTransit leg: ${act.travelTimeToNextMin || 0}m\\nNavigation: ${navUrl}`;

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
}
