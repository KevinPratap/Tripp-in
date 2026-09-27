/**
 * Country to currency, keyed on the ISO 3166-1 alpha-2 code a geocoder returns.
 *
 * The web planner used to carry a hand-written table of about two hundred city names mapped to
 * currencies, and the Android planner carried nothing at all and opened every trip in INR. Both are
 * the same mistake in different directions: a city name is not a reliable key. "Springfield" is in
 * a dozen countries, a table of cities is never finished, and the moment a traveller types a city
 * the table has not heard of, the guess is silently wrong.
 *
 * A country code is the right key. The geocoder already resolves the city to a country, so this maps
 * that country to its legal tender and the client is told the answer rather than working it out. One
 * source of truth, and nothing to drift.
 *
 * Unknown codes return null on purpose. A null means the caller says it does not know and asks the
 * traveller, which is the product's first invariant: never invent a value.
 */

/** ISO 3166-1 alpha-2 to ISO 4217. Sovereign states and the territories that use their own tender. */
const CURRENCY_BY_COUNTRY: Record<string, string> = {
  // Eurozone
  AD: 'EUR', AT: 'EUR', BE: 'EUR', CY: 'EUR', DE: 'EUR', EE: 'EUR', ES: 'EUR', FI: 'EUR',
  FR: 'EUR', GR: 'EUR', HR: 'EUR', IE: 'EUR', IT: 'EUR', LT: 'EUR', LU: 'EUR', LV: 'EUR',
  MC: 'EUR', ME: 'EUR', MT: 'EUR', NL: 'EUR', PT: 'EUR', SI: 'EUR', SK: 'EUR', SM: 'EUR',
  VA: 'EUR', XK: 'EUR',

  // Rest of Europe
  AL: 'ALL', BA: 'BAM', BG: 'BGN', BY: 'BYN', CH: 'CHF', CZ: 'CZK', DK: 'DKK', FO: 'DKK',
  GB: 'GBP', GE: 'GEL', GG: 'GBP', GI: 'GIP', HU: 'HUF', IM: 'GBP', IS: 'ISK', JE: 'GBP',
  LI: 'CHF', MD: 'MDL', MK: 'MKD', NO: 'NOK', PL: 'PLN', RO: 'RON', RS: 'RSD', RU: 'RUB',
  SE: 'SEK', SJ: 'NOK', UA: 'UAH',

  // Asia
  AE: 'AED', AF: 'AFN', AM: 'AMD', AZ: 'AZN', BD: 'BDT', BH: 'BHD', BN: 'BND', BT: 'BTN',
  CN: 'CNY', HK: 'HKD', ID: 'IDR', IL: 'ILS', IN: 'INR', IQ: 'IQD', IR: 'IRR', JO: 'JOD',
  JP: 'JPY', KG: 'KGS', KH: 'KHR', KP: 'KPW', KR: 'KRW', KW: 'KWD', KZ: 'KZT', LA: 'LAK',
  LB: 'LBP', LK: 'LKR', MM: 'MMK', MN: 'MNT', MO: 'MOP', MV: 'MVR', MY: 'MYR', NP: 'NPR',
  OM: 'OMR', PH: 'PHP', PK: 'PKR', PS: 'ILS', QA: 'QAR', SA: 'SAR', SG: 'SGD', SY: 'SYP',
  TH: 'THB', TJ: 'TJS', TM: 'TMT', TR: 'TRY', TW: 'TWD', UZ: 'UZS', VN: 'VND', YE: 'YER',

  // Africa
  AO: 'AOA', BF: 'XOF', BI: 'BIF', BJ: 'XOF', BW: 'BWP', CD: 'CDF', CF: 'XAF', CG: 'XAF',
  CI: 'XOF', CM: 'XAF', CV: 'CVE', DJ: 'DJF', DZ: 'DZD', EG: 'EGP', ER: 'ERN', ET: 'ETB',
  GA: 'XAF', GH: 'GHS', GM: 'GMD', GN: 'GNF', GQ: 'XAF', GW: 'XOF', KE: 'KES', KM: 'KMF',
  LR: 'LRD', LS: 'LSL', LY: 'LYD', MA: 'MAD', MG: 'MGA', ML: 'XOF', MR: 'MRU', MU: 'MUR',
  MW: 'MWK', MZ: 'MZN', NA: 'NAD', NE: 'XOF', NG: 'NGN', RW: 'RWF', SC: 'SCR', SD: 'SDG',
  SL: 'SLE', SN: 'XOF', SO: 'SOS', SS: 'SSP', ST: 'STN', SZ: 'SZL', TD: 'XAF', TG: 'XOF',
  TN: 'TND', TZ: 'TZS', UG: 'UGX', ZA: 'ZAR', ZM: 'ZMW', ZW: 'ZWG',

  // Americas
  AG: 'XCD', AI: 'XCD', AR: 'ARS', AW: 'AWG', BB: 'BBD', BL: 'EUR', BM: 'BMD', BO: 'BOB',
  BR: 'BRL', BS: 'BSD', BZ: 'BZD', CA: 'CAD', CL: 'CLP', CO: 'COP', CR: 'CRC', CU: 'CUP',
  CW: 'XCG', DM: 'XCD', DO: 'DOP', EC: 'USD', FK: 'FKP', GD: 'XCD', GF: 'EUR', GL: 'DKK',
  GP: 'EUR', GT: 'GTQ', GY: 'GYD', HN: 'HNL', HT: 'HTG', JM: 'JMD', KN: 'XCD', KY: 'KYD',
  LC: 'XCD', MF: 'EUR', MQ: 'EUR', MS: 'XCD', MX: 'MXN', NI: 'NIO', PA: 'PAB', PE: 'PEN',
  PM: 'EUR', PR: 'USD', PY: 'PYG', SR: 'SRD', SV: 'USD', SX: 'XCG', TC: 'USD', TT: 'TTD',
  US: 'USD', UY: 'UYU', VC: 'XCD', VE: 'VES', VG: 'USD', VI: 'USD',

  // Oceania
  AS: 'USD', AU: 'AUD', CK: 'NZD', FJ: 'FJD', FM: 'USD', GU: 'USD', KI: 'AUD', MH: 'USD',
  MP: 'USD', NC: 'XPF', NF: 'AUD', NR: 'AUD', NU: 'NZD', NZ: 'NZD', PF: 'XPF', PG: 'PGK',
  PW: 'USD', SB: 'SBD', TK: 'NZD', TO: 'TOP', TV: 'AUD', VU: 'VUV', WF: 'XPF', WS: 'WST'
};

/**
 * The currency a trip to this country should be priced in, or null when the code is not one we
 * know. A null is a real answer: the caller asks the traveller rather than guessing.
 */
export function currencyForCountry(countryCode: string | null | undefined): string | null {
  if (!countryCode) return null;
  const code = countryCode.trim().toUpperCase();
  if (code.length !== 2) return null;
  return CURRENCY_BY_COUNTRY[code] ?? null;
}

/** True when this is a currency the engine can price a trip in. */
export function isKnownCurrency(currency: string | null | undefined): boolean {
  if (!currency) return false;
  const code = currency.trim().toUpperCase();
  return Object.values(CURRENCY_BY_COUNTRY).includes(code);
}
