// Grunddaten-Warnungen (T-W1.4 / T-W1.5): Fach nicht unterrichtet, Deputat überschritten. Berechnet, nie gespeichert.
import type { WarningDto } from '@/api/types'
import type { MockClass, MockTeacher, Store } from '../store'

/** Summe der Wochenstunden dieser Lehrkraft über alle Klassen */
export function plannedHoursOf(teacherId: number, s: Store): number {
  let sum = 0
  for (const c of s.classes) for (const row of c.subjects) if (row.teacherId === teacherId) sum += row.weeklyHours
  return sum
}

export function classWarnings(cls: MockClass, s: Store): WarningDto[] {
  const out: WarningDto[] = []
  const quotaWarned = new Set<number>()
  for (const row of cls.subjects) {
    const t = s.teachers.find((x) => x.id === row.teacherId)
    const subject = s.subjects.find((x) => x.id === row.subjectId)
    if (!t) continue
    if (subject && !t.subjectIds.includes(row.subjectId)) {
      out.push({ code: 'SUBJECT_NOT_TAUGHT', teacherId: t.id, message: `${t.name} unterrichtet das Fach ${subject.name} nicht.` })
    }
    const planned = plannedHoursOf(t.id, s)
    if (planned > t.weeklyHoursQuota && !quotaWarned.has(t.id)) {
      quotaWarned.add(t.id)
      out.push({ code: 'QUOTA_EXCEEDED', teacherId: t.id, message: `${t.name} ist mit ${planned} Wochenstunden über dem Deputat von ${t.weeklyHoursQuota}.` })
    }
  }
  return out
}

export function teacherWarnings(t: MockTeacher, s: Store): WarningDto[] {
  const planned = plannedHoursOf(t.id, s)
  if (planned <= t.weeklyHoursQuota) return []
  return [{ code: 'QUOTA_EXCEEDED', teacherId: t.id, message: `${t.name} ist mit ${planned} Wochenstunden über dem Deputat von ${t.weeklyHoursQuota}.` }]
}
