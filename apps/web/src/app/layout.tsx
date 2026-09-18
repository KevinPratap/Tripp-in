import './globals.css';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: "Trippin' AI — Smart AI Travel Planning",
  description: "Generate verified, physics-checked travel itineraries in seconds."
};

export default function RootLayout({
  children
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-[#F8F9FA] text-[#1A1C1E] antialiased">
        {children}
      </body>
    </html>
  );
}
