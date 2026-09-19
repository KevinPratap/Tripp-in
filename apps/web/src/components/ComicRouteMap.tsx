'use client';

import React, { useEffect, useRef } from 'react';
import 'leaflet/dist/leaflet.css';

interface MapActivity {
  id?: string;
  title: string;
  startTime?: string;
  endTime?: string;
  place?: {
    name: string;
    formattedAddress?: string;
    location?: {
      latitude: number;
      longitude: number;
    };
  };
}

interface ComicRouteMapProps {
  activities: MapActivity[];
  height?: string;
}

/** Popup HTML is injected raw by Leaflet, so every value is escaped before it goes in. */
function escapeHtml(value: string): string {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export default function ComicRouteMap({ activities, height = '360px' }: ComicRouteMapProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);

  // A stop with no coordinates cannot be plotted. It is listed under the map so the number of pins
  // never quietly disagrees with the day's schedule.
  const unlocated = activities
    .map((a, idx) => {
      const lat = a.place?.location?.latitude;
      const lng = a.place?.location?.longitude;
      const hasCoords =
        typeof lat === 'number' &&
        typeof lng === 'number' &&
        !isNaN(lat) &&
        !isNaN(lng) &&
        (lat !== 0 || lng !== 0);
      return { index: idx + 1, title: a.title, hasCoords };
    })
    .filter((stop) => !stop.hasCoords);

  useEffect(() => {
    if (!mapContainerRef.current) return;

    // Filter valid coordinates
    const validPoints = activities
      .map((a, idx) => ({
        index: idx + 1,
        title: a.title,
        time: a.startTime ? `${a.startTime} to ${a.endTime || ''}`.trim() : '',
        address: a.place?.formattedAddress || '',
        placeName: a.place?.name || '',
        lat: a.place?.location?.latitude,
        lng: a.place?.location?.longitude
      }))
      .filter(
        (p): p is {
          index: number;
          title: string;
          time: string;
          address: string;
          placeName: string;
          lat: number;
          lng: number;
        } =>
          typeof p.lat === 'number' &&
          typeof p.lng === 'number' &&
          !isNaN(p.lat) &&
          !isNaN(p.lng) &&
          (p.lat !== 0 || p.lng !== 0)
      );

    if (validPoints.length === 0) return;

    let isMounted = true;

    import('leaflet').then((L) => {
      if (!isMounted || !mapContainerRef.current) return;

      // Clean previous map instance
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }

      const defaultCenter: [number, number] = [validPoints[0].lat, validPoints[0].lng];
      const map = L.map(mapContainerRef.current, {
        center: defaultCenter,
        zoom: 13,
        zoomControl: true,
        scrollWheelZoom: false
      });

      mapInstanceRef.current = map;

      // OpenStreetMap standard tiles: no watermark and no key. CARTO began serving the voyager
      // raster tiles with a large "API KEY REQUIRED" watermark, which is not acceptable on a paid product.
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors',
        maxZoom: 19
      }).addTo(map);

      const latlngs: [number, number][] = validPoints.map((p) => [p.lat, p.lng]);

      // Add Ink outline then crimson dashed route trail
      if (latlngs.length > 1) {
        L.polyline(latlngs, {
          color: '#18181B',
          weight: 5,
          opacity: 0.95
        }).addTo(map);

        L.polyline(latlngs, {
          color: '#E11D48',
          weight: 3,
          dashArray: '6, 6',
          opacity: 1
        }).addTo(map);
      }

      // Numbered comic markers, matching the stop order on the day card
      validPoints.forEach((p) => {
        const customIcon = L.divIcon({
          className: 'comic-marker-wrapper',
          html: `
            <div style="
              position: relative;
              background: #E11D48;
              color: #FFFFFF;
              border: 2.5px solid #18181B;
              font-family: var(--font-comic-display, sans-serif);
              font-weight: 900;
              font-size: 13px;
              line-height: 1;
              width: 30px;
              height: 30px;
              display: flex;
              align-items: center;
              justify-content: center;
              border-radius: 6px;
            ">
              ${p.index}
            </div>
          `,
          iconSize: [30, 30],
          iconAnchor: [15, 15],
          popupAnchor: [0, -16]
        });

        const directionsUrl = `https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lng}&travelmode=walking`;
        const addressLine = p.address
          ? `<div style="font-size: 11px; color: #52525B; line-height: 1.35; margin-top: 4px;">${escapeHtml(p.address)}</div>`
          : '';

        const popupContent = `
          <div style="font-family: sans-serif; padding: 2px; color: #18181B; min-width: 210px;">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span style="background: #E11D48; color: #FFFFFF; border: 2px solid #18181B; font-size: 10px; font-weight: 900; padding: 1px 6px; border-radius: 4px;">
                STOP ${String(p.index).padStart(2, '0')}
              </span>
              ${p.time ? `<span style="font-size: 10px; font-weight: 800; text-transform: uppercase; color: #52525B;">${escapeHtml(p.time)}</span>` : ''}
            </div>
            <div style="font-size: 13px; font-weight: 900; text-transform: uppercase; margin-top: 6px; line-height: 1.2;">
              ${escapeHtml(p.title)}
            </div>
            ${addressLine}
            <a
              href="${directionsUrl}"
              target="_blank"
              rel="noopener noreferrer"
              style="
                display: inline-flex;
                align-items: center;
                gap: 4px;
                margin-top: 10px;
                min-height: 44px;
                padding: 0 14px;
                background: #18181B;
                color: #FFFFFF;
                border: 2px solid #18181B;
                border-radius: 6px;
                font-size: 12px;
                font-weight: 900;
                text-transform: uppercase;
                letter-spacing: 0.04em;
                text-decoration: none;
              "
            >
              Navigate
            </a>
          </div>
        `;

        L.marker([p.lat, p.lng], { icon: customIcon })
          .addTo(map)
          .bindPopup(popupContent, { maxWidth: 280, autoPanPadding: [24, 24] });
      });

      // Fit bounds
      if (latlngs.length > 0) {
        map.fitBounds(latlngs, {
          padding: [40, 40],
          maxZoom: 15
        });
      }
    });

    return () => {
      isMounted = false;
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, [activities]);

  return (
    <div className="comic-panel rounded-2xl overflow-hidden relative bg-[#FAF8F5]">
      <div className="bg-[#FAF8F5] border-b-2 border-[#18181B] px-4 py-2.5 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 bg-[#E11D48] border border-[#18181B]" />
          <span className="font-display font-black text-xs uppercase tracking-wider text-[#18181B]">
            Route map
          </span>
        </div>
        <span className="text-[10px] font-bold text-[#52525B] uppercase tracking-wider hidden sm:inline">
          Carto // OpenStreetMap
        </span>
      </div>
      <div ref={mapContainerRef} style={{ height }} className="w-full relative z-0" />
      {unlocated.length > 0 && (
        <div className="bg-[#FAF8F5] border-t-2 border-[#18181B] px-4 py-2 space-y-1">
          {unlocated.map((stop) => (
            <div key={stop.index} className="flex items-start gap-2">
              <span className="w-5 h-5 shrink-0 bg-white border-2 border-[#18181B] text-[9px] font-black flex items-center justify-center mt-0.5">
                {String(stop.index).padStart(2, '0')}
              </span>
              <span className="text-[10px] font-bold uppercase tracking-wider text-[#52525B] leading-snug break-words">
                {stop.title} - no coordinates published, not plotted
              </span>
            </div>
          ))}
        </div>
      )}
      <div className="bg-[#FAF8F5] border-t-2 border-[#18181B] px-4 py-2">
        <span className="text-[10px] font-bold uppercase tracking-wider text-[#52525B]">
          Tap a numbered stop for its address and walking directions
        </span>
      </div>
    </div>
  );
}
