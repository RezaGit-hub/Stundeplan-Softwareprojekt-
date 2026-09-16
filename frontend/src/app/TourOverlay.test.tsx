// @vitest-environment jsdom
//
// Der Rundgang (#139, T2-3.1): erscheint für die Planung genau dann, wenn das Konto ihn noch nie beendet hat
// (`tourCompletedAt` null), führt Schritt für Schritt an ein Bedienelement und merkt „Überspringen" wie
// „Fertig" am Konto. Geprüft gegen die Attrappe mit eigenem Store, wie die anderen Seitentests (test-utils).
//
// Den Ausschnitt selbst kann dieser Test nicht prüfen: jsdom misst jedes Element mit Breite 0, damit findet der
// Rundgang kein Ziel und lässt die Sperre aus. Geprüft wird die Ablauflogik — Anleitung, Abschluss durch
// Seitenwechsel, Abschluss durch Klick, Ende.
import { describe, expect, it } from 'vitest'
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderPage, testStore } from '@/test-utils'
import { TourOverlay } from './TourOverlay'
import { TOUR_STEPS } from './tour-steps'

/** Die Attrappe antwortet ohne Kunstlatenz; ein kurzer Tick reicht, damit /me durch ist. */
const abwarten = () => act(() => new Promise<void>((r) => setTimeout(r, 30)))

const ERSTER = TOUR_STEPS[0]
const KLICKSCHRITT = TOUR_STEPS.findIndex((s) => s.weiter === 'klick')

describe('TourOverlay (#139)', () => {
  it('führt durch den ersten Schritt und „Überspringen" merkt ihn am Konto', async () => {
    const nutzer = userEvent.setup()
    const store = testStore()
    await renderPage(<TourOverlay />, { store })

    expect(await screen.findByRole('dialog', { name: 'Rundgang' })).toBeTruthy()
    expect(screen.getByRole('heading', { name: ERSTER.title })).toBeTruthy()
    expect(screen.getByText(`Schritt 1 von ${TOUR_STEPS.length}`)).toBeTruthy()
    // Anleitung statt Ergebnis, solange die Zielseite nicht offen ist.
    expect(screen.getByText(ERSTER.action)).toBeTruthy()

    await nutzer.click(screen.getByRole('button', { name: 'Weiter' }))
    expect(screen.getByRole('heading', { name: TOUR_STEPS[1].title })).toBeTruthy()

    await nutzer.click(screen.getByRole('button', { name: 'Überspringen' }))

    const admin = store.users.find((u) => u.username === 'admin')!
    await waitFor(() => expect(admin.tourCompletedAt).toBeTruthy())
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Rundgang' })).toBeNull())
  })

  it('ein Navigationsschritt ist getan, sobald seine Seite offen ist', async () => {
    await renderPage(<TourOverlay />, { route: ERSTER.route })
    await screen.findByRole('dialog', { name: 'Rundgang' })

    expect(await screen.findByText('Das ist passiert:')).toBeTruthy()
    expect(screen.getByText(ERSTER.result)).toBeTruthy()
    expect(screen.queryByText(ERSTER.action)).toBeNull()
  })

  it('ein Klickschritt ist erst getan, wenn das Ziel angeklickt wurde', async () => {
    const nutzer = userEvent.setup()
    const schritt = TOUR_STEPS[KLICKSCHRITT]
    // Das echte Ziel steht in einer fremden Seite; hier reicht ein Knopf mit derselben Beschriftung.
    await renderPage(
      <>
        <button type="button">{schritt.target.text}</button>
        <TourOverlay />
      </>,
      { route: schritt.route },
    )
    await screen.findByRole('dialog', { name: 'Rundgang' })

    for (let i = 0; i < KLICKSCHRITT; i++) await nutzer.click(screen.getByRole('button', { name: 'Weiter' }))
    expect(screen.getByRole('heading', { name: schritt.title })).toBeTruthy()
    // Die Seite des Schritts ist offen, getan ist er trotzdem nicht — es fehlt der Klick.
    expect(screen.getByText(schritt.action)).toBeTruthy()

    await nutzer.click(screen.getByRole('button', { name: schritt.target.text! }))
    expect(await screen.findByText(schritt.result)).toBeTruthy()
  })

  it('„Fertig" am letzten Schritt merkt den Rundgang ebenfalls am Konto', async () => {
    const nutzer = userEvent.setup()
    const store = testStore()
    await renderPage(<TourOverlay />, { store })
    await screen.findByRole('dialog', { name: 'Rundgang' })

    for (let i = 1; i < TOUR_STEPS.length; i++) await nutzer.click(screen.getByRole('button', { name: 'Weiter' }))
    expect(screen.getByRole('heading', { name: TOUR_STEPS[TOUR_STEPS.length - 1].title })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Weiter' })).toBeNull()

    await nutzer.click(screen.getByRole('button', { name: 'Fertig' }))
    await waitFor(() => expect(store.users.find((u) => u.username === 'admin')!.tourCompletedAt).toBeTruthy())
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Rundgang' })).toBeNull())
  })

  it('bleibt weg, wenn das Konto den Rundgang schon beendet hat', async () => {
    const store = testStore()
    store.users.find((u) => u.username === 'admin')!.tourCompletedAt = '2026-09-01T08:00:00Z'
    await renderPage(<TourOverlay />, { store })
    await abwarten()
    expect(screen.queryByRole('dialog', { name: 'Rundgang' })).toBeNull()
  })

  it('eine Lehrkraft bekommt keinen Rundgang', async () => {
    await renderPage(<TourOverlay />, { username: 'meyer' })
    await abwarten()
    expect(screen.queryByRole('dialog', { name: 'Rundgang' })).toBeNull()
  })
})
