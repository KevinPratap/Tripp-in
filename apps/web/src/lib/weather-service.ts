/**
 * Client-side weather forecast utility powered by Open-Meteo (100% Free).
 */

export interface DayWeather {
  date: string;
  condition: string;
  tempMax: number;
  tempMin: number;
  precipitationProbability: number;
  windSpeed: number;
  isRainy: boolean;
  advisoryNote?: string;
}

export async function fetchTripWeather(
  destination: string,
  lat?: number,
  lng?: number
): Promise<DayWeather[]> {
  try {
    let resolvedLat = lat;
    let resolvedLng = lng;

    if (!resolvedLat || !resolvedLng) {
      const geoUrl = `https://photon.komoot.io/api/?q=${encodeURIComponent(destination)}&limit=1`;
      const geoRes = await fetch(geoUrl);
      if (geoRes.ok) {
        const geoData = await geoRes.json();
        const coords = geoData?.features?.[0]?.geometry?.coordinates;
        if (coords) {
          resolvedLng = coords[0];
          resolvedLat = coords[1];
        }
      }
    }

    if (!resolvedLat || !resolvedLng) {
      return getFallbackWeather();
    }

    const weatherUrl = `https://api.open-meteo.com/v1/forecast?latitude=${resolvedLat}&longitude=${resolvedLng}&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max&timezone=auto`;
    const res = await fetch(weatherUrl);
    if (!res.ok) throw new Error(`Open-Meteo error HTTP ${res.status}`);

    const data = await res.json();
    const daily = data?.daily;
    if (!daily || !daily.time) return getFallbackWeather();

    return daily.time.slice(0, 7).map((date: string, i: number) => {
      const code = daily.weather_code[i] ?? 0;
      const precipProb = daily.precipitation_probability_max[i] ?? 0;
      const tMax = Math.round(daily.temperature_2m_max[i] ?? 22);
      const tMin = Math.round(daily.temperature_2m_min[i] ?? 14);
      const isRainy = precipProb >= 50 || (code >= 51 && code <= 67) || (code >= 80 && code <= 82);

      let advisoryNote: string | undefined;
      if (isRainy) {
        advisoryNote = 'Rain contingency: outdoor spots may get wet, umbrella advised.';
      } else if (code >= 95) {
        advisoryNote = 'Thunderstorm warning: seek indoor cultural spots.';
      }

      return {
        date,
        condition: mapWmoToText(code),
        tempMax: tMax,
        tempMin: tMin,
        precipitationProbability: precipProb,
        windSpeed: Math.round(daily.wind_speed_10m_max[i] ?? 10),
        isRainy,
        advisoryNote,
      };
    });
  } catch (err) {
    console.warn('Weather fetch error:', err);
    return getFallbackWeather();
  }
}

function mapWmoToText(code: number): string {
  if (code === 0) return '☀️ Clear Skies';
  if (code === 1 || code === 2) return '⛅ Mainly Clear';
  if (code === 3) return '☁️ Overcast';
  if (code >= 51 && code <= 55) return '🌦️ Light Drizzle';
  if (code >= 61 && code <= 65) return '🌧️ Rain Showers';
  if (code >= 71 && code <= 77) return '❄️ Snow Flurries';
  if (code >= 80 && code <= 82) return '⛈️ Heavy Rain';
  if (code >= 95) return '⚡ Thunderstorms';
  return '⛅ Fair';
}

function getFallbackWeather(): DayWeather[] {
  const today = new Date();
  return Array.from({ length: 5 }, (_, i) => {
    const d = new Date(today);
    d.setDate(today.getDate() + i);
    return {
      date: d.toISOString().split('T')[0],
      condition: '☀️ Clear Skies',
      tempMax: 24,
      tempMin: 16,
      precipitationProbability: 10,
      windSpeed: 12,
      isRainy: false,
    };
  });
}
