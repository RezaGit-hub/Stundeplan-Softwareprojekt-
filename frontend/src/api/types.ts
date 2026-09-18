// DTOs, Enums (als String-Unions) und die Api-Schnittstelle — feldgenau nach
// api-contract/openapi.yaml v1 (Stand 05.09.2026). Nach W1 eingefroren: Änderungen nur additiv,
// Fehler an W1 melden. Bezeichner englisch wie im Vertrag; Texte (message/detail/label) deutsch vom Server.

// ------------------------------------------------------------------ Enums (erasableSyntaxOnly: keine TS-enums)
/** STUDENT = Klassenkonto: ein Konto je Klasse, sieht nur den eigenen Klassenplan (E-50) */
export type Role = 'PLANNING' | 'TEACHER' | 'STUDENT'
export const ROLES = ['PLANNING', 'TEACHER', 'STUDENT'] as const

export type Weekday = 'MO' | 'TU' | 'WE' | 'TH' | 'FR' | 'SA'
export const WEEKDAYS: readonly Weekday[] = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA'] as const

export type View = 'SCHOOL' | 'CLASS' | 'TEACHER' | 'ROOM'
export const VIEWS: readonly View[] = ['SCHOOL', 'CLASS', 'TEACHER', 'ROOM'] as const

export type EntryKind = 'REGULAR' | 'NEEDS_SUBSTITUTION' | 'SUBSTITUTED' | 'GIVEN_AWAY' | 'MOVED' | 'CANCELLED' | 'ADDED'
export const ENTRY_KINDS: readonly EntryKind[] = ['REGULAR', 'NEEDS_SUBSTITUTION', 'SUBSTITUTED', 'GIVEN_AWAY', 'MOVED', 'CANCELLED', 'ADDED'] as const

/** ROOM_BLOCKED_TIME seit v1.1.0 (#133): Stunde in einer wöchentlichen Sperrzeit des Raums */
export type ConflictType = 'ROOM_DOUBLE_BOOKED' | 'TEACHER_DOUBLE_BOOKED' | 'CLASS_DOUBLE_BOOKED' | 'TEACHER_BLOCKED_TIME' | 'ROOM_TYPE_MISMATCH' | 'RULE_VIOLATION' | 'ROOM_BLOCKED_TIME'
export type Severity = 'HARD' | 'SOFT'
export type ObjectKind = 'CLASS' | 'TEACHER' | 'ROOM'

export type RuleType = 'MAX_LESSONS_PER_DAY' | 'MAX_LESSONS_PER_WEEK' | 'NO_LESSONS_IN_BREAKS'
export const RULE_TYPES: readonly RuleType[] = ['MAX_LESSONS_PER_DAY', 'MAX_LESSONS_PER_WEEK', 'NO_LESSONS_IN_BREAKS'] as const
export type RuleScope = 'CLASS' | 'TEACHER'

export type DraftStatus = 'EDITING' | 'SOLVING' | 'FAILED'
export type DraftSource = 'EMPTY' | 'COPY_ACTIVE'

export type EventKind = 'TEACHER_ABSENCE' | 'ROOM_BLOCKED' | 'EXTRA_LESSON'
export const EVENT_KINDS: readonly EventKind[] = ['TEACHER_ABSENCE', 'ROOM_BLOCKED', 'EXTRA_LESSON'] as const
export type ExceptionKind = 'SUBSTITUTION' | 'MOVE' | 'CANCEL' | 'ADDED'
export type ChangelogAction = 'EXCEPTION_CREATED' | 'EXCEPTION_DELETED' | 'TIMETABLE_ACCEPTED'
export type HolidayKind = 'VACATION' | 'PUBLIC_HOLIDAY'
export type WarningCode = 'SUBJECT_NOT_TAUGHT' | 'QUOTA_EXCEEDED'

// ------------------------------------------------------------------ Auth, Konten
export interface HealthDto { status: string; schoolName: string }
export interface LoginRequest { username: string; password: string }
export interface LoginResponse { token: string; role: Role; displayName: string; teacherId: number | null }
/** tourCompletedAt (seit 1.2.0, #139): wann der Rundgang beendet/übersprungen wurde; null = noch nie → Web zeigt ihn */
export interface MeDto { id: number; username: string; role: Role; displayName: string; teacherId: number | null; tourCompletedAt: string | null }
export interface PasswordChange { oldPassword: string; newPassword: string }
/** Body von PUT /api/me/tour (#139): true = Rundgang beendet, false = erneut zeigen */
export interface TourFlag { completed: boolean }
export interface UserDto { id: number; username: string; role: Role; teacherId: number | null; classId: number | null; classShort: string | null }
export interface CreateUser { username: string; password: string; role: Role; teacherId?: number | null; classId?: number | null }
/** Antwortzeile von POST /users/class-accounts — das Passwort kommt genau einmal im Klartext (E-50) */
export interface ClassAccountDto { username: string; classShort: string; password: string }
export interface TeacherAccountDto { username: string; teacherName: string; teacherShort: string; password: string }
// ------------------------------------------------------------------ Grunddaten
export interface WarningDto { code: WarningCode; message: string; teacherId: number | null }

export interface TeacherDto {
  id: number
  name: string
  shortName: string
  weeklyHoursQuota: number
  subjectIds: number[]
  preferredTimeslotIds: number[]
  blockedTimeslotIds: number[]
  active: boolean
  /** Summe der Wochenstunden aus allen Klassenzuordnungen (berechnet) */
  plannedHours: number
  warnings: WarningDto[]
}
export interface TeacherInput {
  name: string
  shortName: string
  weeklyHoursQuota: number
  subjectIds: number[]
  preferredTimeslotIds: number[]
  blockedTimeslotIds: number[]
  active: boolean
}


// ------------------------------------------------------------------ Bereichsweise Sicherung (Version 2)

export type TransferScope = 'subjects' | 'teachers' | 'rooms' | 'classes' | 'timeslots' | 'rules' | 'holidays'
export const TRANSFER_SCOPES: readonly TransferScope[] =
  ['subjects', 'teachers', 'rooms', 'classes', 'timeslots', 'rules', 'holidays'] as const

export interface SubjectRef { id: number; shortName: string }
export interface TeacherRef { id: number; shortName: string }
export interface TimeslotRef { id: number; day: Weekday; position: number }
export interface SectionRefs {
  subjects?: SubjectRef[] | null
  teachers?: TeacherRef[] | null
  timeslots?: TimeslotRef[] | null
}

export interface MasterDataSection {
  version: '2'
  scope: TransferScope
  exportedAt: string
  subjects?: SubjectDto[] | null
  teachers?: TeacherDto[] | null
  rooms?: RoomDto[] | null
  classes?: ClassDto[] | null
  timeslots?: TimeslotDto[] | null
  rules?: RuleDto[] | null
  holidays?: HolidayDto[] | null
  refs?: SectionRefs | null
}

export interface SectionImportResult {
  scope: TransferScope
  created: number
  updated: number
  warnings: string[]
}


export interface SubjectDto { id: number; name: string; shortName: string; requiredRoomType: string | null; color: string }
export type SubjectInput = Omit<SubjectDto, 'id'>

/** blockedTimeslotIds: wöchentliche Sperrzeiten (hart), optional seit v1.1.0 (#133); der Server liefert immer eine Liste, fehlend beim Senden = keine Sperre */
export interface RoomDto { id: number; name: string; roomType: string; active: boolean; blockedTimeslotIds?: number[] }
export type RoomInput = Omit<RoomDto, 'id'>

export interface ClassSubjectDto { subjectId: number; subjectShort: string; weeklyHours: number; teacherId: number; teacherShort: string }
/** Eine Zeile der Fächerliste beim Speichern; null/leer wird vom Server mit 400 abgelehnt */
export interface ClassSubjectInput { subjectId: number; weeklyHours: number | null; teacherId: number | null }
export interface ClassDto { id: number; name: string; grade: number; active: boolean; subjects: ClassSubjectDto[]; warnings: WarningDto[] }
export interface ClassInput { name: string; grade: number; active: boolean }

export interface TimeslotDto { id: number; day: Weekday; position: number; startTime: string; endTime: string; isBreak: boolean }
export interface TimegridDto { slots: TimeslotDto[] }
/** PUT /timegrid: upsert nach (day, position); id optional */
export interface TimeslotInput { id?: number; day: Weekday; position: number; startTime: string; endTime: string; isBreak: boolean }
export interface TimegridInput { slots: TimeslotInput[] }

export interface RuleDto {
  id: number
  type: RuleType
  scope: RuleScope | null
  /** null = schulweit; gesetzt = nur dieser Jahrgang (Soll, E-47) */
  grade: number | null
  value: number
  severity: Severity
  weight: number
  active: boolean
  label: string
}
export interface RuleInput {
  type?: RuleType
  scope?: RuleScope | null
  grade?: number | null
  value: number
  severity: Severity
  weight: number
  active: boolean
}

export interface ConfigDto { schoolName: string; state: string; schoolType: string; maxSolveSeconds: number }
export interface SampleDataResult { classes: number; teachers: number; rooms: number }

// ------------------------------------------------------------------ Planung
export interface LessonDto {
  id: number
  classId: number
  classShort: string
  subjectId: number
  subjectShort: string
  subjectColor: string
  teacherId: number
  teacherShort: string
  roomId: number | null
  roomShort: string | null
  /** null = offene Stunde (dann auch roomId, day, position null) */
  timeslotId: number | null
  day: Weekday | null
  position: number | null
  pinned: boolean
}

export interface ConflictObject { kind: ObjectKind; id: number; label: string }
export interface ConflictDto {
  type: ConflictType
  severity: Severity
  message: string
  lessonIds: number[]
  ruleId: number | null
  objects: ConflictObject[]
}

export interface TimetableDto {
  /** null vor der ersten Annahme */
  id: number | null
  acceptedAt: string | null
  /**
   * Ab welchem Kalendertag der Plan gilt (E-40); null, wenn ohne Stichtag angenommen — dann gilt er ab
   * dem Tag der Annahme. Seit 05.09. im Vertrag (Befund PFLICHTENHEFT-6); vorher riet die Web-App den
   * Beginn aus `acceptedAt` und zeigte bei einem Plan mit Stichtag das falsche Datum.
   */
  validFrom: string | null
  /**
   * Bis zu welchem Kalendertag der Plan gilt (seit 1.4.0, #115); null = unbegrenzt. Mehrere angenommene
   * Pläne dürfen nebeneinander ACTIVE sein, solange ihre Zeiträume sich nicht überschneiden.
   */
  validUntil: string | null
  lessons: LessonDto[]
  conflicts: ConflictDto[]
}

/** Eine Zeile von GET /api/timetables (seit 1.4.0, #115): alle angenommenen Pläne, Beginn aufsteigend. */
export interface TimetableSummaryDto {
  id: number
  acceptedAt: string | null
  validFrom: string | null
  validUntil: string | null
  /** gilt heute */
  current: boolean
}

/**
 * Antwort von POST /api/draft/accept (api.md Zeile 52): der neue aktive Plan plus `warnings[]`,
 * wenn künftige Ausnahmen verfallen. Eigener Typ, damit die Oberfläche `warnings` ohne Cast lesen kann.
 */
export interface AcceptDraftResponse extends TimetableDto {
  warnings: string[]
}

export interface SolveInfo { startedAt: string | null; limitSeconds: number | null; lastScore: string | null; error: string | null }
export interface OpenLessonDto { classId: number; classShort: string; subjectShort: string; missing: number }
export interface DraftDto {
  id: number
  /** Name des Entwurfs (seit 1.3.0, #103); ohne Angabe vergibt der Server „Entwurf n" */
  name: string
  createdAt: string
  status: DraftStatus
  solve: SolveInfo
  lessons: LessonDto[]
  conflicts: ConflictDto[]
  openLessons: OpenLessonDto[]
  warnings: string[]
  /**
   * Wie viele datierte Vertretungen ab heute das Annehmen dieses Entwurfs verfallen ließe — nur die,
   * deren Stunde er verschiebt, umräumt oder streicht. Vertretungen an unverändert übernommenen Stunden
   * wandern beim Annehmen mit (Befund PFLICHTENHEFT-9). `0` heißt: es geht nichts verloren.
   */
  expiringExceptions: number
  /**
   * In der Ablage (seit 1.4.0, #116): der Entwurf ist eingefroren. Stunden ändern, Berechnen und
   * Reparieren antworten dann 409 `draft-finished`; Annehmen, Umbenennen und Löschen bleiben erlaubt.
   */
  finished: boolean
}

/** Eine Zeile von GET /api/drafts (seit 1.3.0, #103) — Kennzahlen ohne Stunden. */
export interface DraftSummaryDto {
  id: number
  name: string
  createdAt: string
  source: DraftSource
  status: DraftStatus
  hardConflicts: number
  finished: boolean
}

/** POST /api/drafts — `name` ist Kann (seit 1.3.0, #103) */
export interface CreateDraft { name?: string; source: DraftSource }
/** PATCH /api/drafts/{id} — nur gesendete Felder wirken; leerer Rumpf ist 400 `validation` (seit 1.4.0) */
export interface DraftPatchInput { name?: string; finished?: boolean }
/** POST /api/drafts/{id}/accept — beides Kann (E-40, #115) */
export interface AcceptDraftInput { validFrom?: string; validUntil?: string }
export interface AddLessonInput { classId: number; subjectId: number; timeslotId: number; roomId: number }
export interface PatchLessonInput { timeslotId?: number; roomId?: number; pinned?: boolean }

// Konflikt per Knopf lösen (#110, seit 1.2.0): POST /api/draft/repair rechnet Vorschläge, speichert nichts.
export interface RepairInput { lessonId: number }
/** Eine Stunde wandert in dieses Zeitfenster und diesen Raum; übernommen per PATCH /draft/lessons/{lessonId} mit pinned:false */
export interface RepairMove { lessonId: number; timeslotId: number; roomId: number }
export interface RepairProposal {
  /** 1 = nur die Konfliktstunde, 2 = dazu die Stunden derselben Klasse am selben Tag, 3 = dazu Lehrkraft und Raum an diesem Tag */
  level: number
  label: string
  moves: RepairMove[]
  /** harte Konflikte im ganzen Entwurf, wenn der Vorschlag übernommen ist */
  hardConflictsAfter: number
  score: string | null
}
export interface RepairResponse { proposals: RepairProposal[] }

// ------------------------------------------------------------------ Aufgelöste Woche
/** Nur die geänderten Werte sind gesetzt (Vertretung: teacher/room; verlegt: date/position) */
export interface EntryOriginal {
  teacherId?: number | null
  teacherShort?: string | null
  roomId?: number | null
  roomShort?: string | null
  date?: string | null
  position?: number | null
}
export interface EntryDto {
  date: string
  timeslotId: number
  day: Weekday
  position: number
  /** null bei Zusatzstunde (ADDED) */
  lessonId: number | null
  classId: number
  classShort: string
  subjectShort: string
  subjectColor: string
  teacherId: number | null
  teacherShort: string | null
  roomId: number | null
  roomShort: string | null
  kind: EntryKind
  original: EntryOriginal | null
  reasons: string[]
  exceptionId: number | null
}
export interface DayDto { date: string; schoolDay: boolean; entries: EntryDto[] }
export interface WeekDto { monday: string; days: DayDto[] }
export interface WeekQuery { date: string; view: View; id?: number }

// ------------------------------------------------------------------ Ereignisse, Vertretung
export interface AffectedDto { date: string; lessonId: number; classShort: string; position: number }
export interface ExtraLessonInput { date: string; timeslotId: number; classId: number; subjectId: number; teacherId: number; roomId: number }
export interface EventDto {
  id: number
  kind: EventKind
  teacherId: number | null
  roomId: number | null
  dateFrom: string
  dateTo: string
  reason: string
  createdAt: string
  createdBy: string
  affected: AffectedDto[]
  /** gesetzt, wenn affected leer ist */
  hint: string | null
  extra?: ExtraLessonInput | null
}
export interface EventInput {
  kind: EventKind
  teacherId?: number | null
  roomId?: number | null
  dateFrom: string
  dateTo: string
  reason: string
  extra?: ExtraLessonInput | null
}
export interface EventQuery { from?: string; to?: string; teacherId?: number; roomId?: number }

export interface PendingRow {
  date: string
  lessonId: number
  classShort: string
  subjectShort: string
  position: number
  teacherShort: string
  roomShort: string
  reasons: string[]
  eventIds: number[]
}

export interface TeacherOption {
  id: number
  shortName: string
  name: string
  teachesSubject: boolean
}
export interface RoomOption { id: number; name: string; roomType: string; matchesRequirement: boolean }
export interface OptionsDto { teachers: TeacherOption[]; rooms: RoomOption[] }

export interface ExceptionDto {
  id: number
  date: string
  lessonId: number | null
  kind: ExceptionKind
  substituteTeacherId: number | null
  substituteRoomId: number | null
  newDate: string | null
  newTimeslotId: number | null
  eventIds: number[]
  /** Folgekonflikte des Tages — Daten, kein Fehler */
  conflicts: ConflictDto[]
}
export interface ExceptionInput {
  date: string
  lessonId: number
  kind: ExceptionKind
  substituteTeacherId?: number | null
  substituteRoomId?: number | null
  newDate?: string | null
  newTimeslotId?: number | null
  eventIds?: number[]
}

// ------------------------------------------------------------------ Verlauf, Ferien
export interface ChangelogItem {
  id: number
  timestamp: string
  action: ChangelogAction
  date: string | null
  position: number | null
  classShort: string | null
  oldAssignment: string
  newAssignment: string
  /** alle Anlässe, "; "-getrennt */
  reasons: string
  username: string
}
export interface ChangelogPage { items: ChangelogItem[]; page: number; size: number; total: number }
export interface ChangelogQuery { from?: string; to?: string; classId?: number; teacherId?: number; lessonId?: number; page?: number; size?: number }

export interface HolidayDto { id: number; name: string; dateFrom: string; dateTo: string; kind: HolidayKind }
export type HolidayInput = Omit<HolidayDto, 'id'>

/** Schuljahre, fuer die zum eingestellten Bundesland eine Ferien- und Feiertagsvorlage mitgeliefert wird (E-51) */
export interface HolidayPresetYearsDto { state: string; schoolYears: string[] }
/** Eingabe von POST /api/holidays/preset */
export interface HolidayPresetInput { schoolYear: string }
/** Ergebnis des Uebernehmens: `added` neu angelegt, `skipped` war schon da */
export interface HolidayPresetResultDto { added: number; skipped: number; state: string; schoolYear: string }


// ------------------------------------------------------------------ Grunddaten-Sicherung
export interface MasterDataExport {
  version: '1'
  exportedAt: string
  subjects: SubjectDto[]
  teachers: TeacherDto[]
  rooms: RoomDto[]
  classes: ClassDto[]
  timeslots: TimeslotDto[]
  rules: RuleDto[]
  holidays: HolidayDto[]
}
export interface MasterDataImport extends MasterDataExport {
  replace: boolean
}
export interface MasterDataImportResult {
  subjects: number
  teachers: number
  rooms: number
  classes: number
  timeslots: number
  rules: number
  holidays: number
  warnings: string[]
}

// ------------------------------------------------------------------ Fehler (RFC 9457)
/** Präfix jedes `type` im Vertrag — der Rest ist der Slug, an dem die Oberfläche einen Fehler erkennt. */
export const PROBLEM_PREFIX = 'urn:pushnpull:problem:'
/**
 * Die Slugs, auf die diese Anwendung eigens reagiert (statt nur den Toast zu zeigen):
 * - `validation` 400 mit `errors[]`
 * - `draft-has-conflicts` 409 beim Annehmen, `conflicts[]` trägt die harten Konflikte
 * - `plan-overlap` 409 beim Annehmen: ein angenommener Plan belegt den Zeitraum schon (seit 1.4.0, #115)
 * - `draft-finished` 409: der Entwurf liegt in der Ablage (seit 1.4.0, #116)
 * - `solving` 409: für diesen Entwurf läuft eine Berechnung
 */
export type ProblemSlug = 'validation' | 'draft-has-conflicts' | 'plan-overlap' | 'draft-finished' | 'solving'

export interface FieldErrorDto { field: string; message: string }
export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  errors?: FieldErrorDto[]
  missing?: string[]
  conflicts?: ConflictDto[]
}

export class ApiError extends Error {
  problem: ProblemDetail
  status: number
  constructor(problem: ProblemDetail) {
    super(problem.title)
    this.name = 'ApiError'
    this.problem = problem
    this.status = problem.status
  }
}

// ------------------------------------------------------------------ Api: eine Methode je Endpunkt
export interface Api {
  health(): Promise<HealthDto>
  login(body: LoginRequest): Promise<LoginResponse>
  logout(): Promise<void>
  me(): Promise<MeDto>
  changeMyPassword(body: PasswordChange): Promise<void>
  /** Rundgang beendet (true) oder erneut zeigen (false) — PUT /api/me/tour (#139) */
  setTour(body: TourFlag): Promise<void>

  listUsers(): Promise<UserDto[]>
  createUser(body: CreateUser): Promise<UserDto>
  /** ein Konto je aktiver Klasse ohne Konto; idempotent, Passwörter nur in dieser Antwort (E-50) */
  createClassAccounts(): Promise<ClassAccountDto[]>
  createTeacherAccounts(): Promise<TeacherAccountDto[]>
  setUserPassword(id: number, body: { password: string }): Promise<void>
  deleteUser(id: number): Promise<void>
  
  listTeachers(): Promise<TeacherDto[]>
  getTeacher(id: number): Promise<TeacherDto>
  createTeacher(body: TeacherInput): Promise<TeacherDto>
  updateTeacher(id: number, body: TeacherInput): Promise<TeacherDto>
  deleteTeacher(id: number): Promise<void>

  listSubjects(): Promise<SubjectDto[]>
  getSubject(id: number): Promise<SubjectDto>
  createSubject(body: SubjectInput): Promise<SubjectDto>
  updateSubject(id: number, body: SubjectInput): Promise<SubjectDto>
  deleteSubject(id: number): Promise<void>

  listRooms(): Promise<RoomDto[]>
  getRoom(id: number): Promise<RoomDto>
  createRoom(body: RoomInput): Promise<RoomDto>
  updateRoom(id: number, body: RoomInput): Promise<RoomDto>
  deleteRoom(id: number): Promise<void>

  listClasses(): Promise<ClassDto[]>
  getClass(id: number): Promise<ClassDto>
  createClass(body: ClassInput): Promise<ClassDto>
  updateClass(id: number, body: ClassInput): Promise<ClassDto>
  deleteClass(id: number): Promise<void>
  replaceClassSubjects(id: number, rows: ClassSubjectInput[]): Promise<ClassDto>

  getTimegrid(): Promise<TimegridDto>
  putTimegrid(body: TimegridInput): Promise<TimegridDto>

  listRules(): Promise<RuleDto[]>
  updateRule(id: number, body: RuleInput): Promise<RuleDto>
  resetRules(): Promise<RuleDto[]>
  createRule(body: RuleInput): Promise<RuleDto>
  deleteRule(id: number): Promise<void>

  getConfig(): Promise<ConfigDto>
  putConfig(body: ConfigDto): Promise<ConfigDto>
  loadSampleData(body?: { replace?: boolean }): Promise<SampleDataResult>

  /** #160: Lehrkräfte, Klassen, Räume und Fächer zusammen mit allem, was noch auf sie verweist —
   *  anders als die einzelnen DELETE-Endpunkte, die bei Verwendung mit 409 ablehnen. */
  clearAllMasterData(): Promise<void>

  getTimetable(): Promise<TimetableDto>
  /** Alle angenommenen Pläne, Beginn aufsteigend (seit 1.4.0, #115) */
  listTimetables(): Promise<TimetableSummaryDto[]>
  getTimetableById(id: number): Promise<TimetableDto>
  getWeek(q: WeekQuery): Promise<WeekDto>
  getMyWeek(q: { date: string }): Promise<WeekDto>
  /** Woche der eigenen Klasse (Rolle STUDENT); serverseitig gefiltert: reasons immer leer (E-50) */
  getMyClassWeek(q: { date: string }): Promise<WeekDto>

  // Entwürfe (seit 1.3.0 mehrere nebeneinander, #103): die Web-App spricht nur die Plural-Pfade
  // `/api/drafts…` an. Die Singular-Pfade `/api/draft…` bleiben im Vertrag für ältere Clients stehen.
  listDrafts(): Promise<DraftSummaryDto[]>
  createDraft(body: CreateDraft): Promise<DraftDto>
  getDraftById(id: number): Promise<DraftDto>
  /** Umbenennen und Ablage (seit 1.4.0, #116); nur gesendete Felder wirken */
  patchDraft(id: number, body: DraftPatchInput): Promise<DraftDto>
  deleteDraftById(id: number): Promise<void>
  addDraftLesson(draftId: number, body: AddLessonInput): Promise<DraftDto>
  patchDraftLesson(draftId: number, lessonId: number, body: PatchLessonInput): Promise<DraftDto>
  deleteDraftLesson(draftId: number, lessonId: number): Promise<DraftDto>
  startSolve(draftId: number, body?: { classId?: number }): Promise<void>
  cancelSolve(draftId: number): Promise<void>
  acceptDraft(draftId: number, body?: AcceptDraftInput): Promise<AcceptDraftResponse>
  /** Vorschläge zum Auflösen eines Konflikts (#110); synchron bis zu 15 s, speichert nichts */
  repairDraft(draftId: number, body: RepairInput): Promise<RepairResponse>

  listEvents(q?: EventQuery): Promise<EventDto[]>
  createEvent(body: EventInput): Promise<EventDto>
  /** Ereignis zurücknehmen; die Zusatzstunde eines EXTRA_LESSON wird mit gelöscht (Vertrag V-6) */
  deleteEvent(id: number): Promise<void>
  listPending(q: { from: string; to: string }): Promise<PendingRow[]>
  getOptions(q: { date: string; lessonId: number }): Promise<OptionsDto>
  listExceptions(q: { from: string; to: string }): Promise<ExceptionDto[]>
  createException(body: ExceptionInput): Promise<ExceptionDto>
  deleteException(id: number): Promise<void>

  getChangelog(q?: ChangelogQuery): Promise<ChangelogPage>

  listHolidays(): Promise<HolidayDto[]>
  createHoliday(body: HolidayInput): Promise<HolidayDto>
  deleteHoliday(id: number): Promise<void>
  listHolidayPresetYears(): Promise<HolidayPresetYearsDto>
  loadHolidayPreset(body: HolidayPresetInput): Promise<HolidayPresetResultDto>

  // Grunddaten-Sicherung (Format-Version 1)
  exportMasterData(): Promise<MasterDataExport>
  importMasterData(body: MasterDataImport): Promise<MasterDataImportResult>

  // Bereichsweise Sicherung (Format-Version 2)
  exportMasterDataSection(scope: TransferScope): Promise<MasterDataSection>
  importMasterDataSection(body: MasterDataSection): Promise<SectionImportResult>
}

