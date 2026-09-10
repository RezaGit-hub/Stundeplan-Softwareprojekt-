// Kachel im Wochenraster (ui.md): drei Zeilen — Fachkürzel fett, zweite Zeile je Sicht, Raumkürzel. Farbe markiert die
// Änderung, nicht das Fach: Rahmen/Badges kommen aus lib/entry-kind.ts und dem Konfliktrahmen. Nie Farbe allein.
import type { CSSProperties, KeyboardEvent, MouseEvent, PointerEvent, ReactNode } from 'react'
import { motion, useReducedMotion } from 'motion/react'
import { Pin, TriangleAlert } from 'lucide-react'
import { useDraggable, type DraggableAttributes, type DraggableSyntheticListeners } from '@dnd-kit/core'
import type { ConflictDto, EntryKind, View } from '@/api/types'
import { ENTRY_KIND, entryLabel } from '@/lib/entry-kind'
import { tileHover } from '@/lib/motion'
import { cn } from '@/lib/utils'
import { ConflictPopover } from './ConflictPopover'
import { conflictFrame, conflictFrameClass, isLesson, type GridEntry, type NormalizedEntry } from '@/lib/grid-entry'

export interface TileDrag {
  setNodeRef(el: HTMLElement | null): void
  attributes: DraggableAttributes
  listeners: DraggableSyntheticListeners
  isDragging: boolean
}

export interface LessonTileProps {
  entry: NormalizedEntry
  view: View
  conflicts?: ConflictDto[]
  /** Klick-Klick: Quelle des Verschiebens */
  selected?: boolean
  /** Sprung aus der Konfliktliste (1 s) */
  highlighted?: boolean
  onClick?(entry: GridEntry, anchor: HTMLElement): void
  /** Klick auf den Pin hebt die Festlegung auf (nur Entwurf) */
  onTogglePin?(lessonId: number, pinned: boolean): void
  drag?: TileDrag
  /** Kopie im DragOverlay: halbtransparent, ohne Bewegung und ohne Interaktion */
  ghost?: boolean
  className?: string
}

const BADGE = 'inline-flex h-4 min-w-4 shrink-0 items-center justify-center gap-0.5 rounded-sm px-1 text-2xs font-bold leading-none'

const KIND_BADGE: Record<EntryKind, string> = {
  REGULAR: '',
  NEEDS_SUBSTITUTION: 'bg-substitution-soft text-substitution-fg ring-1 ring-inset ring-substitution',
  SUBSTITUTED: 'bg-substitution-soft text-substitution-fg ring-1 ring-inset ring-substitution',
  GIVEN_AWAY: '',
  MOVED: 'bg-substitution-soft text-substitution-fg ring-1 ring-inset ring-substitution',
  CANCELLED: 'bg-surface-sunken text-foreground-muted ring-1 ring-inset ring-border-strong',
  ADDED: 'bg-substitution-soft text-substitution-fg ring-1 ring-inset ring-substitution',
}

/** Geänderter Wert: alt durchgestrichen, neu fett in Vertretungs-Orange (Textton, nicht Rahmenton) */
export function Changed({ old, now }: { old?: string | null; now: string | null }) {
  if (!old || old === now) return <>{now ?? '–'}</>
  return (
    <>
      <s className="text-foreground-subtle">{old}</s>
      <span className="font-bold text-substitution-fg"> → {now ?? '?'}</span>
    </>
  )
}

function stop(e: MouseEvent | PointerEvent) {
  e.stopPropagation()
}

function Badges({ entry, conflicts, onTogglePin, ghost, mini = false }: { entry: NormalizedEntry; conflicts?: ConflictDto[]; onTogglePin?: LessonTileProps['onTogglePin']; ghost?: boolean; mini?: boolean }) {
  const ui = ENTRY_KIND[entry.kind]
  const label = isLesson(entry.source) ? '' : entryLabel(entry.source)
  const items: ReactNode[] = []
  if (entry.pinned && !mini) {
    items.push(
      onTogglePin && entry.lessonId != null && !ghost ? (
        <button
          key="pin"
          type="button"
          className={cn(BADGE, 'text-pinned hover:bg-surface-sunken')}
          aria-label="Festlegung aufheben"
          title="Festgelegt — Klick hebt die Festlegung auf"
          onClick={(e) => { stop(e); onTogglePin(entry.lessonId!, false) }}
          onPointerDown={stop}
        >
          <Pin className="size-3" aria-hidden />
        </button>
      ) : (
        <span key="pin" className={cn(BADGE, 'text-pinned')} aria-label="festgelegt" title="festgelegt"><Pin className="size-3" aria-hidden /></span>
      ),
    )
  }
  if (conflicts && conflicts.length > 0) {
    const badge = (
      <button
        key="conflict"
        type="button"
        className={cn(BADGE, 'bg-conflict-soft text-conflict hover:brightness-95')}
        aria-label={`${conflicts.length} ${conflicts.length === 1 ? 'Konflikt' : 'Konflikte'} anzeigen`}
        onClick={stop}
        onPointerDown={stop}
      >
        <TriangleAlert className="size-3" aria-hidden />
        {conflicts.length}
      </button>
    )
    items.push(ghost ? <span key="conflict" className={cn(BADGE, 'bg-conflict-soft text-conflict')}><TriangleAlert className="size-3" aria-hidden />{conflicts.length}</span> : <ConflictPopover key="conflict" conflicts={conflicts}>{badge}</ConflictPopover>)
  }
  if (ui.badge) {
    items.push(<span key="kind" className={cn(BADGE, KIND_BADGE[entry.kind])} aria-label={label || ui.label} title={label || ui.label}>{ui.badge}</span>)
  }
  if (items.length === 0) return null
  return <span className="flex shrink-0 items-center gap-0.5">{items.slice(0, 3)}</span>
}

function tileClasses(entry: NormalizedEntry, frame: ReturnType<typeof conflictFrame>) {
  return cn(
    ENTRY_KIND[entry.kind].className,
    conflictFrameClass(frame),
    entry.pinned && frame === 'none' && 'ring-1 ring-inset ring-pinned',
  )
}

export function LessonTile({ entry, view, conflicts, selected, highlighted, onClick, onTogglePin, drag, ghost, className }: LessonTileProps) {
  const reduce = useReducedMotion()
  const frame = conflictFrame(conflicts)
  const cancelled = entry.kind === 'CANCELLED'
  const label = isLesson(entry.source) ? '' : entryLabel(entry.source)
  const style = { '--subject': entry.subjectColor } as CSSProperties

  const line2: ReactNode =
    view === 'CLASS' ? <Changed old={entry.original?.teacherShort} now={entry.teacherShort} />
    : view === 'TEACHER' ? entry.classShort
    : <>{entry.classShort} <span className="text-foreground-subtle">·</span> <Changed old={entry.original?.teacherShort} now={entry.teacherShort} /></>

  const line3: ReactNode =
    entry.kind === 'GIVEN_AWAY' || cancelled ? <span className="italic">{label}</span>
    : view === 'ROOM' ? null
    : <Changed old={entry.original?.roomShort} now={entry.roomShort} />

  // Nur anklick- oder ziehbare Kacheln sind Schaltflächen (role/tabIndex unten). Reine Anzeige — das Raster der
  // Übersicht (StartPage gibt kein onEntryClick) und die Kopie im DragOverlay — bleibt eine beschriftete Gruppe:
  // ein Tabstopp, der auf Enter nichts tut, wäre eine falsche Zusage an Tastatur und Screenreader.
  const interactive = !ghost && !!(onClick || drag)
  const activate = (e: MouseEvent<HTMLElement> | KeyboardEvent<HTMLElement>) => {
    if (!onClick || ghost) return
    onClick(entry.source, e.currentTarget)
  }

  return (
    <motion.div
      ref={drag?.setNodeRef}
      {...(drag?.attributes ?? {})}
      {...(drag?.listeners ?? {})}
      role={interactive ? 'button' : 'group'}
      tabIndex={interactive ? 0 : undefined}
      data-lesson-id={entry.lessonId ?? undefined}
      aria-label={`${entry.subjectShort} ${entry.classShort}${entry.teacherShort ? `, ${entry.teacherShort}` : ''}${entry.roomShort ? `, ${entry.roomShort}` : ''}${label ? `, ${label}` : ''}`}
      title={label || undefined}
      style={style}
      whileHover={ghost || reduce || !interactive ? undefined : tileHover}
      onClick={activate}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); activate(e) } }}
      className={cn(
        'lesson-tile group/tile relative flex h-full w-full min-w-0 flex-col justify-start rounded-sm px-1.5 py-1 text-left outline-none select-none',
        'transition-shadow hover:shadow-sm focus-visible:ring-[3px] focus-visible:ring-ring/50',
        tileClasses(entry, frame),
        drag && 'cursor-grab active:cursor-grabbing',
        onClick && !drag && 'cursor-pointer',
        drag?.isDragging && 'opacity-40',
        selected && 'ring-2 ring-primary ring-offset-1 ring-offset-background',
        highlighted && 'ring-2 ring-primary shadow-md',
        ghost && 'opacity-50 shadow-md',
        className,
      )}
    >
      <div className="flex items-start justify-between gap-1">
        <span className={cn('truncate text-base leading-5 font-semibold', cancelled && 'line-through')}>{entry.subjectShort}</span>
        <Badges entry={entry} conflicts={conflicts} onTogglePin={onTogglePin} ghost={ghost} />
      </div>
      <div className="truncate text-xs leading-[18px]">{line2}</div>
      {line3 != null && <div className="truncate text-xs leading-[18px] text-foreground-muted tabular-nums">{line3}</div>}
    </motion.div>
  )
}

/** Einzeilige Mini-Kachel für die Schulsicht und für Doppelbelegungen: „7a Ma · R101" */
export function MiniTile({ entry, conflicts, selected, highlighted, onClick, drag, className }: Omit<LessonTileProps, 'view' | 'onTogglePin' | 'ghost'>) {
  const frame = conflictFrame(conflicts)
  const cancelled = entry.kind === 'CANCELLED'
  const label = isLesson(entry.source) ? '' : entryLabel(entry.source)
  const roomChanged = !!entry.original?.roomShort && entry.original.roomShort !== entry.roomShort
  const style = { '--subject': entry.subjectColor } as CSSProperties
  const interactive = !!(onClick || drag) // wie bei LessonTile: kein Tabstopp ohne Wirkung
  const activate = (e: MouseEvent<HTMLElement> | KeyboardEvent<HTMLElement>) => onClick?.(entry.source, e.currentTarget)
  return (
    <div
      ref={drag?.setNodeRef}
      {...(drag?.attributes ?? {})}
      {...(drag?.listeners ?? {})}
      role={interactive ? 'button' : 'group'}
      tabIndex={interactive ? 0 : undefined}
      data-lesson-id={entry.lessonId ?? undefined}
      title={label || undefined}
      style={style}
      onClick={activate}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); activate(e) } }}
      className={cn(
        'lesson-tile flex h-[18px] w-full min-w-0 items-center gap-1 rounded-sm px-1 text-xs leading-none outline-none select-none',
        'transition-shadow hover:shadow-sm focus-visible:ring-[3px] focus-visible:ring-ring/50',
        tileClasses(entry, frame),
        drag && 'cursor-grab active:cursor-grabbing',
        onClick && !drag && 'cursor-pointer',
        drag?.isDragging && 'opacity-40',
        selected && 'ring-2 ring-primary',
        highlighted && 'ring-2 ring-primary shadow-md',
        className,
      )}
    >
      <span className="shrink-0 font-semibold">{entry.classShort}</span>
      <span className={cn('shrink-0', cancelled && 'line-through')}>{entry.subjectShort}</span>
      <span className="shrink-0 text-foreground-subtle">·</span>
      <span className={cn('truncate tabular-nums', roomChanged ? 'font-bold text-substitution-fg' : 'text-foreground-muted')} title={roomChanged ? `vorher ${entry.original?.roomShort}` : undefined}>
        {entry.roomShort ?? '–'}
      </span>
      <span className="ml-auto shrink-0"><Badges entry={entry} conflicts={conflicts} mini /></span>
    </div>
  )
}

/** Kachel mit useDraggable — nur innerhalb von GridDndContext (dnd.tsx) benutzen */
export function DraggableTile({ mini = false, ...tile }: Omit<LessonTileProps, 'drag' | 'ghost'> & { mini?: boolean }) {
  const { setNodeRef, attributes, listeners, isDragging } = useDraggable({ id: tile.entry.lessonId ?? tile.entry.key })
  const drag = { setNodeRef, attributes, listeners, isDragging }
  return mini ? <MiniTile {...tile} drag={drag} /> : <LessonTile {...tile} drag={drag} />
}
