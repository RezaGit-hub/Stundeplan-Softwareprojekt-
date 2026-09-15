// Die Attrappe: implementiert Api vollständig gegen den In-Memory-Store, mit Kunstlatenz und Rollenprüfung
// wie die Pfadregeln in api.md. Fachlogik in ./logic/*. Wo Mock und Backend abweichen, gewinnt das Backend.
import type {
  Api, ClassAccountDto, ClassDto, ClassSubjectInput, ConfigDto, CreateUser, DayDto, DraftDto, EntryDto, EventDto,
  EventInput, ExceptionDto, FieldErrorDto, LessonDto, PendingRow, RoomInput, RuleInput, SubjectInput, TeacherDto,
  TeacherInput, TimegridInput, TimetableDto, UserDto, WeekDto, ClassInput, HolidayInput, HolidayPresetInput,
  MasterDataExport, MasterDataImport, MasterDataImportResult,
  MasterDataSection, SectionImportResult, TransferScope,
} from '@/api/types'
import { authToken } from '@/api/auth-token'
import { addDaysIso, between, startOfIsoWeek } from '@/lib/dates'
import { nowIso, today } from '@/lib/today'
import { latency } from './latency'
import { conflict409, forbidden, notFound, unauthenticated, validation } from './problems'
import {
  activeTimetable, draftTimetable, newTimetable, nextId, rulesFromPreset, store,
  type MockClass, type MockEvent, type MockException, type MockLesson, type MockTeacher, type MockTimetable, type MockUser, type Store,
} from './store'
import { loadSmall } from './data-small'
import { loadFull } from './data-full'
import { applyScenario } from './scenario'
import { checkConflicts } from './logic/conflicts'
import { classWarnings, plannedHoursOf, teacherWarnings } from './logic/warnings'
import { eventsAt, resolveDay, resolveWeek } from './logic/week-resolver'
import { placeLessons } from './logic/solver'
import { substitutionOptions } from './logic/options'
import { createException, deleteException } from './logic/exceptions'
import { HOLIDAY_PRESETS } from './holiday-presets'

export type MockDataset = 'SMALL' | 'FULL'
export interface MockApi extends Api {
  /** Dieselbe Api mit fester Identität (für /app-demo) */
  asUser(username: string): Api
  /** Ausgangszustand neu laden */
  reset(dataset?: MockDataset): void
}

const SOLVE_MS = 3000

// ------------------------------------------------------------------ DTO-Abbildungen
function teacherDto(t: MockTeacher, s: Store): TeacherDto {
  return { ...t, subjectIds: [...t.subjectIds], preferredTimeslotIds: [...t.preferredTimeslotIds], blockedTimeslotIds: [...t.blockedTimeslotIds], plannedHours: plannedHoursOf(t.id, s), warnings: teacherWarnings(t, s) }
}
function classDto(c: MockClass, s: Store): ClassDto {
  return {
    id: c.id, name: c.name, grade: c.grade, active: c.active,
    subjects: c.subjects.map((r) => ({
      subjectId: r.subjectId, subjectShort: s.subjects.find((x) => x.id === r.subjectId)?.shortName ?? '?',
      weeklyHours: r.weeklyHours, teacherId: r.teacherId, teacherShort: s.teachers.find((t) => t.id === r.teacherId)?.shortName ?? '?',
    })),
    warnings: classWarnings(c, s),
  }
}
function lessonDto(l: MockLesson, s: Store): LessonDto {
  const cls = s.classes.find((c) => c.id === l.classId)
  const subject = s.subjects.find((x) => x.id === l.subjectId)
  const teacher = s.teachers.find((t) => t.id === l.teacherId)
  const room = s.rooms.find((r) => r.id === l.roomId)
  const slot = s.timeslots.find((t) => t.id === l.timeslotId)
  return {
    id: l.id, classId: l.classId, classShort: cls?.name ?? '?', subjectId: l.subjectId, subjectShort: subject?.shortName ?? '?',
    subjectColor: subject?.color ?? '#455A64', teacherId: l.teacherId, teacherShort: teacher?.shortName ?? '?',
    roomId: room?.id ?? null, roomShort: room?.name ?? null, timeslotId: slot?.id ?? null, day: slot?.day ?? null, position: slot?.position ?? null, pinned: l.pinned,
  }
}
function timetableDto(t: MockTimetable | undefined, s: Store): TimetableDto {
  if (!t) return { id: null, acceptedAt: null, validFrom: null, lessons: [], conflicts: [] }
  return { id: t.id, acceptedAt: t.acceptedAt, validFrom: t.validFrom, lessons: t.lessons.map((l) => lessonDto(l, s)), conflicts: checkConflicts(t.lessons, s) }
}
function draftDto(d: MockTimetable, s: Store): DraftDto {
  const openLessons: DraftDto['openLessons'] = []
  const warnings: string[] = []
  for (const c of s.classes.filter((c) => c.active)) {
    for (const row of c.subjects) {
      const all = d.lessons.filter((l) => l.classId === c.id && l.subjectId === row.subjectId)
      const placed = all.filter((l) => l.timeslotId != null).length
      const subjectShort = s.subjects.find((x) => x.id === row.subjectId)?.shortName ?? '?'
      const missing = Math.max(0, row.weeklyHours - placed)
      if (missing > 0) openLessons.push({ classId: c.id, classShort: c.name, subjectShort, missing })
      if (all.length > row.weeklyHours) warnings.push(`${c.name} ${subjectShort}: ${all.length} Stunden geplant, ${row.weeklyHours} vorgesehen`)
    }
  }
  return { id: d.id, createdAt: d.createdAt, status: d.draftStatus, solve: { ...d.solve }, lessons: d.lessons.map((l) => lessonDto(l, s)), conflicts: checkConflicts(d.lessons, s), openLessons, warnings, expiringExceptions: expiringCount(d, s, today()) }
}

/**
 * Der fachliche Schlüssel einer belegten Stunde: Klasse, Fach, Zeitfenster, Raum. Genau solange er
 * gleich bleibt, bleibt der Satz einer Vertretung wahr („statt Frau Meyer, Raum R204"). Offene Stunden
 * haben keinen — an ihnen kann keine Vertretung hängen. Wie `DraftService.lessonKey` im Server.
 */
function lessonKey(l: MockLesson): string | null {
  return l.timeslotId == null || l.roomId == null ? null : `${l.classId}/${l.subjectId}/${l.timeslotId}/${l.roomId}`
}

/** Belegte Stunden nach Schlüssel; mehrfach belegte Schlüssel fallen heraus (wie im Server). */
function eindeutig(lessons: MockLesson[]): Map<string, number> {
  const jeSchluessel = new Map<string, number>()
  const mehrdeutig = new Set<string>()
  for (const l of lessons) {
    const k = lessonKey(l)
    if (k == null) continue
    if (jeSchluessel.has(k)) mehrdeutig.add(k)
    jeSchluessel.set(k, l.id)
  }
  for (const k of mehrdeutig) jeSchluessel.delete(k)
  return jeSchluessel
}

/**
 * Welche Stunde des aktiven Plans dieser Entwurf unverändert weiterführt — alte Kennung auf neue.
 * Spiegelt `DraftService.sameLessons`: nur so bleiben Attrappe und Server bei derselben Zahl.
 */
function sameLessons(d: MockTimetable, s: Store): Map<number, number> {
  const active = activeTimetable(s)
  const zuordnung = new Map<number, number>()
  if (!active) return zuordnung
  const neu = eindeutig(d.lessons)
  for (const [k, alteId] of eindeutig(active.lessons)) {
    const neueId = neu.get(k)
    if (neueId != null) zuordnung.set(alteId, neueId)
  }
  return zuordnung
}

/**
 * Wie viele datierte Vertretungen ab `ab` das Annehmen dieses Entwurfs verfallen ließe — nur die, deren
 * Stunde er verschiebt, umräumt oder streicht (Befund PFLICHTENHEFT-9). Zusatzstunden (`lessonId` null)
 * hängen an keinem Plan und verfallen nie.
 */
function expiringCount(d: MockTimetable, s: Store, ab: string): number {
  const gleich = sameLessons(d, s)
  return s.exceptions.filter((x) => x.date >= ab && x.lessonId != null && !gleich.has(x.lessonId)).length
}
function eventDto(e: MockEvent, s: Store): EventDto {
  const affected: EventDto['affected'] = []
  if (e.kind !== 'EXTRA_LESSON') {
    for (let d = e.dateFrom, i = 0; d <= e.dateTo && i < 62; d = addDaysIso(d, 1), i += 1) {
      for (const en of resolveDay(d, s)) {
        if (en.lessonId == null || en.kind === 'CANCELLED') continue
        const hit = (e.kind === 'TEACHER_ABSENCE' && (en.teacherId === e.teacherId || en.original?.teacherId === e.teacherId)) || (e.kind === 'ROOM_BLOCKED' && (en.roomId === e.roomId || en.original?.roomId === e.roomId))
        if (hit) affected.push({ date: d, lessonId: en.lessonId, classShort: en.classShort, position: en.position })
      }
    }
  }
  const hint = e.kind === 'EXTRA_LESSON'
    ? `Zusatzstunde für ${s.classes.find((c) => c.id === e.extra?.classId)?.name ?? '?'} am ${e.dateFrom} angelegt.`
    : affected.length === 0 ? 'Das Ereignis betrifft keine eingeplante Stunde.' : null
  return { id: e.id, kind: e.kind, teacherId: e.teacherId, roomId: e.roomId, dateFrom: e.dateFrom, dateTo: e.dateTo, reason: e.reason, createdAt: e.createdAt, createdBy: e.createdBy, affected, hint, extra: e.extra }
}
function exceptionDto(x: MockException, conflicts: ExceptionDto['conflicts'] = []): ExceptionDto {
  return { id: x.id, date: x.date, lessonId: x.lessonId, kind: x.kind, substituteTeacherId: x.substituteTeacherId, substituteRoomId: x.substituteRoomId, newDate: x.newDate, newTimeslotId: x.newTimeslotId, eventIds: [...x.eventIds], conflicts }
}
function userDto(u: MockUser, s: Store): UserDto {
  const cls = u.classId != null ? s.classes.find((c) => c.id === u.classId) : undefined
  return { id: u.id, username: u.username, role: u.role, teacherId: u.teacherId, classId: u.classId, classShort: cls?.name ?? null }
}

/**
 * Der Filter der Schülersicht — dieselbe Regel wie StudentView im Backend (E-50): reasons wird leer,
 * `original` behält nur die Kürzel. Gefiltert wird hier und nicht in der Oberfläche, weil auch der
 * Server hier filtert; so verhält sich die Attrappe wie das Backend.
 */
function studentWeek(week: WeekDto): WeekDto {
  const entry = (e: EntryDto): EntryDto => ({
    ...e,
    reasons: [],
    original: e.original ? { teacherShort: e.original.teacherShort, roomShort: e.original.roomShort, date: e.original.date, position: e.original.position } : null,
  })
  const day = (d: DayDto): DayDto => ({ ...d, entries: d.entries.map(entry) })
  return { monday: week.monday, days: week.days.map(day) }
}

/** Anzeigename wie AuthService.displayName: Lehrkraftname, „Klasse 7a“ oder Benutzername. */
function displayNameOf(u: MockUser, s: Store): string {
  if (u.teacherId != null) return s.teachers.find((t) => t.id === u.teacherId)?.name ?? u.username
  if (u.classId != null) return `Klasse ${s.classes.find((c) => c.id === u.classId)?.name ?? u.classId}`
  return u.role === 'PLANNING' ? 'Planung' : u.username
}

/** Zufälliges Passwort wie UserService.generatePassword: zehn Zeichen ohne 0/O und 1/l/I. */
function generatedPassword(): string {
  const alphabet = 'abcdefghijkmnpqrstuvwxyz23456789'
  let out = ''
  for (let i = 0; i < 10; i += 1) out += alphabet[Math.floor(Math.random() * alphabet.length)]
  return out
}

// ------------------------------------------------------------------ Validierung
function req(errors: FieldErrorDto[], field: string, value: unknown, message = 'darf nicht leer sein') {
  if (value === undefined || value === null || (typeof value === 'string' && value.trim() === '')) errors.push({ field, message })
}
function min1(errors: FieldErrorDto[], field: string, value: unknown) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 1) errors.push({ field, message: 'muss mindestens 1 sein' })
}
function unique(errors: FieldErrorDto[], field: string, taken: boolean, message: string) {
  if (taken) errors.push({ field, message })
}
function throwIfErrors(errors: FieldErrorDto[]) {
  if (errors.length) throw validation(errors)
}

// ------------------------------------------------------------------ Entwurf-Helfer
function syncRequiredLessons(draft: MockTimetable, s: Store) {
  for (const c of s.classes.filter((c) => c.active)) {
    for (const row of c.subjects) {
      const have = draft.lessons.filter((l) => l.classId === c.id && l.subjectId === row.subjectId).length
      for (let i = have; i < row.weeklyHours; i += 1) {
        draft.lessons.push({ id: nextId(), timetableId: draft.id, classId: c.id, subjectId: row.subjectId, teacherId: row.teacherId, timeslotId: null, roomId: null, pinned: false })
      }
    }
  }
}
function finishSolve(draft: MockTimetable, s: Store, classId?: number) {
  placeLessons(draft, s, { classId })
  const soft = checkConflicts(draft.lessons, s).filter((c) => c.severity === 'SOFT').length
  draft.draftStatus = 'EDITING'
  draft.solve = { ...draft.solve, lastScore: `0hard/0medium/-${soft}soft`, error: null }
  s.solveTimer = null
}
function lessonsInUse(pred: (l: MockLesson) => boolean, s: Store): number {
  return s.timetables.filter((t) => t.status !== 'ARCHIVED').flatMap((t) => t.lessons).filter(pred).length
}

// ------------------------------------------------------------------ Fabrik
function createApi(identity: () => MockUser | null, s: Store): Api {
  const user = () => identity()
  const username = () => user()?.username ?? 'admin'
  async function anyone() { await latency() }
  async function authenticated(): Promise<MockUser> {
    await latency()
    const u = user()
    if (!u) throw unauthenticated('Bitte erneut anmelden: das Token fehlt oder ist abgelaufen.')
    return u
  }
  async function planning(): Promise<MockUser> {
    const u = await authenticated()
    if (u.role !== 'PLANNING') throw forbidden()
    return u
  }
  const draftOr404 = () => {
    const d = draftTimetable(s)
    if (!d) throw notFound('Kein Entwurf vorhanden')
    return d
  }
  const notSolving = (d: MockTimetable) => {
    if (d.draftStatus === 'SOLVING') throw conflict409('solving', 'Berechnung läuft', 'Der Entwurf wird gerade berechnet. Bitte warten oder die Berechnung abbrechen.')
  }

  return {
    // ---------------- Auth
    async health() { await anyone(); return { status: 'UP', schoolName: s.config.schoolName } },
    async login(body) {
      await anyone()
      const u = s.users.find((x) => x.username === body.username?.trim().toLowerCase() && x.password === body.password)
      if (!u) throw unauthenticated()
      const token = `mock-${u.username}-${Math.random().toString(36).slice(2, 10)}`
      s.tokens.set(token, u.id)
      return { token, role: u.role, displayName: displayNameOf(u, s), teacherId: u.teacherId }
    },
    async logout() {
      await anyone()
      const t = authToken.get()
      if (t) s.tokens.delete(t)
    },
    async me() {
      const u = await authenticated()
      return { id: u.id, username: u.username, role: u.role, displayName: displayNameOf(u, s), teacherId: u.teacherId }
    },
    async changeMyPassword(body) {
      const u = await authenticated()
      // Ein Klassenkonto ändert sein Passwort nicht selbst — wie im Backend (SecurityConfig, E-50).
      if (u.role === 'STUDENT') throw forbidden()
      const errors: FieldErrorDto[] = []
      if (u.password !== body.oldPassword) errors.push({ field: 'oldPassword', message: 'Das bisherige Passwort ist falsch' })
      if (!body.newPassword || body.newPassword.length < 8) errors.push({ field: 'newPassword', message: 'mindestens 8 Zeichen' })
      throwIfErrors(errors)
      u.password = body.newPassword
    },

    // ---------------- Konten
    async listUsers() { await planning(); return s.users.map((u) => userDto(u, s)) },
    async createUser(body: CreateUser) {
      await planning()
      const errors: FieldErrorDto[] = []
      const name = body.username?.trim().toLowerCase() ?? ''
      req(errors, 'username', name)
      req(errors, 'password', body.password)
      if (body.password && body.password.length < 8) errors.push({ field: 'password', message: 'mindestens 8 Zeichen' })
      unique(errors, 'username', s.users.some((u) => u.username === name), 'Benutzername ist bereits vergeben')
      if (body.role === 'TEACHER') {
        if (body.teacherId == null) errors.push({ field: 'teacherId', message: 'Lehrkraft fehlt' })
        else if (s.users.some((u) => u.teacherId === body.teacherId)) errors.push({ field: 'teacherId', message: 'Diese Lehrkraft hat bereits ein Konto' })
      }
      if (body.role === 'STUDENT') {
        if (body.classId == null) errors.push({ field: 'classId', message: 'Klasse fehlt' })
        else if (!s.classes.some((c) => c.id === body.classId)) errors.push({ field: 'classId', message: 'Diese Klasse gibt es nicht' })
        else if (s.users.some((u) => u.classId === body.classId)) errors.push({ field: 'classId', message: 'Diese Klasse hat bereits ein Konto' })
      }
      throwIfErrors(errors)
      const u: MockUser = {
        id: nextId(), username: name, password: body.password, role: body.role,
        teacherId: body.role === 'TEACHER' ? (body.teacherId ?? null) : null,
        classId: body.role === 'STUDENT' ? (body.classId ?? null) : null,
      }
      s.users.push(u)
      return userDto(u, s)
    },
    // Ein Konto je aktiver Klasse ohne Konto (E-50); idempotent, Passwörter nur in dieser Antwort.
    async createClassAccounts(): Promise<ClassAccountDto[]> {
      await planning()
      const angelegt: ClassAccountDto[] = []
      for (const c of s.classes) {
        if (!c.active || s.users.some((u) => u.classId === c.id)) continue
        const username = c.name.trim().toLowerCase()
        if (!username || s.users.some((u) => u.username === username)) continue
        const password = generatedPassword()
        s.users.push({ id: nextId(), username, password, role: 'STUDENT', teacherId: null, classId: c.id })
        angelegt.push({ username, classShort: c.name, password })
      }
      return angelegt
    },
    async setUserPassword(id, body) {
      await planning()
      const u = s.users.find((x) => x.id === id)
      if (!u) throw notFound(`Konto ${id} nicht gefunden`)
      if (!body.password || body.password.length < 8) throw validation([{ field: 'password', message: 'mindestens 8 Zeichen' }])
      u.password = body.password
    },

    // ---------------- Lehrkräfte
    async listTeachers() { await planning(); return s.teachers.map((t) => teacherDto(t, s)) },
    async getTeacher(id) {
      await planning()
      const t = s.teachers.find((x) => x.id === id)
      if (!t) throw notFound(`Lehrkraft ${id} nicht gefunden`)
      return teacherDto(t, s)
    },
    async createTeacher(body: TeacherInput) {
      await planning()
      validateTeacher(body, null, s)
      const t: MockTeacher = { id: nextId(), ...normalizeTeacher(body) }
      s.teachers.push(t)
      return teacherDto(t, s)
    },
    async updateTeacher(id, body: TeacherInput) {
      await planning()
      const t = s.teachers.find((x) => x.id === id)
      if (!t) throw notFound(`Lehrkraft ${id} nicht gefunden`)
      validateTeacher(body, id, s)
      Object.assign(t, normalizeTeacher(body))
      return teacherDto(t, s)
    },
    async deleteTeacher(id) {
      await planning()
      const t = s.teachers.find((x) => x.id === id)
      if (!t) throw notFound(`Lehrkraft ${id} nicht gefunden`)
      const lessons = lessonsInUse((l) => l.teacherId === id, s)
      const classes = s.classes.filter((c) => c.subjects.some((r) => r.teacherId === id))
      if (lessons > 0) throw conflict409('in-use', 'Lehrkraft wird noch verwendet', `${t.name} ist in ${lessons} Stunden eingeplant. Deaktivieren statt löschen.`)
      if (classes.length > 0) throw conflict409('in-use', 'Lehrkraft wird noch verwendet', `${t.name} ist ${classes.map((c) => c.name).join(', ')} zugeordnet. Deaktivieren statt löschen.`)
      s.teachers = s.teachers.filter((x) => x.id !== id)
      s.users = s.users.filter((u) => u.teacherId !== id)
    },

    // ---------------- Fächer
    async listSubjects() { await planning(); return s.subjects.map((x) => ({ ...x })) },
    async getSubject(id) {
      await planning()
      const x = s.subjects.find((y) => y.id === id)
      if (!x) throw notFound(`Fach ${id} nicht gefunden`)
      return { ...x }
    },
    async createSubject(body: SubjectInput) {
      await planning()
      validateSubject(body, null, s)
      const x = { id: nextId(), name: body.name.trim(), shortName: body.shortName.trim(), requiredRoomType: body.requiredRoomType?.trim() || null, color: body.color }
      s.subjects.push(x)
      return { ...x }
    },
    async updateSubject(id, body: SubjectInput) {
      await planning()
      const x = s.subjects.find((y) => y.id === id)
      if (!x) throw notFound(`Fach ${id} nicht gefunden`)
      validateSubject(body, id, s)
      Object.assign(x, { name: body.name.trim(), shortName: body.shortName.trim(), requiredRoomType: body.requiredRoomType?.trim() || null, color: body.color })
      return { ...x }
    },
    async deleteSubject(id) {
      await planning()
      const x = s.subjects.find((y) => y.id === id)
      if (!x) throw notFound(`Fach ${id} nicht gefunden`)
      const classes = s.classes.filter((c) => c.subjects.some((r) => r.subjectId === id))
      if (classes.length) throw conflict409('in-use', 'Fach wird noch verwendet', `${x.name} wird in ${classes.map((c) => c.name).join(', ')} unterrichtet.`)
      if (lessonsInUse((l) => l.subjectId === id, s) > 0) throw conflict409('in-use', 'Fach wird noch verwendet', `${x.name} ist noch in Stunden eingeplant.`)
      s.subjects = s.subjects.filter((y) => y.id !== id)
      for (const t of s.teachers) t.subjectIds = t.subjectIds.filter((sid) => sid !== id)
    },

    // ---------------- Räume
    async listRooms() { await planning(); return s.rooms.map((r) => ({ ...r })) },
    async getRoom(id) {
      await planning()
      const r = s.rooms.find((x) => x.id === id)
      if (!r) throw notFound(`Raum ${id} nicht gefunden`)
      return { ...r }
    },
    async createRoom(body: RoomInput) {
      await planning()
      validateRoom(body, null, s)
      const r = { id: nextId(), name: body.name.trim(), roomType: body.roomType.trim(), active: body.active ?? true }
      s.rooms.push(r)
      return { ...r }
    },
    async updateRoom(id, body: RoomInput) {
      await planning()
      const r = s.rooms.find((x) => x.id === id)
      if (!r) throw notFound(`Raum ${id} nicht gefunden`)
      validateRoom(body, id, s)
      Object.assign(r, { name: body.name.trim(), roomType: body.roomType.trim(), active: body.active })
      return { ...r }
    },
    async deleteRoom(id) {
      await planning()
      const r = s.rooms.find((x) => x.id === id)
      if (!r) throw notFound(`Raum ${id} nicht gefunden`)
      const n = lessonsInUse((l) => l.roomId === id, s)
      if (n > 0) throw conflict409('in-use', 'Raum wird noch verwendet', `${r.name} ist in ${n} Stunden eingeplant. Deaktivieren statt löschen.`)
      s.rooms = s.rooms.filter((x) => x.id !== id)
    },

    // ---------------- Klassen
    async listClasses() { await planning(); return s.classes.map((c) => classDto(c, s)) },
    async getClass(id) {
      await planning()
      const c = s.classes.find((x) => x.id === id)
      if (!c) throw notFound(`Klasse ${id} nicht gefunden`)
      return classDto(c, s)
    },
    async createClass(body: ClassInput) {
      await planning()
      validateClass(body, null, s)
      const c: MockClass = { id: nextId(), name: body.name.trim(), grade: body.grade, active: body.active ?? true, subjects: [] }
      s.classes.push(c)
      return classDto(c, s)
    },
    async updateClass(id, body: ClassInput) {
      await planning()
      const c = s.classes.find((x) => x.id === id)
      if (!c) throw notFound(`Klasse ${id} nicht gefunden`)
      validateClass(body, id, s)
      Object.assign(c, { name: body.name.trim(), grade: body.grade, active: body.active })
      return classDto(c, s)
    },
    async deleteClass(id) {
      await planning()
      const c = s.classes.find((x) => x.id === id)
      if (!c) throw notFound(`Klasse ${id} nicht gefunden`)
      const n = lessonsInUse((l) => l.classId === id, s)
      if (n > 0) throw conflict409('in-use', 'Klasse wird noch verwendet', `${c.name} hat ${n} eingeplante Stunden. Deaktivieren statt löschen.`)
      s.classes = s.classes.filter((x) => x.id !== id)
    },
    async replaceClassSubjects(id, rows: ClassSubjectInput[]) {
      await planning()
      const c = s.classes.find((x) => x.id === id)
      if (!c) throw notFound(`Klasse ${id} nicht gefunden`)
      const errors: FieldErrorDto[] = []
      rows.forEach((r, i) => {
        if (!s.subjects.some((x) => x.id === r.subjectId)) errors.push({ field: `subjects[${i}].subjectId`, message: 'Fach fehlt' })
        if (r.weeklyHours == null || r.weeklyHours < 1) errors.push({ field: `subjects[${i}].weeklyHours`, message: 'Wochenstunden fehlen' })
        if (r.teacherId == null || !s.teachers.some((t) => t.id === r.teacherId)) errors.push({ field: `subjects[${i}].teacherId`, message: 'Lehrkraft fehlt' })
        if (rows.findIndex((o) => o.subjectId === r.subjectId) !== i) errors.push({ field: `subjects[${i}].subjectId`, message: 'Fach ist doppelt' })
      })
      throwIfErrors(errors)
      c.subjects = rows.map((r) => ({ subjectId: r.subjectId, weeklyHours: r.weeklyHours!, teacherId: r.teacherId! }))
      // Lehrkraft-Schnappschuss der Stunden nachziehen (Backend: Lesson → ClassSubject → Teacher)
      for (const t of s.timetables) for (const l of t.lessons) {
        if (l.classId !== c.id) continue
        const row = c.subjects.find((r) => r.subjectId === l.subjectId)
        if (row) l.teacherId = row.teacherId
      }
      return classDto(c, s)
    },

    // ---------------- Stundenraster
    async getTimegrid() { await authenticated(); return { slots: s.timeslots.map((t) => ({ ...t })) } },
    async putTimegrid(body: TimegridInput) {
      await planning()
      const errors: FieldErrorDto[] = []
      body.slots.forEach((sl, i) => {
        if (!sl.day) errors.push({ field: `slots[${i}].day`, message: 'darf nicht leer sein' })
        min1(errors, `slots[${i}].position`, sl.position)
        if (!/^\d{2}:\d{2}$/.test(sl.startTime ?? '')) errors.push({ field: `slots[${i}].startTime`, message: 'Uhrzeit HH:mm' })
        if (!/^\d{2}:\d{2}$/.test(sl.endTime ?? '')) errors.push({ field: `slots[${i}].endTime`, message: 'Uhrzeit HH:mm' })
        else if (sl.startTime >= sl.endTime) errors.push({ field: `slots[${i}].endTime`, message: 'muss nach dem Beginn liegen' })
        if (body.slots.findIndex((o) => o.day === sl.day && o.position === sl.position) !== i) errors.push({ field: `slots[${i}].position`, message: 'Position ist doppelt' })
      })
      throwIfErrors(errors)
      const keep = new Set<number>()
      const next = [...s.timeslots]
      for (const sl of body.slots) {
        const existing = next.find((t) => t.day === sl.day && t.position === sl.position)
        if (existing) {
          Object.assign(existing, { startTime: sl.startTime, endTime: sl.endTime, isBreak: !!sl.isBreak })
          keep.add(existing.id)
        } else {
          const created = { id: nextId(), day: sl.day, position: sl.position, startTime: sl.startTime, endTime: sl.endTime, isBreak: !!sl.isBreak }
          next.push(created)
          keep.add(created.id)
        }
      }
      const removed = next.filter((t) => !keep.has(t.id))
      const used = removed.filter((t) => lessonsInUse((l) => l.timeslotId === t.id, s) > 0)
      if (used.length) throw conflict409('in-use', 'Zeitfenster wird noch verwendet', `${used.length} Zeitfenster sind im aktiven Plan oder Entwurf belegt und können nicht entfernt werden.`)
      const removedIds = new Set(removed.map((t) => t.id))
      s.timeslots = next.filter((t) => !removedIds.has(t.id)).sort((a, b) => a.day.localeCompare(b.day) || a.position - b.position)
      for (const t of s.teachers) {
        t.preferredTimeslotIds = t.preferredTimeslotIds.filter((id) => !removedIds.has(id))
        t.blockedTimeslotIds = t.blockedTimeslotIds.filter((id) => !removedIds.has(id))
      }
      return { slots: s.timeslots.map((t) => ({ ...t })) }
    },

    // ---------------- Regelwerk
    async listRules() { await planning(); return s.rules.map((r) => ({ ...r })) },
    async updateRule(id, body: RuleInput) {
      await planning()
      const r = s.rules.find((x) => x.id === id)
      if (!r) throw notFound(`Regel ${id} nicht gefunden`)
      const errors: FieldErrorDto[] = []
      if (r.type !== 'NO_LESSONS_IN_BREAKS') min1(errors, 'value', body.value)
      throwIfErrors(errors)
      Object.assign(r, { value: r.type === 'NO_LESSONS_IN_BREAKS' ? 1 : body.value, severity: body.severity, weight: body.weight ?? 1, active: body.active })
      return { ...r }
    },
    // Beispielsatz laden: die Zeilen des in der Systemkonfiguration eingestellten Bundeslands
    // (schulweite Zeilen und Jahrgangszeilen) ersetzen das Regelwerk — wie rule_preset -> rule (E-48).
    async resetRules() {
      await planning()
      // Wie der Server (VERTRAG R-M2): nach dem Zurücksetzen ist alles an, nur die schulweiten Lehrkraft-Zeilen sind aus.
      // Das Bundesland ist im Vertrag ein Enum (putConfig prüft es) — einen Fall „kein Beispielsatz" gibt es nicht.
      s.rules = rulesFromPreset(s.config.state, false) ?? []
      return s.rules.map((r) => ({ ...r }))
    },
    async createRule(body: RuleInput) {
      await planning()
      const errors: FieldErrorDto[] = []
      if (body.grade == null) errors.push({ field: 'grade', message: 'Jahrgang fehlt (schulweite Regeln kommen aus dem Beispielsatz)' })
      if (!body.type || body.type === 'NO_LESSONS_IN_BREAKS') errors.push({ field: 'type', message: 'Nur Maximalstunden-Regeln je Jahrgang' })
      if (body.scope !== 'CLASS') errors.push({ field: 'scope', message: 'Jahrgangsregeln zählen je Klasse' })
      min1(errors, 'value', body.value)
      throwIfErrors(errors)
      const base = s.rules.find((r) => r.type === body.type && r.grade == null)
      // Wie der Server (VERTRAG R-M1): dieselbe (type, scope, grade) noch einmal → 409 data-exists
      if (s.rules.some((r) => r.type === body.type && r.scope === body.scope && r.grade === body.grade)) {
        throw conflict409('data-exists', 'Regel bereits vorhanden', `Für Jahrgang ${body.grade} gibt es die Regel „${base?.label ?? body.type}“ bereits.`)
      }
      const r = { id: nextId(), type: body.type!, scope: 'CLASS' as const, grade: body.grade!, value: body.value, severity: body.severity, weight: body.weight ?? 1, active: body.active, label: base?.label ?? body.type! }
      s.rules.push(r)
      return { ...r }
    },
    async deleteRule(id) {
      await planning()
      const r = s.rules.find((x) => x.id === id)
      if (!r) throw notFound(`Regel ${id} nicht gefunden`)
      if (r.grade == null) throw conflict409('in-use', 'Regel kann nicht gelöscht werden', 'Schulweite Regeln werden nur deaktiviert, nicht gelöscht.')
      s.rules = s.rules.filter((x) => x.id !== id)
    },

    // ---------------- Konfiguration, Beispieldaten
    async getConfig() { await planning(); return { ...s.config } },
    async putConfig(body: ConfigDto) {
      await planning()
      const errors: FieldErrorDto[] = []
      req(errors, 'schoolName', body.schoolName)
      req(errors, 'state', body.state)
      if (body.state && !rulesFromPreset(body.state, false)) errors.push({ field: 'state', message: 'muss ein Bundesland-Kürzel sein (BW … TH)' })
      req(errors, 'schoolType', body.schoolType)
      min1(errors, 'maxSolveSeconds', body.maxSolveSeconds)
      throwIfErrors(errors)
      s.config = { schoolName: body.schoolName.trim(), state: body.state.trim(), schoolType: body.schoolType.trim(), maxSolveSeconds: body.maxSolveSeconds }
      return { ...s.config }
    },
    async loadSampleData(body) {
      await planning()
      const hasData = s.teachers.length + s.classes.length + s.rooms.length > 0
      if (hasData && !body?.replace) throw conflict409('data-exists', 'Es sind bereits Grunddaten vorhanden', 'Es sind bereits Grunddaten vorhanden. Mit „Ersetzen" werden alle Daten durch den Beispielbestand ersetzt.')
      const tokens = s.tokens
      const planningUsers = s.users.filter((u) => u.role === 'PLANNING')
      loadFull(s)
      // Wie der Server (VERTRAG V-11): Lehrkraft-Konten werden gelöscht, keine neuen angelegt; Planungs-Konten bleiben
      s.users = planningUsers
      s.tokens = tokens
      return { classes: s.classes.length, teachers: s.teachers.length, rooms: s.rooms.length }
    },

    // ---------------- Aktiver Plan, Wochen
    async getTimetable() { await planning(); return timetableDto(activeTimetable(s), s) },
    async getWeek(q) {
      await planning()
      if (!q.date) throw validation([{ field: 'date', message: 'darf nicht leer sein' }])
      return resolveWeek(startOfIsoWeek(q.date), q.view ?? 'SCHOOL', q.id ?? null, s)
    },
    async getMyWeek(q) {
      const u = await authenticated()
      if (u.role === 'STUDENT') throw forbidden()
      if (u.teacherId == null) throw notFound('Dieses Konto ist keiner Lehrkraft zugeordnet')
      return resolveWeek(startOfIsoWeek(q.date ?? today()), 'TEACHER', u.teacherId, s)
    },
    async getMyClassWeek(q) {
      const u = await authenticated()
      if (u.classId == null) throw notFound('Dieses Konto gehört zu keiner Klasse.')
      return studentWeek(resolveWeek(startOfIsoWeek(q.date ?? today()), 'CLASS', u.classId, s))
    },

    // ---------------- Entwurf
    async getDraft() { await planning(); return draftDto(draftOr404(), s) },
    async createDraft(body) {
      await planning()
      if (draftTimetable(s)) throw conflict409('draft-exists', 'Es gibt bereits einen Entwurf', 'Es gibt bereits einen Entwurf. Erst annehmen oder verwerfen.')
      const d = newTimetable('DRAFT', nowIso())
      if (body.source === 'COPY_ACTIVE') {
        const active = activeTimetable(s)
        if (active) {
          const conflicted = new Set(checkConflicts(active.lessons, s).flatMap((c) => c.lessonIds))
          d.lessons = active.lessons.map((l) => ({ ...l, id: nextId(), timetableId: d.id, pinned: !conflicted.has(l.id) }))
        }
      }
      s.timetables.push(d)
      return draftDto(d, s)
    },
    async deleteDraft() {
      await planning()
      const d = draftOr404()
      if (s.solveTimer) { clearTimeout(s.solveTimer); s.solveTimer = null }
      s.timetables = s.timetables.filter((t) => t.id !== d.id)
    },
    async addDraftLesson(body) {
      await planning()
      const d = draftOr404()
      notSolving(d)
      const errors: FieldErrorDto[] = []
      const cls = s.classes.find((c) => c.id === body.classId)
      if (!cls) errors.push({ field: 'classId', message: 'Klasse fehlt' })
      const row = cls?.subjects.find((r) => r.subjectId === body.subjectId)
      if (!row) errors.push({ field: 'subjectId', message: 'Die Klasse hat dieses Fach nicht' })
      if (!s.timeslots.some((t) => t.id === body.timeslotId)) errors.push({ field: 'timeslotId', message: 'Zeitfenster fehlt' })
      if (!s.rooms.some((r) => r.id === body.roomId)) errors.push({ field: 'roomId', message: 'Raum fehlt' })
      throwIfErrors(errors)
      d.lessons.push({ id: nextId(), timetableId: d.id, classId: cls!.id, subjectId: row!.subjectId, teacherId: row!.teacherId, timeslotId: body.timeslotId, roomId: body.roomId, pinned: true })
      return draftDto(d, s)
    },
    async patchDraftLesson(id, body) {
      await planning()
      const d = draftOr404()
      notSolving(d)
      const l = d.lessons.find((x) => x.id === id)
      if (!l) throw notFound(`Stunde ${id} nicht im Entwurf`)
      if (body.timeslotId !== undefined && !s.timeslots.some((t) => t.id === body.timeslotId)) throw validation([{ field: 'timeslotId', message: 'Zeitfenster fehlt' }])
      if (body.roomId !== undefined && !s.rooms.some((r) => r.id === body.roomId)) throw validation([{ field: 'roomId', message: 'Raum fehlt' }])
      // Wie der Server (VERTRAG PL-4): eine offene Stunde braucht Zeitfenster UND Raum — der Server rät keinen Raum
      if (l.timeslotId == null && (body.timeslotId === undefined || body.roomId === undefined)) {
        throw validation([{ field: body.roomId === undefined ? 'roomId' : 'timeslotId', message: 'Eine eingeplante Stunde braucht Zeitfenster und Raum' }])
      }
      if (body.timeslotId !== undefined) {
        l.timeslotId = body.timeslotId
        l.pinned = true
      }
      if (body.roomId !== undefined) {
        l.roomId = body.roomId
        l.pinned = true
      }
      if (body.pinned !== undefined) l.pinned = body.pinned
      return draftDto(d, s)
    },
    async deleteDraftLesson(id) {
      await planning()
      const d = draftOr404()
      notSolving(d)
      if (!d.lessons.some((x) => x.id === id)) throw notFound(`Stunde ${id} nicht im Entwurf`)
      d.lessons = d.lessons.filter((x) => x.id !== id)
      return draftDto(d, s)
    },
    async startSolve(body) {
      await planning()
      const d = draftOr404()
      notSolving(d)
      const missing = s.classes.filter((c) => c.active && c.subjects.length === 0).map((c) => `Klasse ${c.name} hat kein Fach`)
      if (missing.length) throw conflict409('master-data-incomplete', 'Grunddaten unvollständig', 'Vor der Berechnung fehlen Angaben.', { missing })
      const pinned = d.lessons.filter((l) => l.pinned)
      const contradictions = checkConflicts(pinned, s).filter((c) => c.severity === 'HARD')
      if (contradictions.length) throw conflict409('pinned-contradiction', 'Festgelegte Stunden widersprechen sich', 'Festgelegte Stunden stehen im Konflikt. Bitte auflösen oder Festlegung aufheben.', { conflicts: contradictions })
      syncRequiredLessons(d, s)
      d.draftStatus = 'SOLVING'
      d.solve = { startedAt: nowIso(), limitSeconds: s.config.maxSolveSeconds, lastScore: null, error: null }
      const classId = body?.classId
      s.solveTimer = setTimeout(() => finishSolve(d, s, classId), SOLVE_MS)
    },
    async cancelSolve() {
      await planning()
      const d = draftOr404()
      if (d.draftStatus !== 'SOLVING') return
      if (s.solveTimer) clearTimeout(s.solveTimer)
      finishSolve(d, s)
    },
    async acceptDraft(body) {
      await planning()
      const d = draftOr404()
      notSolving(d)
      const active = activeTimetable(s)
      d.validFrom = body?.validFrom ?? null
      // Untergrenze wie im Server: der Gueltigkeitsbeginn, fruehestens heute; Zusatzstunden (lessonId null)
      // haengen an keinem Plan und verfallen nicht (Befunde PFLICHTENHEFT-6 und BACKEND-6).
      const ab = d.validFrom && d.validFrom > today() ? d.validFrom : today()
      // Erst umhaengen, dann tauschen — solange `active` noch aktiv ist, kennt sameLessons beide Plaene.
      // Was der Entwurf unveraendert weiterfuehrt, behaelt seine Vertretungen (Befund PFLICHTENHEFT-9);
      // nur der Rest verfaellt, und nur der steht in der Warnung.
      const gleich = sameLessons(d, s)
      let expiring = 0
      for (const x of s.exceptions) {
        if (x.date < ab || x.lessonId == null) continue
        const neu = gleich.get(x.lessonId)
        if (neu == null) expiring += 1
        else x.lessonId = neu
      }
      if (active) active.status = 'ARCHIVED'
      d.status = 'ACTIVE'
      d.acceptedAt = nowIso()
      s.changelog.push({
        id: nextId(), timestamp: nowIso(), action: 'TIMETABLE_ACCEPTED', date: d.validFrom, position: null, classShort: null,
        oldAssignment: active ? `Plan vom ${active.acceptedAt?.slice(0, 10) ?? '?'}` : 'kein Plan', newAssignment: `Plan angenommen (${d.lessons.filter((l) => l.timeslotId != null).length} Stunden)`,
        reasons: '', username: username(), lessonId: null, classId: null, teacherId: null,
      })
      // Reihenfolge wie im Server (DraftService.accept): erst die offenen Stunden, dann die Vertretungen.
      const open = d.lessons.filter((l) => l.timeslotId == null).length
      const warnings: string[] = []
      if (open === 1) warnings.push('Eine Stunde bleibt unverplant: sie steht im aktiven Plan, aber in keiner Woche.')
      else if (open > 1) warnings.push(`${open} Stunden bleiben unverplant: sie stehen im aktiven Plan, aber in keiner Woche.`)
      const wann = ab === today() ? 'ab heute' : `ab dem ${ab}`
      if (expiring === 1) warnings.push(`Eine Vertretung ${wann} verfaellt: ihre Stunde gibt es im neuen Plan nicht mehr.`)
      else if (expiring > 1) warnings.push(`${expiring} Vertretungen ${wann} verfallen: ihre Stunden gibt es im neuen Plan nicht mehr.`)
      return { ...timetableDto(d, s), warnings }
    },

    // ---------------- Ereignisse, Vertretung
    async listEvents(q) {
      await planning()
      return s.events
        .filter((e) => !q?.from || e.dateTo >= q.from)
        .filter((e) => !q?.to || e.dateFrom <= q.to)
        .filter((e) => q?.teacherId == null || e.teacherId === q.teacherId)
        .filter((e) => q?.roomId == null || e.roomId === q.roomId)
        .map((e) => eventDto(e, s))
    },
    async createEvent(body: EventInput) {
      const u = await planning()
      const errors: FieldErrorDto[] = []
      req(errors, 'reason', body.reason)
      req(errors, 'dateFrom', body.dateFrom)
      req(errors, 'dateTo', body.dateTo)
      if (body.dateFrom && body.dateTo && body.dateTo < body.dateFrom) errors.push({ field: 'dateTo', message: 'muss nach dem Beginn liegen' })
      if (body.kind === 'TEACHER_ABSENCE' && (body.teacherId == null || !s.teachers.some((t) => t.id === body.teacherId))) errors.push({ field: 'teacherId', message: 'Lehrkraft fehlt' })
      if (body.kind === 'ROOM_BLOCKED' && (body.roomId == null || !s.rooms.some((r) => r.id === body.roomId))) errors.push({ field: 'roomId', message: 'Raum fehlt' })
      if (body.kind === 'EXTRA_LESSON') {
        const x = body.extra
        if (!x) errors.push({ field: 'extra', message: 'Angaben zur Zusatzstunde fehlen' })
        else {
          req(errors, 'extra.date', x.date)
          if (!s.timeslots.some((t) => t.id === x.timeslotId)) errors.push({ field: 'extra.timeslotId', message: 'Zeitfenster fehlt' })
          if (!s.classes.some((c) => c.id === x.classId)) errors.push({ field: 'extra.classId', message: 'Klasse fehlt' })
          if (!s.subjects.some((y) => y.id === x.subjectId)) errors.push({ field: 'extra.subjectId', message: 'Fach fehlt' })
          if (!s.teachers.some((t) => t.id === x.teacherId)) errors.push({ field: 'extra.teacherId', message: 'Lehrkraft fehlt' })
          if (!s.rooms.some((r) => r.id === x.roomId)) errors.push({ field: 'extra.roomId', message: 'Raum fehlt' })
        }
      }
      throwIfErrors(errors)
      const e: MockEvent = {
        id: nextId(), kind: body.kind, teacherId: body.kind === 'TEACHER_ABSENCE' ? body.teacherId! : null, roomId: body.kind === 'ROOM_BLOCKED' ? body.roomId! : null,
        dateFrom: body.kind === 'EXTRA_LESSON' ? body.extra!.date : body.dateFrom, dateTo: body.kind === 'EXTRA_LESSON' ? body.extra!.date : body.dateTo,
        reason: body.reason.trim(), createdAt: nowIso(), createdBy: u.username, extra: body.kind === 'EXTRA_LESSON' ? body.extra! : null,
      }
      s.events.push(e)
      if (e.kind === 'EXTRA_LESSON' && e.extra) {
        s.exceptions.push({
          id: nextId(), date: e.extra.date, lessonId: null, kind: 'ADDED', substituteTeacherId: null, substituteRoomId: null,
          newDate: e.extra.date, newTimeslotId: e.extra.timeslotId, eventIds: [e.id],
          added: { classId: e.extra.classId, subjectId: e.extra.subjectId, teacherId: e.extra.teacherId, roomId: e.extra.roomId }, createdAt: nowIso(),
        })
      }
      return eventDto(e, s)
    },
    async deleteEvent(id) {
      await planning()
      const e = s.events.find((x) => x.id === id)
      if (!e) throw notFound(`Ereignis ${id} nicht gefunden`)
      s.events = s.events.filter((x) => x.id !== id)
      // Die Zusatzstunde eines EXTRA_LESSON geht mit; andere Ausnahmen verlieren nur diesen Anlass (Vertrag)
      s.exceptions = s.exceptions.filter((x) => !(x.kind === 'ADDED' && x.eventIds.includes(id)))
      for (const x of s.exceptions) x.eventIds = x.eventIds.filter((eid) => eid !== id)
    },
    async listPending(q) {
      await planning()
      const rows: PendingRow[] = []
      for (let d = q.from, i = 0; d <= q.to && i < 62; d = addDaysIso(d, 1), i += 1) {
        for (const e of resolveDay(d, s)) {
          if (e.kind !== 'NEEDS_SUBSTITUTION' || e.lessonId == null) continue
          const eventIds = eventsAt(d, s).filter((ev) => (ev.kind === 'TEACHER_ABSENCE' && ev.teacherId === e.teacherId) || (ev.kind === 'ROOM_BLOCKED' && ev.roomId === e.roomId)).map((ev) => ev.id)
          rows.push({ date: d, lessonId: e.lessonId, classShort: e.classShort, subjectShort: e.subjectShort, position: e.position, teacherShort: e.teacherShort ?? '—', roomShort: e.roomShort ?? '—', reasons: [...e.reasons], eventIds })
        }
      }
      return rows.sort((a, b) => a.date.localeCompare(b.date) || a.position - b.position || a.classShort.localeCompare(b.classShort))
    },
    async getOptions(q) {
      await planning()
      return substitutionOptions(q.date, q.lessonId, s)
    },
    async listExceptions(q) {
      await planning()
      return s.exceptions.filter((x) => between(x.date, q.from, q.to)).map((x) => exceptionDto(x))
    },
    async createException(body) {
      const u = await planning()
      const { exception, conflicts } = createException(body, u.username, s)
      return exceptionDto(exception, conflicts)
    },
    async deleteException(id) {
      const u = await planning()
      deleteException(id, u.username, s)
    },

    // ---------------- Verlauf
    async getChangelog(q) {
      await planning()
      const size = q?.size && q.size > 0 ? q.size : 50
      const page = q?.page && q.page >= 0 ? q.page : 0
      const items = s.changelog
        .filter((e) => !q?.from || (e.date ?? e.timestamp.slice(0, 10)) >= q.from)
        .filter((e) => !q?.to || (e.date ?? e.timestamp.slice(0, 10)) <= q.to)
        .filter((e) => q?.classId == null || e.classId === q.classId)
        .filter((e) => q?.teacherId == null || e.teacherId === q.teacherId)
        .filter((e) => q?.lessonId == null || e.lessonId === q.lessonId)
        .sort((a, b) => b.timestamp.localeCompare(a.timestamp) || b.id - a.id)
      return { items: items.slice(page * size, page * size + size).map(({ lessonId: _l, classId: _c, teacherId: _t, ...item }) => item), page, size, total: items.length }
    },

    // ---------------- Ferien
    async listHolidays() { await planning(); return s.holidays.map((h) => ({ ...h })) },
    async createHoliday(body: HolidayInput) {
      await planning()
      const errors: FieldErrorDto[] = []
      req(errors, 'name', body.name)
      req(errors, 'dateFrom', body.dateFrom)
      req(errors, 'dateTo', body.dateTo)
      if (body.dateFrom && body.dateTo && body.dateTo < body.dateFrom) errors.push({ field: 'dateTo', message: 'muss nach dem Beginn liegen' })
      throwIfErrors(errors)
      const h = { id: nextId(), name: body.name.trim(), dateFrom: body.dateFrom, dateTo: body.dateTo, kind: body.kind ?? 'VACATION' }
      s.holidays.push(h)
      return { ...h }
    },
    async deleteHoliday(id) {
      await planning()
      if (!s.holidays.some((h) => h.id === id)) throw notFound(`Zeitraum ${id} nicht gefunden`)
      s.holidays = s.holidays.filter((h) => h.id !== id)
    },

        // ---------------- Grunddaten-Sicherung (Format-Version 1)
    // Bildet `ExportService`/`ImportService` des Backends nach: derselbe Vertrag, dieselben Regeln
    // (Version '1', replace-Vorbedingung, Verweise über eine alte→neue Zuordnung). Der Export gibt
    // die Grunddaten als ein Dokument heraus — ohne Systemkonfiguration, Konten, Pläne, Ereignisse,
    // Ausnahmen und Verlauf, genau wie im Vertrag (Modul `transfer`).
    async exportMasterData(): Promise<MasterDataExport> {
      await planning()
      return {
        version: '1',
        exportedAt: nowIso(),
        subjects: s.subjects.map((x) => ({ ...x })),
        teachers: s.teachers.map((t) => teacherDto(t, s)),
        rooms: s.rooms.map((r) => ({ ...r })),
        classes: s.classes.map((c) => classDto(c, s)),
        timeslots: s.timeslots.map((t) => ({ ...t })),
        rules: s.rules.map((r) => ({ ...r })),
        holidays: s.holidays.map((h) => ({ ...h })),
      }
    },
    async importMasterData(body: MasterDataImport): Promise<MasterDataImportResult> {
      await planning()

      if (body.version !== '1') {
        throw validation([{ field: 'version', message: 'wird nicht unterstützt (erwartet 1)' }])
      }

      // Dieselbe Vorbedingung wie POST /api/sample-data: bei vorhandenen Grunddaten ohne replace → 409.
      const hasData = s.subjects.length + s.teachers.length + s.rooms.length
        + s.classes.length + s.timeslots.length > 0
      if (hasData && !body.replace) {
        throw conflict409('data-exists', 'Es sind bereits Grunddaten vorhanden',
          'Die Datenbank enthält bereits Grunddaten. Zum Ersetzen „replace“ setzen.')
      }

      // Stufe 1+2: Pflichtfelder, Eindeutigkeit der Kennungen, Verweise innerhalb der Datei.
      const errors: FieldErrorDto[] = []
      const checkUnique = (rows: { id: number | null }[], feld: string) => {
        const seen = new Set<number>()
        rows.forEach((row, i) => {
          if (row.id == null) errors.push({ field: `${feld}[${i}].id`, message: 'Kennung fehlt' })
          else if (!seen.add(row.id)) errors.push({ field: `${feld}[${i}].id`, message: `Kennung ${row.id} ist doppelt` })
        })
      }
      checkUnique(body.subjects, 'subjects')
      checkUnique(body.teachers, 'teachers')
      checkUnique(body.rooms, 'rooms')
      checkUnique(body.classes, 'classes')
      checkUnique(body.timeslots, 'timeslots')

      const subjectIds = new Set(body.subjects.map((x) => x.id))
      const teacherIds = new Set(body.teachers.map((x) => x.id))
      const slotIds = new Set(body.timeslots.map((x) => x.id))
      body.teachers.forEach((t, i) => {
        t.subjectIds.forEach((id) => {
          if (!subjectIds.has(id)) errors.push({ field: `teachers[${i}].subjectIds`, message: `Fach ${id} gibt es in der Datei nicht` })
        })
        t.preferredTimeslotIds.forEach((id) => {
          if (!slotIds.has(id)) errors.push({ field: `teachers[${i}].preferredTimeslotIds`, message: `Zeitfenster ${id} gibt es in der Datei nicht` })
        })
        t.blockedTimeslotIds.forEach((id) => {
          if (!slotIds.has(id)) errors.push({ field: `teachers[${i}].blockedTimeslotIds`, message: `Zeitfenster ${id} gibt es in der Datei nicht` })
        })
      })
      body.classes.forEach((c, i) => {
        c.subjects.forEach((row, j) => {
          if (!subjectIds.has(row.subjectId)) errors.push({ field: `classes[${i}].subjects[${j}].subjectId`, message: `Fach ${row.subjectId} gibt es in der Datei nicht` })
          if (!teacherIds.has(row.teacherId)) errors.push({ field: `classes[${i}].subjects[${j}].teacherId`, message: `Lehrkraft ${row.teacherId} gibt es in der Datei nicht` })
        })
      })
      if (errors.length) throw validation(errors)

      // Leeren — wie der Server: alles außer Planungs-Konten, Tokens und Systemkonfiguration.
      if (hasData) {
        s.subjects = []
        s.teachers = []
        s.rooms = []
        s.classes = []
        s.timeslots = []
        s.rules = []
        s.holidays = []
        s.timetables = []
        s.events = []
        s.exceptions = []
        s.changelog = []
        s.users = s.users.filter((u) => u.role === 'PLANNING')
      }

      // Einspielen in Abhängigkeitsreihenfolge; Verweise über die Zuordnung umschreiben.
      const subjMap = new Map<number, number>()
      const teachMap = new Map<number, number>()
      const slotMap = new Map<number, number>()

      s.subjects = body.subjects.map((x) => {
        const neu = { ...x, id: nextId() }
        subjMap.set(x.id, neu.id)
        return neu
      })
      s.rooms = body.rooms.map((x) => ({ ...x, id: nextId() }))
      s.timeslots = body.timeslots.map((x) => {
        const neu = { ...x, id: nextId() }
        slotMap.set(x.id, neu.id)
        return neu
      })
      s.teachers = body.teachers.map((t): MockTeacher => {
        const neu: MockTeacher = {
          id: nextId(),
          name: t.name, shortName: t.shortName, weeklyHoursQuota: t.weeklyHoursQuota,
          subjectIds: t.subjectIds.map((id) => subjMap.get(id)!),
          preferredTimeslotIds: t.preferredTimeslotIds.map((id) => slotMap.get(id)!),
          blockedTimeslotIds: t.blockedTimeslotIds.map((id) => slotMap.get(id)!),
          active: t.active,
        }
        teachMap.set(t.id, neu.id)
        return neu
      })
      s.classes = body.classes.map((c): MockClass => ({
        id: nextId(),
        name: c.name, grade: c.grade, active: c.active,
        subjects: c.subjects.map((row) => ({
          subjectId: subjMap.get(row.subjectId)!,
          weeklyHours: row.weeklyHours,
          teacherId: teachMap.get(row.teacherId)!,
        })),
      }))
      s.rules = body.rules.map((r) => ({ ...r, id: nextId() }))
      s.holidays = body.holidays.map((h) => ({ ...h, id: nextId() }))

      return {
        subjects: s.subjects.length,
        teachers: s.teachers.length,
        rooms: s.rooms.length,
        classes: s.classes.length,
        timeslots: s.timeslots.length,
        rules: s.rules.length,
        holidays: s.holidays.length,
        warnings: [],
      }
    },

    // Mitgelieferte Vorlage je Bundesland (E-51). Wie im Server: reine Daten aus holiday-presets.ts,
    // kein Abruf aus dem Internet. Das Bundesland kommt aus der Systemkonfiguration.
    async listHolidayPresetYears() {
      await planning()
      return { state: s.config.state, schoolYears: Object.keys(HOLIDAY_PRESETS[s.config.state] ?? {}).sort() }
    },
    async loadHolidayPreset(body: HolidayPresetInput) {
      await planning()
      const state = s.config.state
      const vorlage = HOLIDAY_PRESETS[state]?.[body.schoolYear]
      if (!vorlage) {
        const vorhanden = Object.keys(HOLIDAY_PRESETS[state] ?? {}).sort()
        throw conflict409('preset-missing', 'Keine Vorlage',
          `Für ${state} und das Schuljahr ${body.schoolYear} gibt es keine Vorlage.`
          + (vorhanden.length ? ` Vorhanden: ${vorhanden.join(', ')}.` : ' Für dieses Bundesland liegt keine Vorlage bei.'))
      }
      // Derselbe Schlüssel wie im Server: Anfang, Ende, Bezeichnung.
      const key = (from: string, to: string, name: string) => `${from}|${to}|${name.trim()}`
      const vorhanden = new Set(s.holidays.map((h) => key(h.dateFrom, h.dateTo, h.name)))
      let added = 0
      let skipped = 0
      for (const zeile of vorlage) {
        if (vorhanden.has(key(zeile.from, zeile.to, zeile.label))) { skipped++; continue }
        vorhanden.add(key(zeile.from, zeile.to, zeile.label))
        s.holidays.push({ id: nextId(), name: zeile.label, dateFrom: zeile.from, dateTo: zeile.to, kind: zeile.kind })
        added++
      }
      return { added, skipped, state, schoolYear: body.schoolYear }
    },

        // Bereichsweise Sicherung (Format-Version 2)
    async exportMasterDataSection(scope: TransferScope): Promise<MasterDataSection> {
      await planning()
      const now = nowIso()
      const build = (): MasterDataSection => ({
        version: '2', scope, exportedAt: now,
        subjects: scope === 'subjects' ? s.subjects.map((x) => ({ ...x })) : null,
        teachers: scope === 'teachers' ? s.teachers.map((t) => teacherDto(t, s)) : null,
        rooms: scope === 'rooms' ? s.rooms.map((r) => ({ ...r })) : null,
        classes: scope === 'classes' ? s.classes.map((c) => classDto(c, s)) : null,
        timeslots: scope === 'timeslots' ? s.timeslots.map((t) => ({ ...t })) : null,
        rules: scope === 'rules' ? s.rules.map((r) => ({ ...r })) : null,
        holidays: scope === 'holidays' ? s.holidays.map((h) => ({ ...h })) : null,
        refs: null,
      })
      const doc = build()
      if (scope === 'teachers') {
        const subjIds = new Set(s.teachers.flatMap((t) => t.subjectIds))
        const slotIds = new Set(s.teachers.flatMap((t) => [...t.preferredTimeslotIds, ...t.blockedTimeslotIds]))
        doc.refs = {
          subjects: s.subjects.filter((x) => subjIds.has(x.id)).map((x) => ({ id: x.id, shortName: x.shortName })),
          timeslots: s.timeslots.filter((x) => slotIds.has(x.id)).map((x) => ({ id: x.id, day: x.day, position: x.position })),
        }
      } else if (scope === 'classes') {
        const subjIds = new Set(s.classes.flatMap((c) => c.subjects.map((r) => r.subjectId)))
        const teachIds = new Set(s.classes.flatMap((c) => c.subjects.map((r) => r.teacherId)))
        doc.refs = {
          subjects: s.subjects.filter((x) => subjIds.has(x.id)).map((x) => ({ id: x.id, shortName: x.shortName })),
          teachers: s.teachers.filter((x) => teachIds.has(x.id)).map((x) => ({ id: x.id, shortName: x.shortName })),
        }
      }
      return doc
    },

    async importMasterDataSection(body: MasterDataSection): Promise<SectionImportResult> {
      await planning()
      if (body.version !== '2') throw validation([{ field: 'version', message: 'wird nicht unterstützt (erwartet 2)' }])
      const fehler: FieldErrorDto[] = []
      const resolve = (refs: { id: number; shortName?: string; day?: string; position?: number }[] | null | undefined,
                       lookup: (r: any) => number | null): Map<number, number> => {
        const map = new Map<number, number>()
        for (const r of refs ?? []) {
          const neu = lookup(r)
          if (neu != null) map.set(r.id, neu)
        }
        return map
      }
      let created = 0, updated = 0

      if (body.scope === 'subjects') {
        for (const x of body.subjects ?? []) {
          const alt = s.subjects.find((y) => y.shortName.toLowerCase() === x.shortName.toLowerCase())
          if (alt) { Object.assign(alt, { name: x.name, shortName: x.shortName, requiredRoomType: x.requiredRoomType, color: x.color }); updated++ }
          else { s.subjects.push({ id: nextId(), name: x.name, shortName: x.shortName, requiredRoomType: x.requiredRoomType, color: x.color }); created++ }
        }
      } else if (body.scope === 'rooms') {
        for (const x of body.rooms ?? []) {
          const alt = s.rooms.find((y) => y.name.toLowerCase() === x.name.toLowerCase())
          if (alt) { Object.assign(alt, { name: x.name, roomType: x.roomType, active: x.active }); updated++ }
          else { s.rooms.push({ id: nextId(), name: x.name, roomType: x.roomType, active: x.active }); created++ }
        }
      } else if (body.scope === 'timeslots') {
        s.timeslots = (body.timeslots ?? []).map((x) => ({ ...x, id: nextId() }))
        created = s.timeslots.length
      } else if (body.scope === 'rules') {
        s.rules = (body.rules ?? []).map((x) => ({ ...x, id: nextId() }))
        created = s.rules.length
      } else if (body.scope === 'holidays') {
        for (const x of body.holidays ?? []) {
          const alt = s.holidays.find((y) => y.name === x.name && y.dateFrom === x.dateFrom && y.dateTo === x.dateTo)
          if (!alt) { s.holidays.push({ ...x, id: nextId() }); created++ }
        }
      } else if (body.scope === 'teachers') {
        const subjMap = resolve(body.refs?.subjects, (r) => s.subjects.find((y) => y.shortName.toLowerCase() === r.shortName.toLowerCase())?.id ?? null)
        const slotMap = resolve(body.refs?.timeslots, (r) => s.timeslots.find((y) => y.day === r.day && y.position === r.position)?.id ?? null)
        for (let i = 0; i < (body.teachers ?? []).length; i++) {
          const t = body.teachers![i]
          const subjectIds = t.subjectIds.map((id) => subjMap.get(id))
          const blocked = t.blockedTimeslotIds.map((id) => slotMap.get(id))
          const preferred = t.preferredTimeslotIds.map((id) => slotMap.get(id))
          subjectIds.forEach((v, j) => { if (v == null) fehler.push({ field: `teachers[${i}].subjectIds[${j}]`, message: 'Fach nicht gefunden' }) })
          if (fehler.length) continue
          const alt = s.teachers.find((y) => y.shortName.toUpperCase() === t.shortName.toUpperCase())
          if (alt) { Object.assign(alt, { name: t.name, weeklyHoursQuota: t.weeklyHoursQuota, subjectIds: subjectIds as number[], preferredTimeslotIds: preferred as number[], blockedTimeslotIds: blocked as number[], active: t.active }); updated++ }
          else { s.teachers.push({ id: nextId(), name: t.name, shortName: t.shortName, weeklyHoursQuota: t.weeklyHoursQuota, subjectIds: subjectIds as number[], preferredTimeslotIds: preferred as number[], blockedTimeslotIds: blocked as number[], active: t.active }); created++ }
        }
      } else if (body.scope === 'classes') {
        const subjMap = resolve(body.refs?.subjects, (r) => s.subjects.find((y) => y.shortName.toLowerCase() === r.shortName.toLowerCase())?.id ?? null)
        const teachMap = resolve(body.refs?.teachers, (r) => s.teachers.find((y) => y.shortName.toUpperCase() === r.shortName.toUpperCase())?.id ?? null)
        for (const c of body.classes ?? []) {
          const subjects = c.subjects.map((row) => ({
            subjectId: subjMap.get(row.subjectId)!, weeklyHours: row.weeklyHours, teacherId: teachMap.get(row.teacherId)!,
          }))
          const alt = s.classes.find((y) => y.name.toLowerCase() === c.name.toLowerCase())
          if (alt) { Object.assign(alt, { name: c.name, grade: c.grade, active: c.active, subjects }); updated++ }
          else { s.classes.push({ id: nextId(), name: c.name, grade: c.grade, active: c.active, subjects }); created++ }
        }
      }
      if (fehler.length) throw validation(fehler)
      return { scope: body.scope, created, updated, warnings: [] }
    },
  }
}

// ------------------------------------------------------------------ Validierungen der Grunddaten
function validateTeacher(body: TeacherInput, selfId: number | null, s: Store) {
  const errors: FieldErrorDto[] = []
  req(errors, 'name', body.name)
  req(errors, 'shortName', body.shortName)
  min1(errors, 'weeklyHoursQuota', body.weeklyHoursQuota)
  const short = body.shortName?.trim().toUpperCase()
  unique(errors, 'shortName', s.teachers.some((t) => t.id !== selfId && t.shortName.toUpperCase() === short), 'Kürzel ist bereits vergeben')
  throwIfErrors(errors)
}
function normalizeTeacher(body: TeacherInput) {
  return {
    name: body.name.trim(), shortName: body.shortName.trim().toUpperCase(), weeklyHoursQuota: body.weeklyHoursQuota,
    subjectIds: [...new Set(body.subjectIds ?? [])], preferredTimeslotIds: [...new Set(body.preferredTimeslotIds ?? [])],
    blockedTimeslotIds: [...new Set(body.blockedTimeslotIds ?? [])], active: body.active ?? true,
  }
}
function validateSubject(body: SubjectInput, selfId: number | null, s: Store) {
  const errors: FieldErrorDto[] = []
  req(errors, 'name', body.name)
  req(errors, 'shortName', body.shortName)
  req(errors, 'color', body.color)
  unique(errors, 'shortName', s.subjects.some((x) => x.id !== selfId && x.shortName.toLowerCase() === body.shortName?.trim().toLowerCase()), 'Kürzel ist bereits vergeben')
  unique(errors, 'name', s.subjects.some((x) => x.id !== selfId && x.name.toLowerCase() === body.name?.trim().toLowerCase()), 'Name ist bereits vergeben')
  throwIfErrors(errors)
}
function validateRoom(body: RoomInput, selfId: number | null, s: Store) {
  const errors: FieldErrorDto[] = []
  req(errors, 'name', body.name)
  req(errors, 'roomType', body.roomType)
  unique(errors, 'name', s.rooms.some((r) => r.id !== selfId && r.name.toLowerCase() === body.name?.trim().toLowerCase()), 'Name ist bereits vergeben')
  throwIfErrors(errors)
}
function validateClass(body: ClassInput, selfId: number | null, s: Store) {
  const errors: FieldErrorDto[] = []
  req(errors, 'name', body.name)
  min1(errors, 'grade', body.grade)
  unique(errors, 'name', s.classes.some((c) => c.id !== selfId && c.name.toLowerCase() === body.name?.trim().toLowerCase()), 'Name ist bereits vergeben')
  throwIfErrors(errors)
}

// ------------------------------------------------------------------ Start, Identität, Export
function datasetFromEnv(): MockDataset {
  return import.meta.env.VITE_MOCK_DATASET === 'FULL' ? 'FULL' : 'SMALL'
}
function reset(dataset: MockDataset = datasetFromEnv()): void {
  if (dataset === 'FULL') loadFull(store)
  else loadSmall(store)
  if (import.meta.env.VITE_MOCK_SCENARIO === 'leitszenario') applyScenario(store)
}
reset()

const tokenIdentity = (): MockUser | null => {
  const t = authToken.get()
  if (!t) return null
  const id = store.tokens.get(t)
  if (id != null) return store.users.find((u) => u.id === id) ?? null
  // Nach dem Neuladen ist der Store neu, die Sitzung aus dem localStorage aber noch da (F-5a). Der
  // Benutzername steckt im Mock-Token — damit gilt die Sitzung weiter, wie sie es gegen das Backend täte.
  const name = /^mock-([a-z0-9]+)-/i.exec(t)?.[1]?.toLowerCase()
  const u = name ? store.users.find((x) => x.username === name) : undefined
  if (!u) return null
  store.tokens.set(t, u.id)
  return u
}

export const mockApi: MockApi = Object.assign(createApi(tokenIdentity, store), {
  asUser: (username: string): Api => createApi(() => store.users.find((u) => u.username === username) ?? null, store),
  reset,
})

/** Für Tests: eigene Api auf einem eigenen Store, mit fester Identität */
export function createMockApiForTests(s: Store, username = 'admin'): Api {
  return createApi(() => s.users.find((u) => u.username === username) ?? null, s)
}
