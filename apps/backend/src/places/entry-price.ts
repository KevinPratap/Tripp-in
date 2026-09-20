import { EntryPriceModel } from '@trippin/shared-types';

/**
 * Turns the published OpenStreetMap price tags on a place into something we are allowed to show.
 *
 * The rules this product holds itself to:
 * - A price is only ever reported when the source states it. No tag means no price, which is
 *   NOT the same as free, so this returns undefined rather than a zero.
 * - `fee=no` is a real claim of free entry and is safe to show as free.
 * - `fee=yes` with no amount means the venue charges but publishes nothing: charged, amount unknown.
 * - A number in a currency we cannot see the trip in is left out of totals instead of converted.
 */

const SYMBOL_TO_CURRENCY: Record<string, string> = {
  '€': 'EUR',
  '$': 'USD',
  '£': 'GBP',
  '¥': 'JPY',
  '₹': 'INR',
  '₩': 'KRW',
  '฿': 'THB',
  R$: 'BRL'
};

const CURRENCY_CODES = [
  'EUR', 'USD', 'GBP', 'JPY', 'CHF', 'SEK', 'NOK', 'DKK', 'PLN', 'CZK', 'HUF',
  'INR', 'AUD', 'CAD', 'NZD', 'SGD', 'HKD', 'KRW', 'CNY', 'THB', 'TRY', 'MXN',
  'BRL', 'ZAR', 'AED', 'ILS', 'ISK', 'RON', 'BGN', 'HRK', 'RSD', 'UAH', 'VND',
  'IDR', 'MYR', 'PHP', 'TWD'
];

const FREE_WORDS = ['no', 'none', 'free', 'gratis', 'gratuit', '0'];
const CHARGED_WORDS = ['yes', 'varies', 'variable', 'interval', 'donation', 'donations', 'paid', 'some'];

/** A published figure in the thousands is almost certainly a typo or a phone number, not an entry fee. */
const SANITY_CEILING = 100000;

function detectCurrency(text: string): string | undefined {
  for (const [symbol, code] of Object.entries(SYMBOL_TO_CURRENCY)) {
    if (text.includes(symbol)) return code;
  }
  const upper = text.toUpperCase();
  const wordMatch = upper.match(/[A-Z]{3}/g);
  if (wordMatch) {
    const hit = wordMatch.find((w) => CURRENCY_CODES.includes(w));
    if (hit) return hit;
  }
  return undefined;
}

/**
 * Reads the numbers out of a published amount. Handles "5 EUR", "7.50 EUR", "4-8€" and the
 * European "3,00 EUR" decimal comma. Returns low and high so a textual range stays a range.
 */
export function parseAmounts(text: string): { min?: number; max?: number } {
  const normalised = text
    .replace(/(\d),(\d{2})(?!\d)/g, '$1.$2')
    .replace(/(\d)\.(?=\d{3}\b)/g, '$1');
  const raw = normalised.match(/\d+(?:\.\d{1,2})?/g);
  if (!raw) return {};
  const numbers = raw
    .map((n) => Number.parseFloat(n))
    .filter((n) => Number.isFinite(n) && n >= 0 && n <= SANITY_CEILING);
  if (numbers.length === 0) return {};
  return { min: Math.min(...numbers), max: Math.max(...numbers) };
}

/**
 * @param extratags the extra tags Nominatim already returns alongside every place (we ask with
 *   extratags=1, so this costs no extra request and no extra dependency).
 */
export function parseEntryPrice(extratags?: Record<string, any>): EntryPriceModel | undefined {
  if (!extratags) return undefined;

  const asText = (value: any): string | undefined =>
    typeof value === 'string' && value.trim().length > 0 ? value.trim() : undefined;

  const charge = asText(extratags.charge) ?? asText(extratags['fee:amount']);
  const fee = asText(extratags.fee);
  const value = charge ?? fee;
  if (!value) return undefined;

  const detail = charge ? `charge=${charge}` : `fee=${fee}`;
  const lower = value.toLowerCase();

  if (FREE_WORDS.includes(lower) || /^0(\.0+)?\s*[a-z€$£¥]*$/.test(lower)) {
    return { status: 'FREE', source: 'OSM', sourceDetail: detail };
  }

  const { min, max } = parseAmounts(value);
  if (min !== undefined && max !== undefined) {
    const currency = detectCurrency(value);
    if (currency) {
      return {
        status: 'CHARGED_KNOWN',
        amountMin: min,
        amountMax: max,
        currency,
        source: 'OSM',
        sourceDetail: detail
      };
    }
    // A number we cannot attach to a currency is not usable, and converting would invent a rate.
    return { status: 'CHARGED_UNKNOWN', source: 'OSM', sourceDetail: `amount without a currency (${detail})` };
  }

  if (CHARGED_WORDS.includes(lower) || lower.startsWith('yes')) {
    return { status: 'CHARGED_UNKNOWN', source: 'OSM', sourceDetail: detail };
  }

  // An unrecognised value is still an admission that something is published, but we will not
  // read a price out of it. Charged, amount unknown, and the raw value is kept for the traveller.
  return { status: 'CHARGED_UNKNOWN', source: 'OSM', sourceDetail: detail };
}

/** One plain sentence for the traveller, used next to a stop. */
export function describeEntryPrice(price?: EntryPriceModel): string | undefined {
  if (!price) return undefined;
  switch (price.status) {
    case 'FREE':
      return 'Free entry (OpenStreetMap)';
    case 'CHARGED_KNOWN': {
      const amount =
        price.amountMin === price.amountMax
          ? `${price.amountMin}`
          : `${price.amountMin} to ${price.amountMax}`;
      return `${amount} ${price.currency || ''}`.trim() + ' entry (OpenStreetMap)';
    }
    case 'CHARGED_UNKNOWN':
      return 'Entry is charged, price not published (OpenStreetMap)';
    default:
      return undefined;
  }
}
