// Port des ConflictChecker (solver.md): Doppelbelegung, gesperrte Zeit, Raumart, Raumsperre (#133), Regelverstöße.
// Läuft bei jedem Lesen von Entwurf/aktivem Plan und nach jeder Draft-Mutation. Konflikte sind Daten, nie Fehler.
import type { ConflictDto, ConflictObject, RuleDto, RuleScope, RuleType, TimeslotDto } from '@/api/types'
import { DAY_LABEL, lessonNumber } from '@/lib/timegrid'
import type { MockClass, MockLesson, MockTeacher, Store } from '../store'

function slotPhrase(slot: TimeslotDto, slots: TimeslotDto[]): string {
  const n = lessonNumber(slot, slots)
  return n == null ? `am ${DAY_LABEL[slot.day]} in der Pause` : `am ${DAY_LABEL[slot.day]} in der ${n}. Stunde`
}

function groupBy<T>(items: T[], key: (t: T) => string | null): Map<string, T[]> {
  const m = new Map<string, T[]>()
  for (const it of items) {
    const k = key(it)
    if (k == null) continue
    const list = m.get(k)
    if (list) list.push(it)
    else m.set(k, [it])
  }
  return m
}

export function checkConflicts(lessons: MockLesson[], s: Store): ConflictDto[] {
  const out: ConflictDto[] = []
  const placed = lessons.filter((l): l is MockLesson & { timeslotId: number } => l.timeslotId != null)
  const slotOf = (l: MockLesson) => s.timeslots.find((t) => t.id === l.timeslotId)
  const classOf = (l: MockLesson) => s.classes.find((c) => c.id === l.classId)
  const teacherOf = (l: MockLesson) => s.teachers.find((t) => t.id === l.teacherId)
  const roomOf = (l: MockLesson) => s.rooms.find((r) => r.id === l.roomId)
  const subjectOf = (l: MockLesson) => s.subjects.find((x) => x.id === l.subjectId)
  const classShorts = (ls: MockLesson[]) => Array.from(new Set(ls.map((l) => classOf(l)?.name ?? '?'))).join(', ')

  // 1. Doppelbelegung Raum / Lehrkraft / Klasse
  for (const [key, group] of groupBy(placed, (l) => (l.roomId == null ? null : `${l.timeslotId}:${l.roomId}`))) {
    if (group.length < 2) continue
    const slot = slotOf(group[0])!
    const room = roomOf(group[0])
    const obj: ConflictObject = { kind: 'ROOM', id: group[0].roomId!, label: room?.name ?? key }
    out.push({ type: 'ROOM_DOUBLE_BOOKED', severity: 'HARD', ruleId: null, lessonIds: group.map((l) => l.id), objects: [obj],
      message: `Raum ${obj.label} ist ${slotPhrase(slot, s.timeslots)} doppelt belegt (${classShorts(group)}).` })
  }
  for (const [, group] of groupBy(placed, (l) => `${l.timeslotId}:${l.teacherId}`)) {
    if (group.length < 2) continue
    const slot = slotOf(group[0])!
    const t = teacherOf(group[0])
    const obj: ConflictObject = { kind: 'TEACHER', id: group[0].teacherId, label: t?.shortName ?? '?' }
    out.push({ type: 'TEACHER_DOUBLE_BOOKED', severity: 'HARD', ruleId: null, lessonIds: group.map((l) => l.id), objects: [obj],
      message: `Lehrkraft ${t?.name ?? obj.label} ist ${slotPhrase(slot, s.timeslots)} doppelt belegt (${classShorts(group)}).` })
  }
  for (const [, group] of groupBy(placed, (l) => `${l.timeslotId}:${l.classId}`)) {
    if (group.length < 2) continue
    const slot = slotOf(group[0])!
    const c = classOf(group[0])
    const obj: ConflictObject = { kind: 'CLASS', id: group[0].classId, label: c?.name ?? '?' }
    const subjects = group.map((l) => subjectOf(l)?.shortName ?? '?').join(', ')
    out.push({ type: 'CLASS_DOUBLE_BOOKED', severity: 'HARD', ruleId: null, lessonIds: group.map((l) => l.id), objects: [obj],
      message: `Klasse ${obj.label} ist ${slotPhrase(slot, s.timeslots)} doppelt belegt (${subjects}).` })
  }

  // 2. Gesperrte Zeit der Lehrkraft
  for (const l of placed) {
    const t = teacherOf(l)
    if (!t || !t.blockedTimeslotIds.includes(l.timeslotId)) continue
    const slot = slotOf(l)!
    const n = lessonNumber(slot, s.timeslots)
    out.push({ type: 'TEACHER_BLOCKED_TIME', severity: 'HARD', ruleId: null, lessonIds: [l.id],
      objects: [{ kind: 'TEACHER', id: t.id, label: t.shortName }],
      message: `Gesperrte Zeit der Lehrkraft: ${t.name}, ${DAY_LABEL[slot.day]} ${n == null ? 'Pause' : `${n}. Stunde`}` })
  }

  // 3. Raumanforderung des Fachs
  for (const l of placed) {
    const subject = subjectOf(l)
    const room = roomOf(l)
    if (!subject?.requiredRoomType || !room || room.roomType === subject.requiredRoomType) continue
    out.push({ type: 'ROOM_TYPE_MISMATCH', severity: 'HARD', ruleId: null, lessonIds: [l.id],
      objects: [{ kind: 'ROOM', id: room.id, label: room.name }],
      message: `${subject.name} braucht ${subject.requiredRoomType}, ${room.name} ist ${room.roomType}` })
  }

  // 4. Gesperrte Zeit des Raums (#133, v1.1.0) — Wortlaut wie ConflictChecker.addRoomBlockedTimes
  for (const l of placed) {
    const room = roomOf(l)
    if (!room || !(room.blockedTimeslotIds ?? []).includes(l.timeslotId)) continue
    const slot = slotOf(l)!
    out.push({ type: 'ROOM_BLOCKED_TIME', severity: 'HARD', ruleId: null, lessonIds: [l.id],
      objects: [{ kind: 'ROOM', id: room.id, label: room.name }],
      message: `Raum ${room.name} ist ${slotPhrase(slot, s.timeslots)} gesperrt.` })
  }

  // 5. Regelverstöße (nur aktive Regeln; Jahrgangsregel ersetzt die schulweite für diesen Jahrgang)
  const active = s.rules.filter((r) => r.active)
  const breakRule = active.find((r) => r.type === 'NO_LESSONS_IN_BREAKS')
  if (breakRule) {
    for (const l of placed) {
      const slot = slotOf(l)
      if (!slot?.isBreak) continue
      const c = classOf(l)
      out.push({ type: 'RULE_VIOLATION', severity: breakRule.severity, ruleId: breakRule.id, lessonIds: [l.id],
        objects: c ? [{ kind: 'CLASS', id: c.id, label: c.name }] : [],
        message: `Stunde liegt in der Pause: ${c?.name ?? '?'}, ${DAY_LABEL[slot.day]} ${slot.startTime}–${slot.endTime}` })
    }
  }
  const maxTypes: RuleType[] = ['MAX_LESSONS_PER_DAY', 'MAX_LESSONS_PER_WEEK']
  const scopes: RuleScope[] = ['CLASS', 'TEACHER']
  for (const type of maxTypes) {
    for (const scope of scopes) {
      const rulesOf = active.filter((r) => r.type === type && r.scope === scope)
      if (rulesOf.length === 0) continue
      const schoolWide = rulesOf.find((r) => r.grade == null)
      const objects: (MockClass | MockTeacher)[] = scope === 'CLASS' ? s.classes : s.teachers
      for (const obj of objects) {
        const rule: RuleDto | undefined = scope === 'CLASS' ? (rulesOf.find((r) => r.grade === (obj as MockClass).grade) ?? schoolWide) : schoolWide
        if (!rule) continue
        const own = placed.filter((l) => (scope === 'CLASS' ? l.classId === obj.id : l.teacherId === obj.id))
        const label = scope === 'CLASS' ? `Klasse ${obj.name}` : `Lehrkraft ${obj.name}`
        const co: ConflictObject = { kind: scope, id: obj.id, label: scope === 'CLASS' ? obj.name : (obj as MockTeacher).shortName }
        if (type === 'MAX_LESSONS_PER_DAY') {
          for (const [, group] of groupBy(own, (l) => slotOf(l)?.day ?? null)) {
            if (group.length <= rule.value) continue
            const day = slotOf(group[0])!.day
            out.push({ type: 'RULE_VIOLATION', severity: rule.severity, ruleId: rule.id, lessonIds: group.map((l) => l.id), objects: [co],
              message: `${rule.label} (${rule.value}) überschritten: ${label}, ${DAY_LABEL[day]}: ${group.length} Stunden` })
          }
        } else if (own.length > rule.value) {
          out.push({ type: 'RULE_VIOLATION', severity: rule.severity, ruleId: rule.id, lessonIds: own.map((l) => l.id), objects: [co],
            message: `${rule.label} (${rule.value}) überschritten: ${label}: ${own.length} Stunden` })
        }
      }
    }
  }
  return out
}

// Konflikte je Stunde, für Kachel-Badges. Steht seit 05.09. in lib/conflicts.ts, damit Attrappe und
// Oberfläche dieselbe Rechnung benutzen (Befund FRONTEND-8); der Weiterexport hält die alten Importe gültig.
export { conflictsByLesson } from '@/lib/conflicts'
