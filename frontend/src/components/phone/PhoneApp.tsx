// Die App im Telefon: Zustandsautomat (phone-state.ts) + Datenfluss aus SPEZIFIKATION 5.4 gegen `mockApi.asUser(as)`.
// Login läuft über mockApi.login (prüft Passwort, berührt das Web-Token nicht); Abmelden nur lokal — mockApi.logout()
// würde das Token der Web-Sitzung löschen. Offline-Cache = Ref mit letzter Woche je Montag (filesDir/week-<monday>.json).
import { useCallback, useEffect, useReducer, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { CalendarDays, LayoutGrid, Settings } from 'lucide-react'
import { problemOf } from '@/api/errors'
import type { EntryDto } from '@/api/types'
import { mockApi } from '@/mock/mock-api'
import { latency } from '@/mock/latency'
import { addDaysIso, addWeeks, formatDate, startOfIsoWeek } from '@/lib/dates'
import { BASE, EASE, pageTransition } from '@/lib/motion'
import { now } from '@/lib/today'
import { cn } from '@/lib/utils'
import { PhoneDayScreen } from './PhoneDayScreen'
import { PhoneDetailSheet } from './PhoneDetailSheet'
import { PhoneSettingsScreen, type CheckResult } from './PhoneSettingsScreen'
import { PhoneWeekScreen } from './PhoneWeekScreen'
import {
  baseScreen, countChangedToday, initialPhoneState, isPhoneUser, isReachableUrl, mondayOf, phoneReducer,
  type CachedWeek, type PhoneUser, type Prefs, type Screen,
} from './phone-state'

export interface PhoneAppProps {
  as: PhoneUser
  /** „Heute" des Telefons (Statusleiste, Heute-Knopf, „Heute geändert") */
  date: string
  /** Server aus: Abruf schlägt fehl, Cache oder Fehler */
  offline: boolean
  /** Erststart: keine Serveradresse, keine Anmeldung */
  firstStart?: boolean
  /** Anmeldung im Telefon mit einem anderen Demo-Konto */
  onAccountChange?(user: PhoneUser): void
}

const NAV: { key: 'day' | 'week' | 'settings'; label: string; icon: typeof CalendarDays }[] = [
  { key: 'day', label: 'Tag', icon: CalendarDays },
  { key: 'week', label: 'Woche', icon: LayoutGrid },
  { key: 'settings', label: 'Einstellungen', icon: Settings },
]

/** Material-3-Navigationsleiste: Symbol in einer Pille, darunter Beschriftung */
function PhoneNavBar({ active, onSelect }: { active: 'day' | 'week' | 'settings'; onSelect(key: 'day' | 'week' | 'settings'): void }) {
  return (
    <nav className="flex h-16 shrink-0 items-start border-t bg-surface pt-2" aria-label="Hauptnavigation">
      {NAV.map((n) => {
        const isActive = n.key === active
        return (
          <button key={n.key} type="button" onClick={() => onSelect(n.key)} aria-current={isActive ? 'page' : undefined} className="group flex flex-1 flex-col items-center gap-0.5">
            <span className="relative flex h-7 w-14 items-center justify-center">
              {isActive && <motion.span layoutId="phone-nav-pill" className="absolute inset-0 rounded-full bg-primary-soft" transition={{ duration: BASE, ease: EASE }} />}
              <n.icon className={cn('relative size-5', isActive ? 'text-primary-soft-foreground' : 'text-foreground-subtle group-hover:text-foreground')} />
            </span>
            <span className={cn('text-2xs', isActive ? 'font-semibold' : 'text-foreground-subtle')}>{n.label}</span>
          </button>
        )
      })}
    </nav>
  )
}

/** Zeitstempel für „Stand: 21.09.2026 07:42" — Datum des Telefons, echte Uhrzeit */
function stamp(today: string): string {
  return `${formatDate(today)} ${now().toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}`
}

export function PhoneApp({ as, date, offline, firstStart = false, onAccountChange }: PhoneAppProps) {
  const [state, dispatch] = useReducer(phoneReducer, { date, firstStart }, initialPhoneState)
  const [refreshing, setRefreshing] = useState(false)
  const cache = useRef<Record<string, CachedWeek>>({})
  const request = useRef(0)
  // Neueste Props/State für asynchrone Abläufe (Effekte und Handler laufen nach dem Commit)
  const latest = useRef({ state, date, offline })
  useEffect(() => {
    latest.current = { state, date, offline }
  })

  // ---------------- vm.refresh(): eine Kette, keine Abkürzung (5.4)
  const load = useCallback(async (dateIso: string, prefs: Prefs, opts: { silent?: boolean } = {}) => {
    if (!prefs.serverUrl) {
      dispatch({ type: 'ui', ui: { kind: 'noServer' } })
      dispatch({ type: 'navigate', screen: { kind: 'settings' } })
      return
    }
    if (!prefs.token || !prefs.username) {
      dispatch({ type: 'ui', ui: { kind: 'loginRequired', message: null } })
      dispatch({ type: 'navigate', screen: { kind: 'settings' } })
      return
    }
    const monday = startOfIsoWeek(dateIso)
    const id = ++request.current
    if (opts.silent) setRefreshing(true)
    else dispatch({ type: 'ui', ui: { kind: 'loading' } })
    const { offline: off, date: today } = latest.current
    const online = !off && isReachableUrl(prefs.serverUrl)
    try {
      if (online) {
        const api = mockApi.asUser(prefs.username)
        const [week, grid] = await Promise.all([api.getMyWeek({ date: monday }), api.getTimegrid()])
        if (id !== request.current) return
        const entry: CachedWeek = { week, slots: grid.slots, savedAt: stamp(today) }
        cache.current[monday] = entry
        dispatch({ type: 'ui', ui: { kind: 'data', week, slots: grid.slots, loadedAt: entry.savedAt, offline: false, changedToday: countChangedToday(week, today) } })
      } else {
        await latency() // simulierter Verbindungsversuch
        if (id !== request.current) return
        const cached = cache.current[monday]
        if (cached) dispatch({ type: 'ui', ui: { kind: 'data', week: cached.week, slots: cached.slots, loadedAt: cached.savedAt, offline: true, changedToday: countChangedToday(cached.week, today) } })
        else dispatch({ type: 'ui', ui: { kind: 'error', message: 'Server nicht erreichbar und kein gespeicherter Stand.' } })
      }
    } catch (err) {
      if (id !== request.current) return
      const problem = problemOf(err)
      if (problem.status === 401) {
        dispatch({ type: 'prefs', prefs: { token: null } })
        dispatch({ type: 'ui', ui: { kind: 'loginRequired', message: 'Sitzung abgelaufen, bitte neu anmelden.' } })
        dispatch({ type: 'navigate', screen: { kind: 'settings' } })
      } else {
        dispatch({ type: 'ui', ui: { kind: 'error', message: problem.detail ?? problem.title } })
      }
    } finally {
      if (id === request.current) setRefreshing(false)
    }
  }, [])

  // Konto, für das die stille Anmeldung schon lief (einmal je Konto, damit „Abmelden" nicht sofort wieder anmeldet)
  const autoLoginDone = useRef<PhoneUser | null>(null)

  // ---------------- Anmeldung: POST /api/auth/login → Token in DataStore → refresh()
  const loginAs = useCallback(async (username: string, password: string): Promise<string | null> => {
    try {
      const res = await mockApi.login({ username, password })
      const prefs: Prefs = { ...latest.current.state.prefs, token: res.token, username, displayName: res.displayName }
      dispatch({ type: 'prefs', prefs })
      dispatch({ type: 'navigate', screen: { kind: 'day', date: latest.current.date } })
      if (isPhoneUser(username)) {
        autoLoginDone.current = username // Handanmeldung zählt wie die stille
        onAccountChange?.(username)
      }
      void load(latest.current.date, prefs)
      return null
    } catch (err) {
      const problem = problemOf(err)
      if (problem.status === 401) return 'Benutzername oder Passwort ist falsch.'
      if (problem.status === 0) return 'Server nicht erreichbar.'
      return problem.detail ?? problem.title
    }
  }, [load, onAccountChange])

  // Start bzw. Kontowechsel über die Steuerung: stille Anmeldung mit Passwort = Benutzername
  useEffect(() => {
    if (firstStart || autoLoginDone.current === as) return
    autoLoginDone.current = as
    void loginAs(as, as)
  }, [as, firstStart, loginAs])

  // Datum der Vorschau geändert → Tagesansicht und Abruf (nicht beim ersten Rendern)
  const dateSeen = useRef(date)
  useEffect(() => {
    if (dateSeen.current === date) return
    dateSeen.current = date
    const prefs = latest.current.state.prefs
    if (!prefs.token) return
    dispatch({ type: 'navigate', screen: { kind: 'day', date } })
    void load(date, prefs)
  }, [date, load])

  // Server an/aus → laufende Sicht neu abrufen
  const offlineSeen = useRef(offline)
  useEffect(() => {
    if (offlineSeen.current === offline) return
    offlineSeen.current = offline
    const { state: s, date: today } = latest.current
    if (!s.prefs.token) return
    void load(mondayOf(s.screen, today), s.prefs)
  }, [offline, load])

  // ---------------- Handler
  const base = baseScreen(state.screen)
  const shownMonday = state.ui.kind === 'data' ? state.ui.week.monday : null
  const goDay = (d: string) => {
    dispatch({ type: 'navigate', screen: { kind: 'day', date: d } })
    if (startOfIsoWeek(d) !== shownMonday) void load(d, state.prefs)
  }
  const goWeek = (monday: string) => {
    dispatch({ type: 'navigate', screen: { kind: 'week', monday } })
    if (monday !== shownMonday) void load(monday, state.prefs)
  }
  const refresh = () => void load(mondayOf(state.screen, date), state.prefs, { silent: state.ui.kind === 'data' })
  const openEntry = (entry: EntryDto) => dispatch({ type: 'navigate', screen: { kind: 'detail', entry, from: state.screen } })
  const toSettings = () => dispatch({ type: 'navigate', screen: { kind: 'settings' } })
  const onNav = (key: 'day' | 'week' | 'settings') => {
    if (key === 'settings') toSettings()
    else if (key === 'week') goWeek(mondayOf(state.screen, date))
    else goDay(base.kind === 'week' ? base.monday : date)
  }
  const checkConnection = async (url: string): Promise<CheckResult> => {
    if (!latest.current.offline && isReachableUrl(url)) {
      try {
        const h = await mockApi.health()
        return { ok: true, schoolName: h.schoolName }
      } catch {
        return { ok: false }
      }
    }
    await latency()
    return { ok: false }
  }
  const saveUrl = (url: string) => {
    const prefs: Prefs = { ...state.prefs, serverUrl: url }
    dispatch({ type: 'prefs', prefs: { serverUrl: url } })
    if (prefs.token) {
      dispatch({ type: 'navigate', screen: { kind: 'day', date } })
      void load(date, prefs)
    } else {
      dispatch({ type: 'ui', ui: { kind: 'loginRequired', message: null } })
    }
  }
  const logout = () => {
    dispatch({ type: 'prefs', prefs: { token: null, username: null, displayName: null } })
    dispatch({ type: 'ui', ui: { kind: 'loginRequired', message: null } })
    dispatch({ type: 'navigate', screen: { kind: 'settings' } })
  }
  const restart = () => {
    dispatch({ type: 'restart', today: date })
    void load(date, state.prefs)
  }

  const detail: EntryDto | null = state.screen.kind === 'detail' ? state.screen.entry : null
  const loggedIn = !!state.prefs.token
  const screenKey: Screen['kind'] = base.kind

  return (
    <div className="relative flex h-full flex-col">
      <div className="relative min-h-0 flex-1 overflow-hidden">
        <AnimatePresence mode="wait" initial={false}>
          <motion.div key={screenKey} {...pageTransition} className="absolute inset-0 flex flex-col">
            {base.kind === 'settings' && (
              <PhoneSettingsScreen
                ui={state.ui}
                prefs={state.prefs}
                onCheck={checkConnection}
                onSaveUrl={saveUrl}
                onLogin={loginAs}
                onLogout={logout}
                onRestart={restart}
                onBack={() => dispatch({ type: 'back', today: date })}
              />
            )}
            {base.kind === 'day' && (
              <PhoneDayScreen
                ui={state.ui}
                date={base.date}
                today={date}
                refreshing={refreshing}
                onPrev={() => goDay(addDaysIso(base.date, -1))}
                onNext={() => goDay(addDaysIso(base.date, 1))}
                onToday={() => goDay(date)}
                onRefresh={refresh}
                onEntry={openEntry}
                onSettings={toSettings}
              />
            )}
            {base.kind === 'week' && (
              <PhoneWeekScreen
                ui={state.ui}
                monday={base.monday}
                today={date}
                refreshing={refreshing}
                onPrev={() => goWeek(addWeeks(base.monday, -1))}
                onNext={() => goWeek(addWeeks(base.monday, 1))}
                onThisWeek={() => goWeek(startOfIsoWeek(date))}
                onDay={goDay}
                onEntry={openEntry}
                onRefresh={refresh}
                onSettings={toSettings}
              />
            )}
          </motion.div>
        </AnimatePresence>
      </div>
      {loggedIn && <PhoneNavBar active={base.kind} onSelect={onNav} />}
      <AnimatePresence>
        {detail && state.ui.kind === 'data' && (
          <PhoneDetailSheet key="detail" entry={detail} slots={state.ui.slots} onClose={() => dispatch({ type: 'back', today: date })} />
        )}
      </AnimatePresence>
    </div>
  )
}
