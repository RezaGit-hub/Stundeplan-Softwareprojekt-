// @vitest-environment jsdom
//
// Die Entwurfsliste (#103) ist seit 1.3.0 der Einstieg in die Planung: mehrere Varianten nebeneinander,
// eine davon wird angenommen. Geprüft wird gegen die Attrappe, was die Liste können muss — anlegen,
// umbenennen, ablegen und wieder öffnen (#116), verwerfen — und dass der leere Zustand kein Fehler ist.
import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { newTimetable, nextId, type Store } from '@/mock/store'
import { nowIso } from '@/lib/today'
import { renderPage, testStore } from '@/test-utils'
import { DraftListPage } from './DraftListPage'

/** Zwei Entwürfe im Beispielbestand: eine Kopie des aktiven Plans und ein leerer. */
function storeMitEntwuerfen(): Store {
  const s = testStore()
  const active = s.timetables.find((t) => t.status === 'ACTIVE')!
  const kopie = newTimetable('DRAFT', nowIso(), { name: 'Variante A', source: 'COPY_ACTIVE' })
  kopie.lessons = active.lessons.map((l) => ({ ...l, id: nextId(), timetableId: kopie.id }))
  const leer = newTimetable('DRAFT', nowIso(), { name: 'Variante B', source: 'EMPTY' })
  s.timetables.push(kopie, leer)
  return s
}

/** Eine Zeilenaktion anklicken — sie trägt den Namen des Entwurfs, damit die Zeile eindeutig ist. */
async function aktion(beschriftung: string, nutzer: ReturnType<typeof userEvent.setup>) {
  await nutzer.click(await screen.findByRole('button', { name: beschriftung }))
}

describe('Entwurfsliste (#103)', () => {
  it('zeigt jeden Entwurf mit Quelle, Status und harten Konflikten', async () => {
    const s = storeMitEntwuerfen()
    await renderPage(<DraftListPage />, { route: '/entwurf', store: s })

    expect(await screen.findByRole('heading', { level: 1, name: 'Entwürfe' })).toBeTruthy()
    // Jüngster zuerst (Vertrag 1.3.0).
    const namen = screen.getAllByRole('row').slice(1).map((r) => r.querySelector('td')?.textContent)
    expect(namen).toEqual(['Variante B', 'Variante A'])
    expect(screen.getByText('Kopie des aktiven Plans')).toBeTruthy()
    expect(screen.getByText('leer begonnen')).toBeTruthy()
    expect(screen.getAllByText('in Arbeit')).toHaveLength(2)
  })

  it('ohne Entwurf steht ein leerer Zustand mit dem Weg nach vorn — kein Fehler', async () => {
    await renderPage(<DraftListPage />, { route: '/entwurf' })

    expect(await screen.findByText('Noch kein Entwurf')).toBeTruthy()
    expect(screen.getByRole('button', { name: /Ersten Entwurf anlegen/ })).toBeTruthy()
  })

  it('legt einen benannten Entwurf an', async () => {
    const nutzer = userEvent.setup()
    const s = testStore()
    await renderPage(<DraftListPage />, { route: '/entwurf', store: s })

    await nutzer.click(await screen.findByRole('button', { name: /Ersten Entwurf anlegen/ }))
    await nutzer.type(screen.getByLabelText('Name'), 'Variante B')
    await nutzer.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(s.timetables.some((t) => t.status === 'DRAFT' && t.name === 'Variante B')).toBe(true))
  })

  it('benennt einen Entwurf um', async () => {
    const nutzer = userEvent.setup()
    const s = storeMitEntwuerfen()
    await renderPage(<DraftListPage />, { route: '/entwurf', store: s })

    await aktion('„Variante A" umbenennen', nutzer)
    const feld = await screen.findByLabelText('Name')
    await nutzer.clear(feld)
    await nutzer.type(feld, 'Variante A2')
    await nutzer.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(s.timetables.some((t) => t.name === 'Variante A2')).toBe(true))
  })

  it('legt einen Entwurf in die Ablage und öffnet ihn wieder (#116)', async () => {
    const nutzer = userEvent.setup()
    const s = storeMitEntwuerfen()
    const a = s.timetables.find((t) => t.name === 'Variante A')!
    await renderPage(<DraftListPage />, { route: '/entwurf', store: s })

    await aktion('„Variante A" in die Ablage legen', nutzer)
    await waitFor(() => expect(a.finished).toBe(true))
    expect(await screen.findByText('Ablage')).toBeTruthy()

    await aktion('„Variante A" wieder öffnen', nutzer)
    await waitFor(() => expect(a.finished).toBe(false))
    // Nicht nur der Bestand — die Liste muss den Entwurf auch wieder als offen zeigen: Kennzeichen „Ablage"
    // weg, beide Entwürfe „in Arbeit", und der Knopf führt wieder in die Ablage statt zurück.
    await waitFor(() => expect(screen.queryByText('Ablage')).toBeNull())
    expect(screen.getAllByText('in Arbeit')).toHaveLength(2)
    expect(screen.getByRole('button', { name: '„Variante A" in die Ablage legen' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: '„Variante A" wieder öffnen' })).toBeNull()
  })

  // Aus der Befehlspalette (⌘K) führt „Neuen Entwurf anlegen" nach /entwurf?aktion=anlegen — die Liste
  // öffnet den Dialog selbst, sonst wäre der Weg ein Klick länger als die beiden Aktionen daneben.
  it('öffnet den Anlegen-Dialog, wenn `aktion=anlegen` in der Adresse steht', async () => {
    await renderPage(<DraftListPage />, { route: '/entwurf?aktion=anlegen', store: storeMitEntwuerfen() })

    expect(await screen.findByRole('dialog', { name: 'Neuer Entwurf' })).toBeTruthy()
  })

  it('verwirft einen Entwurf erst nach Rückfrage — die anderen bleiben', async () => {
    const nutzer = userEvent.setup()
    const s = storeMitEntwuerfen()
    await renderPage(<DraftListPage />, { route: '/entwurf', store: s })

    await aktion('„Variante A" verwerfen', nutzer)
    expect(await screen.findByText(/Entwurf „Variante A" verwerfen\?/)).toBeTruthy()
    await nutzer.click(screen.getByRole('button', { name: 'Verwerfen' }))

    await waitFor(() => expect(s.timetables.filter((t) => t.status === 'DRAFT').map((t) => t.name)).toEqual(['Variante B']))
  })
})
