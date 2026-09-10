// @vitest-environment jsdom
//
// Der Annehmen-Dialog ist die letzte Bremse vor einem Schritt, den niemand rückgängig machen kann
// (Befund FRONTEND-6 — bis 05.09. war kein Dialog dieser Anwendung gerendert getestet).
//
// Geprüft wird die Sperrlogik, und zwar in beide Richtungen: geht nichts verloren, darf der Dialog nicht
// grundlos ein Häkchen verlangen; geht etwas verloren, muss er es verlangen. Beides hängt seit dem
// 05.09. an `expiringExceptions` aus dem Entwurf statt an einer eigenen Zählung im Dialog
// (Befund PFLICHTENHEFT-9) — genau diese Umstellung sichern die Fälle hier ab.
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { OpenLessonDto } from '@/api/types'
import { AcceptDialog } from './AcceptDialog'

function zeige(props: { expiringExceptions?: number; warnings?: string[]; openLessons?: OpenLessonDto[] } = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(
    <QueryClientProvider client={qc}>
      <AcceptDialog
        open
        onOpenChange={() => {}}
        warnings={props.warnings ?? []}
        openLessons={props.openLessons ?? []}
        expiringExceptions={props.expiringExceptions ?? 0}
      />
    </QueryClientProvider>,
  )
  return { annehmen: () => screen.getByRole('button', { name: 'Annehmen' }) }
}

describe('AcceptDialog', () => {
  it('ohne verfallende Vertretungen fragt er nichts und ist sofort bereit', () => {
    const { annehmen } = zeige({ expiringExceptions: 0 })

    expect((annehmen() as HTMLButtonElement).disabled).toBe(false)
    expect(screen.queryByText(/verfäll/i)).toBeNull()
    expect(screen.queryByLabelText('Verstanden — trotzdem annehmen')).toBeNull()
  })

  it('verfallen Vertretungen, bleibt „Annehmen" gesperrt, bis das Häkchen sitzt', async () => {
    const nutzer = userEvent.setup()
    const { annehmen } = zeige({ expiringExceptions: 2 })

    expect(screen.getByText(/2 Vertretungen ab heute/)).toBeTruthy()
    expect((annehmen() as HTMLButtonElement).disabled).toBe(true)

    await nutzer.click(screen.getByLabelText('Verstanden — trotzdem annehmen'))
    expect((annehmen() as HTMLButtonElement).disabled).toBe(false)
  })

  it('eine einzelne Vertretung wird im Singular genannt', () => {
    zeige({ expiringExceptions: 1 })
    expect(screen.getByText(/Eine Vertretung ab heute/)).toBeTruthy()
  })

  it('offene Stunden stehen als eigene Warnung da — sie verschwinden aus jeder Wochensicht', () => {
    zeige({ openLessons: [{ classId: 3, classShort: '9c', subjectShort: 'Ma', missing: 2 }] })
    expect(screen.getByText(/2 Stunden sind noch offen/)).toBeTruthy()
  })

  it('Warnungen des Entwurfs werden unverändert durchgereicht', () => {
    zeige({ warnings: ['9c Ma: 2 Stunden geplant, 1 vorgesehen'] })
    expect(screen.getByText('9c Ma: 2 Stunden geplant, 1 vorgesehen')).toBeTruthy()
  })

  it('das Datumsfeld für „gültig ab" erscheint erst auf Wunsch (E-40)', async () => {
    const nutzer = userEvent.setup()
    zeige()

    // Ohne Häkchen gilt der Plan sofort; das Datumsfeld wäre eine Frage, die niemand gestellt hat.
    expect(screen.queryByText('Bis dahin gilt der bisherige Plan.')).toBeNull()

    await nutzer.click(screen.getByLabelText('Erst ab einem Datum gültig'))
    expect(screen.getByText('Bis dahin gilt der bisherige Plan.')).toBeTruthy()
  })
})
