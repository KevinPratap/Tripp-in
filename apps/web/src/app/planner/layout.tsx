import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Route Planner',
  description:
    'Set a destination, dates, pace and budget. The deterministic engine verifies transit times, opening hours and cost before the itinerary is locked.'
};

export default function PlannerLayout({
  children
}: {
  children: React.ReactNode;
}) {
  return children;
}
