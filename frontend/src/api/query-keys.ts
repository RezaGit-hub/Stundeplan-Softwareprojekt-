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
  timetable: ['timetable'] as const,
  /** Präfix für alle Wochen (Invalidierung) */
  weeks: ['week'] as const,
  week: (view: View, id: number | null | undefined, monday: string) => ['week', view, id ?? null, monday] as const,
  myWeeks: ['my-week'] as const,
  myWeek: (monday: string) => ['my-week', monday] as const,
  /** Präfix aller Klassenwochen der Schülersicht (E-50) */
  classWeeks: ['class-week'] as const,
  classWeek: (monday: string) => ['class-week', monday] as const,
  draft: ['draft'] as const,
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
  changelog: (q: ChangelogQuery) => ['changelog', q] as const,
  changelogAll: ['changelog'] as const,
  holidays: ['holidays'] as const,
  holidayPresetYears: ['holiday-preset-years'] as const,
}
