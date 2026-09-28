import { ExploreService } from './explore.service';

describe('ExploreService', () => {
  const redis = { get: jest.fn().mockResolvedValue(null), set: jest.fn() } as any;
  const realFetch = global.fetch;
  afterEach(() => {
    global.fetch = realFetch;
    jest.clearAllMocks();
  });

  it('follows the streets when routing answers, and lists places beside the walk', async () => {
    global.fetch = jest.fn(async (url: any) => {
      if (String(url).includes('routing.openstreetmap.de')) {
        return {
          ok: true,
          json: async () => ({
            routes: [{ distance: 1234, duration: 900, geometry: { coordinates: [[0, 0], [0.005, 0], [0.01, 0]] } }]
          })
        };
      }
      return {
        ok: true,
        json: async () => ({
          elements: [{ type: 'node', id: 7, lat: 0.0003, lon: 0.004, tags: { amenity: 'cafe', name: 'Corner Cafe' } }]
        })
      };
    }) as any;
    const result = await new ExploreService(redis).route([
      { latitude: 0, longitude: 0 },
      { latitude: 0, longitude: 0.01 }
    ]);
    expect(result.legs[0]).toMatchObject({ source: 'OSRM', durationMinutes: 15, distanceMeters: 1234 });
    expect(result.legs[0].coordinates[1]).toEqual([0, 0.005]);
    expect(result.along.map((p) => p.name)).toEqual(['Corner Cafe']);
    expect(redis.set).toHaveBeenCalled();
  });

  it('falls back to a straight line with no invented walking time, and does not cache it', async () => {
    global.fetch = jest.fn(async () => {
      throw new Error('offline');
    }) as any;
    const result = await new ExploreService(redis).route([
      { latitude: 0, longitude: 0 },
      { latitude: 0, longitude: 0.01 }
    ]);
    expect(result.legs[0].source).toBe('straight');
    expect(result.legs[0].durationMinutes).toBeNull();
    expect(result.along).toEqual([]);
    expect(redis.set).not.toHaveBeenCalled();
  });

  it('answers nearby with an empty list when OpenStreetMap is unreachable', async () => {
    global.fetch = jest.fn(async () => ({ ok: false, status: 504 })) as any;
    const places = await new ExploreService(redis).nearby({ latitude: 1, longitude: 1 }, 500);
    expect(places).toEqual([]);
  });
});
