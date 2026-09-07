// Banner-Zone der App: Offline („Keine Verbindung — Stand: …") und „Heute geändert: n Stunden" (Soll, E-40).
import type { ReactNode } from 'react'
import { WifiOff, BellRing } from 'lucide-react'
import { cn } from '@/lib/utils'

export function PhoneBanner({ tone, children }: { tone: 'offline' | 'changed'; children: ReactNode }) {
  const Icon = tone === 'offline' ? WifiOff : BellRing
  return (
    <div
      role="status"
      className={cn(
        'flex items-center gap-2 px-4 py-2 text-xs font-medium',
        tone === 'offline' ? 'bg-offline text-offline-foreground' : 'bg-substitution-soft text-substitution-fg',
      )}
    >
      <Icon className="size-3.5 shrink-0" aria-hidden />
      <span>{children}</span>
    </div>
  )
}
