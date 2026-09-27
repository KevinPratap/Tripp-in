import { parsePhotonFeature, parsePhotonResponse } from './photon.provider';

/**
 * Photon's response shape, as its API documents it. The live service is not reachable from the build
 * environment, so these fixtures stand in for it and every assertion here is about our own handling:
 * which features we keep, what we derive, and what we refuse to invent. The shape itself still wants
 * one check against the real endpoint before release.
 */

const lisbon = {
  geometry: { type: 'Point', coordinates: [-9.1365919, 38.7077507] },
  type: 'Feature',
  properties: {
    osm_id: 1101953774,
    osm_type: 'R',
    country: 'Portugal',
    osm_key: 'place',
    countrycode: 'pt',
    osm_value: 'city',
    name: 'Lisbon',
    county: 'Lisbon',
    state: 'Lisbon',
    type: 'city'
  }
};

const tokyo = {
  geometry: { type: 'Point', coordinates: [139.7638947, 35.6828387] },
  type: 'Feature',
  properties: {
    osm_id: 1543125,
    osm_type: 'R',
    country: 'Japan',
    osm_key: 'place',
    countrycode: 'jp',
    osm_value: 'city',
    name: 'Tokyo',
    state: 'Tokyo',
    type: 'city'
  }
};

/** A cafe. Photon returns these for a city query and they are not somewhere you plan a trip to. */
const aCafe = {
  geometry: { type: 'Point', coordinates: [-9.14, 38.71] },
  type: 'Feature',
  properties: {
    osm_id: 999,
    osm_type: 'N',
    country: 'Portugal',
    osm_key: 'amenity',
    countrycode: 'pt',
    osm_value: 'cafe',
    name: 'Lisbon Coffee Roasters',
    state: 'Lisbon',
    type: 'house'
  }
};

describe('parsePhotonFeature', () => {
  it('reads a city into a suggestion a client can act on', () => {
    expect(parsePhotonFeature(lisbon)).toEqual({
      id: 'osm:R:1101953774',
      name: 'Lisbon',
      region: 'Lisbon',
      country: 'Portugal',
      countryCode: 'PT',
      currency: 'EUR',
      latitude: 38.7077507,
      longitude: -9.1365919,
      label: 'Lisbon, Portugal'
    });
  });

  it('reads the position longitude first, the way GeoJSON orders it', () => {
    // Getting this backwards would put Lisbon in the Atlantic off Liberia and the engine would plan
    // a trip around the wrong point entirely.
    const parsed = parsePhotonFeature(tokyo);
    expect(parsed?.latitude).toBeCloseTo(35.68, 2);
    expect(parsed?.longitude).toBeCloseTo(139.76, 2);
  });

  it('derives the currency from the country, which is the INR bug this exists to fix', () => {
    expect(parsePhotonFeature(lisbon)?.currency).toBe('EUR');
    expect(parsePhotonFeature(tokyo)?.currency).toBe('JPY');
  });

  it('drops a region that only repeats the name, so the label does not read "Lisbon, Lisbon"', () => {
    expect(parsePhotonFeature(lisbon)?.label).toBe('Lisbon, Portugal');
    expect(parsePhotonFeature(tokyo)?.label).toBe('Tokyo, Japan');
  });

  it('keeps the region when it adds something', () => {
    const springfield = {
      geometry: { type: 'Point', coordinates: [-89.65, 39.8] },
      properties: {
        osm_id: 1,
        osm_type: 'R',
        country: 'United States',
        osm_key: 'place',
        countrycode: 'us',
        osm_value: 'city',
        name: 'Springfield',
        state: 'Illinois'
      }
    };
    expect(parsePhotonFeature(springfield)?.label).toBe('Springfield, Illinois, United States');
  });

  it('refuses a cafe, a shop or a building', () => {
    expect(parsePhotonFeature(aCafe)).toBeNull();
  });

  it('refuses a feature with no usable coordinates rather than defaulting them to zero', () => {
    // A suggestion at 0,0 would silently plan a trip in the Gulf of Guinea.
    expect(parsePhotonFeature({ properties: { name: 'X', osm_key: 'place', osm_value: 'city' } })).toBeNull();
    expect(
      parsePhotonFeature({
        geometry: { coordinates: ['nope', 'nope'] },
        properties: { name: 'X', osm_key: 'place', osm_value: 'city' }
      })
    ).toBeNull();
    expect(
      parsePhotonFeature({
        geometry: { coordinates: [200, 100] },
        properties: { name: 'X', osm_key: 'place', osm_value: 'city' }
      })
    ).toBeNull();
  });

  it('refuses a nameless feature', () => {
    expect(
      parsePhotonFeature({ geometry: { coordinates: [1, 1] }, properties: { osm_key: 'place', osm_value: 'city' } })
    ).toBeNull();
  });

  it('says the currency is unknown rather than guessing, when the country is not one we know', () => {
    const antarctic = {
      geometry: { coordinates: [0, -75] },
      properties: { osm_id: 5, osm_type: 'N', osm_key: 'place', osm_value: 'region', name: 'Queen Maud Land', countrycode: 'aq' }
    };
    const parsed = parsePhotonFeature(antarctic);
    expect(parsed?.name).toBe('Queen Maud Land');
    expect(parsed?.currency).toBeNull();
  });

  it('still produces an id when OSM ids are missing', () => {
    const parsed = parsePhotonFeature({
      geometry: { coordinates: [12.5, 41.9] },
      properties: { name: 'Rome', osm_key: 'place', osm_value: 'city', countrycode: 'it' }
    });
    expect(parsed?.id).toBe('photon:41.9,12.5');
  });

  it('handles nothing at all', () => {
    expect(parsePhotonFeature(null)).toBeNull();
    expect(parsePhotonFeature(undefined)).toBeNull();
    expect(parsePhotonFeature({})).toBeNull();
  });
});

describe('parsePhotonResponse', () => {
  it('keeps the places and drops the rest', () => {
    const results = parsePhotonResponse({ features: [aCafe, lisbon, tokyo] }, 6);
    expect(results.map((r) => r.name)).toEqual(['Lisbon', 'Tokyo']);
  });

  it('de-duplicates a city OSM holds as both a node and a relation', () => {
    const asNode = { ...lisbon, properties: { ...lisbon.properties, osm_type: 'N', osm_id: 42 } };
    const results = parsePhotonResponse({ features: [lisbon, asNode] }, 6);
    expect(results).toHaveLength(1);
  });

  it('honours the limit after filtering, not before', () => {
    const results = parsePhotonResponse({ features: [aCafe, aCafe, lisbon, tokyo] }, 1);
    expect(results).toHaveLength(1);
    expect(results[0].name).toBe('Lisbon');
  });

  it('answers with an empty list for a malformed or empty body', () => {
    expect(parsePhotonResponse({ features: [] }, 6)).toEqual([]);
    expect(parsePhotonResponse({}, 6)).toEqual([]);
    expect(parsePhotonResponse(null, 6)).toEqual([]);
    expect(parsePhotonResponse('<html>502</html>', 6)).toEqual([]);
  });
});
