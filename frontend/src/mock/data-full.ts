// Datensatz FULL: 12 Klassen / 24 Lehrkräfte / 18 Räume, deterministisch (fester Seed), aktiver Plan aus dem Mock-Solver.
import { nowIso, today } from '@/lib/today'
import { addDaysIso } from '@/lib/dates'
import { SUBJECT_COLORS } from '@/lib/subject-colors'
import { clearStore, defaultRules, defaultTimeslots, newTimetable, nextId, store, type MockClass, type MockTeacher, type Store } from './store'
import { placeLessons } from './logic/solver'

/** mulberry32 — kleiner deterministischer Zufallsgenerator */
function rng(seed: number) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const SURNAMES = [
  'Ahrens', 'Bergmann', 'Claussen', 'Dreyer', 'Eggert', 'Frahm', 'Gerlach', 'Hansen', 'Ibsen', 'Jensen', 'Kröger', 'Lorenzen',
  'Matthiesen', 'Nissen', 'Ohlsen', 'Petersen', 'Quast', 'Rasmussen', 'Sievers', 'Thomsen', 'Ullrich', 'Voss', 'Wiese', 'Zimmer',
]
const SUBJECTS: [string, string, string | null][] = [
  ['Mathematik', 'Ma', null], ['Deutsch', 'De', null], ['Englisch', 'En', null], ['Physik', 'Ph', 'Fachraum Physik'], ['Chemie', 'Ch', 'Fachraum Chemie'],
  ['Biologie', 'Bio', null], ['Geschichte', 'Ge', null], ['Erdkunde', 'Ek', null], ['Sport', 'Sp', 'Sporthalle'], ['Kunst', 'Ku', 'Kunstraum'],
]

export function loadFull(s: Store = store): void {
  clearStore(s)
  const rand = rng(20260904)
  const pick = <T,>(arr: T[]) => arr[Math.floor(rand() * arr.length)]
  const slots = defaultTimeslots()
  s.timeslots = slots
  s.rules = defaultRules(true)
  s.config = { schoolName: 'Gymnasium Musterstadt', state: 'SH', schoolType: 'GYMNASIUM', maxSolveSeconds: 60 }

  s.subjects = SUBJECTS.map(([name, shortName, requiredRoomType], i) => ({ id: i + 1, name, shortName, requiredRoomType, color: SUBJECT_COLORS[i] }))

  const rooms: string[] = []
  for (let i = 1; i <= 12; i += 1) rooms.push(`R${100 + i}`)
  s.rooms = [
    ...rooms.map((name, i) => ({ id: i + 1, name, roomType: 'Klassenraum', active: true })),
    { id: 13, name: 'P1', roomType: 'Fachraum Physik', active: true }, { id: 14, name: 'P2', roomType: 'Fachraum Physik', active: true },
    { id: 15, name: 'C1', roomType: 'Fachraum Chemie', active: true }, { id: 16, name: 'SH1', roomType: 'Sporthalle', active: true },
    { id: 17, name: 'SH2', roomType: 'Sporthalle', active: true }, { id: 18, name: 'K1', roomType: 'Kunstraum', active: true },
  ]

  // 24 Lehrkräfte: jedes Fach ist mindestens 4-mal besetzt (24 × 2 = 48 Fachzuordnungen / 10 Fächer)
  const lessonSlots = slots.filter((t) => !t.isBreak)
  const usedShort = new Set<string>()
  s.teachers = SURNAMES.map((name, i): MockTeacher => {
    let shortName = name.slice(0, 3).toUpperCase()
    if (usedShort.has(shortName)) shortName = (name[0] + name[Math.floor(name.length / 2)] + name[name.length - 1]).toUpperCase()
    usedShort.add(shortName)
    const first = (i % 10) + 1
    let second = ((i * 3 + 1) % 10) + 1
    if (second === first) second = (second % 10) + 1
    const blocked = rand() < 0.3 ? Array.from({ length: 1 + Math.floor(rand() * 2) }, () => pick(lessonSlots).id) : []
    const preferred = rand() < 0.4 ? Array.from({ length: 2 + Math.floor(rand() * 3) }, () => pick(lessonSlots).id).filter((id) => !blocked.includes(id)) : []
    return { id: i + 1, name, shortName, weeklyHoursQuota: 20 + Math.floor(rand() * 7), subjectIds: [first, second], preferredTimeslotIds: [...new Set(preferred)], blockedTimeslotIds: [...new Set(blocked)], active: true }
  })

  // 12 Klassen 5a–10b, je 8 Fächer, 28–30 Wochenstunden; Lehrkraft = fachgleiche mit geringster Auslastung
  const load = new Map<number, number>()
  const leastLoaded = (subjectId: number) => {
    const cands = s.teachers.filter((t) => t.subjectIds.includes(subjectId)).sort((a, b) => (load.get(a.id) ?? 0) - (load.get(b.id) ?? 0) || a.id - b.id)
    return cands[0]
  }
  const names = ['5a', '5b', '6a', '6b', '7a', '7b', '8a', '8b', '9a', '9b', '10a', '10b']
  s.classes = names.map((name, i): MockClass => {
    const grade = Number.parseInt(name, 10)
    const others = [4, 5, 6, 7, 8, 9, 10].sort(() => rand() - 0.5).slice(0, 5)
    const hours: [number, number][] = [[1, 4], [2, 4], [3, 4], [others[0], 4], [others[1], 3], [others[2], 3], [others[3], 3], [others[4], 3]]
    const extra = Math.floor(rand() * 3) // 28..30
    for (let k = 0; k < extra; k += 1) hours[4 + k][1] += 1
    const subjects = hours.map(([subjectId, weeklyHours]) => {
      const t = leastLoaded(subjectId)
      load.set(t.id, (load.get(t.id) ?? 0) + weeklyHours)
      return { subjectId, weeklyHours, teacherId: t.id }
    })
    return { id: i + 1, name, grade, active: true, subjects }
  })

  s.users = [
    { id: 1, username: 'admin', password: 'admin', role: 'PLANNING', teacherId: null, classId: null },
    ...s.teachers.map((t, i) => ({ id: 2 + i, username: t.shortName.toLowerCase(), password: t.shortName.toLowerCase(), role: 'TEACHER' as const, teacherId: t.id, classId: null })),
  ]
  s.holidays = [{ id: 1, name: 'Herbstferien', dateFrom: '2026-10-19', dateTo: '2026-10-31', kind: 'VACATION' }]

  // Aktiver Plan: alle Stunden als offene Zeilen, dann Mock-Solver synchron; nichts festgelegt
  const active = newTimetable('ACTIVE', nowIso(), { name: 'Erster Plan' })
  // Angenommen vor zwei Monaten: ein Plan gilt ab dem Tag seiner Annahme (PlanReader.startOf), ein heute
  // angenommener zeigt in der laufenden Woche also nichts. Der Beispielbestand soll in jeder Woche einen
  // Plan haben, deshalb liegt die Annahme bewusst zurück.
  active.acceptedAt = `${addDaysIso(today(), -60)}T08:00:00.000Z`
  for (const c of s.classes) for (const row of c.subjects) for (let k = 0; k < row.weeklyHours; k += 1) {
    active.lessons.push({ id: nextId(), timetableId: active.id, classId: c.id, subjectId: row.subjectId, teacherId: row.teacherId, timeslotId: null, roomId: null, pinned: false })
  }
  s.timetables = [active]
  placeLessons(active, s)
}
