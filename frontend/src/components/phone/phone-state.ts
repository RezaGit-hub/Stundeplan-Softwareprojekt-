// Zustandsautomat der Android-App (SPEZIFIKATION 5.1) als reiner Reducer — dieselben fünf UiStates und vier Screens
// wie `ui/UiState.kt`. Kein Netz hier drin: die asynchronen Schritte (Login, Abruf, Verbindung prüfen) macht PhoneApp
// und schickt Ergebnisse als Aktionen. So kann das Team den Ablauf 1:1 nach Kotlin übertragen.
import type { EntryDto, TimeslotDto, WeekDto } from '@/api/types'
import { startOfIsoWeek } from '@/lib/dates'

export const PHONE_USERS = ['meyer', 'schulz', 'weber'] as const
export type PhoneUser = (typeof PHONE_USERS)[number]
export function isPhoneUser(v: string | null | undefined): v is PhoneUser {
  return PHONE_USERS.includes(v as PhoneUser)
}

/** Vorbelegung der Serveradresse (android.md, `Defaults.DEFAULT_SERVER_URL`, ohne `/api/`) */
export const DEFAULT_SERVER_URL = 'http://134.245.1.240:1500'
export const APP_VERSION = 'Stundenplan 0.1 (Vorschau)'

// ------------------------------------------------------------------ Screen (sealed interface Screen)
export type Screen =
  | { kind: 'settings' }
  | { kind: 'day'; date: string }
  | { kind: 'week'; monday: string }
  | { kind: 'detail'; entry: EntryDto; from: Screen }

// ------------------------------------------------------------------ UiState (sealed interface UiState)
export type UiState =
  | { kind: 'loading' }
  | { kind: 'data'; week: WeekDto; slots: TimeslotDto[]; loadedAt: string; offline: boolean; changedToday: number }
  | { kind: 'noServer' }
  | { kind: 'loginRequired'; message: string | null }
  | { kind: 'error'; message: string }

/** DataStore-Preferences der App (serverUrl, token, displayName) */
export interface Prefs {
  serverUrl: string | null
  token: string | null
  username: string | null
  displayName: string | null
}

/** Offline-Cache: `filesDir/week-<monday>.json` + Zeitstempel */
export interface CachedWeek { week: WeekDto; slots: TimeslotDto[]; savedAt: string }

/** Der Offline-Cache selbst liegt als Ref in PhoneApp (wird nie gerendert), nur der Eintragstyp steht hier. */
export interface PhoneState {
  screen: Screen
  ui: UiState
  prefs: Prefs
}

export type PhoneAction =
  | { type: 'navigate'; screen: Screen }
  | { type: 'back'; today: string }
  | { type: 'ui'; ui: UiState }
  | { type: 'prefs'; prefs: Partial<Prefs> }
  | { type: 'restart'; today: string }

export function initialPhoneState(opts: { date: string; firstStart: boolean }): PhoneState {
  if (opts.firstStart) {
    return { screen: { kind: 'settings' }, ui: { kind: 'noServer' }, prefs: { serverUrl: null, token: null, username: null, displayName: null } }
  }
  return { screen: { kind: 'day', date: opts.date }, ui: { kind: 'loading' }, prefs: { serverUrl: DEFAULT_SERVER_URL, token: null, username: null, displayName: null } }
}

/** Zurück-Regel aus 5.3: Detail → from, Woche → Tag(Montag), Einstellungen → Tag(heute) nur wenn angemeldet, Tag → nichts */
function back(state: PhoneState, today: string): PhoneState {
  const s = state.screen
  if (s.kind === 'detail') return { ...state, screen: s.from }
  if (s.kind === 'week') return { ...state, screen: { kind: 'day', date: s.monday } }
  if (s.kind === 'settings' && state.prefs.token) return { ...state, screen: { kind: 'day', date: today } }
  return state
}

export function phoneReducer(state: PhoneState, action: PhoneAction): PhoneState {
  switch (action.type) {
    case 'navigate':
      return { ...state, screen: action.screen }
    case 'back':
      return back(state, action.today)
    case 'ui':
      return { ...state, ui: action.ui }
    case 'prefs':
      return { ...state, prefs: { ...state.prefs, ...action.prefs } }
    case 'restart':
      // Prozessneustart: DataStore und Cache bleiben, Screen und UiState beginnen von vorn
      return { ...state, screen: { kind: 'day', date: action.today }, ui: { kind: 'loading' } }
  }
}

/** Der Screen unter einem Bottom Sheet */
export function baseScreen(screen: Screen): Exclude<Screen, { kind: 'detail' }> {
  return screen.kind === 'detail' ? baseScreen(screen.from) : screen
}

/** Montag der Woche, die der Screen zeigt */
export function mondayOf(screen: Screen, today: string): string {
  const b = baseScreen(screen)
  if (b.kind === 'day') return startOfIsoWeek(b.date)
  if (b.kind === 'week') return b.monday
  return startOfIsoWeek(today)
}

/** Einträge mit date == heute und kind != REGULAR (Soll E-40) */
export function countChangedToday(week: WeekDto, today: string): number {
  return week.days.filter((d) => d.date === today).flatMap((d) => d.entries).filter((e) => e.kind !== 'REGULAR').length
}

/**
 * Erreichbarkeit einer Serveradresse in der Vorschau: gültige http(s)-URL, nicht aus TEST-NET-1 (192.0.2.0/24, RFC 5737,
 * nie geroutet — Testfall T-A3.1 nutzt `http://192.0.2.1:9999`). Alles andere gilt als erreichbar (Attrappe antwortet).
 */
export function isReachableUrl(url: string | null): boolean {
  if (!url) return false
  try {
    const u = new URL(url.trim())
    return (u.protocol === 'http:' || u.protocol === 'https:') && !u.hostname.startsWith('192.0.2.')
  } catch {
    return false
  }
}
