import { currencyForCountry, isKnownCurrency } from './country-currency';

describe('currencyForCountry', () => {
  it('prices a trip in the destination currency, which is the case the planners got wrong', () => {
    // The Android planner opened every trip in INR and the web planner defaulted to USD. Both of
    // these resolve from the country the geocoder returns instead.
    expect(currencyForCountry('PT')).toBe('EUR');
    expect(currencyForCountry('JP')).toBe('JPY');
    expect(currencyForCountry('IN')).toBe('INR');
    expect(currencyForCountry('US')).toBe('USD');
    expect(currencyForCountry('GB')).toBe('GBP');
  });

  it('reads a lowercase code, because geocoders return one', () => {
    // Nominatim's address.country_code and Photon's countrycode are both lowercase.
    expect(currencyForCountry('pt')).toBe('EUR');
    expect(currencyForCountry('jp')).toBe('JPY');
  });

  it('tolerates surrounding whitespace', () => {
    expect(currencyForCountry(' de ')).toBe('EUR');
  });

  it('says it does not know rather than guessing a currency', () => {
    expect(currencyForCountry('ZZ')).toBeNull();
    expect(currencyForCountry('')).toBeNull();
    expect(currencyForCountry(null)).toBeNull();
    expect(currencyForCountry(undefined)).toBeNull();
  });

  it('rejects anything that is not a two letter code instead of part-matching it', () => {
    expect(currencyForCountry('PRT')).toBeNull();
    expect(currencyForCountry('P')).toBeNull();
    expect(currencyForCountry('Portugal')).toBeNull();
  });

  it('gives every eurozone member the euro, not just the large ones', () => {
    for (const code of ['AT', 'BE', 'CY', 'EE', 'FI', 'GR', 'HR', 'IE', 'LT', 'LU', 'LV', 'MT', 'SI', 'SK']) {
      expect({ code, currency: currencyForCountry(code) }).toEqual({ code, currency: 'EUR' });
    }
  });

  it('gives a territory the tender it actually uses, not its parent country default', () => {
    expect(currencyForCountry('HK')).toBe('HKD');
    expect(currencyForCountry('MO')).toBe('MOP');
    expect(currencyForCountry('GL')).toBe('DKK');
    expect(currencyForCountry('PR')).toBe('USD');
    expect(currencyForCountry('GF')).toBe('EUR');
  });

  it('returns a three letter ISO 4217 code for every country it knows', () => {
    // Guards against a typo in the table producing something the engine cannot price in.
    const codes = ['PT', 'JP', 'IN', 'US', 'BR', 'ZA', 'AU', 'TH', 'KE', 'MX', 'NO', 'CH'];
    for (const code of codes) {
      const currency = currencyForCountry(code);
      expect({ code, valid: /^[A-Z]{3}$/.test(currency || '') }).toEqual({ code, valid: true });
    }
  });
});

describe('isKnownCurrency', () => {
  it('accepts a currency the table can price in', () => {
    expect(isKnownCurrency('EUR')).toBe(true);
    expect(isKnownCurrency('jpy')).toBe(true);
  });

  it('rejects anything else, so an unpriceable currency never reaches the engine', () => {
    expect(isKnownCurrency('XXX')).toBe(false);
    expect(isKnownCurrency('')).toBe(false);
    expect(isKnownCurrency(null)).toBe(false);
  });
});
