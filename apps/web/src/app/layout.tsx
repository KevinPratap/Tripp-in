import './globals.css';
import type { Metadata } from 'next';
import { Space_Grotesk, Plus_Jakarta_Sans } from 'next/font/google';

const spaceGrotesk = Space_Grotesk({
  subsets: ['latin'],
  variable: '--font-comic-display',
  weight: ['500', '600', '700']
});

const jakarta = Plus_Jakarta_Sans({
  subsets: ['latin'],
  variable: '--font-comic-body',
  weight: ['400', '500', '600', '700', '800']
});

export const metadata: Metadata = {
  metadataBase: new URL(
    process.env.NEXT_PUBLIC_SITE_URL || 'https://web-production-a9ec6.up.railway.app'
  ),
  title: {
    default: "TRIPPIN' // The Comic Travel Itinerary Engine",
    template: "%s // TRIPPIN'"
  },
  description:
    'Conflict-free travel planning with graphic novel attitude. Every schedule is checked for opening hours, real transit physics, pace and budget before you see it.',
  applicationName: "Trippin' AI",
  openGraph: {
    type: 'website',
    siteName: "Trippin' AI",
    title: "TRIPPIN' // The Comic Travel Itinerary Engine",
    description:
      'Plan a route and watch a deterministic engine verify transit times, opening hours, pace and budget before the itinerary is locked.',
    url: '/'
  },
  twitter: {
    card: 'summary_large_image',
    title: "TRIPPIN' // The Comic Travel Itinerary Engine",
    description:
      'Deterministic itinerary validation for real trips: transit physics, opening hours, pace and budget.'
  },
  robots: { index: true, follow: true },
  // Static asset in public/ on purpose: Next.js metadata routes (icon.svg, robots.ts,
  // sitemap.ts) break the webpack build when the project path contains an apostrophe,
  // which is the case for this project folder.
  icons: {
    icon: '/favicon.svg',
    shortcut: '/favicon.svg',
    apple: '/favicon.svg'
  }
};

export default function RootLayout({
  children
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className={`${spaceGrotesk.variable} ${jakarta.variable}`}>
      <body className="min-h-screen font-sans bg-[#FAF8F5] text-[#18181B] antialiased selection:bg-[#E11D48] selection:text-white">
        {children}
      </body>
    </html>
  );
}
