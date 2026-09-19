/**
 * RFC-5545 compliant iCalendar (.ics) generator.
 * Produces calendar files compatible with Apple Calendar, Google Calendar, Outlook, and Android.
 */

export function generateIcsContent(trip: any, itinerary: any): string {
  const destination = trip?.destination || trip?.destinationName || 'Destination';
  const nowStamp = new Date().toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z';
  const days = itinerary?.days || [];

  const events: string[] = [];

  for (const day of days) {
    const rawDate = day.date ? day.date.split('T')[0] : '2026-06-01';
    const [year, month, dayNum] = rawDate.split('-');

    const activities = day.activities || [];
    for (const act of activities) {
      const [startH, startM] = (act.startTime || '09:00').split(':');
      const [endH, endM] = (act.endTime || '11:00').split(':');

      const dtStart = `${year}${month}${dayNum}T${startH.padStart(2, '0')}${startM.padStart(2, '0')}00`;
      const dtEnd = `${year}${month}${dayNum}T${endH.padStart(2, '0')}${endM.padStart(2, '0')}00`;

      const title = (act.title || act.placeName || 'Field Activity').replace(/[,;\\]/g, ' ');
      const location = (act.place?.formattedAddress || `${title}, ${destination}`).replace(/[,;\\]/g, ' ');
      const navUrl = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${title}, ${destination}`)}`;
      
      const description = [
        act.reason || 'Planned itinerary stop',
        `Transit leg: ${act.travelTimeFromPreviousMinutes || act.travelTimeToNextMin || 0}m`,
        `Turn-by-turn Navigation: ${navUrl}`
      ].join('\n');

      events.push([
        'BEGIN:VEVENT',
        `UID:${act.id || Math.random().toString(36).substring(2)}@trippin.ai`,
        `DTSTAMP:${nowStamp}`,
        `DTSTART:${dtStart}`,
        `DTEND:${dtEnd}`,
        `SUMMARY:${title}`,
        `DESCRIPTION:${description}`,
        `LOCATION:${location}`,
        'STATUS:CONFIRMED',
        'END:VEVENT'
      ].join('\r\n'));
    }
  }

  return [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Trippin AI//NONSGML Field Itinerary v1.0//EN',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    `X-WR-CALNAME:${destination} Field Issue`,
    'X-WR-TIMEZONE:UTC',
    ...events,
    'END:VCALENDAR'
  ].join('\r\n');
}

export function downloadTripCalendar(trip: any, itinerary: any): void {
  const destination = trip?.destination || trip?.destinationName || 'Trip';
  const icsText = generateIcsContent(trip, itinerary);
  const blob = new Blob([icsText], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);

  const a = document.createElement('a');
  a.href = url;
  a.download = `${destination.toLowerCase().replace(/[^a-z0-9]/g, '_')}_itinerary.ics`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
