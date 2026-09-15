// Offizielles Logo (Team PushnPull, siehe Pflichtenheft-Deckblatt): abgerundetes Quadrat mit
// Kalenderraster und Tausch-Symbol. Feste Markenfarben, nicht über currentColor einfärbbar.
export function Logo({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" className={className} aria-hidden>
      <rect x="2" y="2" width="36" height="36" rx="8" stroke="#1E293B" strokeWidth="2.5" />
      <rect x="7" y="7.5" width="26" height="6" rx="1.5" fill="#1E293B" />
      <rect x="7" y="17" width="7" height="7" rx="1.5" fill="#D1D5DB" />
      <rect x="16.5" y="17" width="7" height="7" rx="1.5" fill="#F97316" />
      <rect x="26" y="17" width="7" height="7" rx="1.5" fill="#D1D5DB" />
      <rect x="7" y="26" width="7" height="7" rx="1.5" fill="#D1D5DB" />
      <rect x="16.5" y="26" width="7" height="7" rx="1.5" fill="#D1D5DB" />
      <rect x="26" y="26" width="7" height="7" rx="1.5" fill="#D1D5DB" />
      <path d="M18.5 19.3h4.4M21.4 18l1.5 1.3-1.5 1.3M21.5 21.7h-4.4M18.6 23l-1.5-1.3 1.5-1.3" stroke="#fff" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}