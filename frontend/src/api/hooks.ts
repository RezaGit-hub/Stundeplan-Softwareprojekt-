// TanStack Query: ein Hook je GET, ein Mutations-Hook je Schreibendpunkt mit fester Invalidierung.
// Kein Zustand außerhalb von Query + URL + lokalem useState. Fehler-Toasts kommen aus dem onError jeder Mutation (errors.ts);
// 400-Feldfehler fängt die Seite mit useFieldErrors().handle(err) im catch. Fehler, die die Seite selbst darstellt
// (409 in-use als Dialog, 409 data-exists als Rückfrage), schaltet sie mit { quiet: (err) => boolean } stumm.
import { QueryClient, useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query'
import { api } from './api'
import { qk } from './query-keys'
import { toastProblem } from './errors'
import {
  ApiError, type AddLessonInput, type ChangelogQuery, type ClassInput, type ClassSubjectInput, type ConfigDto, type CreateUser,
  type DraftDto, type DraftSource, type EventInput, type EventQuery, type ExceptionInput, type HolidayInput,
  type HolidayPresetInput, type PasswordChange,
  type PatchLessonInput, type RoomInput, type RuleInput, type SubjectInput, type TeacherInput, type TimegridInput, type View,
} from './types'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 10_000, refetchOnWindowFocus: false },
    mutations: { onError: toastProblem },
  },
})

interface Opts { enabled?: boolean }

// ------------------------------------------------------------------ Lesen
export function useHealth() {
  return useQuery({ queryKey: qk.health, queryFn: api.health, staleTime: Infinity })
}
export function useMe(opts: Opts = {}) {
  return useQuery({ queryKey: qk.me, queryFn: api.me, enabled: opts.enabled ?? true })
}
export function useUsers(opts: Opts = {}) {
  return useQuery({ queryKey: qk.users, queryFn: api.listUsers, enabled: opts.enabled ?? true })
}
export function useTeachers(opts: Opts = {}) {
  return useQuery({ queryKey: qk.teachers, queryFn: api.listTeachers, enabled: opts.enabled ?? true })
}
export function useTeacher(id: number | null | undefined) {
  return useQuery({ queryKey: qk.teacher(id ?? 0), queryFn: () => api.getTeacher(id!), enabled: id != null })
}
export function useSubjects(opts: Opts = {}) {
  return useQuery({ queryKey: qk.subjects, queryFn: api.listSubjects, enabled: opts.enabled ?? true })
}
export function useRooms(opts: Opts = {}) {
  return useQuery({ queryKey: qk.rooms, queryFn: api.listRooms, enabled: opts.enabled ?? true })
}
export function useClasses(opts: Opts = {}) {
  return useQuery({ queryKey: qk.classes, queryFn: api.listClasses, enabled: opts.enabled ?? true })
}
export function useClass(id: number | null | undefined) {
  return useQuery({ queryKey: qk.class(id ?? 0), queryFn: () => api.getClass(id!), enabled: id != null })
}
export function useTimegrid() {
  return useQuery({ queryKey: qk.timegrid, queryFn: api.getTimegrid })
}
export function useRules() {
  return useQuery({ queryKey: qk.rules, queryFn: api.listRules })
}
export function useConfig() {
  return useQuery({ queryKey: qk.config, queryFn: api.getConfig })
}
export function useTimetable() {
  return useQuery({ queryKey: qk.timetable, queryFn: api.getTimetable })
}
/** Aufgelöste Woche; bei CLASS/TEACHER/ROOM erst mit id. `enabled: false` wartet, bis die Woche feststeht. */
export function useWeek(q: { monday: string; view: View; id?: number | null }, opts: Opts = {}) {
  return useQuery({
    queryKey: qk.week(q.view, q.id, q.monday),
    queryFn: () => api.getWeek({ date: q.monday, view: q.view, id: q.id ?? undefined }),
    enabled: (opts.enabled ?? true) && (q.view === 'SCHOOL' || q.id != null),
  })
}
export function useMyWeek(monday: string) {
  return useQuery({ queryKey: qk.myWeek(monday), queryFn: () => api.getMyWeek({ date: monday }) })
}
/** Woche der eigenen Klasse (Rolle STUDENT). Der Server filtert: reasons ist immer leer (E-50). */
export function useMyClassWeek(monday: string) {
  return useQuery({ queryKey: qk.classWeek(monday), queryFn: () => api.getMyClassWeek({ date: monday }) })
}
/** data === null heißt: kein Entwurf (404 ist ein Zustand, kein Fehler). Während SOLVING alle 2 s neu laden. */
export function useDraft(opts: Opts = {}) {
  return useQuery({
    queryKey: qk.draft,
    queryFn: (): Promise<DraftDto | null> =>
      api.getDraft().catch((err: unknown) => {
        if (err instanceof ApiError && err.status === 404) return null
        throw err
      }),
    retry: false,
    refetchInterval: (query) => (query.state.data?.status === 'SOLVING' ? 2000 : false),
    enabled: opts.enabled ?? true,
  })
}
export function useEvents(q: EventQuery, opts: Opts = {}) {
  return useQuery({ queryKey: qk.events(q), queryFn: () => api.listEvents(q), enabled: opts.enabled ?? true })
}
export function usePending(from: string, to: string, opts: Opts = {}) {
  return useQuery({ queryKey: qk.pending(from, to), queryFn: () => api.listPending({ from, to }), enabled: opts.enabled ?? true })
}
export function useOptions(date: string | null | undefined, lessonId: number | null | undefined) {
  return useQuery({
    queryKey: qk.options(date ?? '', lessonId ?? 0),
    queryFn: () => api.getOptions({ date: date!, lessonId: lessonId! }),
    enabled: !!date && lessonId != null,
  })
}
export function useChangelog(q: ChangelogQuery) {
  return useQuery({ queryKey: qk.changelog(q), queryFn: () => api.getChangelog(q) })
}
export function useHolidays() {
  return useQuery({ queryKey: qk.holidays, queryFn: api.listHolidays })
}
/** Schuljahre der mitgelieferten Vorlage zum eingestellten Bundesland (E-51); leer = keine Vorlage. */
export function useHolidayPresetYears() {
  return useQuery({ queryKey: qk.holidayPresetYears, queryFn: api.listHolidayPresetYears })
}

// ------------------------------------------------------------------ Schreiben
function invalidate(qc: QueryClient, ...keys: QueryKey[]) {
  for (const queryKey of keys) void qc.invalidateQueries({ queryKey })
}
/** Grunddaten ändern Konflikte und Warnungen: Liste + Plan + Entwurf + Wochen neu laden */
const MASTER_DATA_KEYS: QueryKey[] = [qk.teachers, qk.classes, qk.subjects, qk.rooms, qk.timetable, qk.draft, qk.weeks, qk.optionsAll]

/**
 * quiet(err) === true → kein Toast; die Seite zeigt den Fehler selbst (Dialog, Banner).
 * cancel → diese Abfragen vor der Mutation abbrechen. Nötig überall dort, wo die Mutation ihren Cache selbst
 * schreibt (setQueryData): eine schon laufende GET-Antwort (Solver-Polling alle 2 s) kann sonst NACH der
 * Mutation eintreffen und den frischen Stand mit dem alten überschreiben.
 * scope → Mutationen mit derselben Kennung laufen nacheinander (TanStack-Mutation-Scope). Nötig überall dort,
 * wo jede Antwort ein ganzes DTO in den Cache schreibt: zwei gleichzeitige Entwurfs-Mutationen (schnell
 * hintereinander ziehen, pinnen, löschen) liefern sonst in beliebiger Reihenfolge, und die späte alte Antwort
 * überschreibt die frische.
 */
export interface MutationOpts { quiet?: (err: unknown) => boolean; cancel?: QueryKey[]; scope?: string }

/** Laufende Abfragen abbrechen; ihre Antwort wird verworfen statt in den Cache geschrieben. */
export async function cancelQueryKeys(qc: QueryClient, keys: QueryKey[]): Promise<void> {
  await Promise.all(keys.map((queryKey) => qc.cancelQueries({ queryKey })))
}

function useApiMutation<TVars, TData>(fn: (vars: TVars) => Promise<TData>, after: (qc: QueryClient, data: TData, vars: TVars) => void, opts?: MutationOpts) {
  const qc = useQueryClient()
  const cancel = opts?.cancel
  return useMutation({
    mutationFn: fn,
    scope: opts?.scope ? { id: opts.scope } : undefined,
    onMutate: cancel ? () => cancelQueryKeys(qc, cancel) : undefined,
    onSuccess: (data, vars) => after(qc, data, vars),
    onError: (err) => { if (!opts?.quiet?.(err)) toastProblem(err) },
  })
}

// Lehrkräfte
export const useCreateTeacher = () => useApiMutation((body: TeacherInput) => api.createTeacher(body), (qc) => invalidate(qc, ...MASTER_DATA_KEYS))
export const useUpdateTeacher = () => useApiMutation((v: { id: number; body: TeacherInput }) => api.updateTeacher(v.id, v.body), (qc, _d, v) => invalidate(qc, qk.teacher(v.id), ...MASTER_DATA_KEYS))
export const useDeleteTeacher = (opts?: MutationOpts) => useApiMutation((id: number) => api.deleteTeacher(id), (qc) => invalidate(qc, qk.users, ...MASTER_DATA_KEYS), opts)
// Fächer
export const useCreateSubject = () => useApiMutation((body: SubjectInput) => api.createSubject(body), (qc) => invalidate(qc, ...MASTER_DATA_KEYS))
export const useUpdateSubject = () => useApiMutation((v: { id: number; body: SubjectInput }) => api.updateSubject(v.id, v.body), (qc) => invalidate(qc, ...MASTER_DATA_KEYS))
export const useDeleteSubject = (opts?: MutationOpts) => useApiMutation((id: number) => api.deleteSubject(id), (qc) => invalidate(qc, ...MASTER_DATA_KEYS), opts)
// Räume
export const useCreateRoom = () => useApiMutation((body: RoomInput) => api.createRoom(body), (qc) => invalidate(qc, ...MASTER_DATA_KEYS))
export const useUpdateRoom = () => useApiMutation((v: { id: number; body: RoomInput }) => api.updateRoom(v.id, v.body), (qc) => invalidate(qc, ...MASTER_DATA_KEYS))
export const useDeleteRoom = (opts?: MutationOpts) => useApiMutation((id: number) => api.deleteRoom(id), (qc) => invalidate(qc, ...MASTER_DATA_KEYS), opts)
// Klassen
export const useCreateClass = () => useApiMutation((body: ClassInput) => api.createClass(body), (qc) => invalidate(qc, ...MASTER_DATA_KEYS))
export const useUpdateClass = () => useApiMutation((v: { id: number; body: ClassInput }) => api.updateClass(v.id, v.body), (qc, _d, v) => invalidate(qc, qk.class(v.id), ...MASTER_DATA_KEYS))
export const useDeleteClass = (opts?: MutationOpts) => useApiMutation((id: number) => api.deleteClass(id), (qc) => invalidate(qc, ...MASTER_DATA_KEYS), opts)
export const useReplaceClassSubjects = () =>
  useApiMutation((v: { id: number; rows: ClassSubjectInput[] }) => api.replaceClassSubjects(v.id, v.rows), (qc, data, v) => {
    qc.setQueryData(qk.class(v.id), data)
    invalidate(qc, ...MASTER_DATA_KEYS)
  })
// Stundenraster
export const usePutTimegrid = (opts?: MutationOpts) => useApiMutation((body: TimegridInput) => api.putTimegrid(body), (qc, data) => {
  qc.setQueryData(qk.timegrid, data)
  invalidate(qc, qk.timetable, qk.draft, qk.weeks, qk.myWeeks, qk.teachers)
}, opts)
// Regelwerk
export const useUpdateRule = () => useApiMutation((v: { id: number; body: RuleInput }) => api.updateRule(v.id, v.body), (qc) => invalidate(qc, qk.rules, qk.timetable, qk.draft))
export const useResetRules = () => useApiMutation(() => api.resetRules(), (qc) => invalidate(qc, qk.rules, qk.timetable, qk.draft))
export const useCreateRule = () => useApiMutation((body: RuleInput) => api.createRule(body), (qc) => invalidate(qc, qk.rules, qk.timetable, qk.draft))
export const useDeleteRule = () => useApiMutation((id: number) => api.deleteRule(id), (qc) => invalidate(qc, qk.rules, qk.timetable, qk.draft))
// Konfiguration, Beispieldaten
export const usePutConfig = () => useApiMutation((body: ConfigDto) => api.putConfig(body), (qc, data) => {
  qc.setQueryData(qk.config, data)
  // Mit dem Bundesland wechselt auch die mitgelieferte Ferien- und Feiertagsvorlage (E-51).
  invalidate(qc, qk.health, qk.holidayPresetYears)
})
export const useLoadSampleData = (opts?: MutationOpts) => useApiMutation((body?: { replace?: boolean }) => api.loadSampleData(body), (qc) => void qc.invalidateQueries(), opts)
// Entwurf — jede Mutation schreibt qk.draft selbst, deshalb vorher das laufende GET /draft abbrechen (cancel)
// und alle Entwurfs-Mutationen nacheinander laufen lassen (scope), damit keine späte Antwort eine frischere überschreibt.
const DRAFT: MutationOpts = { cancel: [qk.draft], scope: 'draft' }
export const useCreateDraft = () => useApiMutation((body: { source: DraftSource }) => api.createDraft(body), (qc, data) => qc.setQueryData(qk.draft, data), DRAFT)
export const useDeleteDraft = () => useApiMutation(() => api.deleteDraft(), (qc) => qc.setQueryData(qk.draft, null), DRAFT)
export const useAddDraftLesson = () => useApiMutation((body: AddLessonInput) => api.addDraftLesson(body), (qc, data) => qc.setQueryData(qk.draft, data), DRAFT)
export const usePatchDraftLesson = () => useApiMutation((v: { id: number; body: PatchLessonInput }) => api.patchDraftLesson(v.id, v.body), (qc, data) => qc.setQueryData(qk.draft, data), DRAFT)
export const useDeleteDraftLesson = () => useApiMutation((id: number) => api.deleteDraftLesson(id), (qc, data) => qc.setQueryData(qk.draft, data), DRAFT)
export const useStartSolve = () => useApiMutation((body?: { classId?: number }) => api.startSolve(body), (qc) => invalidate(qc, qk.draft), DRAFT)
export const useCancelSolve = () => useApiMutation(() => api.cancelSolve(), (qc) => invalidate(qc, qk.draft), DRAFT)
export const useAcceptDraft = () => useApiMutation((body?: { validFrom?: string }) => api.acceptDraft(body), (qc) => {
  qc.setQueryData(qk.draft, null)
  invalidate(qc, qk.timetable, qk.weeks, qk.myWeeks, qk.classWeeks, qk.changelogAll, qk.pendingAll, qk.optionsAll)
}, DRAFT)
// Ereignisse, Vertretung
const SUBSTITUTION_KEYS: QueryKey[] = [qk.pendingAll, qk.weeks, qk.myWeeks, qk.classWeeks, qk.changelogAll, qk.optionsAll, qk.eventsAll, qk.exceptionsAll]
export const useCreateEvent = () => useApiMutation((body: EventInput) => api.createEvent(body), (qc) => invalidate(qc, ...SUBSTITUTION_KEYS))
export const useDeleteEvent = () => useApiMutation((id: number) => api.deleteEvent(id), (qc) => invalidate(qc, ...SUBSTITUTION_KEYS))
export const useCreateException = () => useApiMutation((body: ExceptionInput) => api.createException(body), (qc) => invalidate(qc, ...SUBSTITUTION_KEYS))
export const useDeleteException = () => useApiMutation((id: number) => api.deleteException(id), (qc) => invalidate(qc, ...SUBSTITUTION_KEYS))
// Ferien
export const useCreateHoliday = () => useApiMutation((body: HolidayInput) => api.createHoliday(body), (qc) => invalidate(qc, qk.holidays, qk.weeks, qk.myWeeks, qk.classWeeks, qk.pendingAll))
export const useDeleteHoliday = () => useApiMutation((id: number) => api.deleteHoliday(id), (qc) => invalidate(qc, qk.holidays, qk.weeks, qk.myWeeks, qk.classWeeks, qk.pendingAll))
export const useLoadHolidayPreset = () => useApiMutation((body: HolidayPresetInput) => api.loadHolidayPreset(body), (qc) => invalidate(qc, qk.holidays, qk.weeks, qk.myWeeks, qk.classWeeks, qk.pendingAll))
// Konten
export const useCreateUser = () => useApiMutation((body: CreateUser) => api.createUser(body), (qc) => invalidate(qc, qk.users))
export const useCreateClassAccounts = () => useApiMutation(() => api.createClassAccounts(), (qc) => invalidate(qc, qk.users))
export const useSetUserPassword = () => useApiMutation((v: { id: number; body: { password: string } }) => api.setUserPassword(v.id, v.body), () => {})
export const useChangeMyPassword = () => useApiMutation((body: PasswordChange) => api.changeMyPassword(body), () => {})

export const useDeleteUser = (opts?: MutationOpts) => useApiMutation((id: number) => api.deleteUser(id), (qc) => invalidate(qc, qk.users), opts)