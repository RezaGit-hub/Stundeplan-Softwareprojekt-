// Echter HTTP-Client gegen /api (relativ, keine Env — Umzug läuft über nginx/Proxy, E-14).
import { ApiError, type Api, type ProblemDetail } from './types'
import { authToken } from './auth-token'
import { clearSession } from './session'

const BASE = '/api'

let onUnauthorized: () => void = () => {}
/** Vom AuthProvider gesetzt: Toast "Sitzung abgelaufen" + Redirect nach /login */
export function setOnUnauthorized(fn: () => void): void {
  onUnauthorized = fn
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
type Query = object

function toQueryString(query?: Query): string {
  if (!query) return ''
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(query as Record<string, unknown>)) if (v !== undefined && v !== null) p.set(k, String(v))
  const s = p.toString()
  return s ? `?${s}` : ''
}

export async function request<T>(method: Method, path: string, opts: { query?: Query; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' }
  const token = authToken.get()
  if (token) headers.Authorization = `Bearer ${token}`
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'

  let res: Response
  try {
    res = await fetch(`${BASE}${path}${toQueryString(opts.query)}`, {
      method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    })
  } catch {
    throw new ApiError({ type: 'urn:pushnpull:problem:network', title: 'Server nicht erreichbar', status: 0 })
  }

  if (res.ok) {
    if (res.status === 204 || res.status === 202) return undefined as T
    const text = await res.text()
    if (!text) return undefined as T
    try {
      return JSON.parse(text) as T
    } catch {
      // Kaputtes JSON in einer 200-Antwort (Proxy-Fehlerseite, halber Body): kein roher SyntaxError nach oben,
      // sondern dasselbe ProblemDetail wie bei jedem anderen Fehler — die Oberfläche zeigt nur deutschen Text.
      throw new ApiError({
        type: 'urn:pushnpull:problem:invalid-response',
        title: 'Unerwartete Antwort vom Server',
        status: res.status,
        detail: 'Die Antwort des Servers war nicht lesbar. Bitte die Seite neu laden.',
      })
    }
  }

  let problem: ProblemDetail
  const contentType = res.headers.get('content-type') ?? ''
  if (contentType.includes('json')) {
    const raw = (await res.json().catch(() => null)) as Partial<ProblemDetail> | null
    problem = { type: raw?.type ?? 'urn:pushnpull:problem:unknown', title: raw?.title ?? 'Serverfehler', status: res.status, ...raw }
  } else {
    problem = { type: 'urn:pushnpull:problem:unknown', title: 'Serverfehler', status: res.status }
  }

  if (res.status === 401) {
    // Token weg — auch der gespeicherte Eintrag, sonst versucht das nächste Neuladen dieselbe tote Sitzung (F-5a).
    authToken.clear()
    clearSession()
    onUnauthorized()
  }
  throw new ApiError(problem)
}

export const httpApi: Api = {
  health: () => request('GET', '/health'),
  login: (body) => request('POST', '/auth/login', { body }),
  logout: () => request('DELETE', '/auth/token'),
  me: () => request('GET', '/me'),
  changeMyPassword: (body) => request('PUT', '/me/password', { body }),
  setTour: (body) => request('PUT', '/me/tour', { body }),

  listUsers: () => request('GET', '/users'),
  createUser: (body) => request('POST', '/users', { body }),
  createClassAccounts: () => request('POST', '/users/class-accounts', { body: {} }),
  createTeacherAccounts: () => request('POST', '/users/teacher-accounts', { body: {} }),
  setUserPassword: (id, body) => request('PUT', `/users/${id}/password`, { body }),
deleteUser: (id) => request('DELETE', `/users/${id}`),

  listTeachers: () => request('GET', '/teachers'),
  getTeacher: (id) => request('GET', `/teachers/${id}`),
  createTeacher: (body) => request('POST', '/teachers', { body }),
  updateTeacher: (id, body) => request('PUT', `/teachers/${id}`, { body }),
  deleteTeacher: (id) => request('DELETE', `/teachers/${id}`),

  listSubjects: () => request('GET', '/subjects'),
  getSubject: (id) => request('GET', `/subjects/${id}`),
  createSubject: (body) => request('POST', '/subjects', { body }),
  updateSubject: (id, body) => request('PUT', `/subjects/${id}`, { body }),
  deleteSubject: (id) => request('DELETE', `/subjects/${id}`),

  listRooms: () => request('GET', '/rooms'),
  getRoom: (id) => request('GET', `/rooms/${id}`),
  createRoom: (body) => request('POST', '/rooms', { body }),
  updateRoom: (id, body) => request('PUT', `/rooms/${id}`, { body }),
  deleteRoom: (id) => request('DELETE', `/rooms/${id}`),

  listClasses: () => request('GET', '/classes'),
  getClass: (id) => request('GET', `/classes/${id}`),
  createClass: (body) => request('POST', '/classes', { body }),
  updateClass: (id, body) => request('PUT', `/classes/${id}`, { body }),
  deleteClass: (id) => request('DELETE', `/classes/${id}`),
  replaceClassSubjects: (id, rows) => request('PUT', `/classes/${id}/subjects`, { body: rows }),

  getTimegrid: () => request('GET', '/timegrid'),
  putTimegrid: (body) => request('PUT', '/timegrid', { body }),

  listRules: () => request('GET', '/rules'),
  updateRule: (id, body) => request('PUT', `/rules/${id}`, { body }),
  resetRules: () => request('POST', '/rules/reset'),
  createRule: (body) => request('POST', '/rules', { body }),
  deleteRule: (id) => request('DELETE', `/rules/${id}`),

  getConfig: () => request('GET', '/config'),
  putConfig: (body) => request('PUT', '/config', { body }),
  loadSampleData: (body) => request('POST', '/sample-data', { body: body ?? {} }),

  
  clearAllMasterData: () => request('DELETE', '/clear-data/all'),

  getTimetable: () => request('GET', '/timetable'),
  listTimetables: () => request('GET', '/timetables'),
  getTimetableById: (id) => request('GET', `/timetables/${id}`),
  getWeek: (q) => request('GET', '/timetable/week', { query: q }),
  getMyWeek: (q) => request('GET', '/me/timetable/week', { query: q }),
  getMyClassWeek: (q) => request('GET', '/me/class-week', { query: q }),

  listDrafts: () => request('GET', '/drafts'),
  createDraft: (body) => request('POST', '/drafts', { body }),
  getDraftById: (id) => request('GET', `/drafts/${id}`),
  patchDraft: (id, body) => request('PATCH', `/drafts/${id}`, { body }),
  deleteDraftById: (id) => request('DELETE', `/drafts/${id}`),
  addDraftLesson: (draftId, body) => request('POST', `/drafts/${draftId}/lessons`, { body }),
  patchDraftLesson: (draftId, lessonId, body) => request('PATCH', `/drafts/${draftId}/lessons/${lessonId}`, { body }),
  deleteDraftLesson: (draftId, lessonId) => request('DELETE', `/drafts/${draftId}/lessons/${lessonId}`),
  startSolve: (draftId, body) => request('POST', `/drafts/${draftId}/solve`, { body: body ?? {} }),
  cancelSolve: (draftId) => request('DELETE', `/drafts/${draftId}/solve`),
  acceptDraft: (draftId, body) => request('POST', `/drafts/${draftId}/accept`, { body: body ?? {} }),
  repairDraft: (draftId, body) => request('POST', `/drafts/${draftId}/repair`, { body }),

  listEvents: (q) => request('GET', '/events', { query: q }),
  createEvent: (body) => request('POST', '/events', { body }),
  deleteEvent: (id) => request('DELETE', `/events/${id}`),
  listPending: (q) => request('GET', '/substitutions/pending', { query: q }),
  getOptions: (q) => request('GET', '/substitutions/options', { query: q }),
  listExceptions: (q) => request('GET', '/exceptions', { query: q }),
  createException: (body) => request('POST', '/exceptions', { body }),
  deleteException: (id) => request('DELETE', `/exceptions/${id}`),

    listSubstitutionRequests: (teacherId) => request('GET', '/substitution-requests', { query: teacherId != null ? { teacherId } : undefined }),
  createSubstitutionRequests: (body) => request('POST', '/substitution-requests', { body }),
  withdrawSubstitutionRequest: (id) => request('DELETE', `/substitution-requests/${id}`),
  listMySubstitutionRequests: (open) => request('GET', '/substitution-requests/me', { query: open ? { open: true } : undefined }),
  answerSubstitutionRequest: (id, body) => request('PATCH', `/substitution-requests/me/${id}`, { body }),

  getChangelog: (q) => request('GET', '/changelog', { query: q }),

  listHolidays: () => request('GET', '/holidays'),
  createHoliday: (body) => request('POST', '/holidays', { body }),
  deleteHoliday: (id) => request('DELETE', `/holidays/${id}`),
  listHolidayPresetYears: () => request('GET', '/holidays/preset-years'),
  loadHolidayPreset: (body) => request('POST', '/holidays/preset', { body }),

  exportMasterData: () => request('GET', '/masterdata/export'),
  importMasterData: (body) => request('POST', '/masterdata/import', { body }),

  exportMasterDataSection: (scope) => request('GET', `/masterdata/${scope}/export`),
  importMasterDataSection: (body) => request('POST', `/masterdata/${body.scope}/import`, { body }),
}
