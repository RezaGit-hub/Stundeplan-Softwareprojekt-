// Aufbau für Komponententests, die eine ganze Seite rendern (Befund FRONTEND-6, 05.09.).
//
// Eine Seite braucht drei Dinge, die im Betrieb `main.tsx` und `router.tsx` stellen: einen Router (die
// Seiten lesen und schreiben ihren Zustand in der URL), einen QueryClient und eine angemeldete Sitzung.
// `renderPage` stellt alle drei und hängt die Attrappe als `api` ein — dieselbe, gegen die auch die
// Logiktests laufen, mit demselben Beispielbestand.
//
// Die Attrappe wird hier direkt gesetzt (`setApi`) und nicht über `VITE_MOCK` geladen: so hat jeder Test
// seinen eigenen Store und die Fälle beeinflussen sich nicht.
import type { ReactElement, ReactNode } from 'react'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setApi } from '@/api/api'
import { authToken } from '@/api/auth-token'
import { clearSession, writeSession } from '@/api/session'
import { AuthProvider } from '@/app/auth'
import { emptyStore, type Store } from '@/mock/store'
import { loadSmall } from '@/mock/data-small'
import { createMockApiForTests } from '@/mock/mock-api'

/** Ein frischer Store mit dem kleinen Beispielbestand (wie in den Logiktests der Attrappe). */
export function testStore(): Store {
  const s = emptyStore()
  loadSmall(s)
  return s
}

/**
 * Rendert eine Seite unter `route`, angemeldet als `username`.
 *
 * Die Anmeldung läuft über die Attrappe, damit Token und Rolle echt sind: `AuthProvider` prüft einen
 * gespeicherten Eintrag beim Start mit `GET /api/me` — ein erfundenes Token führte dort zu 401 und die
 * Seite landete auf der Anmeldemaske statt im Test.
 */
export async function renderPage(ui: ReactElement, opts: { route?: string; username?: string; store?: Store } = {}) {
  const s = opts.store ?? testStore()
  const name = opts.username ?? 'admin'
  const api = createMockApiForTests(s, name)
  setApi(api)

  // Im kleinen Beispielbestand ist das Kennwort gleich dem Benutzernamen (mock/data-small.ts).
  clearSession()
  const angemeldet = await api.login({ username: name, password: name })
  authToken.set(angemeldet.token)
  writeSession({ token: angemeldet.token, role: angemeldet.role, displayName: angemeldet.displayName, teacherId: angemeldet.teacherId })

  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[opts.route ?? '/']}>
      <QueryClientProvider client={qc}>
        <AuthProvider>{children}</AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
  return render(ui, { wrapper: Wrapper })
}
