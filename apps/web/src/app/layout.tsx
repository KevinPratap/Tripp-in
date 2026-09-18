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
  title: "TRIPPIN' // The Comic Travel Itinerary Engine",
  description: "Conflict-free travel planning with graphic novel attitude. Real transit physics, zero hallucinations."
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
