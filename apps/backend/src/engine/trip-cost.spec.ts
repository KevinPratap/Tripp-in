import { ItineraryModel } from '@trippin/shared-types';
import { buildTripCost, compareToBudget, CostAssumptions } from './trip-cost';

const act = (id: string, title: string, type: any, price?: any) => ({
  id,
  placeId: id,
  title,
  type,
  startTime: '09:00',
  endTime: '10:00',
  durationMinutes: 60,
  place: price ? { price } : undefined
});

const twoDayTrip = {
  days: [
    {
      id: 'd1',
      date: '2026-10-12',
      dayIndex: 1,
      activities: [
        act('a1', 'Free park', 'PARK', { status: 'FREE', source: 'OSM' }),
        act('a2', 'City museum', 'MUSEUM', {
          status: 'CHARGED_KNOWN',
          amountMin: 7.5,
          amountMax: 7.5,
          currency: 'EUR',
          source: 'OSM'
        }),
        act('a3', 'Dinner', 'RESTAURANT')
      ]
    },
    {
      id: 'd2',
      date: '2026-10-13',
      dayIndex: 2,
      activities: [
        act('a4', 'Gallery', 'MUSEUM', {
          status: 'CHARGED_KNOWN',
          amountMin: 10,
          amountMax: 10,
          currency: 'EUR',
          source: 'OSM'
        }),
        act('a5', 'Tower', 'ATTRACTION', { status: 'CHARGED_UNKNOWN', source: 'OSM', sourceDetail: 'fee=yes' }),
        act('a6', 'Old town', 'ATTRACTION')
      ]
    }
  ]
} as unknown as Pick<ItineraryModel, 'days'>;

const rates: CostAssumptions = {
  stayPerNightMin: 25,
  stayPerNightMax: 45,
  foodPerDayMin: 15,
  foodPerDayMax: 25,
  localTransitPerDayMin: 5,
  localTransitPerDayMax: 8
};

describe('buildTripCost', () => {
  it('totals the day rates and the published entry fees as a range', () => {
    const cost = buildTripCost(twoDayTrip, 'EUR', rates);
    expect(cost.nights).toBe(1);
    expect(cost.days).toBe(2);
    // stay 25-45 x 1 night, food 15-25 x 2 days, local transit 5-8 x 2 days, entries 17.50
    expect(cost.totalMin).toBe(round(25 + 30 + 10 + 17.5));
    expect(cost.totalMax).toBe(round(45 + 50 + 16 + 17.5));
  });

  it('never averages a range into a single number', () => {
    const cost = buildTripCost(twoDayTrip, 'EUR', rates);
    expect(cost.totalMin).not.toBe(cost.totalMax);
  });

  it('counts free stops, unstated fees and stops with no published price separately', () => {
    const cost = buildTripCost(twoDayTrip, 'EUR', rates);
    expect(cost.entries.freeCount).toBe(1);
    expect(cost.entries.unknownCount).toBe(1);
    expect(cost.unpricedStops).toBe(1);
    // Four stops carry price information: one free park, two museums with a published amount,
    // and the tower that charges without publishing an amount.
    expect(cost.pricedStops).toBe(4);
  });

  it('calls the total a floor when anything is unpriced, so it is never read as a ceiling', () => {
    const cost = buildTripCost(twoDayTrip, 'EUR', rates);
    expect(cost.isFloor).toBe(true);
    expect(cost.notes.join(' ')).toContain('minimum');
    expect(cost.notes.join(' ')).toContain('never a quote');
  });

  it('excludes the day rates when the traveller gave none, and says so', () => {
    const cost = buildTripCost(twoDayTrip, 'EUR', {});
    expect(cost.stay.source).toBe('NOT_SET');
    expect(cost.totalMin).toBe(17.5);
    expect(cost.notes.join(' ')).toContain('have not told us what you spend');
    // No rate given must never be silently read as zero spend.
    expect(cost.isFloor).toBe(true);
  });

  it('shows a foreign-currency price but keeps it out of the total', () => {
    const trip = {
      days: [
        {
          id: 'd1',
          date: '2026-10-12',
          dayIndex: 1,
          activities: [
            act('b1', 'Tokyo Tower', 'ATTRACTION', {
              status: 'CHARGED_KNOWN',
              amountMin: 1200,
              amountMax: 1200,
              currency: 'JPY',
              source: 'OSM'
            })
          ]
        }
      ]
    } as unknown as Pick<ItineraryModel, 'days'>;
    const cost = buildTripCost(trip, 'EUR', rates);
    expect(cost.entries.foreignCurrencyCount).toBe(1);
    expect(cost.entries.knownMin).toBe(0);
    expect(cost.entries.lines[0].excludedForeignCurrency).toBe(true);
    expect(cost.notes.join(' ')).toContain('exchange rate we do not have');
  });

  it('does not treat a restaurant with no price as an unpriced entry stop', () => {
    const trip = {
      days: [
        { id: 'd1', date: '2026-10-12', dayIndex: 1, activities: [act('c1', 'Lunch', 'RESTAURANT'), act('c2', 'Metro', 'TRANSIT')] }
      ]
    } as unknown as Pick<ItineraryModel, 'days'>;
    const cost = buildTripCost(trip, 'EUR', rates);
    expect(cost.unpricedStops).toBe(0);
    expect(cost.pricedStops).toBe(0);
  });
});

describe('compareToBudget', () => {
  it('says nothing when the traveller set no budget', () => {
    expect(compareToBudget(buildTripCost(twoDayTrip, 'EUR', rates), undefined)).toBeUndefined();
  });

  it('tells the truth when even the cheapest version is over budget', () => {
    const cost = buildTripCost(twoDayTrip, 'EUR', rates);
    expect(compareToBudget(cost, 50)).toContain('Even the cheapest version');
  });

  it('confirms when the whole range fits', () => {
    const cost = buildTripCost(twoDayTrip, 'EUR', rates);
    expect(compareToBudget(cost, 500)).toContain('fits inside');
  });

  it('warns when only the top of the range exceeds the budget', () => {
    const cost = buildTripCost(twoDayTrip, 'EUR', rates);
    expect(compareToBudget(cost, 120)).toContain('could reach');
  });
});

function round(n: number): number {
  return Math.round(n * 100) / 100;
}
