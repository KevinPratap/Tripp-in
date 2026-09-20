import { Injectable, NotFoundException, Logger } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../common/prisma/prisma.service';
import { RedisService } from '../common/redis/redis.service';
import {
  TravellerDto,
  PerTravellerCostDto,
  TripOptionDto,
  StopSupportDto,
  TripCostModel,
  ActivityModel
} from '@trippin/shared-types';
import {
  CreateTravellerDto,
  UpdateTravellerDto,
  JoinTripDto,
  VALID_INTERESTS
} from './dto/traveller.dto';

@Injectable()
export class TravellersService {
  private readonly logger = new Logger(TravellersService.name);
  private readonly inMemoryTravellers = new Map<string, TravellerDto[]>();

  constructor(
    private readonly prisma: PrismaService,
    private readonly redis: RedisService
  ) {}

  private cacheKey(tripId: string): string {
    return `trip:travellers:${tripId}`;
  }

  private filterVocabulary(items?: string[]): string[] {
    if (!Array.isArray(items)) return [];
    return items
      .map((item) => String(item).trim().toLowerCase())
      .filter((item) => (VALID_INTERESTS as readonly string[]).includes(item));
  }

  async getTravellers(tripId: string): Promise<TravellerDto[]> {
    const key = this.cacheKey(tripId);
    const cached = await this.redis.get<TravellerDto[]>(key);
    if (cached && Array.isArray(cached) && cached.length > 0) {
      return cached;
    }

    const mem = this.inMemoryTravellers.get(tripId);
    if (mem && Array.isArray(mem) && mem.length > 0) {
      return mem;
    }

    // 1. Check database trip.costAssumptionsJson._travellers on cache miss or restart
    try {
      const trip = await this.prisma.trip.findUnique({
        where: { id: tripId },
        select: { costAssumptionsJson: true }
      });
      const stored = trip?.costAssumptionsJson as Record<string, unknown> | null;
      if (stored && Array.isArray(stored._travellers) && stored._travellers.length > 0) {
        const dtos = stored._travellers as TravellerDto[];
        this.inMemoryTravellers.set(tripId, dtos);
        await this.redis.set(this.cacheKey(tripId), dtos, 86400 * 30);
        return dtos;
      }
    } catch (err) {
      this.logger.debug(`Could not read travellers from trip json: ${(err as Error).message}`);
    }

    // 2. Fallback: check database trip travelers table as baseline
    try {
      const dbTravelers = await this.prisma.tripTraveler.findMany({
        where: { tripId },
        orderBy: { createdAt: 'asc' }
      });

      if (dbTravelers && dbTravelers.length > 0) {
        const dtos: TravellerDto[] = dbTravelers.map((t) => ({
          id: t.id,
          name: t.name,
          budgetCap: null,
          interests: [],
          dislikes: [],
          pace: null,
          joinedAt: t.createdAt.toISOString()
        }));
        this.inMemoryTravellers.set(tripId, dtos);
        await this.redis.set(this.cacheKey(tripId), dtos, 86400 * 30);
        return dtos;
      }
    } catch (err) {
      this.logger.debug(`Could not read travellers from tripTraveler: ${(err as Error).message}`);
    }

    return [];
  }

  async addTraveller(tripId: string, dto: CreateTravellerDto): Promise<TravellerDto> {
    const trip = await this.prisma.trip.findUnique({ where: { id: tripId } });
    if (!trip) {
      throw new NotFoundException(`Trip with ID ${tripId} not found.`);
    }

    const current = await this.getTravellers(tripId);
    const newTraveller: TravellerDto = {
      id: randomUUID(),
      name: dto.name.trim(),
      budgetCap: typeof dto.budgetCap === 'number' ? dto.budgetCap : null,
      interests: this.filterVocabulary(dto.interests),
      dislikes: this.filterVocabulary(dto.dislikes),
      pace: dto.pace || null,
      joinedAt: new Date().toISOString()
    };

    const updated = [...current, newTraveller];
    await this.saveTravellers(tripId, updated);

    // Insert row into trip_travelers table for relational integrity
    if (this.prisma.tripTraveler?.create) {
      try {
        await this.prisma.tripTraveler.create({
          data: {
            id: newTraveller.id,
            tripId,
            name: newTraveller.name,
            email: `${newTraveller.id}@guest.trippin.local`,
            role: 'TRAVELER'
          }
        });
      } catch (err) {
        this.logger.warn(`Failed to insert tripTraveler row: ${(err as Error).message}`);
      }
    }

    this.logger.log(`Traveller "${newTraveller.name}" (${newTraveller.id}) added to trip ${tripId}`);
    return newTraveller;
  }

  async updateTraveller(
    tripId: string,
    travellerId: string,
    dto: UpdateTravellerDto
  ): Promise<TravellerDto> {
    const current = await this.getTravellers(tripId);
    const index = current.findIndex((t) => t.id === travellerId);
    if (index === -1) {
      throw new NotFoundException(`Traveller ${travellerId} not found in trip ${tripId}.`);
    }

    const target = current[index];
    const updated: TravellerDto = {
      ...target,
      name: dto.name !== undefined ? dto.name.trim() : target.name,
      budgetCap:
        dto.budgetCap !== undefined
          ? typeof dto.budgetCap === 'number'
            ? dto.budgetCap
            : null
          : target.budgetCap,
      interests:
        dto.interests !== undefined ? this.filterVocabulary(dto.interests) : target.interests,
      dislikes:
        dto.dislikes !== undefined ? this.filterVocabulary(dto.dislikes) : target.dislikes,
      pace: dto.pace !== undefined ? dto.pace || null : target.pace
    };

    current[index] = updated;
    await this.saveTravellers(tripId, current);

    if (this.prisma.tripTraveler?.updateMany) {
      try {
        await this.prisma.tripTraveler.updateMany({
          where: { tripId, id: travellerId },
          data: { name: updated.name }
        });
      } catch (err) {
        this.logger.warn(`Failed to update tripTraveler row: ${(err as Error).message}`);
      }
    }

    return updated;
  }

  async deleteTraveller(tripId: string, travellerId: string): Promise<void> {
    const current = await this.getTravellers(tripId);
    const filtered = current.filter((t) => t.id !== travellerId);
    if (filtered.length === current.length) {
      throw new NotFoundException(`Traveller ${travellerId} not found in trip ${tripId}.`);
    }
    await this.saveTravellers(tripId, filtered);

    if (this.prisma.tripTraveler?.deleteMany) {
      try {
        await this.prisma.tripTraveler.deleteMany({
          where: { tripId, id: travellerId }
        });
      } catch (err) {
        this.logger.warn(`Failed to delete tripTraveler row: ${(err as Error).message}`);
      }
    }
  }

  async joinTrip(dto: JoinTripDto): Promise<{ tripId: string; traveller: TravellerDto }> {
    const token = dto.token.trim();
    let tripId: string | null = null;

    // Enforce token lookup strictly against active unrevoked share token
    const share = await this.prisma.tripShare.findUnique({
      where: { token }
    });

    if (!share || share.revokedAt) {
      throw new NotFoundException('Trip invite token is not valid or has been revoked.');
    }

    tripId = share.tripId;

    const traveller = await this.addTraveller(tripId, {
      name: dto.name,
      budgetCap: dto.budgetCap,
      interests: dto.interests,
      dislikes: dto.dislikes,
      pace: dto.pace
    });

    return { tripId, traveller };
  }

  private async saveTravellers(tripId: string, travellers: TravellerDto[]): Promise<void> {
    this.inMemoryTravellers.set(tripId, travellers);
    await this.redis.set(this.cacheKey(tripId), travellers, 86400 * 30);

    // Persist to Postgres on trip.costAssumptionsJson._travellers and keep travelersCount synced
    try {
      const trip = await this.prisma.trip.findUnique({
        where: { id: tripId },
        select: { costAssumptionsJson: true, travelersCount: true }
      });
      if (trip) {
        const existingJson =
          trip.costAssumptionsJson &&
          typeof trip.costAssumptionsJson === 'object' &&
          !Array.isArray(trip.costAssumptionsJson)
            ? (trip.costAssumptionsJson as Record<string, unknown>)
            : {};

        await this.prisma.trip.update({
          where: { id: tripId },
          data: {
            travelersCount: Math.max(travellers.length, 1),
            costAssumptionsJson: {
              ...existingJson,
              _travellers: JSON.parse(JSON.stringify(travellers))
            } as Prisma.InputJsonValue
          }
        });
      }
    } catch (err) {
      this.logger.warn(`Failed to persist travellers to Postgres for trip ${tripId}: ${(err as Error).message}`);
    }
  }

  /**
   * Rule 3: overCap is true when the computed share exceeds that traveller's own budgetCap.
   * Evaluated against shareMax (the high end of the projected range), so that if a trip's
   * upper cost bound risks exceeding a traveller's cap, the conflict is surfaced to the group.
   *
   * Cost Basis Invariant:
   * TripCostModel is computed strictly on a 'PER_PERSON' basis (day rates for stay, food,
   * local transit, and entries represent individual costs per traveller).
   * Therefore, shareMin and shareMax represent each traveller's individual projected cost
   * and must NOT be divided by travellers.length (doing so would divide per-person daily rates).
   */
  computePerTravellerCost(
    travellers: TravellerDto[],
    cost?: TripCostModel
  ): PerTravellerCostDto[] {
    if (!travellers || travellers.length === 0 || !cost) {
      return [];
    }

    const shareMin = cost.totalMin;
    const shareMax = cost.totalMax;

    return travellers.map((t) => ({
      travellerId: t.id,
      shareMin,
      shareMax,
      overCap: t.budgetCap !== null && t.budgetCap !== undefined && shareMax > t.budgetCap
    }));
  }

  /**
   * Rule 2: support.want and support.total are counts of real travellers with real interests.
   * Total = count of registered travellers with interest/dislike data.
   */
  computeStopSupport(
    activity: ActivityModel,
    travellers: TravellerDto[]
  ): StopSupportDto | undefined {
    if (!travellers || travellers.length === 0) {
      return undefined;
    }

    const activeTravellers = travellers.filter(
      (t) => (t.interests && t.interests.length > 0) || (t.dislikes && t.dislikes.length > 0)
    );

    if (activeTravellers.length === 0) {
      return undefined;
    }

    const matchedInterests = this.extractActivityInterests(activity);
    let want = 0;
    const against: string[] = [];

    for (const t of activeTravellers) {
      const wantsStop = t.interests?.some((i) => matchedInterests.includes(i));
      const dislikesStop = t.dislikes?.some((d) => matchedInterests.includes(d));

      if (wantsStop) {
        want++;
      }
      if (dislikesStop) {
        against.push(t.name);
      }
    }

    return {
      want,
      total: activeTravellers.length,
      against
    };
  }

  private extractActivityInterests(activity: ActivityModel): string[] {
    const text = `${activity.title} ${activity.reason || ''} ${activity.type || ''}`.toLowerCase();
    const matches = new Set<string>();

    if (text.includes('museum') || activity.type === 'MUSEUM') {
      matches.add('museums');
      matches.add('culture');
      matches.add('history');
    }
    if (text.includes('food') || text.includes('restaurant') || text.includes('cafe') || activity.type === 'RESTAURANT' || activity.type === 'CAFE') {
      matches.add('food');
    }
    if (text.includes('night') || text.includes('bar') || activity.type === 'NIGHTLIFE') {
      matches.add('nightlife');
    }
    if (text.includes('park') || text.includes('garden') || text.includes('nature') || activity.type === 'PARK') {
      matches.add('nature');
      matches.add('relaxation');
    }
    if (text.includes('temple') || text.includes('shrine') || text.includes('historic') || text.includes('castle')) {
      matches.add('history');
      matches.add('culture');
      matches.add('landmark');
    }
    if (text.includes('tower') || text.includes('landmark') || activity.type === 'ATTRACTION') {
      matches.add('landmark');
      matches.add('photography');
    }
    if (text.includes('shop') || text.includes('market') || text.includes('mall')) {
      matches.add('shopping');
    }
    if (text.includes('spa') || text.includes('wellness') || text.includes('bath') || text.includes('onsen')) {
      matches.add('wellness');
      matches.add('relaxation');
    }
    if (text.includes('hike') || text.includes('adventure') || text.includes('climb')) {
      matches.add('adventure');
      matches.add('nature');
    }

    return Array.from(matches);
  }

  /**
   * Invariant 1 (Never invent data) & INTERFACE.md Rule 4:
   * An option may only be listed if the engine actually produced it under that objective.
   * Returns empty array unless legitimate options were generated by the multi-run engine.
   */
  computePlanOptions(
    tripId: string,
    currency: string,
    cost?: TripCostModel,
    generatedOptions?: TripOptionDto[]
  ): TripOptionDto[] {
    if (generatedOptions && generatedOptions.length > 0) {
      return generatedOptions;
    }
    return [];
  }
}
