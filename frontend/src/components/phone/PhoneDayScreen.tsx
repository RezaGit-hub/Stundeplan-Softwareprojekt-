// Tagesansicht (Startscreen der App): Kopf mit Wochentag + Datum, Tageswechsel, Banner-Zone, Liste der eigenen Stunden
// (links Stundennummer + Zeit, rechts EntryCard). Pull-to-Refresh ist im Browser ein Knopf und ein Hinweistext.
import type { ReactNode } from 'react'
import { motion } from 'motion/react'
import { ChevronLeft, ChevronRight, RefreshCw, CloudOff } from 'lucide-react'
import type { EntryDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/dates'
import { listItem } from '@/lib/motion'
import { DAY_LABEL, dayOfDate, lessonNumber, slotById } from '@/lib/timegrid'
import { cn } from '@/lib/utils'
import { PhoneAppBar } from './PhoneFrame'
import { PhoneBanner } from './PhoneBanner'
import { PhoneEntryCard } from './PhoneEntryCard'
import type { UiState } from './phone-state'

export interface PhoneDayScreenProps {
  ui: UiState
  date: string
  today: string
  refreshing: boolean
  onPrev(): void
  onNext(): void
  onToday(): void
  onRefresh(): void
  onEntry(entry: EntryDto): void
  onSettings(): void
}

function weekdayName(date: string): string {
  const d = dayOfDate(date)
  return d ? DAY_LABEL[d] : 'Sonntag'
}

/** Banner-Zone: Offline-Stand und „Heute geändert" — Woche und Tag teilen sie sich */
export function PhoneBanners({ ui, showChanged }: { ui: UiState; showChanged: boolean }) {
  if (ui.kind !== 'data') return null
  return (
    <>
      {ui.offline && <PhoneBanner tone="offline">Keine Verbindung — Stand: {ui.loadedAt}</PhoneBanner>}
      {showChanged && ui.changedToday > 0 && (
        <PhoneBanner tone="changed">Heute geändert: {ui.changedToday} {ui.changedToday === 1 ? 'Stunde' : 'Stunden'}</PhoneBanner>
      )}
    </>
  )
}

/** Fehlerzustand mit „Erneut versuchen" und „Einstellungen" (Tag und Woche) */
export function PhoneErrorState({ message, onRetry, onSettings }: { message: string; onRetry(): void; onSettings(): void }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 px-8 text-center">
      <span className="flex size-12 items-center justify-center rounded-full bg-surface-sunken text-foreground-subtle"><CloudOff className="size-6" /></span>
      <p className="text-sm">{message}</p>
      <div className="flex gap-2">
        <Button size="sm" className="rounded-full" onClick={onRetry}>Erneut versuchen</Button>
        <Button size="sm" variant="outline" className="rounded-full" onClick={onSettings}>Einstellungen</Button>
      </div>
    </div>
  )
}

export function PhoneDayScreen({ ui, date, today, refreshing, onPrev, onNext, onToday, onRefresh, onEntry, onSettings }: PhoneDayScreenProps) {
  const isToday = date === today
  const day = ui.kind === 'data' ? ui.week.days.find((d) => d.date === date) : undefined
  const slots = ui.kind === 'data' ? ui.slots : []
  const entries = day ? [...day.entries].sort((a, b) => a.position - b.position) : []

  let body: ReactNode
  if (ui.kind === 'loading') {
    body = (
      <ul className="space-y-3 px-4 pt-3">
        {[0, 1, 2, 3].map((i) => (
          <li key={i} className="flex gap-3">
            <Skeleton className="h-16 w-11 rounded-lg" />
            <Skeleton className="h-16 flex-1 rounded-xl" />
          </li>
        ))}
      </ul>
    )
  } else if (ui.kind === 'error') {
    body = <PhoneErrorState message={ui.message} onRetry={onRefresh} onSettings={onSettings} />
  } else if (ui.kind !== 'data') {
    body = null
  } else if (!day || !day.schoolDay) {
    body = <p className="px-6 pt-12 text-center text-sm text-foreground-subtle">{!day ? 'Kein Schultag' : 'Unterrichtsfrei'}</p>
  } else if (entries.length === 0) {
    body = <p className="px-6 pt-12 text-center text-sm text-foreground-subtle">Keine Stunden an diesem Tag</p>
  } else {
    body = (
      <ul className="space-y-3 px-4 pt-2 pb-4">
        {entries.map((e, i) => {
          const slot = slotById(slots, e.timeslotId)
          const n = slot ? lessonNumber(slot, slots) : null
          return (
            <motion.li key={`${e.date}-${e.timeslotId}-${e.lessonId ?? 'x'}-${e.kind}`} {...listItem(i)} className="flex items-stretch gap-3">
              <div className="flex w-11 shrink-0 flex-col items-center justify-center rounded-lg bg-surface-sunken py-1">
                <span className="text-lg leading-tight font-bold tabular-nums">{n ?? '–'}</span>
                <span className="text-2xs leading-tight text-foreground-subtle tabular-nums">{slot?.startTime ?? ''}</span>
                <span className="text-2xs leading-tight text-foreground-subtle tabular-nums">{slot?.endTime ?? ''}</span>
              </div>
              <PhoneEntryCard entry={e} onClick={() => onEntry(e)} />
            </motion.li>
          )
        })}
      </ul>
    )
  }

  return (
    <div className="flex h-full flex-col">
      <PhoneAppBar
        title={weekdayName(date)}
        subtitle={formatDate(date)}
        actions={
          <Button variant="ghost" size="icon" className="rounded-full" onClick={onRefresh} aria-label="Aktualisieren" disabled={ui.kind === 'loading'}>
            <RefreshCw className={cn(refreshing && 'animate-spin')} />
          </Button>
        }
      />
      <div className="flex shrink-0 items-center justify-between px-3 pb-2">
        <Button variant="ghost" size="icon-sm" className="rounded-full" onClick={onPrev} aria-label="Vorheriger Tag"><ChevronLeft /></Button>
        <Button
          variant={isToday ? 'secondary' : 'outline'}
          size="xs"
          className="rounded-full px-3"
          onClick={onToday}
          aria-pressed={isToday}
        >
          Heute
        </Button>
        <Button variant="ghost" size="icon-sm" className="rounded-full" onClick={onNext} aria-label="Nächster Tag"><ChevronRight /></Button>
      </div>
      <PhoneBanners ui={ui} showChanged={isToday} />
      <div className="min-h-0 flex-1 overflow-y-auto">
        {ui.kind === 'data' && (
          <p className="pt-1 pb-0.5 text-center text-2xs text-foreground-subtle/70" aria-hidden>
            {refreshing ? 'Wird aktualisiert…' : '↓ Zum Aktualisieren ziehen'}
          </p>
        )}
        {body}
      </div>
    </div>
  )
}
