// Telefon-Gehäuse aus reinem CSS (Magic-UI-Vorbild, keine Abhängigkeit): 360 × 760 px Bildschirm, Kamera-Insel,
// Statusleiste mit Uhrzeit aus now(), Home-Indikator. `dark`/`light` (Klassen aus index.css) schalten das Theme nur im Telefon um.
import { useEffect, useState, type ReactNode } from 'react'
import { BatteryFull, Signal, Wifi } from 'lucide-react'
import { cn } from '@/lib/utils'
import { now } from '@/lib/today'

function clock(): string {
  return now().toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
}

/** Material-3-Top-App-Bar (56 px): optional Zurück/Icon links, Titel + Untertitel, Aktionen rechts */
export function PhoneAppBar({ title, subtitle, leading, actions }: { title: string; subtitle?: string; leading?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex h-14 shrink-0 items-center gap-1 px-2">
      {leading}
      <div className={cn('min-w-0 flex-1', !leading && 'pl-2')}>
        <div className="truncate text-base leading-tight font-semibold">{title}</div>
        {subtitle && <div className="truncate text-xs text-foreground-subtle">{subtitle}</div>}
      </div>
      {actions && <div className="flex shrink-0 items-center">{actions}</div>}
    </div>
  )
}

export function PhoneFrame({ children, dark = false, title }: { children: ReactNode; dark?: boolean; title?: string }) {
  const [time, setTime] = useState(clock)
  useEffect(() => {
    const id = window.setInterval(() => setTime(clock()), 30_000)
    return () => window.clearInterval(id)
  }, [])

  return (
    <div
      className={cn('phone-frame relative shrink-0 rounded-[2.5rem] border-8 border-neutral-900 bg-black shadow-lg', dark ? 'dark' : 'light')}
      role="region"
      aria-label={title ?? 'Telefon-Vorschau'}
    >
      <div className="relative flex h-[760px] w-[360px] flex-col overflow-hidden rounded-[2rem] bg-background text-foreground">
        {/* Statusleiste */}
        <div className="relative flex h-9 shrink-0 items-center justify-between px-5 text-xs font-semibold tabular-nums">
          <span>{time}</span>
          <span aria-hidden className="absolute top-2 left-1/2 h-5 w-20 -translate-x-1/2 rounded-full bg-black" />
          <span className="flex items-center gap-1" aria-hidden>
            <Signal className="size-3" />
            <Wifi className="size-3" />
            <BatteryFull className="size-3.5" />
          </span>
        </div>
        <div className="min-h-0 flex-1">{children}</div>
        {/* Home-Indikator */}
        <div aria-hidden className="pointer-events-none absolute bottom-1.5 left-1/2 h-1 w-28 -translate-x-1/2 rounded-full bg-foreground/25" />
      </div>
    </div>
  )
}
