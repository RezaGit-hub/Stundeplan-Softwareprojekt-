// Wochenraster (SPEZIFIKATION 6.3, W3): CSS-Grid 64 px Zeitachse + ein Tag je Spalte, 36 px Kopf, 72 px je Zeitfenster.
// Entwurf und Woche liefern LessonDto bzw. EntryDto; grid-entry.ts normalisiert. Interaktiv (DnD, leere Zelle, Pin)
// nur, wenn `interactive` übergeben wird — die Seite entscheidet (nur Entwurf, nur Klassensicht).
import { Fragment, useMemo, useState, type CSSProperties } from 'react'
import type { ConflictDto, TimeslotDto, View, Weekday } from '@/api/types'
import { formatDay } from '@/lib/dates'
import { DAY_LABEL, DAY_SHORT, daysOf, lessonNumber, maxPositions } from '@/lib/timegrid'
import { cn } from '@/lib/utils'
import { DroppableCell, GridDndContext } from './dnd'
import { GridCell, type WeekGridInteractive } from './GridCell'
import { LessonTile } from './LessonTile'
import { TimeAxisCell } from './TimeAxis'
import { findByLessonId, groupByTimeslot, type GridEntry } from '@/lib/grid-entry'

export type { GridEntry } from '@/lib/grid-entry'

export interface WeekGridProps {
  /** GET /api/timegrid; Tage = Tage mit ≥ 1 Slot, Reihenfolge MO..SA */
  slots: TimeslotDto[]
  /** Entwurf: LessonDto[] (timeslotId != null); Woche: EntryDto[] */
  entries: GridEntry[]
  view: View
  /** Spalte tönen (bg-today) */
  todayDay?: Weekday
  /** Kopf „Mo 21.09."; ohne Datum nur „Mo" */
  dateByDay?: Partial<Record<Weekday, string>>
  /** Badge ⚠ n, Rahmen rot; nur SOFT → gestrichelt */
  conflictsByLessonId?: Map<number, ConflictDto[]>
  /** Klick-Klick-Rückfall: Quelle hervorheben */
  selectedLessonId?: number | null
  onEntryClick?(entry: GridEntry, anchor: HTMLElement): void
  /** nur Entwurf */
  interactive?: WeekGridInteractive
  className?: string
  /** Sprung aus der Konfliktliste: Kachel 1 s hervorheben */
  highlightLessonId?: number | null
  /** unterrichtsfreie Tage der Woche (schoolDay false) */
  closedDays?: Weekday[]
  /** Berechnung läuft: aria-busy, Inhalt bleibt sichtbar */
  busy?: boolean
}

function DayHeader({ day, date, isToday, closed, style }: { day: Weekday; date?: string; isToday: boolean; closed: boolean; style: CSSProperties }) {
  return (
    <div
      style={style}
      className={cn(
        'sticky top-0 z-10 flex items-center justify-center gap-1.5 border-b border-l border-grid-line bg-background text-xs font-medium text-foreground-subtle',
        isToday && 'font-semibold text-foreground',
        closed && 'bg-surface-sunken/60',
      )}
      title={DAY_LABEL[day]}
    >
      {isToday && <span className="size-1.5 rounded-full bg-primary" aria-label="heute" />}
      <span>{date ? formatDay(date) : DAY_SHORT[day]}</span>
      {closed && <span className="text-2xs font-normal">· unterrichtsfrei</span>}
    </div>
  )
}

export function WeekGrid(props: WeekGridProps) {
  const { slots, entries, view, todayDay, dateByDay, conflictsByLessonId, selectedLessonId, onEntryClick, interactive, className, highlightLessonId, closedDays, busy } = props
  const days = useMemo(() => daysOf(slots), [slots])
  const rows = maxPositions(slots)
  const cells = useMemo(() => groupByTimeslot(entries), [entries])
  const slotAt = useMemo(() => new Map(slots.map((s) => [`${s.day}-${s.position}`, s])), [slots])
  const [activeId, setActiveId] = useState<number | null>(null)
  const activeEntry = activeId != null ? findByLessonId(cells, activeId) : null

  const grid = (
    <div
      className={cn('week-grid-scroll overflow-x-auto rounded-lg border bg-card shadow-xs', className)}
      aria-busy={busy || undefined}
      aria-label="Wochenraster"
    >
      {/* --col-min ist die Mindestbreite einer Tagesspalte; der Druck setzt sie auf 0, damit alle Tage auf die Seite passen (index.css) */}
      <div
        className="week-grid grid min-w-fit"
        style={{ gridTemplateColumns: `76px repeat(${Math.max(days.length, 1)}, minmax(var(--col-min, 140px), 1fr))`, gridTemplateRows: `36px repeat(${rows}, 72px)` }}
      >
        <div className="sticky top-0 z-10 border-b border-grid-line bg-background" style={{ gridColumn: 1, gridRow: 1 }} />
        {days.map((d, i) => (
          <DayHeader key={d} day={d} date={dateByDay?.[d]} isToday={todayDay === d} closed={!!closedDays?.includes(d)} style={{ gridColumn: i + 2, gridRow: 1 }} />
        ))}
        {Array.from({ length: rows }, (_, r) => r + 1).map((position) => {
          const axisSlot = days.map((d) => slotAt.get(`${d}-${position}`)).find((s): s is TimeslotDto => !!s)
          return (
            <Fragment key={position}>
              <TimeAxisCell slot={axisSlot} number={axisSlot ? lessonNumber(axisSlot, slots) : null} style={{ gridColumn: 1, gridRow: position + 1 }} />
              {days.map((d, i) => {
                const slot = slotAt.get(`${d}-${position}`)
                const cell = {
                  slot, slots, day: d, position, view,
                  entries: slot ? (cells.get(slot.id) ?? []) : [],
                  isToday: todayDay === d,
                  closed: !!closedDays?.includes(d),
                  conflictsByLessonId, selectedLessonId, highlightLessonId, onEntryClick, interactive,
                  activeColor: activeEntry?.subjectColor ?? null,
                  style: { gridColumn: i + 2, gridRow: position + 1 },
                }
                return interactive && slot ? <DroppableCell key={d} id={slot.id} {...cell} /> : <GridCell key={d} {...cell} />
              })}
            </Fragment>
          )
        })}
      </div>
    </div>
  )

  if (!interactive) return grid
  return (
    <GridDndContext
      onDragStart={setActiveId}
      onDragEnd={(lessonId, timeslotId) => {
        setActiveId(null)
        if (lessonId != null && timeslotId != null) interactive.onMove(lessonId, timeslotId)
      }}
      overlay={activeEntry ? <LessonTile entry={activeEntry} view={view} conflicts={activeEntry.lessonId != null ? conflictsByLessonId?.get(activeEntry.lessonId) : undefined} ghost /> : null}
    >
      {grid}
    </GridDndContext>
  )
}
