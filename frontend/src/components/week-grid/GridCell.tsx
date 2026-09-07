// Eine Zelle des Rasters: leer (im Entwurf klickbar), eine Kachel, oder gestapelte Mini-Kacheln (Schulsicht, Doppelbelegung).
import { useState, type CSSProperties } from 'react'
import { Plus } from 'lucide-react'
import type { ConflictDto, TimeslotDto, View, Weekday } from '@/api/types'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { slotLongLabel } from '@/lib/timegrid'
import { cn } from '@/lib/utils'
import { DraggableTile, LessonTile, MiniTile } from './LessonTile'
import type { GridEntry, NormalizedEntry } from '@/lib/grid-entry'

export interface WeekGridInteractive {
  onMove(lessonId: number, timeslotId: number): void
  onEmptyCell(timeslotId: number): void
  onTogglePin(lessonId: number, pinned: boolean): void
}

export interface GridCellProps {
  slot: TimeslotDto | undefined
  slots: TimeslotDto[]
  day: Weekday
  position: number
  entries: NormalizedEntry[]
  view: View
  isToday: boolean
  /** unterrichtsfrei (Ferien): Zelle bleibt leer und grau */
  closed: boolean
  conflictsByLessonId?: Map<number, ConflictDto[]>
  selectedLessonId?: number | null
  highlightLessonId?: number | null
  onEntryClick?(entry: GridEntry, anchor: HTMLElement): void
  interactive?: WeekGridInteractive
  /** Fachfarbe der gezogenen Kachel (Zielzelle bekommt den Rahmen) */
  activeColor?: string | null
  /** von DroppableCell gesetzt */
  dropRef?(el: HTMLElement | null): void
  isOver?: boolean
  style?: CSSProperties
}

const MAX_VISIBLE = 3

export function GridCell(p: GridCellProps) {
  const { slot, slots, entries, view, isToday, closed, conflictsByLessonId, selectedLessonId, highlightLessonId, onEntryClick, interactive, activeColor, dropRef, isOver, style } = p
  const [moreOpen, setMoreOpen] = useState(false)
  const conflictsOf = (e: NormalizedEntry) => (e.lessonId != null ? conflictsByLessonId?.get(e.lessonId) : undefined)
  const tileProps = (e: NormalizedEntry) => ({
    entry: e,
    conflicts: conflictsOf(e),
    selected: selectedLessonId != null && e.lessonId === selectedLessonId,
    highlighted: highlightLessonId != null && e.lessonId === highlightLessonId,
    onClick: onEntryClick,
  })

  let content = null
  if (slot && !closed) {
    if (entries.length === 0) {
      if (interactive && !slot.isBreak) {
        const moving = selectedLessonId != null
        content = (
          <button
            type="button"
            className={cn(
              'flex h-full w-full items-center justify-center rounded-sm text-foreground-subtle transition-[opacity,background-color] hover:bg-accent focus-visible:opacity-100 focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none',
              moving ? 'opacity-60 hover:opacity-100 hover:ring-2 hover:ring-inset hover:ring-primary' : 'opacity-0 hover:opacity-100',
            )}
            aria-label={`${moving ? 'Hierher verschieben' : 'Stunde eintragen'}: ${slotLongLabel(slot, slots)}`}
            onClick={() => (moving ? interactive.onMove(selectedLessonId, slot.id) : interactive.onEmptyCell(slot.id))}
          >
            <Plus className="size-4" aria-hidden />
          </button>
        )
      }
    } else if (view === 'SCHOOL' || entries.length > 1) {
      const visible = entries.length <= MAX_VISIBLE ? entries : entries.slice(0, MAX_VISIBLE - 1)
      const rest = entries.slice(visible.length)
      content = (
        <div className="flex h-full flex-col gap-0.5">
          {visible.map((e) => (interactive && e.lessonId != null ? <DraggableTile key={e.key} {...tileProps(e)} view={view} mini /> : <MiniTile key={e.key} {...tileProps(e)} />))}
          {rest.length > 0 && (
            <Popover open={moreOpen} onOpenChange={setMoreOpen}>
              <PopoverTrigger asChild>
                <button type="button" className="h-[18px] w-fit rounded-sm bg-surface-sunken px-1.5 text-2xs font-medium text-foreground-muted hover:bg-accent">+{rest.length}</button>
              </PopoverTrigger>
              <PopoverContent align="start" className="w-64 gap-1">
                {rest.map((e) => <MiniTile key={e.key} {...tileProps(e)} onClick={(entry, anchor) => { setMoreOpen(false); onEntryClick?.(entry, anchor) }} />)}
              </PopoverContent>
            </Popover>
          )}
        </div>
      )
    } else {
      const e = entries[0]
      content = interactive && e.lessonId != null
        ? <DraggableTile {...tileProps(e)} view={view} onTogglePin={interactive.onTogglePin} />
        : <LessonTile {...tileProps(e)} view={view} />
    }
  }

  return (
    <div
      ref={dropRef}
      style={{ ...style, ...(activeColor ? ({ '--subject': activeColor } as CSSProperties) : {}) }}
      className={cn(
        'relative min-w-0 border-b border-l border-grid-line p-1',
        isToday && 'bg-today',
        !slot && 'bg-surface-sunken/40',
        slot?.isBreak && 'bg-break-row',
        closed && 'bg-surface-sunken/60',
        isOver && 'ring-2 ring-inset ring-(--subject)',
      )}
      aria-label={slot ? slotLongLabel(slot, slots) : undefined}
    >
      {content}
    </div>
  )
}
