// In-Memory-Zustand der Attrappe. Keine Persistenz: Neuladen = Ausgangszustand (VITE_MOCK_DATASET).
// Interne Datensätze sind schlanker als die DTOs (z. B. Klasse ohne Warnungen) — die DTOs baut mock-api.ts.
import type {
  ChangelogItem, ConfigDto, DraftSource, DraftStatus, EventKind, ExceptionKind, HolidayDto, Role, RuleDto, RuleType, SolveInfo,
  SubjectDto, RoomDto, TimeslotDto, ExtraLessonInput,
} from '@/api/types'
import { planValidFrom } from '@/lib/dates'
import { today } from '@/lib/today'
import { RULE_PRESETS } from './rule-presets'

export interface MockTeacher {
  id: number
  name: string
  shortName: string
  weeklyHoursQuota: number
  subjectIds: number[]
  preferredTimeslotIds: number[]
  blockedTimeslotIds: number[]
  active: boolean
}
export interface MockClassSubject { subjectId: number; weeklyHours: number; teacherId: number }
export interface MockClass { id: number; name: string; grade: number; active: boolean; subjects: MockClassSubject[] }
/** classId nur bei der Rolle STUDENT (Klassenkonto, E-50) — wie user_account.class_id in V1 */
export interface MockUser {
  id: number; username: string; password: string; role: Role; teacherId: number | null; classId: number | null
  /** Rundgang beendet (#139); fehlt oder null = noch nie — wie `user_account.tour_completed_at` */
  tourCompletedAt?: string | null
}

export type TimetableStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
export interface MockLesson {
  id: number
  timetableId: number
  classId: number
  subjectId: number
  /** Schnappschuss aus ClassSubject; replaceClassSubjects zieht ihn nach */
  teacherId: number
  timeslotId: number | null
  roomId: number | null
  pinned: boolean
}
export interface MockTimetable {
  id: number
  status: TimetableStatus
  /** Name des Entwurfs (#103); angenommene Pläne tragen ihn weiter, gezeigt wird er nur am Entwurf */
  name: string
  /** Woraus der Entwurf entstanden ist (#103) — nur für die Entwurfsliste */
  source: DraftSource
  createdAt: string
  acceptedAt: string | null
  /** Kann (E-40): Plan gilt erst ab diesem Datum; bis dahin gilt der Vorgänger */
  validFrom: string | null
  /** Kann (#115): Plan gilt bis zu diesem Tag einschließlich; null = unbegrenzt */
  validUntil: string | null
  /** In der Ablage (#116): der Entwurf ist eingefroren, bis ihn jemand wieder öffnet */
  finished: boolean
  draftStatus: DraftStatus
  solve: SolveInfo
  lessons: MockLesson[]
}
export interface MockEvent {
  id: number
  kind: EventKind
  teacherId: number | null
  roomId: number | null
  dateFrom: string
  dateTo: string
  reason: string
  createdAt: string
  createdBy: string
  extra: ExtraLessonInput | null
}
export interface MockException {
  id: number
  date: string
  lessonId: number | null
  kind: ExceptionKind
  substituteTeacherId: number | null
  substituteRoomId: number | null
  newDate: string | null
  newTimeslotId: number | null
  eventIds: number[]
  /** nur ADDED (Zusatzstunde) */
  added: { classId: number; subjectId: number; teacherId: number; roomId: number } | null
  createdAt: string
}
export interface MockChangelogEntry extends ChangelogItem {
  lessonId: number | null
  classId: number | null
  teacherId: number | null
}

export interface Store {
  teachers: MockTeacher[]
  subjects: SubjectDto[]
  rooms: RoomDto[]
  classes: MockClass[]
  timeslots: TimeslotDto[]
  rules: RuleDto[]
  config: ConfigDto
  users: MockUser[]
  timetables: MockTimetable[]
  events: MockEvent[]
  exceptions: MockException[]
  changelog: MockChangelogEntry[]
  holidays: HolidayDto[]
  /** token → userId */
  tokens: Map<string, number>
  /** Ein laufender Solver je Entwurf (#103): Entwurfskennung → Zeitgeber */
  solveTimers: Map<number, ReturnType<typeof setTimeout>>
}

/** Vorbelegtes Bundesland der Systemkonfiguration (E-37, wie system_config in V2) */
export const DEFAULT_STATE = 'SH'

let idCounter = 1000
/** Fortlaufende IDs für alles, was zur Laufzeit entsteht (Datensätze nutzen kleine feste IDs) */
export function nextId(): number {
  idCounter += 1
  return idCounter
}

export function emptyStore(): Store {
  return {
    teachers: [], subjects: [], rooms: [], classes: [], timeslots: [], rules: [],
    config: { schoolName: '', state: DEFAULT_STATE, schoolType: 'GYMNASIUM', maxSolveSeconds: 60 },
    users: [], timetables: [], events: [], exceptions: [], changelog: [], holidays: [],
    tokens: new Map(), solveTimers: new Map(),
  }
}

/** Der eine Store der Attrappe. reset() tauscht den Inhalt, nicht das Objekt. */
export const store: Store = emptyStore()

export function clearStore(s: Store = store): void {
  for (const timer of s.solveTimers.values()) clearTimeout(timer)
  Object.assign(s, emptyStore(), { tokens: s.tokens })
}

/**
 * Ab welchem Kalendertag ein angenommener Plan gilt: sein Stichtag, sonst der Tag der Annahme
 * (Europe/Berlin) — wie `PlanReader.startOf` im Server.
 */
export function planStart(t: MockTimetable): string {
  return t.validFrom ?? (t.acceptedAt ? planValidFrom(t.acceptedAt) : t.createdAt.slice(0, 10))
}

/** Gilt dieser Plan an diesem Kalendertag? */
export function planCovers(t: MockTimetable, dateIso: string): boolean {
  return planStart(t) <= dateIso && (t.validUntil == null || dateIso <= t.validUntil)
}

/** Alle angenommenen Pläne, Beginn aufsteigend (#115). Ihre Zeiträume überschneiden sich nie. */
export function activePlans(s: Store = store): MockTimetable[] {
  return s.timetables
    .filter((t) => t.status === 'ACTIVE')
    .sort((a, b) => planStart(a).localeCompare(planStart(b)) || a.id - b.id)
}

/**
 * „Der aktive Plan" (GET /api/timetable): der angenommene Plan, der heute gilt; sonst der mit dem
 * nächsten künftigen Beginn; sonst keiner. Seit #115 darf es mehrere geben.
 */
export function activeTimetable(s: Store = store): MockTimetable | undefined {
  const heute = today()
  const plans = activePlans(s)
  return plans.find((t) => planCovers(t, heute)) ?? plans.find((t) => planStart(t) > heute)
}

/** Alle Entwürfe, jüngster zuerst (#103). */
export function draftTimetables(s: Store = store): MockTimetable[] {
  return s.timetables.filter((t) => t.status === 'DRAFT').sort((a, b) => b.id - a.id)
}
/** Der jüngste Entwurf — was die Singular-Pfade `/api/draft…` im Vertrag meinen. */
export function draftTimetable(s: Store = store): MockTimetable | undefined {
  return draftTimetables(s)[0]
}
export function findLesson(id: number, s: Store = store): MockLesson | undefined {
  for (const t of s.timetables) {
    const l = t.lessons.find((x) => x.id === id)
    if (l) return l
  }
  return undefined
}

export function newTimetable(status: TimetableStatus, createdAt: string, opts: { name?: string; source?: DraftSource } = {}): MockTimetable {
  return {
    id: nextId(), status, name: opts.name ?? 'Entwurf 1', source: opts.source ?? 'EMPTY',
    createdAt, acceptedAt: null, validFrom: null, validUntil: null, finished: false, draftStatus: 'EDITING',
    solve: { startedAt: null, limitSeconds: null, lastScore: null, error: null }, lessons: [],
  }
}

/** „Entwurf n" mit der ersten freien Nummer — wie der Server, wenn kein Name gesendet wird (#103). */
export function nextDraftName(s: Store = store): string {
  const genommen = new Set(s.timetables.map((t) => t.name))
  for (let n = 1; ; n += 1) {
    const name = `Entwurf ${n}`
    if (!genommen.has(name)) return name
  }
}

/** Standard-Stundenraster: Mo–Fr, 6 Stunden, große Pause nach der 2. (Pflichtenheft) */
export function defaultTimeslots(): TimeslotDto[] {
  const rows: [number, string, string, boolean][] = [
    [1, '08:00', '08:45', false], [2, '08:50', '09:35', false], [3, '09:35', '09:55', true],
    [4, '09:55', '10:40', false], [5, '10:45', '11:30', false], [6, '11:50', '12:35', false], [7, '12:40', '13:25', false],
  ]
  const days = ['MO', 'TU', 'WE', 'TH', 'FR'] as const
  const slots: TimeslotDto[] = []
  days.forEach((day, di) => {
    for (const [position, startTime, endTime, isBreak] of rows) {
      slots.push({ id: di * 7 + position, day, position, startTime, endTime, isBreak })
    }
  })
  return slots
}

/**
 * Beschriftung einer Regelzeile. Dieselben Texte wie RULE_TYPE_LABEL in den Grunddaten-Helfern —
 * hier bewusst noch einmal, damit die Attrappe nichts aus der Oberflaeche importiert.
 */
const RULE_LABEL: Record<RuleType, string> = {
  MAX_LESSONS_PER_DAY: 'Maximale Stunden pro Tag',
  MAX_LESSONS_PER_WEEK: 'Maximale Stunden pro Woche',
  NO_LESSONS_IN_BREAKS: 'Keine Stunden in Pausen',
}

/**
 * Beispielsatz eines Bundeslands als Regelwerk — dasselbe, was POST /api/rules/reset im Backend tut
 * (rule_preset → rule, E-48). Unbekanntes Kürzel → null, den 409 wirft die Attrappe selbst.
 *
 * Die Kennungen sind fest vergeben, damit ein „Beispielsatz laden" dieselbe Zeile wie zuvor trifft:
 * 1..5 für die schulweiten Zeilen in der Reihenfolge der Voreinstellung, danach die Jahrgangszeilen.
 * Die Lehrkraftzeilen sind wie im Seed V2 abschaltbar, damit ein Testszenario keine Verstöße meldet.
 */
export function rulesFromPreset(state: string, teacherRulesActive: boolean): RuleDto[] | null {
  const rows = RULE_PRESETS[state]
  if (!rows) return null
  return rows.map((p, i) => ({
    id: i + 1,
    type: p.type,
    scope: p.scope,
    grade: p.grade,
    value: p.value,
    severity: p.severity,
    weight: p.weight,
    active: p.scope === 'TEACHER' && p.grade == null ? teacherRulesActive : true,
    label: RULE_LABEL[p.type],
  }))
}

/** Regelwerk Seed V2: der Beispielsatz des vorbelegten Bundeslands (Schleswig-Holstein, E-37/E-48) */
export function defaultRules(teacherRulesActive: boolean): RuleDto[] {
  return rulesFromPreset(DEFAULT_STATE, teacherRulesActive) ?? []
}

/** Zeitfenster-ID nach Tag und Position (nur für Datensätze) */
export function slotId(slots: TimeslotDto[], day: TimeslotDto['day'], position: number): number {
  const s = slots.find((x) => x.day === day && x.position === position)
  if (!s) throw new Error(`Kein Zeitfenster ${day} ${position}`)
  return s.id
}
