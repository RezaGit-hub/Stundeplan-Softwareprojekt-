// Gemeinsame Typen der Vertretungsseite: welche Stunde ist markiert, was wurde gerade aufgelöst.
import type { ConflictDto, ExceptionKind, PendingRow } from '@/api/types'

export interface SelectedLesson { date: string; lessonId: number }

export const rowKey = (r: SelectedLesson) => `${r.date}:${r.lessonId}`

/** Ergebnis einer Zuweisung — bleibt bis zum Wochenwechsel in der Tabelle sichtbar (durchgezogener Rand, alt durchgestrichen) */
export interface ResolvedRow {
  row: PendingRow
  kind: ExceptionKind
  /** Kennung der angelegten Ausnahme — ohne sie liesse sich eine falsche Zuweisung nicht zuruecknehmen (FRONTEND-2) */
  exceptionId: number
  /** neue Lehrkraft (Kürzel), nur wenn geändert */
  teacherShort?: string
  /** neuer Raum (Kürzel), nur wenn geändert */
  roomShort?: string
  /** Ziel beim Verlegen, z. B. "Di 3." */
  target?: string
  conflicts: ConflictDto[]
}
