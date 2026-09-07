// Vertretungsoptionen (W-6, T-W6.1): freie Lehrkräfte (fachgleiche zuerst, dann Belastung, dann Name) und freie Räume
// (passende Raumart zuerst). Keine Berechnung, nur Filtern und Sortieren.
import type { OptionsDto, RoomOption, TeacherOption } from '@/api/types'
import { weekRange, between } from '@/lib/dates'
import { findLesson, type Store } from '../store'
import { notFound } from '../problems'
import { eventsAt, resolveDay } from './week-resolver'

export function substitutionsThisWeek(teacherId: number, dateIso: string, s: Store): number {
  const { from, to } = weekRange(dateIso)
  return s.exceptions.filter((x) => x.kind === 'SUBSTITUTION' && x.substituteTeacherId === teacherId && between(x.date, from, to)).length
}

export function substitutionOptions(dateIso: string, lessonId: number, s: Store): OptionsDto {
  const lesson = findLesson(lessonId, s)
  if (!lesson || lesson.timeslotId == null) throw notFound(`Stunde ${lessonId} nicht gefunden`)
  const timeslotId = lesson.timeslotId
  const subject = s.subjects.find((x) => x.id === lesson.subjectId)
  const day = resolveDay(dateIso, s).filter((e) => e.kind !== 'CANCELLED' && e.timeslotId === timeslotId && e.lessonId !== lessonId)
  const events = eventsAt(dateIso, s)
  const absentTeachers = new Set(events.filter((e) => e.kind === 'TEACHER_ABSENCE').map((e) => e.teacherId))
  const blockedRooms = new Set(events.filter((e) => e.kind === 'ROOM_BLOCKED').map((e) => e.roomId))

  const teachers: TeacherOption[] = s.teachers
    .filter((t) => t.active && t.id !== lesson.teacherId && !absentTeachers.has(t.id))
    .filter((t) => !t.blockedTimeslotIds.includes(timeslotId))
    .filter((t) => !day.some((e) => e.teacherId === t.id))
    .map((t) => ({ id: t.id, shortName: t.shortName, name: t.name, teachesSubject: subject ? t.subjectIds.includes(subject.id) : false, load: substitutionsThisWeek(t.id, dateIso, s) }))
    .sort((a, b) => Number(b.teachesSubject) - Number(a.teachesSubject) || a.load - b.load || a.name.localeCompare(b.name))
    // Belastungshinweis am 04.09. gestrichen (VERTRAG V-5): die Zahl ordnet nur, sie verlässt die Attrappe nicht
    .map(({ load: _load, ...t }) => t)

  const required = subject?.requiredRoomType ?? null
  const rooms: RoomOption[] = s.rooms
    .filter((r) => r.active && !blockedRooms.has(r.id))
    .filter((r) => !day.some((e) => e.roomId === r.id))
    .map((r) => ({ id: r.id, name: r.name, roomType: r.roomType, matchesRequirement: required ? r.roomType === required : true }))
    .sort((a, b) => Number(b.matchesRequirement) - Number(a.matchesRequirement) || a.name.localeCompare(b.name))

  return { teachers, rooms }
}
