import { parseEntryPrice, parseAmounts, describeEntryPrice } from './entry-price';

/**
 * The strings in this file are real values read out of OpenStreetMap in Lisbon and Porto during
 * development (600 venues sampled, about a quarter carried a fee tag). They are the contract:
 * if the parser stops handling one of these, a traveller sees a wrong or missing price.
 */
describe('parseEntryPrice from real OpenStreetMap tags', () => {
  it('reads a plain published amount', () => {
    expect(parseEntryPrice({ tourism: 'museum', charge: '5 EUR' })).toMatchObject({
      status: 'CHARGED_KNOWN',
      amountMin: 5,
      amountMax: 5,
      currency: 'EUR',
      source: 'OSM'
    });
  });

  it('reads a decimal amount', () => {
    expect(parseEntryPrice({ tourism: 'attraction', charge: '7.50 EUR' })).toMatchObject({
      status: 'CHARGED_KNOWN',
      amountMin: 7.5,
      amountMax: 7.5,
      currency: 'EUR'
    });
  });

  it('reads a European decimal comma', () => {
    expect(parseEntryPrice({ charge: '3,00 EUR' })).toMatchObject({ amountMin: 3, amountMax: 3 });
  });

  it('keeps a published range as a range instead of averaging it', () => {
    expect(parseEntryPrice({ tourism: 'museum', fee: '4-8€' })).toMatchObject({
      status: 'CHARGED_KNOWN',
      amountMin: 4,
      amountMax: 8,
      currency: 'EUR'
    });
  });

  it('treats fee=no as a real claim of free entry', () => {
    expect(parseEntryPrice({ leisure: 'park', fee: 'no' })).toMatchObject({
      status: 'FREE',
      source: 'OSM'
    });
  });

  it('treats a zero charge as free', () => {
    expect(parseEntryPrice({ fee: '0' })).toMatchObject({ status: 'FREE' });
  });

  it('says charged and unknown when a fee exists with no published amount', () => {
    expect(parseEntryPrice({ tourism: 'museum', fee: 'yes' })).toMatchObject({
      status: 'CHARGED_UNKNOWN'
    });
  });

  it('says charged and unknown rather than inventing a currency', () => {
    // A bare number is not a price we can total, and converting it would invent an exchange rate.
    expect(parseEntryPrice({ charge: '5' })).toMatchObject({ status: 'CHARGED_UNKNOWN' });
  });

  it('handles a donation request as charged and unknown', () => {
    expect(parseEntryPrice({ tourism: 'museum', fee: 'donation' })).toMatchObject({
      status: 'CHARGED_UNKNOWN'
    });
  });

  it('returns nothing at all when nothing is published, which is not free', () => {
    expect(parseEntryPrice({ tourism: 'museum' })).toBeUndefined();
    expect(parseEntryPrice(undefined)).toBeUndefined();
  });

  it('refuses a wildly implausible figure instead of printing it', () => {
    expect(parseEntryPrice({ charge: '1234567 EUR' })).toMatchObject({
      status: 'CHARGED_UNKNOWN'
    });
  });
});

describe('parseAmounts', () => {
  it('returns nothing for text with no number in it', () => {
    expect(parseAmounts('varies')).toEqual({});
  });

  it('keeps thousands from being read as cents', () => {
    expect(parseAmounts('1.500 EUR')).toMatchObject({ min: 1500, max: 1500 });
  });
});

describe('describeEntryPrice', () => {
  it('states the source in the sentence a traveller reads', () => {
    expect(describeEntryPrice({ status: 'FREE', source: 'OSM' })).toBe('Free entry (OpenStreetMap)');
    expect(
      describeEntryPrice({ status: 'CHARGED_KNOWN', amountMin: 7.5, amountMax: 7.5, currency: 'EUR', source: 'OSM' })
    ).toBe('7.5 EUR entry (OpenStreetMap)');
    expect(describeEntryPrice({ status: 'CHARGED_UNKNOWN', source: 'OSM' })).toBe(
      'Entry is charged, price not published (OpenStreetMap)'
    );
    expect(describeEntryPrice(undefined)).toBeUndefined();
  });
});
