// Sitzung über das Neuladen (F-5a): Der Eintrag im localStorage hält die Anmeldung, eine 401 beim Start leert ihn.
import { beforeEach, describe, expect, it } from 'vitest'
import { ApiError, type MeDto } from './types'
import { authToken } from './auth-token'
import { clearSession, readSession, restoreSession, writeSession } from './session'

/** localStorage gibt es in Node nicht — hier dieselbe Schnittstelle, im Speicher. */
function installStorage(): void {
  const map = new Map<string, string>()
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      get length() { return map.size },
      key: (i: number) => [...map.keys()][i] ?? null,
      getItem: (k: string) => map.get(k) ?? null,
      setItem: (k: string, v: string) => void map.set(k, v),
      removeItem: (k: string) => void map.delete(k),
      clear: () => map.clear(),
    } satisfies Storage,
  })
}

const PLANNER: MeDto = { id: 1, username: 'admin', role: 'PLANNING', displayName: 'Planung', teacherId: null, tourCompletedAt: null }

describe('Sitzung im localStorage (F-5a)', () => {
  beforeEach(() => {
    installStorage()
    authToken.clear()
  })

  it('Reload hält die Sitzung: gespeicherter Eintrag plus gültiges /me ergibt wieder den angemeldeten Benutzer', async () => {
    writeSession({ token: 'mock-admin-abc', role: 'PLANNING', displayName: 'Planung', teacherId: null })

    const user = await restoreSession(() => Promise.resolve(PLANNER))

    expect(user).toEqual({ token: 'mock-admin-abc', role: 'PLANNING', displayName: 'Planung', teacherId: null })
    expect(authToken.get()).toBe('mock-admin-abc')
    expect(readSession()?.token).toBe('mock-admin-abc')
  })

  it('401 beim Start leert die Sitzung', async () => {
    writeSession({ token: 'abgelaufen', role: 'PLANNING', displayName: 'Planung', teacherId: null })

    const user = await restoreSession(() => Promise.reject(new ApiError({ type: 'urn:test', title: 'Nicht angemeldet', status: 401 })))

    expect(user).toBeNull()
    expect(authToken.get()).toBeNull()
    expect(readSession()).toBeNull()
  })

  it('Netzfehler beim Start leert die Sitzung ebenso', async () => {
    writeSession({ token: 'egal', role: 'TEACHER', displayName: 'Meyer', teacherId: 1 })

    expect(await restoreSession(() => Promise.reject(new ApiError({ type: 'urn:test', title: 'Server nicht erreichbar', status: 0 })))).toBeNull()
    expect(readSession()).toBeNull()
  })

  it('ohne Eintrag passiert nichts, Abmelden räumt ihn weg', async () => {
    expect(await restoreSession(() => Promise.resolve(PLANNER))).toBeNull()

    writeSession({ token: 'mock-meyer-xyz', role: 'TEACHER', displayName: 'Meyer', teacherId: 1 })
    clearSession()
    expect(readSession()).toBeNull()
  })

  it('kaputter Eintrag zählt als keiner', () => {
    globalThis.localStorage.setItem('stundenplan.session', '{nicht json')
    expect(readSession()).toBeNull()
    globalThis.localStorage.setItem('stundenplan.session', JSON.stringify({ token: 'x', role: 'HAUSMEISTER' }))
    expect(readSession()).toBeNull()
  })
})
