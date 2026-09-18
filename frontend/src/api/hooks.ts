// TanStack Query: ein Hook je GET, ein Mutations-Hook je Schreibendpunkt mit fester Invalidierung.
// Kein Zustand außerhalb von Query + URL + lokalem useState. Fehler-Toasts kommen aus dem onError jeder Mutation (errors.ts);
// 400-Feldfehler fängt die Seite mit useFieldErrors().handle(err) im catch. Fehler, die die Seite selbst darstellt
// (409 in-use als Dialog, 409 data-exists als Rückfrage), schaltet sie mit { quiet: (err) => boolean } stumm.
import {
  QueryClient,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryKey,
  type UseMutationResult,
} from '@tanstack/react-query'

import {
  ApiError,
  type AcceptDraftInput, type AddLessonInput, type ChangelogQuery, type ClassInput, type ClassSubjectInput,
  type ConfigDto, type CreateDraft, type CreateUser,
  type DraftDto, type DraftPatchInput,
  type EventInput, type EventQuery, type ExceptionInput,
  type HolidayInput, type HolidayPresetInput,
  type MasterDataExport, type MasterDataImport, type MasterDataImportResult, type MasterDataSection,
  type PasswordChange, type PatchLessonInput,
  type RepairInput,                              // ← nur wenn in types.ts vorhanden
  type RoomInput, type RuleInput, type SectionImportResult, type SubjectInput,
  type TeacherInput, type TimegridInput, type TourFlag, type TransferScope, type View,
} from './types'

import { api } from './api'
import { qk } from './query-keys'
import { toastProblem } from './errors'
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
/** Alle angenommenen Pläne mit ihrem Zeitraum (seit 1.4.0, #115). Genau einer trägt `current: true`, wenn heute einer gilt. */
export function useTimetables(opts: Opts = {}) {
  return useQuery({ queryKey: qk.timetables, queryFn: api.listTimetables, enabled: opts.enabled ?? true })
}
/** Ein angenommener Plan (ACTIVE oder ARCHIVED) — für die Planwahl auf /plan. */
export function usePlanById(id: number | null | undefined) {
  return useQuery({ queryKey: qk.timetableById(id ?? 0), queryFn: () => api.getTimetableById(id!), enabled: id != null })
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
/** Die Entwurfsliste, jüngster zuerst (seit 1.3.0, #103). Leere Liste = kein Entwurf, kein Fehler. */
export function useDrafts(opts: Opts = {}) {
  return useQuery({
    queryKey: qk.drafts,
    queryFn: api.listDrafts,
    enabled: opts.enabled ?? true,
    // Solange irgendein Entwurf rechnet, hält die Liste ihre Zahlen frisch — wie die Entwurfsseite selbst.
    refetchInterval: (query) => (query.state.data?.some((d) => d.status === 'SOLVING') ? 2000 : false),
  })
}
/** data === null heißt: diesen Entwurf gibt es nicht mehr (404 ist ein Zustand, kein Fehler). Während SOLVING alle 2 s neu laden. */
export function useDraft(id: number | null | undefined) {
  return useQuery({
    queryKey: qk.draft(id ?? 0),
    queryFn: (): Promise<DraftDto | null> =>
      api.getDraftById(id!).catch((err: unknown) => {
        if (err instanceof ApiError && err.status === 404) return null
        throw err
      }),
    enabled: id != null,
    retry: false,
    refetchInterval: (query) => (query.state.data?.status === 'SOLVING' ? 2000 : false),
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
const MASTER_DATA_KEYS: QueryKey[] = [qk.teachers, qk.classes, qk.subjects, qk.rooms, qk.timetable, qk.timetables, qk.draftAll, qk.drafts, qk.weeks, qk.optionsAll]

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
  invalidate(qc, qk.timetable, qk.timetables, qk.draftAll, qk.drafts, qk.weeks, qk.myWeeks, qk.teachers)
}, opts)
// Regelwerk
export const useUpdateRule = () => useApiMutation((v: { id: number; body: RuleInput }) => api.updateRule(v.id, v.body), (qc) => invalidate(qc, qk.rules, qk.timetable, qk.draftAll, qk.drafts))
export const useResetRules = () => useApiMutation(() => api.resetRules(), (qc) => invalidate(qc, qk.rules, qk.timetable, qk.draftAll, qk.drafts))
export const useCreateRule = () => useApiMutation((body: RuleInput) => api.createRule(body), (qc) => invalidate(qc, qk.rules, qk.timetable, qk.draftAll, qk.drafts))
export const useDeleteRule = () => useApiMutation((id: number) => api.deleteRule(id), (qc) => invalidate(qc, qk.rules, qk.timetable, qk.draftAll, qk.drafts))
// Konfiguration, Beispieldaten
export const usePutConfig = () => useApiMutation((body: ConfigDto) => api.putConfig(body), (qc, data) => {
  qc.setQueryData(qk.config, data)
  // Mit dem Bundesland wechselt auch die mitgelieferte Ferien- und Feiertagsvorlage (E-51).
  invalidate(qc, qk.health, qk.holidayPresetYears)
})
export const useLoadSampleData = (opts?: MutationOpts) => useApiMutation((body?: { replace?: boolean }) => api.loadSampleData(body), (qc) => void qc.invalidateQueries(), opts)
// Grunddaten komplett löschen (#160): eine Anfrage statt Schleife über die Einzel-Endpunkte (#148, alt).
// Löscht serverseitig mit Kaskade, unabhängig davon, ob etwas noch verwendet wird — deshalb hier keine
// gezielten Query-Keys, sondern volle Invalidierung (wie bei useLoadSampleData): fast alles kann betroffen sein.
export const useClearAllMasterData = (opts?: MutationOpts) => useApiMutation(() => api.clearAllMasterData(), (qc) => void qc.invalidateQueries(), opts)

// Entwurf — jede Mutation schreibt qk.draft(id) selbst, deshalb vorher das laufende GET /drafts/{id}
// abbrechen (cancel) und alle Mutationen DIESES Entwurfs nacheinander laufen lassen (scope). Seit es
// mehrere Entwürfe nebeneinander gibt (#103), trägt der Bereich die Kennung: ein zweiter Entwurf wartet
// nicht mit, obwohl ihn die Änderung nichts angeht.
const forDraft = (id: number): MutationOpts => ({ cancel: [qk.draft(id)], scope: `draft-${id}` })
/** Nach jeder Entwurfsänderung: der Entwurf selbst kommt fertig aus der Antwort, die Liste zählt neu. */
const draftChanged = (qc: QueryClient, data: DraftDto) => {
  qc.setQueryData(qk.draft(data.id), data)
  invalidate(qc, qk.drafts)
}
export const useCreateDraft = () => useApiMutation((body: CreateDraft) => api.createDraft(body), draftChanged)
export const usePatchDraft = (id: number, opts?: MutationOpts) => useApiMutation((body: DraftPatchInput) => api.patchDraft(id, body), draftChanged, { ...forDraft(id), ...opts })
export const useDeleteDraft = (id: number) => useApiMutation(() => api.deleteDraftById(id), (qc) => {
  qc.setQueryData(qk.draft(id), null)
  invalidate(qc, qk.drafts)
}, forDraft(id))
export const useAddDraftLesson = (id: number) => useApiMutation((body: AddLessonInput) => api.addDraftLesson(id, body), draftChanged, forDraft(id))
export const usePatchDraftLesson = (id: number) => useApiMutation((v: { lessonId: number; body: PatchLessonInput }) => api.patchDraftLesson(id, v.lessonId, v.body), draftChanged, forDraft(id))
export const useDeleteDraftLesson = (id: number) => useApiMutation((lessonId: number) => api.deleteDraftLesson(id, lessonId), draftChanged, forDraft(id))
export const useStartSolve = (id: number) => useApiMutation((body?: { classId?: number }) => api.startSolve(id, body), (qc) => invalidate(qc, qk.draft(id), qk.drafts), forDraft(id))
export const useCancelSolve = (id: number) => useApiMutation(() => api.cancelSolve(id), (qc) => invalidate(qc, qk.draft(id), qk.drafts), forDraft(id))
/**
 * Annehmen (#115): der Entwurf wird zum angenommenen Plan mit Zeitraum. Danach kann es mehrere
 * angenommene Pläne geben — deshalb wandern `qk.timetables` und jeder Plan-Eintrag mit in die
 * Auffrischung. Andere Entwürfe bleiben stehen, nur dieser verschwindet aus der Liste.
 */
export const useAcceptDraft = (id: number, opts?: MutationOpts) => useApiMutation((body?: AcceptDraftInput) => api.acceptDraft(id, body), (qc) => {
  qc.setQueryData(qk.draft(id), null)
  // Auch die übrigen Entwürfe neu laden: ihre Vorschau der verfallenden Vertretungen bezog sich auf den alten Plan.
  invalidate(qc, qk.drafts, qk.draftAll, qk.timetable, qk.timetables, qk.weeks, qk.myWeeks, qk.classWeeks, qk.changelogAll, qk.pendingAll, qk.optionsAll)
}, { ...forDraft(id), ...opts })
// Konflikt lösen (#110): drei Solver-Läufe, bis zu 15 s, speichert nichts. Dieselben Entwurfs-Optionen wie die
// übrigen Mutationen — während der Serie gilt der Entwurf serverseitig als SOLVING, weitere Entwurfs-Mutationen
// warten (scope) statt mit 409 zu scheitern; danach den Entwurf neu laden, falls ein Poll SOLVING gesehen hat.
export const useRepairDraft = (id: number) => useApiMutation((body: RepairInput) => api.repairDraft(id, body), (qc) => invalidate(qc, qk.draft(id)), forDraft(id))
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
export const useCreateTeacherAccounts = () => useApiMutation(() => api.createTeacherAccounts(), (qc) => invalidate(qc, qk.users))
export const useSetUserPassword = () => useApiMutation((v: { id: number; body: { password: string } }) => api.setUserPassword(v.id, v.body), () => {})
export const useChangeMyPassword = () => useApiMutation((body: PasswordChange) => api.changeMyPassword(body), () => {})

export function useExportMasterData(): UseMutationResult<MasterDataExport, Error, void> {
  return useApiMutation(() => api.exportMasterData(), () => {})
}

export function useImportMasterData(opts?: MutationOpts): UseMutationResult<MasterDataImportResult, Error, MasterDataImport> {
  return useApiMutation(
    (body: MasterDataImport) => api.importMasterData(body),
    (qc) => void qc.invalidateQueries(),
    opts,
  )
}

export function useExportMasterDataSection(scope: TransferScope): UseMutationResult<MasterDataSection, Error, void> {
  return useApiMutation(() => api.exportMasterDataSection(scope), () => {})
}

export function useImportMasterDataSection(scope: TransferScope, opts?: MutationOpts): UseMutationResult<SectionImportResult, Error, MasterDataSection> {
  return useApiMutation(
    (body: MasterDataSection) => {
      // readSectionFile prüft dasselbe schon vor dem Aufruf; hier steht es noch einmal, weil der Hook
      // auch programmatisch (ohne Datei) aufgerufen werden kann.
      if (body.scope !== scope) {
        throw new Error(`Das Dokument enthält „${body.scope}", nicht „${scope}".`)
      }
      return api.importMasterDataSection(body)
    },
    (qc) => void qc.invalidateQueries(),
    opts,
  )
}

export const useDeleteUser = (opts?: MutationOpts) => useApiMutation((id: number) => api.deleteUser(id), (qc) => invalidate(qc, qk.users), opts)

// Rundgang (#139): danach /me neu laden — daran hängt, ob das Overlay in der Shell erscheint.
export const useSetTour = () => useApiMutation((body: TourFlag) => api.setTour(body), (qc) => invalidate(qc, qk.me))

