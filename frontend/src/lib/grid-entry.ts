// Normalisierung für das Wochenraster: LessonDto (Entwurf, aktiver Plan) und EntryDto (aufgelöste Woche) werden auf
// eine gemeinsame Form gebracht. Das Raster färbt und beschriftet nur — kind, Konflikte, Warnungen kommen vom Server.
import type { ConflictDto, EntryDto, EntryKind, EntryOriginal, LessonDto, Weekday } from '@/api/types'

export type GridEntry = LessonDto | EntryDto

export interface NormalizedEntry {
  /** eindeutig innerhalb eines Rasters */
  key: string
  /** null nur bei Zusatzstunden (ADDED) */
  lessonId: number | null
  timeslotId: number
  day: Weekday
  position: number
  /** nur bei Wocheneinträgen */
  date: string | null
  classShort: string
  subjectShort: string
  subjectColor: string
  teacherShort: string | null
  roomShort: string | null
  kind: EntryKind
  pinned: boolean
  original: EntryOriginal | null
  reasons: string[]
  source: GridEntry
}

export function isLesson(e: GridEntry): e is LessonDto {
  return 'pinned' in e
}

/** null = offene Stunde (kein Zeitfenster) — die steht nicht im Raster, sondern im Panel „Offene Stunden" */
export function normalizeEntry(e: GridEntry, index: number): NormalizedEntry | null {
  if (isLesson(e)) {
    if (e.timeslotId == null || e.day == null || e.position == null) return null
    return {
      key: `l-${e.id}`, lessonId: e.id, timeslotId: e.timeslotId, day: e.day, position: e.position, date: null,
      classShort: e.classShort, subjectShort: e.subjectShort, subjectColor: e.subjectColor,
      teacherShort: e.teacherShort, roomShort: e.roomShort,
      kind: 'REGULAR', pinned: e.pinned, original: null, reasons: [], source: e,
    }
  }
  return {
    key: `e-${e.date}-${e.timeslotId}-${e.lessonId ?? 'neu'}-${index}`, lessonId: e.lessonId, timeslotId: e.timeslotId,
    day: e.day, position: e.position, date: e.date,
    classShort: e.classShort, subjectShort: e.subjectShort, subjectColor: e.subjectColor,
    teacherShort: e.teacherShort, roomShort: e.roomShort,
    kind: e.kind, pinned: false, original: e.original, reasons: e.reasons, source: e,
  }
}

/** timeslotId → Einträge der Zelle, Reihenfolge wie geliefert */
export function groupByTimeslot(entries: GridEntry[]): Map<number, NormalizedEntry[]> {
  const map = new Map<number, NormalizedEntry[]>()
  entries.forEach((e, i) => {
    const n = normalizeEntry(e, i)
    if (!n) return
    const list = map.get(n.timeslotId)
    if (list) list.push(n)
    else map.set(n.timeslotId, [n])
  })
  return map
}

export function findByLessonId(cells: Map<number, NormalizedEntry[]>, lessonId: number): NormalizedEntry | null {
  for (const list of cells.values()) {
    const hit = list.find((e) => e.lessonId === lessonId)
    if (hit) return hit
  }
  return null
}

export type ConflictFrame = 'none' | 'hard' | 'soft'

/** hart → durchgezogen, nur weich → gestrichelt */
export function conflictFrame(conflicts: ConflictDto[] | undefined): ConflictFrame {
  if (!conflicts || conflicts.length === 0) return 'none'
  return conflicts.some((c) => c.severity === 'HARD') ? 'hard' : 'soft'
}

/** Tailwind-Klassen des Konfliktrahmens (Klassen aus SPEZIFIKATION Abschnitt 2) */
export function conflictFrameClass(frame: ConflictFrame): string {
  if (frame === 'hard') return 'border-2 border-conflict'
  if (frame === 'soft') return 'border-2 border-dashed border-conflict'
  return ''
}
