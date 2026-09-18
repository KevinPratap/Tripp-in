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

export default function ComicRouteMap({ activities, height = '360px' }: ComicRouteMapProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);

  useEffect(() => {
    if (!mapContainerRef.current) return;

    // Filter valid coordinates
    const validPoints = activities
      .map((a, idx) => ({
        index: idx + 1,
        title: a.title,
        time: a.startTime ? `${a.startTime} - ${a.endTime}` : '',
        address: a.place?.formattedAddress || '',
        lat: a.place?.location?.latitude,
        lng: a.place?.location?.longitude
      }))
      .filter(
        (p): p is { index: number; title: string; time: string; address: string; lat: number; lng: number } =>
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

      // Carto Voyager / Positron High-contrast Clean Tiles
      L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; OpenStreetMap contributors &copy; CARTO',
        maxZoom: 19
      }).addTo(map);

      const latlngs: [number, number][] = validPoints.map((p) => [p.lat, p.lng]);

      // Add Red Dashed Route Trail
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

      // Add Custom Comic Markers
      validPoints.forEach((p) => {
        const customIcon = L.divIcon({
          className: 'comic-marker-wrapper',
          html: `
            <div style="
              background: #E11D48;
              color: #FFFFFF;
              border: 2px solid #18181B;
              box-shadow: 2.5px 2.5px 0px #18181B;
              font-family: var(--font-comic-display, sans-serif);
              font-weight: 900;
              font-size: 11px;
              width: 28px;
              height: 28px;
              display: flex;
              align-items: center;
              justify-content: center;
              border-radius: 4px;
              transform: translate(-14px, -14px);
            ">
              ${p.index}
            </div>
          `,
          iconSize: [28, 28],
          iconAnchor: [0, 0]
        });

        const popupContent = `
          <div style="font-family: sans-serif; padding: 4px; color: #18181B;">
            <div style="font-size: 9px; font-weight: 900; text-transform: uppercase; color: #E11D48; letter-spacing: 0.05em;">
              STOP 0${p.index} ${p.time ? `· ${p.time}` : ''}
            </div>
            <div style="font-size: 13px; font-weight: 800; text-transform: uppercase; margin-top: 2px;">
              ${p.title}
            </div>
            ${p.address ? `<div style="font-size: 11px; color: #52525B; margin-top: 2px;">${p.address}</div>` : ''}
          </div>
        `;

        L.marker([p.lat, p.lng], { icon: customIcon })
          .addTo(map)
          .bindPopup(popupContent);
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
      <div className="bg-[#FAF8F5] border-b-2 border-[#18181B] px-4 py-2.5 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 bg-[#E11D48] border border-[#18181B]" />
          <span className="font-display font-black text-xs uppercase tracking-wider text-[#18181B]">
            Interactive Route Radar
          </span>
        </div>
        <span className="text-[10px] font-bold text-[#52525B] uppercase tracking-wider">
          Carto // OpenStreetMap
        </span>
      </div>
      <div ref={mapContainerRef} style={{ height }} className="w-full relative z-0" />
    </div>
  );
}
