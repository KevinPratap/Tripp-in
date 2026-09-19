import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Trip Field Ticket',
  description:
    'A verified Trippin itinerary: day by day stops with transit times, addresses and calendar export.'
};

export default function TripLayout({
  children
}: {
  children: React.ReactNode;
}) {
  return children;
}
