// Ausnahmen anlegen/aufheben samt Verlaufseintrag — von mock-api.ts und scenario.ts gemeinsam benutzt.
import type { ConflictDto, EntryDto, ExceptionInput } from '@/api/types'
import { nowIso } from '@/lib/today'
import { DAY_SHORT, lessonNumber } from '@/lib/timegrid'
import { findLesson, nextId, type MockException, type Store } from '../store'
import { conflict409, notFound, validation } from '../problems'
import { eventsAt, resolveDay } from './week-resolver'

function assignmentText(teacherShort: string | null, roomShort: string | null, s: Store, teacherId?: number | null): string {
  const t = teacherId != null ? s.teachers.find((x) => x.id === teacherId) : undefined
  return `${t?.name ?? teacherShort ?? '—'} / ${roomShort ?? '—'}`
}

/** Anlässe der Ereignisse als "; "-getrennter Text */
export function reasonsText(eventIds: number[], s: Store): string {
  return eventIds.map((id) => s.events.find((e) => e.id === id)?.reason).filter(Boolean).join('; ')
}

/** Ereignisse, die diese Stunde an diesem Datum betreffen */
export function affectingEvents(lessonId: number, dateIso: string, s: Store): number[] {
  const l = findLesson(lessonId, s)
  if (!l) return []
  return eventsAt(dateIso, s)
    .filter((e) => (e.kind === 'TEACHER_ABSENCE' && e.teacherId === l.teacherId) || (e.kind === 'ROOM_BLOCKED' && e.roomId === l.roomId))
    .map((e) => e.id)
}

/** Folgekonflikte eines Tages: Doppelbelegung Lehrkraft/Raum/Klasse auf der aufgelösten Woche */
export function dayConflicts(dateIso: string, s: Store): ConflictDto[] {
  const entries = resolveDay(dateIso, s).filter((e) => e.kind !== 'CANCELLED')
  const out: ConflictDto[] = []
  const check = (kind: 'TEACHER' | 'ROOM' | 'CLASS', key: (e: EntryDto) => number | null, type: ConflictDto['type'], name: (e: EntryDto) => string) => {
    const groups = new Map<string, EntryDto[]>()
    for (const e of entries) {
      const k = key(e)
      if (k == null) continue
      const gk = `${e.timeslotId}:${k}`
      groups.set(gk, [...(groups.get(gk) ?? []), e])
    }
    for (const g of groups.values()) {
      if (g.length < 2) continue
      const slot = s.timeslots.find((t) => t.id === g[0].timeslotId)
      const n = slot ? lessonNumber(slot, s.timeslots) : null
      const label = name(g[0])
      out.push({
        type, severity: 'HARD', ruleId: null,
        lessonIds: g.map((e) => e.lessonId).filter((id): id is number => id != null),
        objects: [{ kind, id: key(g[0])!, label }],
        message: `${kind === 'TEACHER' ? 'Lehrkraft' : kind === 'ROOM' ? 'Raum' : 'Klasse'} ${label} ist am ${slot ? DAY_SHORT[slot.day] : '?'} ${n ?? '?'}. Stunde doppelt belegt (${g.map((e) => e.classShort).join(', ')}).`,
      })
    }
  }
  check('TEACHER', (e) => e.teacherId, 'TEACHER_DOUBLE_BOOKED', (e) => e.teacherShort ?? '?')
  check('ROOM', (e) => e.roomId, 'ROOM_DOUBLE_BOOKED', (e) => e.roomShort ?? '?')
  check('CLASS', (e) => e.classId, 'CLASS_DOUBLE_BOOKED', (e) => e.classShort)
  return out
}

export function createException(input: ExceptionInput, username: string, s: Store): { exception: MockException; conflicts: ConflictDto[] } {
  const errors: { field: string; message: string }[] = []
  if (!input.date) errors.push({ field: 'date', message: 'darf nicht leer sein' })
  const lesson = findLesson(input.lessonId, s)
  if (!lesson) throw notFound(`Stunde ${input.lessonId} nicht gefunden`)
  if (input.kind === 'SUBSTITUTION' && input.substituteTeacherId == null && input.substituteRoomId == null) {
    errors.push({ field: 'substituteTeacherId', message: 'Lehrkraft oder Raum wählen' })
  }
  if (input.kind === 'MOVE') {
    if (!input.newDate) errors.push({ field: 'newDate', message: 'darf nicht leer sein' })
    if (input.newTimeslotId == null) errors.push({ field: 'newTimeslotId', message: 'darf nicht leer sein' })
  }
  if (input.kind === 'ADDED') errors.push({ field: 'kind', message: 'Zusatzstunden werden über Ereignisse angelegt' })
  if (errors.length) throw validation(errors)

  const before = resolveDay(input.date, s).find((e) => e.lessonId === lesson.id)
  const oldAssignment = assignmentText(before?.teacherShort ?? null, before?.roomShort ?? null, s, before?.teacherId)

  // UNIQUE(lessonId, date) wie der Server (VERTRAG V-7): eine zweite Ausnahme für dieselbe Stunde am selben Tag → 409
  if (s.exceptions.some((x) => x.lessonId === lesson.id && x.date === input.date)) {
    throw conflict409('exception-exists', 'Ausnahme bereits vorhanden', 'Für diese Stunde gibt es an diesem Tag schon eine Ausnahme. Erst aufheben, dann neu eintragen.')
  }
  const exception: MockException = {
    id: nextId(), date: input.date, lessonId: lesson.id, kind: input.kind,
    substituteTeacherId: input.substituteTeacherId ?? null, substituteRoomId: input.substituteRoomId ?? null,
    newDate: input.newDate ?? null, newTimeslotId: input.newTimeslotId ?? null,
    eventIds: input.eventIds?.length ? input.eventIds : affectingEvents(lesson.id, input.date, s),
    added: null, createdAt: nowIso(),
  }
  s.exceptions.push(exception)

  let newAssignment: string
  if (input.kind === 'CANCEL') newAssignment = 'entfällt'
  else if (input.kind === 'MOVE') {
    const target = s.timeslots.find((t) => t.id === input.newTimeslotId)
    newAssignment = target ? `${DAY_SHORT[target.day]}, ${lessonNumber(target, s.timeslots) ?? '?'}. Stunde` : 'verlegt'
  } else {
    const after = resolveDay(input.date, s).find((e) => e.lessonId === lesson.id)
    newAssignment = assignmentText(after?.teacherShort ?? null, after?.roomShort ?? null, s, after?.teacherId)
  }
  const cls = s.classes.find((c) => c.id === lesson.classId)
  const slot = s.timeslots.find((t) => t.id === lesson.timeslotId)
  s.changelog.push({
    id: nextId(), timestamp: nowIso(), action: 'EXCEPTION_CREATED', date: input.date, position: slot?.position ?? null,
    classShort: cls?.name ?? null, oldAssignment, newAssignment, reasons: reasonsText(exception.eventIds, s), username,
    lessonId: lesson.id, classId: lesson.classId, teacherId: lesson.teacherId,
  })

  const conflicts = [...dayConflicts(input.date, s), ...(input.kind === 'MOVE' && input.newDate && input.newDate !== input.date ? dayConflicts(input.newDate, s) : [])]
    .filter((c) => c.lessonIds.includes(lesson.id))
  return { exception, conflicts }
}

export function deleteException(id: number, username: string, s: Store): void {
  const x = s.exceptions.find((e) => e.id === id)
  if (!x) throw notFound(`Ausnahme ${id} nicht gefunden`)
  const lesson = x.lessonId != null ? findLesson(x.lessonId, s) : undefined
  const before = lesson ? resolveDay(x.date, s).find((e) => e.lessonId === lesson.id) : undefined
  s.exceptions = s.exceptions.filter((e) => e.id !== id)
  const after = lesson ? resolveDay(x.date, s).find((e) => e.lessonId === lesson.id) : undefined
  const cls = lesson ? s.classes.find((c) => c.id === lesson.classId) : undefined
  const slot = lesson ? s.timeslots.find((t) => t.id === lesson.timeslotId) : undefined
  s.changelog.push({
    id: nextId(), timestamp: nowIso(), action: 'EXCEPTION_DELETED', date: x.date, position: slot?.position ?? null,
    classShort: cls?.name ?? null,
    oldAssignment: x.kind === 'CANCEL' ? 'entfällt' : assignmentText(before?.teacherShort ?? null, before?.roomShort ?? null, s, before?.teacherId),
    newAssignment: assignmentText(after?.teacherShort ?? null, after?.roomShort ?? null, s, after?.teacherId),
    reasons: reasonsText(x.eventIds, s), username,
    lessonId: lesson?.id ?? null, classId: lesson?.classId ?? null, teacherId: lesson?.teacherId ?? null,
  })
}
