// Beschriftung einer Rasterposition an einem Datum: "1." (Pausen werden nicht mitgezählt, lib/timegrid).
// Ohne Raster oder ohne passenden Slot bleibt die rohe Position.
import type { TimeslotDto } from '@/api/types'
import { DAY_SHORT, dayOfDate, lessonNumber } from '@/lib/timegrid'

export function slotAt(slots: TimeslotDto[] | undefined, dateIso: string, position: number): TimeslotDto | undefined {
  const day = dayOfDate(dateIso)
  return day && slots ? slots.find((s) => s.day === day && s.position === position) : undefined
}

/** "1." */
export function positionLabel(slots: TimeslotDto[] | undefined, dateIso: string, position: number): string {
  const slot = slotAt(slots, dateIso, position)
  const n = slot && slots ? lessonNumber(slot, slots) : null
  return `${n ?? position}.`
}

/** "Mo 1." — für Verlegen-Texte */
export function dayPositionLabel(slots: TimeslotDto[] | undefined, dateIso: string, position: number): string {
  const day = dayOfDate(dateIso)
  return `${day ? DAY_SHORT[day] : '?'} ${positionLabel(slots, dateIso, position)}`
}

/** "08:00–08:45" oder leer */
export function slotTime(slots: TimeslotDto[] | undefined, dateIso: string, position: number): string {
  const slot = slotAt(slots, dateIso, position)
  return slot ? `${slot.startTime}–${slot.endTime}` : ''
}
