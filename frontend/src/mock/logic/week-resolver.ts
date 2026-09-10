// Wochenauflöser: Regelplan + Ereignisse + Ausnahmen + Ferien → WeekDto (SPEZIFIKATION 7.7).
import type { DayDto, EntryDto, View, WeekDto } from '@/api/types'
import { addDaysIso, between } from '@/lib/dates'
import { DAY_SHORT, dayOfDate, daysOf, lessonNumber, weekdayIndex } from '@/lib/timegrid'
import type { MockLesson, MockTimetable, Store } from '../store'

export function isHoliday(dateIso: string, s: Store): boolean {
  return s.holidays.some((h) => between(dateIso, h.dateFrom, h.dateTo))
}

/** Der am Datum gültige Plan: ACTIVE, außer sein validFrom liegt in der Zukunft — dann der zuletzt archivierte */
export function planValidAt(dateIso: string, s: Store): MockTimetable | undefined {
  const active = s.timetables.find((t) => t.status === 'ACTIVE')
  if (!active) return undefined
  if (!active.validFrom || active.validFrom <= dateIso) return active
  const archived = s.timetables.filter((t) => t.status === 'ARCHIVED')
  return archived[archived.length - 1]
}

export function eventsAt(dateIso: string, s: Store) {
  return s.events.filter((e) => e.kind !== 'EXTRA_LESSON' && between(dateIso, e.dateFrom, e.dateTo))
}

function baseEntry(l: MockLesson, dateIso: string, timeslotId: number, s: Store): EntryDto | null {
  const slot = s.timeslots.find((t) => t.id === timeslotId)
  const cls = s.classes.find((c) => c.id === l.classId)
  const subject = s.subjects.find((x) => x.id === l.subjectId)
  const teacher = s.teachers.find((t) => t.id === l.teacherId)
  const room = s.rooms.find((r) => r.id === l.roomId)
  if (!slot || !cls || !subject) return null
  return {
    date: dateIso, timeslotId, day: slot.day, position: slot.position, lessonId: l.id,
    classId: cls.id, classShort: cls.name, subjectShort: subject.shortName, subjectColor: subject.color,
    teacherId: teacher?.id ?? null, teacherShort: teacher?.shortName ?? null,
    roomId: room?.id ?? null, roomShort: room?.name ?? null,
    kind: 'REGULAR', original: null, reasons: [], exceptionId: null,
  }
}

function addReason(e: EntryDto, r: string) {
  if (!e.reasons.includes(r)) e.reasons.push(r)
}

/** Alle Einträge eines Tages (Sicht SCHOOL), noch ohne Sichtfilter */
export function resolveDay(dateIso: string, s: Store): EntryDto[] {
  const day = dayOfDate(dateIso)
  if (!day || isHoliday(dateIso, s)) return []
  const plan = planValidAt(dateIso, s)
  const entries: EntryDto[] = []
  if (plan) {
    for (const l of plan.lessons) {
      if (l.timeslotId == null) continue
      const slot = s.timeslots.find((t) => t.id === l.timeslotId)
      if (!slot || slot.day !== day) continue
      const e = baseEntry(l, dateIso, l.timeslotId, s)
      if (e) entries.push(e)
    }
  }
  // Ereignisse: Ausfall / Raumsperrung → vertretungsbedürftig
  for (const ev of eventsAt(dateIso, s)) {
    for (const e of entries) {
      const hit = (ev.kind === 'TEACHER_ABSENCE' && ev.teacherId === e.teacherId) || (ev.kind === 'ROOM_BLOCKED' && ev.roomId === e.roomId)
      if (!hit) continue
      e.kind = 'NEEDS_SUBSTITUTION'
      addReason(e, ev.reason)
    }
  }
  // Ausnahmen dieses Datums
  const reasonsOf = (ids: number[]) => ids.map((id) => s.events.find((ev) => ev.id === id)?.reason).filter((r): r is string => !!r)
  for (const x of s.exceptions.filter((x) => x.date === dateIso && x.lessonId != null)) {
    const e = entries.find((en) => en.lessonId === x.lessonId)
    if (!e) continue
    e.exceptionId = x.id
    for (const r of reasonsOf(x.eventIds)) addReason(e, r)
    if (x.kind === 'SUBSTITUTION') {
      e.kind = 'SUBSTITUTED'
      e.original = {}
      if (x.substituteTeacherId != null && x.substituteTeacherId !== e.teacherId) {
        const t = s.teachers.find((t) => t.id === x.substituteTeacherId)
        if (e.teacherId != null) e.original.teacherId = e.teacherId
        if (e.teacherShort) e.original.teacherShort = e.teacherShort
        e.teacherId = t?.id ?? null
        e.teacherShort = t?.shortName ?? null
      }
      if (x.substituteRoomId != null && x.substituteRoomId !== e.roomId) {
        const r = s.rooms.find((r) => r.id === x.substituteRoomId)
        if (e.roomId != null) e.original.roomId = e.roomId
        if (e.roomShort) e.original.roomShort = e.roomShort
        e.roomId = r?.id ?? null
        e.roomShort = r?.name ?? null
      }
    } else if (x.kind === 'CANCEL') {
      e.kind = 'CANCELLED'
    } else if (x.kind === 'MOVE' && x.newDate && x.newTimeslotId != null) {
      const target = s.timeslots.find((t) => t.id === x.newTimeslotId)
      const n = target ? lessonNumber(target, s.timeslots) : null
      e.kind = 'CANCELLED'
      e.reasons = [`verlegt → ${target ? DAY_SHORT[target.day] : '?'} ${n ?? '?'}.`, ...e.reasons]
    }
  }
  // Ziele verlegter Stunden, die auf dieses Datum fallen
  for (const x of s.exceptions.filter((x) => x.kind === 'MOVE' && x.newDate === dateIso && x.newTimeslotId != null && x.lessonId != null)) {
    const l = s.timetables.flatMap((t) => t.lessons).find((l) => l.id === x.lessonId)
    if (!l) continue
    const e = baseEntry(l, dateIso, x.newTimeslotId!, s)
    if (!e) continue
    const originSlot = s.timeslots.find((t) => t.id === l.timeslotId)
    e.kind = 'MOVED'
    e.exceptionId = x.id
    e.original = { date: x.date, position: originSlot?.position }
    e.reasons = reasonsOf(x.eventIds)
    entries.push(e)
  }
  // Zusatzstunden
  for (const x of s.exceptions.filter((x) => x.kind === 'ADDED' && x.date === dateIso && x.added && x.newTimeslotId != null)) {
    const a = x.added!
    const fake: MockLesson = { id: -1, timetableId: -1, classId: a.classId, subjectId: a.subjectId, teacherId: a.teacherId, timeslotId: x.newTimeslotId, roomId: a.roomId, pinned: false }
    const e = baseEntry(fake, dateIso, x.newTimeslotId!, s)
    if (!e) continue
    e.lessonId = null
    e.kind = 'ADDED'
    e.exceptionId = x.id
    e.reasons = reasonsOf(x.eventIds)
    entries.push(e)
  }
  return entries.sort((a, b) => a.position - b.position || a.classShort.localeCompare(b.classShort))
}

export function filterView(entries: EntryDto[], view: View, id: number | null | undefined): EntryDto[] {
  if (view === 'SCHOOL') return entries
  if (id == null) return []
  if (view === 'CLASS') return entries.filter((e) => e.classId === id)
  if (view === 'ROOM') return entries.filter((e) => e.roomId === id)
  const out: EntryDto[] = []
  for (const e of entries) {
    if (e.teacherId === id) out.push(e)
    else if (e.original?.teacherId === id) out.push({ ...e, kind: 'GIVEN_AWAY' })
  }
  return out
}

export function resolveWeek(mondayIso: string, view: View, id: number | null | undefined, s: Store): WeekDto {
  const days: DayDto[] = daysOf(s.timeslots).map((day) => {
    const date = addDaysIso(mondayIso, weekdayIndex(day))
    const schoolDay = !isHoliday(date, s)
    return { date, schoolDay, entries: schoolDay ? filterView(resolveDay(date, s), view, id) : [] }
  })
  return { monday: mondayIso, days }
}
