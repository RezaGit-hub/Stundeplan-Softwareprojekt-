// Alle Query-Schlüssel an einer Stelle. Invalidierung: queryClient.invalidateQueries({ queryKey: qk.teachers }).
import type { ChangelogQuery, EventQuery, View } from './types'

export const qk = {
  health: ['health'] as const,
  me: ['me'] as const,
  users: ['users'] as const,
  teachers: ['teachers'] as const,
  teacher: (id: number) => ['teacher', id] as const,
  subjects: ['subjects'] as const,
  rooms: ['rooms'] as const,
  classes: ['classes'] as const,
  class: (id: number) => ['class', id] as const,
  timegrid: ['timegrid'] as const,
  rules: ['rules'] as const,
  config: ['config'] as const,
  /** Der aktive Plan — zugleich Präfix von `timetableById` */
  timetable: ['timetable'] as const,
  /** Alle angenommenen Pläne (seit 1.4.0, #115) */
  timetables: ['timetables'] as const,
  timetableById: (id: number) => ['timetable', id] as const,
  /** Präfix für alle Wochen (Invalidierung) */
  weeks: ['week'] as const,
  week: (view: View, id: number | null | undefined, monday: string) => ['week', view, id ?? null, monday] as const,
  myWeeks: ['my-week'] as const,
  myWeek: (monday: string) => ['my-week', monday] as const,
  /** Präfix aller Klassenwochen der Schülersicht (E-50) */
  classWeeks: ['class-week'] as const,
  classWeek: (monday: string) => ['class-week', monday] as const,
  /**
   * Entwürfe (seit 1.3.0 mehrere nebeneinander, #103): die Liste und je Entwurf ein eigener Eintrag.
   * `draftAll` ist bewusst das Präfix von `draft(id)` — wer Grunddaten oder Regeln ändert, wirft damit
   * jeden Entwurf auf einmal weg, ohne die Kennungen zu kennen.
   */
  drafts: ['drafts'] as const,
  draftAll: ['draft'] as const,
  draft: (id: number) => ['draft', id] as const,
  events: (q: EventQuery) => ['events', q] as const,
  eventsAll: ['events'] as const,
  pending: (from: string, to: string) => ['pending', from, to] as const,
  pendingAll: ['pending'] as const,
  options: (date: string, lessonId: number) => ['options', date, lessonId] as const,
  optionsAll: ['options'] as const,
  // Ohne Hook, seit der Annehmen-Dialog die Zahl der verfallenden Vertretungen vom Server bekommt
  // (`expiringExceptions` im Entwurf, Befund PFLICHTENHEFT-9) statt GET /api/exceptions selbst zu zählen.
  // Schlüssel und Invalidierung bleiben stehen: wer die Liste baut, hat sie sonst still ohne Auffrischung.
  exceptions: (from: string, to: string) => ['exceptions', from, to] as const,
  exceptionsAll: ['exceptions'] as const,
  /** Präfix aller Admin-Anfragen, optional nach Lehrkraft gefiltert (V14) */
  substitutionRequestsAll: ['substitution-requests'] as const,
  substitutionRequests: (teacherId?: number) => ['substitution-requests', teacherId ?? null] as const,
  /** Präfix aller Lehrkraft-Sichten auf ihre eigenen Anfragen */
  mySubstitutionRequestsAll: ['my-substitution-requests'] as const,
  mySubstitutionRequests: (open?: boolean) => ['my-substitution-requests', open ?? null] as const,

  changelog: (q: ChangelogQuery) => ['changelog', q] as const,
  changelogAll: ['changelog'] as const,
  holidays: ['holidays'] as const,
  holidayPresetYears: ['holiday-preset-years'] as const,
}
