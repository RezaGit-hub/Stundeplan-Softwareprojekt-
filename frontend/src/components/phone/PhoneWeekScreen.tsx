// Wochenansicht: Kachelmatrix Mo–Fr (Sa nur, wenn im Raster) × Stunden, Kürzel Fach/Klasse/Raum, gleiche Farblogik
// wie die Tagesansicht. Tipp auf Kachel → Detail, Tipp auf Tageskopf → Tagesansicht. Pausenzeile grau.
import type { CSSProperties } from 'react'
import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react'
import type { DayDto, EntryDto, EntryKind, TimeslotDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { addDaysIso, formatDay, startOfIsoWeek, weekLabel } from '@/lib/dates'
import { ENTRY_KIND } from '@/lib/entry-kind'
import { DAY_SHORT, daysOf, lessonNumber, maxPositions, slotsOfDay, weekdayIndex } from '@/lib/timegrid'
import { cn } from '@/lib/utils'
import { PhoneAppBar } from './PhoneFrame'
import { PhoneBanners, PhoneErrorState } from './PhoneDayScreen'
import type { UiState } from './phone-state'

export interface PhoneWeekScreenProps {
  ui: UiState
  monday: string
  today: string
  refreshing: boolean
  onPrev(): void
  onNext(): void
  onThisWeek(): void
  onDay(date: string): void
  onEntry(entry: EntryDto): void
  onRefresh(): void
  onSettings(): void
}

const LEGEND_KINDS: EntryKind[] = ['SUBSTITUTED', 'NEEDS_SUBSTITUTION', 'CANCELLED', 'ADDED']

function WeekTile({ entry, onClick }: { entry: EntryDto; onClick(): void }) {
  const ui = ENTRY_KIND[entry.kind]
  const cancelled = entry.kind === 'CANCELLED'
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn('lesson-tile relative flex h-full w-full flex-col overflow-hidden rounded-sm border border-transparent border-l-0 px-1 py-0.5 pl-2 text-left leading-tight', ui.className)}
      style={{ '--subject': entry.subjectColor } as CSSProperties}
      aria-label={`${entry.subjectShort} ${entry.classShort}${ui.label ? `, ${ui.label}` : ''}`}
    >
      <span aria-hidden className="absolute inset-y-0 left-0 w-0.75 bg-(--subject)" />
      <span className="flex items-center justify-between gap-1">
        <span className={cn('truncate text-xs font-semibold', cancelled && 'line-through')}>{entry.subjectShort}</span>
        {ui.badge && <span aria-hidden className={cn('text-2xs font-bold', cancelled ? 'text-cancelled' : 'text-substitution-fg')}>{ui.badge}</span>}
      </span>
      <span className="truncate text-2xs">{entry.classShort}</span>
      <span className="truncate text-2xs tabular-nums">{entry.roomShort ?? ''}</span>
    </button>
  )
}

function Grid({ slots, days, today, onDay, onEntry }: { slots: TimeslotDto[]; days: DayDto[]; today: string; onDay(date: string): void; onEntry(e: EntryDto): void }) {
  const weekdays = daysOf(slots)
  const rows = maxPositions(slots)
  const byDay = new Map(days.map((d) => [d.date, d]))
  const monday = days[0]?.date
  return (
    <div className="px-2">
      {/* Kopfzeile */}
      <div className="flex">
        <div className="w-8 shrink-0" />
        {weekdays.map((wd) => {
          const date = monday ? addDaysIso(monday, weekdayIndex(wd)) : ''
          const isToday = date === today
          return (
            <button
              key={wd}
              type="button"
              onClick={() => onDay(date)}
              className={cn('flex h-9 min-w-0 flex-1 flex-col items-center justify-center rounded-t-md text-xs hover:bg-accent', isToday && 'bg-today font-semibold')}
              aria-label={`Tagesansicht ${formatDay(date)}`}
            >
              <span>{DAY_SHORT[wd]}</span>
              <span className="text-2xs text-foreground-subtle tabular-nums">{date.slice(8, 10)}.{date.slice(5, 7)}.</span>
            </button>
          )
        })}
      </div>
      {/* Zeilen */}
      {Array.from({ length: rows }, (_, i) => i + 1).map((pos) => {
        const anySlot = slots.find((s) => s.position === pos)
        const isBreak = !!anySlot?.isBreak
        return (
          <div key={pos} className={cn('flex border-t border-grid-line', isBreak ? 'h-6 bg-break-row' : 'h-14')}>
            <div className={cn('flex w-8 shrink-0 flex-col items-center justify-center', isBreak ? 'text-2xs text-break-foreground' : '')}>
              {isBreak ? (
                <span>P</span>
              ) : (
                <>
                  <span className="text-sm leading-none font-bold tabular-nums">{anySlot ? lessonNumber(anySlot, slots) : ''}</span>
                  <span className="mt-0.5 text-2xs leading-none text-foreground-subtle tabular-nums">{anySlot?.startTime ?? ''}</span>
                </>
              )}
            </div>
            {weekdays.map((wd) => {
              const date = monday ? addDaysIso(monday, weekdayIndex(wd)) : ''
              const day = byDay.get(date)
              const slot = slotsOfDay(slots, wd).find((s) => s.position === pos)
              const entries = day?.entries.filter((e) => e.position === pos) ?? []
              const first = entries[0]
              return (
                <div key={wd} className={cn('min-w-0 flex-1 border-l border-grid-line p-0.5', date === today && 'bg-today', !slot && 'bg-surface-sunken/60')}>
                  {isBreak || !slot ? null : first ? (
                    <div className="relative h-full">
                      <WeekTile entry={first} onClick={() => onEntry(first)} />
                      {entries.length > 1 && <span className="absolute right-0.5 bottom-0.5 rounded-sm bg-surface px-1 text-2xs font-medium shadow-xs">+{entries.length - 1}</span>}
                    </div>
                  ) : day && !day.schoolDay ? (
                    <div className="h-full rounded-sm bg-surface-sunken/60" aria-hidden />
                  ) : null}
                </div>
              )
            })}
          </div>
        )
      })}
      <div className="border-t border-grid-line" />
    </div>
  )
}

export function PhoneWeekScreen({ ui, monday, today, refreshing, onPrev, onNext, onThisWeek, onDay, onEntry, onRefresh, onSettings }: PhoneWeekScreenProps) {
  return (
    <div className="flex h-full flex-col">
      <PhoneAppBar
        title="Woche"
        subtitle={weekLabel(monday)}
        actions={
          <Button variant="ghost" size="icon" className="rounded-full" onClick={onRefresh} aria-label="Aktualisieren" disabled={ui.kind === 'loading'}>
            <RefreshCw className={cn(refreshing && 'animate-spin')} />
          </Button>
        }
      />
      <div className="flex shrink-0 items-center justify-between px-3 pb-1">
        <Button variant="ghost" size="icon-sm" className="rounded-full" onClick={onPrev} aria-label="Vorherige Woche"><ChevronLeft /></Button>
        <Button
          variant={monday === startOfIsoWeek(today) ? 'secondary' : 'outline'}
          size="xs"
          className="rounded-full px-3"
          onClick={onThisWeek}
          aria-pressed={monday === startOfIsoWeek(today)}
        >
          Diese Woche
        </Button>
        <Button variant="ghost" size="icon-sm" className="rounded-full" onClick={onNext} aria-label="Nächste Woche"><ChevronRight /></Button>
      </div>
      <PhoneBanners ui={ui} showChanged />
      <div className="min-h-0 flex-1 overflow-y-auto pt-1 pb-3">
        {ui.kind === 'loading' && (
          <div className="space-y-1 px-2 pt-2">
            {[0, 1, 2, 3, 4, 5].map((i) => <Skeleton key={i} className="h-12 w-full rounded-md" />)}
          </div>
        )}
        {ui.kind === 'error' && <PhoneErrorState message={ui.message} onRetry={onRefresh} onSettings={onSettings} />}
        {ui.kind === 'data' && (
          <>
            <Grid slots={ui.slots} days={ui.week.days} today={today} onDay={onDay} onEntry={onEntry} />
            <ul className="mt-3 flex flex-wrap gap-x-3 gap-y-1 px-3 text-2xs text-foreground-subtle" aria-label="Legende">
              {LEGEND_KINDS.map((k) => (
                <li key={k} className="flex items-center gap-1">
                  <span className={cn('inline-flex size-4 items-center justify-center rounded-sm font-bold', k === 'CANCELLED' ? 'bg-surface-sunken text-cancelled' : 'bg-substitution text-primary-foreground')}>{ENTRY_KIND[k].badge}</span>
                  {ENTRY_KIND[k].label}
                </li>
              ))}
              <li className="flex items-center gap-1"><span className="inline-block h-3 w-4 rounded-sm bg-today" /> heute</li>
            </ul>
          </>
        )}
      </div>
    </div>
  )
}
