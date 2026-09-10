// Sitzung über das Neuladen und den Browser-Neustart retten (F-5a, Entscheidung Nutzer 05.09.).
//
// api.md Auth-Fluss 5 hielt das Token nur im Speicher — dann ist jedes F5 ein neues Anmelden. Deshalb liegt
// die Sitzung zusätzlich im **localStorage**: Sie hält, bis abgemeldet wird oder das Token abläuft
// (Server: `app.auth.token-ttl`, seit dem NACHTRAG 05.09. 30 Tage).
//
// Der Eintrag ist nur ein Vorschlag, kein Beweis: beim Start prüft `restoreSession` ihn mit GET /api/me.
// Jede 401 (auch mitten im Betrieb, siehe client.ts) löscht ihn.
import { authToken } from './auth-token'
import { ROLES, type LoginResponse, type MeDto, type Role } from './types'

const KEY = 'stundenplan.session'

/** Token plus Principal — genau das, was der Login liefert. */
export type StoredSession = LoginResponse

function isRole(v: unknown): v is Role {
  return typeof v === 'string' && (ROLES as readonly string[]).includes(v)
}

/** localStorage kann fehlen (Node, Tests) oder werfen (Privatmodus, gesperrte Website-Daten). */
function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null
  } catch {
    return null
  }
}

export function readSession(): StoredSession | null {
  try {
    const raw = storage()?.getItem(KEY)
    if (!raw) return null
    const p = JSON.parse(raw) as Partial<StoredSession> | null
    if (!p || typeof p.token !== 'string' || p.token.length === 0 || !isRole(p.role)) return null
    return { token: p.token, role: p.role, displayName: String(p.displayName ?? ''), teacherId: typeof p.teacherId === 'number' ? p.teacherId : null }
  } catch {
    return null
  }
}

export function writeSession(s: StoredSession): void {
  try {
    storage()?.setItem(KEY, JSON.stringify(s))
  } catch { /* ohne Speicher bleibt es beim bisherigen Verhalten: Neuladen = neu anmelden */ }
}

export function clearSession(): void {
  try {
    storage()?.removeItem(KEY)
  } catch { /* nichts zu löschen */ }
}

/**
 * Beim App-Start: gespeicherte Sitzung übernehmen und mit GET /api/me prüfen.
 * Antwortet der Server 401 oder gar nicht, ist der Eintrag wertlos — er wird gelöscht und es geht nach /login.
 */
export async function restoreSession(me: () => Promise<MeDto>): Promise<LoginResponse | null> {
  const stored = readSession()
  if (!stored) return null
  authToken.set(stored.token)
  try {
    // Der Server hat das letzte Wort: Rolle und Name kommen aus /me, nicht aus dem Eintrag.
    const who = await me()
    const fresh: StoredSession = { token: stored.token, role: who.role, displayName: who.displayName, teacherId: who.teacherId }
    writeSession(fresh)
    return fresh
  } catch {
    authToken.clear()
    clearSession()
    return null
  }
}
