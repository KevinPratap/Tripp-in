import {
  distanceToSegmentMeters,
  kindOf,
  parsePoints,
  placeAlong,
  placesFromOverpass,
  thinPath,
  RouteLeg,
  ExplorePlace
} from './explore.logic';

describe('explore logic', () => {
  it('sorts OSM tags into the kinds a traveller scans for', () => {
    expect(kindOf({ amenity: 'cafe' })).toBe('coffee');
    expect(kindOf({ shop: 'bakery' })).toBe('sweets');
    expect(kindOf({ amenity: 'ice_cream' })).toBe('sweets');
    expect(kindOf({ amenity: 'restaurant' })).toBe('food');
    expect(kindOf({ amenity: 'pub' })).toBe('drinks');
    expect(kindOf({ tourism: 'viewpoint' })).toBe('viewpoint');
    expect(kindOf({ tourism: 'gallery' })).toBe('museum');
    expect(kindOf({ leisure: 'park' })).toBe('park');
    expect(kindOf({ historic: 'monument' })).toBe('sight');
    expect(kindOf({ amenity: 'bank' })).toBeNull();
  });

  it('keeps named places only, uses way centres, dedupes and sorts nearest first', () => {
    const origin = { latitude: 38.7, longitude: -9.2 };
    const places = placesFromOverpass(
      [
        { type: 'node', id: 1, lat: 38.71, lon: -9.2, tags: { amenity: 'cafe', name: 'Far Cafe' } },
        { type: 'way', id: 2, center: { lat: 38.7005, lon: -9.2 }, tags: { leisure: 'park', name: 'Near Park' } },
        { type: 'node', id: 3, lat: 38.7001, lon: -9.2, tags: { amenity: 'cafe' } },
        { type: 'node', id: 4, lat: 38.7002, lon: -9.2, tags: { amenity: 'bank', name: 'A Bank' } },
        { type: 'node', id: 5, lat: 38.7101, lon: -9.2, tags: { amenity: 'cafe', name: 'far cafe' } }
      ],
      origin,
      10
    );
    expect(places.map((p) => p.name)).toEqual(['Near Park', 'Far Cafe']);
    expect(places[0].id).toBe('osm_way_2');
    expect(places[0].openingHours).toBeUndefined();
    expect(places[0].source).toBe('OSM');
  });

  it('passes opening hours through exactly as OSM lists them', () => {
    const [p] = placesFromOverpass(
      [{ type: 'node', id: 9, lat: 1, lon: 1, tags: { amenity: 'cafe', name: 'X', opening_hours: 'Mo-Fr 08:00-18:00' } }],
      { latitude: 1, longitude: 1 },
      5
    );
    expect(p.openingHours).toBe('Mo-Fr 08:00-18:00');
  });

  it('measures how far a point sits off a segment', () => {
    const a = { latitude: 0, longitude: 0 };
    const b = { latitude: 0, longitude: 0.01 };
    const off = distanceToSegmentMeters({ latitude: 0.0005, longitude: 0.005 }, a, b);
    expect(off).toBeGreaterThan(50);
    expect(off).toBeLessThan(60);
  });

  it('pins each place to its nearest leg and drops the ones far off the route', () => {
    const legs: RouteLeg[] = [
      { coordinates: [[0, 0], [0, 0.01]], distanceMeters: 1100, durationMinutes: 14, source: 'OSRM' },
      { coordinates: [[0, 0.01], [0.01, 0.01]], distanceMeters: 1100, durationMinutes: 14, source: 'OSRM' }
    ];
    const place = (name: string, latitude: number, longitude: number): ExplorePlace => ({
      id: name, name, kind: 'coffee', latitude, longitude, distanceMeters: 0, source: 'OSM'
    });
    const along = placeAlong(
      [place('on first', 0.0002, 0.005), place('on second', 0.005, 0.0101), place('far', 0.005, 0.003)],
      legs,
      80,
      10
    );
    expect(along.map((p) => [p.name, p.legIndex])).toEqual([['on first', 0], ['on second', 1]]);
  });

  it('thins a long path but keeps both ends', () => {
    const path = Array.from({ length: 500 }, (_, i) => ({ latitude: i, longitude: 0 }));
    const thin = thinPath(path, 50);
    expect(thin).toHaveLength(50);
    expect(thin[0].latitude).toBe(0);
    expect(thin[49].latitude).toBe(499);
  });

  it('parses stop lists and refuses anything that is not a coordinate', () => {
    expect(parsePoints('38.7,-9.2;38.71,-9.21')).toEqual([
      { latitude: 38.7, longitude: -9.2 },
      { latitude: 38.71, longitude: -9.21 }
    ]);
    expect(parsePoints('91,0;0,0')).toBeNull();
  });
});
