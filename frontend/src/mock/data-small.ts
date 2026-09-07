// Datensatz SMALL = Testdatenbestand des Pflichtenhefts (SPEZIFIKATION 7.5). Feste IDs: Klasse 7a = 1,
// Lehrkraft Meyer = 1, Raum R101 = 1 — die Screenshot-Links verlassen sich darauf.
import { nowIso } from '@/lib/today'
import { clearStore, defaultRules, defaultTimeslots, newTimetable, nextId, slotId, store, type MockLesson, type Store } from './store'

export function loadSmall(s: Store = store): void {
  clearStore(s)
  const slots = defaultTimeslots()
  s.timeslots = slots
  s.rules = defaultRules(false)
  s.config = { schoolName: 'Gymnasium Musterstadt', state: 'SH', schoolType: 'GYMNASIUM', maxSolveSeconds: 60 }

  s.subjects = [
    { id: 1, name: 'Mathematik', shortName: 'Ma', requiredRoomType: null, color: '#1976D2' },
    { id: 2, name: 'Deutsch', shortName: 'De', requiredRoomType: null, color: '#388E3C' },
    { id: 3, name: 'Physik', shortName: 'Ph', requiredRoomType: 'Fachraum Physik', color: '#F57C00' },
  ]
  s.rooms = [
    { id: 1, name: 'R101', roomType: 'Klassenraum', active: true },
    { id: 2, name: 'R204', roomType: 'Klassenraum', active: true },
    { id: 3, name: 'P1', roomType: 'Fachraum Physik', active: true },
  ]
  s.teachers = [
    { id: 1, name: 'Meyer', shortName: 'MEY', weeklyHoursQuota: 25, subjectIds: [1, 3], preferredTimeslotIds: [], blockedTimeslotIds: [], active: true },
    { id: 2, name: 'Schulz', shortName: 'SCZ', weeklyHoursQuota: 2, subjectIds: [1], preferredTimeslotIds: [], blockedTimeslotIds: [slotId(slots, 'FR', 6), slotId(slots, 'FR', 7)], active: true },
    { id: 3, name: 'Weber', shortName: 'WEB', weeklyHoursQuota: 25, subjectIds: [2], preferredTimeslotIds: [], blockedTimeslotIds: [], active: true },
  ]
  s.classes = [
    { id: 1, name: '7a', grade: 7, active: true, subjects: [
      { subjectId: 1, weeklyHours: 2, teacherId: 1 }, { subjectId: 2, weeklyHours: 1, teacherId: 3 }, { subjectId: 3, weeklyHours: 1, teacherId: 1 },
    ] },
    { id: 2, name: '8b', grade: 8, active: true, subjects: [
      { subjectId: 1, weeklyHours: 2, teacherId: 1 }, { subjectId: 2, weeklyHours: 1, teacherId: 3 },
    ] },
    { id: 3, name: '9c', grade: 9, active: true, subjects: [{ subjectId: 1, weeklyHours: 1, teacherId: 2 }] },
  ]
  s.users = [
    { id: 1, username: 'admin', password: 'admin', role: 'PLANNING', teacherId: null, classId: null },
    { id: 2, username: 'meyer', password: 'meyer', role: 'TEACHER', teacherId: 1, classId: null },
    { id: 3, username: 'schulz', password: 'schulz', role: 'TEACHER', teacherId: 2, classId: null },
    { id: 4, username: 'weber', password: 'weber', role: 'TEACHER', teacherId: 3, classId: null },
    // Klassenkonto der 7a (E-50). Im Mock ist das Passwort wie überall der Benutzername.
    { id: 5, username: '7a', password: '7a', role: 'STUDENT', teacherId: null, classId: 1 },
  ]
  s.holidays = [{ id: 1, name: 'Herbstferien', dateFrom: '2026-10-19', dateTo: '2026-10-31', kind: 'VACATION' }]

  // Aktiver Plan: Mo 7a 1 Ma R101 · 7a 2 De R101 · 7a 4 Ph P1 · 8b 2 Ma R204 · 8b 4 De R204 · 8b 5 Ma R204 · Di 7a 1 Ma R101; offen: 9c Ma
  const active = newTimetable('ACTIVE', nowIso())
  active.acceptedAt = active.createdAt
  const row = (classId: number, subjectId: number, teacherId: number, day: 'MO' | 'TU', position: number, roomId: number): MockLesson => ({
    id: nextId(), timetableId: active.id, classId, subjectId, teacherId, timeslotId: slotId(slots, day, position), roomId, pinned: true,
  })
  active.lessons = [
    row(1, 1, 1, 'MO', 1, 1), row(1, 2, 3, 'MO', 2, 1), row(1, 3, 1, 'MO', 4, 3),
    row(2, 1, 1, 'MO', 2, 2), row(2, 2, 3, 'MO', 4, 2), row(2, 1, 1, 'MO', 5, 2),
    row(1, 1, 1, 'TU', 1, 1),
  ]
  s.timetables = [active]
}
