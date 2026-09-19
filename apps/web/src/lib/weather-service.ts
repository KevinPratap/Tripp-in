/**
 * Trip weather lookup, powered by Open-Meteo.
 *
 * Rules this file must keep:
 * - Real forecast data only. If the lookup fails, or coordinates cannot be resolved,
 *   return an empty list. Never synthesize a forecast.
 * - Only return days inside the Open-Meteo forecast window that are also one of the
 *   trip's dates. A trip next January has no forecast yet, and the UI must say so
 *   rather than showing this week's weather for it.
 * - No emojis anywhere in the returned text.
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
  tripDates: string[] = [],
  lat?: number,
  lng?: number
): Promise<DayWeather[]> {
  try {
    let resolvedLat = lat;
    let resolvedLng = lng;

    if (resolvedLat === undefined || resolvedLng === undefined) {
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

    // No coordinates means no forecast. Return nothing and let the UI say so.
    if (resolvedLat === undefined || resolvedLng === undefined) {
      return [];
    }

    const weatherUrl = `https://api.open-meteo.com/v1/forecast?latitude=${resolvedLat}&longitude=${resolvedLng}&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max&timezone=auto`;
    const res = await fetch(weatherUrl);
    if (!res.ok) {
      throw new Error(`Open-Meteo error HTTP ${res.status}`);
    }

    const data = await res.json();
    const daily = data?.daily;
    if (!daily?.time?.length) {
      return [];
    }

    const byDate = new Map<string, DayWeather>();
    daily.time.forEach((date: string, i: number) => {
      const code = daily.weather_code?.[i] ?? 0;
      const precipProb = daily.precipitation_probability_max?.[i] ?? 0;
      const tMax = daily.temperature_2m_max?.[i];
      const tMin = daily.temperature_2m_min?.[i];

      // A day without real temperature values is not a usable forecast entry.
      if (tMax === null || tMax === undefined || tMin === null || tMin === undefined) {
        return;
      }

      const isRainy = precipProb >= 50 || (code >= 51 && code <= 67) || (code >= 80 && code <= 82);

      let advisoryNote: string | undefined;
      if (code >= 95) {
        advisoryNote = 'Thunderstorm risk. Indoor stops are the safer pick.';
      } else if (isRainy) {
        advisoryNote = 'Rain likely. Outdoor stops may need an umbrella or a swap.';
      }

      byDate.set(date, {
        date,
        condition: mapWmoToText(code),
        tempMax: Math.round(tMax),
        tempMin: Math.round(tMin),
        precipitationProbability: precipProb,
        windSpeed: Math.round(daily.wind_speed_10m_max?.[i] ?? 0),
        isRainy,
        advisoryNote
      });
    });

    if (tripDates.length === 0) {
      return [];
    }

    // Ordered by the trip's own days, and only days with a real forecast.
    return tripDates
      .map((date) => (date || '').slice(0, 10))
      .map((date) => byDate.get(date))
      .filter((entry): entry is DayWeather => Boolean(entry));
  } catch (err) {
    console.warn('Weather lookup unavailable:', err);
    return [];
  }
}

function mapWmoToText(code: number): string {
  if (code === 0) return 'Clear skies';
  if (code === 1 || code === 2) return 'Mainly clear';
  if (code === 3) return 'Overcast';
  if (code >= 45 && code <= 48) return 'Fog';
  if (code >= 51 && code <= 55) return 'Light drizzle';
  if (code >= 56 && code <= 57) return 'Freezing drizzle';
  if (code >= 61 && code <= 65) return 'Rain';
  if (code >= 66 && code <= 67) return 'Freezing rain';
  if (code >= 71 && code <= 77) return 'Snow';
  if (code >= 80 && code <= 82) return 'Heavy rain showers';
  if (code >= 85 && code <= 86) return 'Snow showers';
  if (code >= 95) return 'Thunderstorms';
  return 'Unsettled';
}
