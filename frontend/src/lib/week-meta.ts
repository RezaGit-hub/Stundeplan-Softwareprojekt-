// Die drei Ableitungen, die jede Wochenseite aus `week.days` braucht. Sie standen bis zum 05.09. in
// vier Seiten (Plan, Mein Plan, Klassenplan, Übersicht) Zeile für Zeile gleich — wer die Feiertagslogik
// ändert, änderte sie viermal (Befund FRONTEND-8).
import type { DayDto, EntryDto, Weekday } from '@/api/types'
import { dayOfDate } from '@/lib/timegrid'

export type WeekMeta = {
  /** Alle Einträge der Woche, Tage hintereinander. */
  entries: EntryDto[]
  /** Zu jedem Wochentag des Rasters das Kalenderdatum dieser Woche. */
  dateByDay: Partial<Record<Weekday, string>>
  /** Wochentage ohne Unterricht (Ferien, Feiertag) — das Raster zeigt sie grau. */
  closedDays: Weekday[]
}

export function weekMeta(days: DayDto[]): WeekMeta {
  const entries: EntryDto[] = []
  const dateByDay: Partial<Record<Weekday, string>> = {}
  const closedDays: Weekday[] = []
  for (const day of days) {
    entries.push(...day.entries)
    const wd = dayOfDate(day.date)
    if (!wd) continue
    dateByDay[wd] = day.date
    if (!day.schoolDay) closedDays.push(wd)
  }
  return { entries, dateByDay, closedDays }
}

/** Wie viele Einträge dieses Tages von der Regel abweichen — die Zahl der Leiste „heute geändert". */
export function changedOn(entries: EntryDto[], date: string): number {
  return entries.filter((e) => e.date === date && e.kind !== 'REGULAR').length
}
