// Leitszenario (nur VITE_MOCK_SCENARIO=leitszenario, für Screenshots): Entwurf = Kopie des aktiven Plans mit der 9c-Stunde
// Mo 1. in R101; Ereignisse Meyer „Krankmeldung" und R101 „Wasserschaden" am Heute; Vertretung 7a 1. Stunde durch Schulz/R204.
import { nowIso, today } from '@/lib/today'
import { activeTimetable, newTimetable, nextId, slotId, type Store } from './store'
import { createException } from './logic/exceptions'

export function applyScenario(s: Store): void {
  const active = activeTimetable(s)
  if (!active) return
  const day = today()
  const meyer = s.teachers.find((t) => t.shortName === 'MEY')
  const schulz = s.teachers.find((t) => t.shortName === 'SCZ')
  const r101 = s.rooms.find((r) => r.name === 'R101')
  const r204 = s.rooms.find((r) => r.name === 'R204')
  const c9 = s.classes.find((c) => c.name === '9c')
  if (!meyer || !schulz || !r101 || !r204 || !c9) return

  // Entwurf: Kopie + 9c Ma Mo 1. in R101 (T-W3.1: Raumkonflikt mit 7a)
  const draft = newTimetable('DRAFT', nowIso(), { name: 'Entwurf 1', source: 'COPY_ACTIVE' })
  draft.lessons = active.lessons.map((l) => ({ ...l, id: nextId(), timetableId: draft.id, pinned: true }))
  const row = c9.subjects[0]
  if (row) draft.lessons.push({ id: nextId(), timetableId: draft.id, classId: c9.id, subjectId: row.subjectId, teacherId: row.teacherId, timeslotId: slotId(s.timeslots, 'MO', 1), roomId: r101.id, pinned: true })
  s.timetables.push(draft)

  // Ereignisse am Heute
  const absence = { id: nextId(), kind: 'TEACHER_ABSENCE' as const, teacherId: meyer.id, roomId: null, dateFrom: day, dateTo: day, reason: 'Krankmeldung', createdAt: nowIso(), createdBy: 'admin', extra: null }
  const blocked = { id: nextId(), kind: 'ROOM_BLOCKED' as const, teacherId: null, roomId: r101.id, dateFrom: day, dateTo: day, reason: 'Wasserschaden', createdAt: nowIso(), createdBy: 'admin', extra: null }
  s.events.push(absence, blocked)

  // Vertretung 7a 1. Stunde (Meyer/R101 → Schulz/R204), nur wenn Heute ein Montag mit dieser Stunde ist
  const mo1 = slotId(s.timeslots, 'MO', 1)
  const lesson = active.lessons.find((l) => l.timeslotId === mo1 && l.classId === s.classes.find((c) => c.name === '7a')?.id)
  const isMonday = new Date(`${day}T12:00:00`).getDay() === 1
  if (lesson && isMonday) {
    createException({ date: day, lessonId: lesson.id, kind: 'SUBSTITUTION', substituteTeacherId: schulz.id, substituteRoomId: r204.id, eventIds: [absence.id, blocked.id] }, 'admin', s)
  }
}
