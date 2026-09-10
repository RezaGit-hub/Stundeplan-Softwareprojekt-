// Anmeldung: Token im Speicher (authToken) und zusätzlich im localStorage (F-5a, Entscheidung Nutzer 05.09.) —
// Neuladen und Browser-Neustart bleiben angemeldet, bis abgemeldet wird oder das Token abläuft (Server:
// app.auth.token-ttl, 30 Tage). Beim Start wird der gespeicherte Eintrag mit GET /api/me geprüft; 401 oder
// Netzfehler löschen ihn und es geht nach /login.
// Im Mock akzeptiert jede Route ?demo=admin|meyer|schulz|weber und meldet still an (Screenshots, Demo-Links).
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { api, IS_MOCK } from '@/api/api'
import { authToken } from '@/api/auth-token'
import { setOnUnauthorized } from '@/api/client'
import { queryClient } from '@/api/hooks'
import { clearSession, readSession, restoreSession, writeSession } from '@/api/session'
import type { LoginResponse, Role } from '@/api/types'

export interface AuthContextValue {
  user: LoginResponse | null
  token: string | null
  role: Role | null
  /** true, solange die stille Demo-Anmeldung (?demo=) läuft */
  booting: boolean
  login(username: string, password: string): Promise<LoginResponse>
  logout(): Promise<void>
}
const AuthContext = createContext<AuthContextValue | null>(null)

function demoUserFromUrl(): string | null {
  if (!IS_MOCK) return null
  const { search, hash } = window.location
  const q = search || (hash.includes('?') ? hash.slice(hash.indexOf('?')) : '')
  const v = new URLSearchParams(q).get('demo')
  return v && /^[a-z0-9]+$/i.test(v) ? v.toLowerCase() : null
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<LoginResponse | null>(null)
  // Die gespeicherte Sitzung einmal beim ersten Rendern lesen — danach entscheidet nur noch der Zustand.
  const [stored] = useState(() => readSession())
  const [booting, setBooting] = useState(() => demoUserFromUrl() !== null || readSession() !== null)
  const started = useRef(false)

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.login({ username, password })
    authToken.set(res.token)
    writeSession(res)
    setUser(res)
    queryClient.clear()
    return res
  }, [])

  const logout = useCallback(async () => {
    try { await api.logout() } catch { /* Sitzung ist ohnehin beendet */ }
    authToken.clear()
    clearSession()
    setUser(null)
    queryClient.clear()
  }, [])

  // 401 vom Client: Token weg, Toast, RequireRole leitet nach /login
  useEffect(() => {
    setOnUnauthorized(() => {
      clearSession()
      setUser(null)
      queryClient.clear()
      toast.error('Sitzung abgelaufen, bitte neu anmelden.', { duration: 4000 })
    })
  }, [])

  // stille Demo-Anmeldung (nur Mock) — hat Vorrang vor der gespeicherten Sitzung
  useEffect(() => {
    const demo = demoUserFromUrl()
    if (!demo || started.current) return
    started.current = true
    login(demo, demo).catch(() => undefined).finally(() => setBooting(false))
  }, [login])

  // Start ohne Demo: die gespeicherte Sitzung mit GET /api/me prüfen (F-5a). Ungültig → Eintrag weg, /login.
  useEffect(() => {
    if (!stored || started.current) return
    started.current = true
    restoreSession(api.me).then(setUser).finally(() => setBooting(false))
  }, [stored])

  const value = useMemo<AuthContextValue>(
    () => ({ user, token: user?.token ?? null, role: user?.role ?? null, booting, login, logout }),
    [user, booting, login, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth braucht einen AuthProvider')
  return ctx
}
