// Helfer rund um das Stundenraster (TimeslotDto). Die angezeigte Stundennummer wird gezählt, nie gespeichert.
import type { TimeslotDto, Weekday } from '@/api/types'
import { WEEKDAYS } from '@/api/types'
import { isoDay } from '@/lib/dates'

export const DAY_LABEL: Record<Weekday, string> = {
  MO: 'Montag', TU: 'Dienstag', WE: 'Mittwoch', TH: 'Donnerstag', FR: 'Freitag', SA: 'Samstag',
}
export const DAY_SHORT: Record<Weekday, string> = {
  MO: 'Mo', TU: 'Di', WE: 'Mi', TH: 'Do', FR: 'Fr', SA: 'Sa',
}

/** Tage mit mindestens einem Zeitfenster, Reihenfolge MO..SA */
export function daysOf(slots: TimeslotDto[]): Weekday[] {
  const present = new Set(slots.map((s) => s.day))
  return WEEKDAYS.filter((d) => present.has(d))
}

export function slotsOfDay(slots: TimeslotDto[], day: Weekday): TimeslotDto[] {
  return slots.filter((s) => s.day === day).sort((a, b) => a.position - b.position)
}

export function slotById(slots: TimeslotDto[], id: number | null | undefined): TimeslotDto | undefined {
  return id == null ? undefined : slots.find((s) => s.id === id)
}

/** Höchste Position über alle Tage = Zeilen des Rasters */
export function maxPositions(slots: TimeslotDto[]): number {
  return slots.reduce((m, s) => Math.max(m, s.position), 0)
}

/** Stundennummer ohne Pausen (1..n je Tag); null bei einer Pause */
export function lessonNumber(slot: TimeslotDto, slots: TimeslotDto[]): number | null {
  if (slot.isBreak) return null
  return slotsOfDay(slots, slot.day).filter((s) => !s.isBreak && s.position <= slot.position).length
}

/** "1. Stunde" oder "Pause" */
export function slotLabel(slot: TimeslotDto, slots: TimeslotDto[]): string {
  const n = lessonNumber(slot, slots)
  return n == null ? 'Pause' : `${n}. Stunde`
}

/** "Montag, 1. Stunde" */
export function slotLongLabel(slot: TimeslotDto, slots: TimeslotDto[]): string {
  return `${DAY_LABEL[slot.day]}, ${slotLabel(slot, slots)}`
}

/** Wochentag eines Datums; Sonntag → null (kein Unterrichtstag) */
export function dayOfDate(dateIso: string): Weekday | null {
  const d = isoDay(dateIso)
  return d >= 1 && d <= 6 ? WEEKDAYS[d - 1] : null
}

export function weekdayIndex(day: Weekday): number {
  return WEEKDAYS.indexOf(day)
}
