// Mock-Solver: Greedy, deterministisch (SPEZIFIKATION 7.7). Kein Timefold. Ergebnis hat nie Doppelbelegung;
// gepinnte Stunden werden nie verändert; was keinen gültigen Platz findet, bleibt offen (timeslotId und roomId null).
import type { RuleScope } from '@/api/types'
import type { MockLesson, MockTimetable, Store } from '../store'
import { checkConflicts } from './conflicts'

function hardMaxPerDay(scope: RuleScope, grade: number | null, s: Store): number | null {
  const rules = s.rules.filter((r) => r.active && r.severity === 'HARD' && r.type === 'MAX_LESSONS_PER_DAY' && r.scope === scope)
  const rule = (scope === 'CLASS' ? rules.find((r) => r.grade === grade) : undefined) ?? rules.find((r) => r.grade == null)
  return rule?.value ?? null
}

export function placeLessons(draft: MockTimetable, s: Store, opts: { classId?: number } = {}): void {
  const inScope = (l: MockLesson) => !l.pinned && (opts.classId == null || l.classId === opts.classId)

  // Ungepinnte Stunden mit hartem Konflikt werden neu platziert
  const hardIds = new Set(checkConflicts(draft.lessons, s).filter((c) => c.severity === 'HARD').flatMap((c) => c.lessonIds))
  for (const l of draft.lessons) if (inScope(l) && hardIds.has(l.id)) { l.timeslotId = null; l.roomId = null }

  const weeklyHoursOf = (l: MockLesson) => s.classes.find((c) => c.id === l.classId)?.subjects.find((r) => r.subjectId === l.subjectId)?.weeklyHours ?? 0
  const open = draft.lessons
    .filter((l) => inScope(l) && l.timeslotId == null)
    .sort((a, b) => a.classId - b.classId || weeklyHoursOf(b) - weeklyHoursOf(a) || a.subjectId - b.subjectId || a.id - b.id)

  const lessonSlots = s.timeslots.filter((t) => !t.isBreak).sort((a, b) => a.day.localeCompare(b.day) || a.position - b.position)
  const specialTypes = new Set(s.subjects.map((x) => x.requiredRoomType).filter((x): x is string => !!x))
  const roomsSorted = [...s.rooms].filter((r) => r.active).sort((a, b) => a.name.localeCompare(b.name))

  const placedAt = (pred: (l: MockLesson) => boolean, timeslotId: number) => draft.lessons.some((l) => l.timeslotId === timeslotId && pred(l))
  const countDay = (pred: (l: MockLesson) => boolean, day: string) =>
    draft.lessons.filter((l) => l.timeslotId != null && pred(l) && s.timeslots.find((t) => t.id === l.timeslotId)?.day === day).length

  for (const lesson of open) {
    const cls = s.classes.find((c) => c.id === lesson.classId)
    const teacher = s.teachers.find((t) => t.id === lesson.teacherId)
    const subject = s.subjects.find((x) => x.id === lesson.subjectId)
    if (!cls || !subject) continue
    const maxClass = hardMaxPerDay('CLASS', cls.grade, s)
    const maxTeacher = hardMaxPerDay('TEACHER', null, s)
    const required = subject.requiredRoomType
    const roomOrder = required
      ? roomsSorted.filter((r) => r.roomType === required)
      : [...roomsSorted.filter((r) => !specialTypes.has(r.roomType)), ...roomsSorted.filter((r) => specialTypes.has(r.roomType))]

    // Slot-Reihenfolge: Tag mit den wenigsten Stunden der Klasse zuerst, dann Position; bevorzugte Zeiten der Lehrkraft vor allen anderen
    const dayLoad = (day: string) => countDay((l) => l.classId === cls.id, day)
    const ordered = [...lessonSlots].sort((a, b) => dayLoad(a.day) - dayLoad(b.day) || a.day.localeCompare(b.day) || a.position - b.position)
    const preferred = new Set(teacher?.preferredTimeslotIds ?? [])
    const candidates = [...ordered.filter((t) => preferred.has(t.id)), ...ordered.filter((t) => !preferred.has(t.id))]

    const tryPlace = (relaxSubjectPerDay: boolean): boolean => {
      for (const slot of candidates) {
        if (placedAt((l) => l.classId === cls.id, slot.id)) continue
        if (teacher && (placedAt((l) => l.teacherId === teacher.id, slot.id) || teacher.blockedTimeslotIds.includes(slot.id))) continue
        if (!relaxSubjectPerDay && draft.lessons.some((l) => l.classId === cls.id && l.subjectId === subject.id && l.timeslotId != null && s.timeslots.find((t) => t.id === l.timeslotId)?.day === slot.day)) continue
        if (maxClass != null && countDay((l) => l.classId === cls.id, slot.day) >= maxClass) continue
        if (teacher && maxTeacher != null && countDay((l) => l.teacherId === teacher.id, slot.day) >= maxTeacher) continue
        const room = roomOrder.find((r) => !placedAt((l) => l.roomId === r.id, slot.id))
        if (!room) continue
        lesson.timeslotId = slot.id
        lesson.roomId = room.id
        return true
      }
      return false
    }
    if (!tryPlace(false)) tryPlace(true)
  }
}
