// Klick-Matrix Tag × Stunde für bevorzugte und gesperrte Zeiten einer Lehrkraft.
// Zustand je Zelle: leer → bevorzugt → gesperrt → leer. Pausen sind nicht klickbar.
import { useMemo } from 'react'
import type { TimeslotDto, Weekday } from '@/api/types'
import { DAY_SHORT, daysOf, lessonNumber, maxPositions, slotsOfDay } from '@/lib/timegrid'
import { cn } from '@/lib/utils'

type CellState = 'none' | 'preferred' | 'blocked'

export function TeacherTimeGrid({ slots, preferred, blocked, onChange }: {
  slots: TimeslotDto[]
  preferred: number[]
  blocked: number[]
  onChange(next: { preferred: number[]; blocked: number[] }): void
}) {
  const days = useMemo(() => daysOf(slots), [slots])
  const rows = useMemo(() => maxPositions(slots), [slots])
  const preferredSet = new Set(preferred)
  const blockedSet = new Set(blocked)

  const stateOf = (id: number): CellState => (blockedSet.has(id) ? 'blocked' : preferredSet.has(id) ? 'preferred' : 'none')

  const cycle = (id: number) => {
    const state = stateOf(id)
    const nextPreferred = preferred.filter((x) => x !== id)
    const nextBlocked = blocked.filter((x) => x !== id)
    if (state === 'none') nextPreferred.push(id)
    else if (state === 'preferred') nextBlocked.push(id)
    onChange({ preferred: nextPreferred, blocked: nextBlocked })
  }

  const slotAt = (day: Weekday, position: number) => slotsOfDay(slots, day).find((s) => s.position === position)
  const positions = Array.from({ length: rows }, (_, i) => i + 1)
  // Beschriftung der Zeile: Stundennummer aus dem ersten Tag, der diese Position kennt
  const rowLabel = (position: number): string => {
    for (const d of days) {
      const s = slotAt(d, position)
      if (s) return s.isBreak ? 'Pause' : `${lessonNumber(s, slots)}.`
    }
    return String(position)
  }
  const rowIsBreak = (position: number) => days.every((d) => slotAt(d, position)?.isBreak ?? true)

  if (slots.length === 0) {
    return <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-foreground-subtle">Noch kein Stundenraster angelegt — Zeiten lassen sich erst danach festlegen.</p>
  }

  return (
    <div>
      <div
        className="grid select-none gap-px overflow-hidden rounded-md border bg-border text-xs"
        style={{ gridTemplateColumns: `2.75rem repeat(${days.length}, minmax(0, 1fr))` }}
        role="grid"
        aria-label="Bevorzugte und gesperrte Zeiten"
      >
        <div className="bg-surface-sunken" aria-hidden />
        {days.map((d) => (
          <div key={d} className="bg-surface-sunken py-1 text-center font-medium text-foreground-muted" role="columnheader">{DAY_SHORT[d]}</div>
        ))}
        {positions.map((position) => {
          const isBreak = rowIsBreak(position)
          return (
            <div key={position} className="contents" role="row">
              <div className={cn('flex items-center justify-center bg-surface-sunken tabular-nums', isBreak ? 'text-2xs text-break-foreground' : 'font-medium text-foreground-muted')} role="rowheader">
                {rowLabel(position)}
              </div>
              {days.map((d) => {
                const s = slotAt(d, position)
                if (!s || s.isBreak) {
                  return <div key={d} className="h-7 bg-break-row" aria-hidden />
                }
                const state = stateOf(s.id)
                return (
                  <button
                    key={d}
                    type="button"
                    role="gridcell"
                    aria-label={`${DAY_SHORT[d]} ${lessonNumber(s, slots)}. Stunde: ${LABEL[state]}`}
                    aria-pressed={state !== 'none'}
                    onClick={() => cycle(s.id)}
                    className={cn(
                      'relative h-7 bg-card transition-colors hover:bg-accent focus-visible:z-10',
                      state === 'preferred' && 'bg-primary/15 hover:bg-primary/25',
                      state === 'blocked' && 'bg-destructive/15 hover:bg-destructive/25',
                    )}
                  >
                    {state === 'blocked' && (
                      <svg className="absolute inset-0 size-full text-destructive/70" viewBox="0 0 10 10" preserveAspectRatio="none" aria-hidden>
                        <line x1="0" y1="10" x2="10" y2="0" stroke="currentColor" strokeWidth="0.6" vectorEffect="non-scaling-stroke" />
                      </svg>
                    )}
                    {state === 'preferred' && <span className="absolute inset-x-0 bottom-0 h-0.5 bg-primary" aria-hidden />}
                  </button>
                )
              })}
            </div>
          )
        })}
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-2xs text-foreground-subtle">
        <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-xs border bg-card" aria-hidden />keine Angabe</span>
        <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-xs border bg-primary/15" aria-hidden />bevorzugt</span>
        <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-xs border bg-destructive/15" aria-hidden />gesperrt</span>
        <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-xs border bg-break-row" aria-hidden />Pause</span>
        <span className="ml-auto">Klick wechselt: keine Angabe → bevorzugt → gesperrt</span>
      </div>
    </div>
  )
}

const LABEL: Record<CellState, string> = { none: 'keine Angabe', preferred: 'bevorzugt', blocked: 'gesperrt' }
