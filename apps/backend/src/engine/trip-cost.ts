import { ItineraryModel, TripCostModel, DailyRateModel, EntryCostLineModel, ActivityType } from '@trippin/shared-types';

/**
 * Builds what a trip costs, per person, as ranges only.
 *
 * Two honest sources go in:
 * 1. Published entry fees per stop, read from OpenStreetMap tags (free, exact amount, or charged
 *    with no published amount).
 * 2. The traveller's own day rates for stay, food and local transport. Those are their numbers,
 *    labelled as theirs, and when they are not given the line is excluded rather than guessed.
 *
 * What is deliberately NOT here: any per-stop price for a meal, a bed or a train ride. Those are
 * per-day costs, and inventing a per-stop figure for them was the original defect.
 */

export interface CostAssumptions {
  stayPerNightMin?: number;
  stayPerNightMax?: number;
  foodPerDayMin?: number;
  foodPerDayMax?: number;
  localTransitPerDayMin?: number;
  localTransitPerDayMax?: number;
}

/** Activities whose missing price is a real gap. The rest are covered by a day rate above. */
const ENTRY_TYPES: ActivityType[] = ['ATTRACTION', 'MUSEUM', 'PARK', 'NIGHTLIFE'];

const r2 = (n: number): number => Math.round(n * 100) / 100;

function buildRate(
  unit: 'PER_NIGHT' | 'PER_DAY',
  units: number,
  min?: number,
  max?: number
): DailyRateModel {
  const hasMin = typeof min === 'number' && Number.isFinite(min) && min >= 0;
  const hasMax = typeof max === 'number' && Number.isFinite(max) && (max as number) >= 0;
  if (!hasMin && !hasMax) {
    return { unit, units, source: 'NOT_SET' };
  }
  const low = hasMin ? (min as number) : (max as number);
  const high = hasMax ? (max as number) : (min as number);
  return {
    unit,
    units,
    perUnitMin: r2(Math.min(low, high)),
    perUnitMax: r2(Math.max(low, high)),
    source: 'USER_INPUT'
  };
}

const spread = (rate: DailyRateModel): { min: number; max: number } =>
  rate.source === 'USER_INPUT' && typeof rate.perUnitMin === 'number'
    ? { min: rate.perUnitMin * rate.units, max: (rate.perUnitMax as number) * rate.units }
    : { min: 0, max: 0 };

export function buildTripCost(
  itinerary: Pick<ItineraryModel, 'days'>,
  currency: string,
  assumptions: CostAssumptions = {}
): TripCostModel {
  const days = Math.max(itinerary.days?.length || 0, 1);
  const nights = Math.max(days - 1, 0);

  const stay = buildRate('PER_NIGHT', nights, assumptions.stayPerNightMin, assumptions.stayPerNightMax);
  const food = buildRate('PER_DAY', days, assumptions.foodPerDayMin, assumptions.foodPerDayMax);
  const localTransit = buildRate(
    'PER_DAY',
    days,
    assumptions.localTransitPerDayMin,
    assumptions.localTransitPerDayMax
  );

  const lines: EntryCostLineModel[] = [];
  let knownMin = 0;
  let knownMax = 0;
  let freeCount = 0;
  let unknownCount = 0;
  let foreignCurrencyCount = 0;
  let pricedStops = 0;
  let unpricedStops = 0;

  for (const day of itinerary.days || []) {
    for (const activity of day.activities || []) {
      const price = activity.place?.price;
      if (!price) {
        if (ENTRY_TYPES.includes(activity.type)) unpricedStops++;
        continue;
      }
      pricedStops++;
      const line: EntryCostLineModel = {
        activityId: activity.id,
        title: activity.title,
        status: price.status,
        sourceDetail: price.sourceDetail
      };

      if (price.status === 'FREE') {
        freeCount++;
      } else if (price.status === 'CHARGED_KNOWN') {
        // A price in another currency is shown but never converted: that would need a rate we do not have.
        if (price.currency && currency && price.currency !== currency) {
          foreignCurrencyCount++;
          line.excludedForeignCurrency = true;
          line.amountMin = price.amountMin;
          line.amountMax = price.amountMax;
          line.currency = price.currency;
        } else {
          const low = price.amountMin ?? price.amountMax ?? 0;
          const high = price.amountMax ?? price.amountMin ?? 0;
          knownMin += low;
          knownMax += high;
          line.amountMin = low;
          line.amountMax = high;
          line.currency = price.currency;
        }
      } else {
        unknownCount++;
      }
      lines.push(line);
    }
  }

  const totalMin = r2(knownMin + spread(stay).min + spread(food).min + spread(localTransit).min);
  const totalMax = r2(knownMax + spread(stay).max + spread(food).max + spread(localTransit).max);

  const missingTravellerRates =
    stay.source === 'NOT_SET' || food.source === 'NOT_SET' || localTransit.source === 'NOT_SET';

  const notes: string[] = [];
  if (missingTravellerRates) {
    notes.push(
      'Stay, food and local transport are not in this total, because you have not told us what you spend on them.'
    );
  }
  if (unknownCount > 0) {
    notes.push(
      `${unknownCount} ${unknownCount === 1 ? 'stop charges' : 'stops charge'} an entry fee but do not publish the amount, so this is a minimum.`
    );
  }
  if (unpricedStops > 0) {
    notes.push(
      `No entry price is published for ${unpricedStops} ${unpricedStops === 1 ? 'stop' : 'stops'}, so they are not in the total.`
    );
  }
  if (foreignCurrencyCount > 0) {
    notes.push(
      `${foreignCurrencyCount} ${foreignCurrencyCount === 1 ? 'stop prices' : 'stops price'} in another currency and are shown but not counted, because converting them would need an exchange rate we do not have.`
    );
  }
  notes.push('Every figure here is a range from a named source, never a quote from the venue.');

  return {
    currency,
    basis: 'PER_PERSON',
    nights,
    days,
    stay,
    food,
    localTransit,
    entries: {
      lines,
      knownMin: r2(knownMin),
      knownMax: r2(knownMax),
      freeCount,
      unknownCount,
      foreignCurrencyCount
    },
    totalMin,
    totalMax,
    isFloor: missingTravellerRates || unknownCount > 0 || unpricedStops > 0 || foreignCurrencyCount > 0,
    pricedStops,
    unpricedStops,
    notes
  };
}

/** One plain sentence comparing the honest range to what the traveller said they can spend. */
export function compareToBudget(cost: TripCostModel, budget?: number): string | undefined {
  if (typeof budget !== 'number' || !Number.isFinite(budget) || budget <= 0) return undefined;
  if (cost.totalMin > budget) {
    return `Even the cheapest version of this plan, ${cost.totalMin} ${cost.currency}, is above your ${budget} ${cost.currency} budget.`;
  }
  if (cost.totalMax <= budget) {
    return `This plan fits inside your ${budget} ${cost.currency} budget.`;
  }
  return `This plan could reach ${cost.totalMax} ${cost.currency}, above your ${budget} ${cost.currency} budget.`;
}
