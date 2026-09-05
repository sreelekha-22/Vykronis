import type { Metadata } from 'next';

import './globals.css';

export const metadata: Metadata = {
  title: 'Vykronis — Incident view',
  description: 'Vykronis UI: the incident view on top of operator observability',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}