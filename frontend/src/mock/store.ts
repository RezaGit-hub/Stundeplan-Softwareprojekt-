// In-Memory-Zustand der Attrappe. Keine Persistenz: Neuladen = Ausgangszustand (VITE_MOCK_DATASET).
// Interne Datensätze sind schlanker als die DTOs (z. B. Klasse ohne Warnungen) — die DTOs baut mock-api.ts.
import type {
  ChangelogItem, ConfigDto, DraftStatus, EventKind, ExceptionKind, HolidayDto, Role, RuleDto, RuleType, SolveInfo,
  SubjectDto, RoomDto, TimeslotDto, ExtraLessonInput,
} from '@/api/types'
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
  createdAt: string
  acceptedAt: string | null
  /** Kann (E-40): Plan gilt erst ab diesem Datum; bis dahin gilt der zuletzt archivierte */
  validFrom: string | null
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
  solveTimer: ReturnType<typeof setTimeout> | null
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
    tokens: new Map(), solveTimer: null,
  }
}

/** Der eine Store der Attrappe. reset() tauscht den Inhalt, nicht das Objekt. */
export const store: Store = emptyStore()

export function clearStore(s: Store = store): void {
  if (s.solveTimer) clearTimeout(s.solveTimer)
  Object.assign(s, emptyStore(), { tokens: s.tokens })
}

export function activeTimetable(s: Store = store): MockTimetable | undefined {
  return s.timetables.find((t) => t.status === 'ACTIVE')
}
export function draftTimetable(s: Store = store): MockTimetable | undefined {
  return s.timetables.find((t) => t.status === 'DRAFT')
}
export function findLesson(id: number, s: Store = store): MockLesson | undefined {
  for (const t of s.timetables) {
    const l = t.lessons.find((x) => x.id === id)
    if (l) return l
  }
  return undefined
}

export function newTimetable(status: TimetableStatus, createdAt: string): MockTimetable {
  return {
    id: nextId(), status, createdAt, acceptedAt: null, validFrom: null, draftStatus: 'EDITING',
    solve: { startedAt: null, limitSeconds: null, lastScore: null, error: null }, lessons: [],
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
