// @vitest-environment jsdom
//
// Der Rundgang (#139, T2-3.1): erscheint für die Planung genau dann, wenn das Konto ihn noch nie beendet hat
// (`tourCompletedAt` null), führt Schritt für Schritt an ein Bedienelement und merkt „Überspringen" wie
// „Fertig" am Konto. Geprüft gegen die Attrappe mit eigenem Store, wie die anderen Seitentests (test-utils).
//
// Den Ausschnitt selbst kann dieser Test nicht prüfen: jsdom misst jedes Element mit Breite 0, damit findet der
// Rundgang kein Ziel und lässt die Sperre aus. Geprüft wird die Ablauflogik — Anleitung, Abschluss durch
// Seitenwechsel, Abschluss durch Klick, Ende.
//
// Zur Beschriftung: Solange die Aufgabe eines Schritts offen ist, heißt der Knopf „Überspringen" und tritt
// optisch zurück — der Hauptweg ist das hervorgehobene Element. „Weiter" erscheint erst, wenn der Schritt
// getan ist. Links steht „Beenden", rechts „Zurück" und „Weiter"/„Überspringen".
import { describe, expect, it } from 'vitest'
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderPage, testStore } from '@/test-utils'
import { TourOverlay } from './TourOverlay'
import { TOUR_STEPS } from './tour-steps'

/** Die Attrappe antwortet ohne Kunstlatenz; ein kurzer Tick reicht, damit /me durch ist. */
const abwarten = () => act(() => new Promise<void>((r) => setTimeout(r, 30)))

// Der Rundgang beginnt mit Erklaerschritten auf der Übersicht; geprüft wird der erste Schritt, der eine
// Seite öffnet, und der erste, der einen Klick verlangt.
const NAVI = TOUR_STEPS.findIndex((s) => s.weiter === 'route')
const ERSTER = TOUR_STEPS[NAVI]
const KLICKSCHRITT = TOUR_STEPS.findIndex((s) => s.weiter === 'klick')
/** „Überspringen" laesst das ganze Thema aus: der naechste Schritt auf einer anderen Seite. */
const NAECHSTE_SEITE = TOUR_STEPS.findIndex((s, i) => i > NAVI && s.route !== ERSTER.route)

/** Klickt sich bis zum letzten Schritt durch, egal ob „Weiter" oder „Überspringen" dasteht. */
/** Klickt die Erklärschritte des Auftakts weg, bis der erste Schritt mit Seitenwechsel dran ist. */
async function bisZumNavigationsschritt(nutzer: ReturnType<typeof userEvent.setup>) {
  for (let i = 0; i < NAVI; i++) {
    const knopf = screen.queryByRole('button', { name: 'Weiter' })
    if (!knopf) return
    await nutzer.click(knopf)
  }
}

async function bisZumEnde(nutzer: ReturnType<typeof userEvent.setup>) {
  for (let i = 0; i < TOUR_STEPS.length; i++) {
    const knopf = screen.queryByRole('button', { name: 'Überspringen' })
    if (!knopf) return
    await nutzer.click(knopf)
  }
}

describe('TourOverlay (#139)', () => {
  it('führt durch den ersten Schritt und „Überspringen" merkt ihn am Konto', async () => {
    const nutzer = userEvent.setup()
    const store = testStore()
    await renderPage(<TourOverlay />, { store })

    expect(await screen.findByRole('dialog', { name: 'Rundgang' })).toBeTruthy()
    await bisZumNavigationsschritt(nutzer)
    expect(screen.getByRole('heading', { name: ERSTER.title })).toBeTruthy()
    expect(screen.getByText(`Schritt ${NAVI + 1} von ${TOUR_STEPS.length}`)).toBeTruthy()
    // Anleitung statt Ergebnis, solange die Zielseite nicht offen ist.
    expect(screen.getByText(ERSTER.action)).toBeTruthy()

    // „Überspringen" laesst nicht nur diesen Schritt aus, sondern alles zu dieser Seite.
    await nutzer.click(screen.getByRole('button', { name: 'Überspringen' }))
    await waitFor(() => expect(screen.getByRole('heading', { name: TOUR_STEPS[NAECHSTE_SEITE].title })).toBeTruthy(), { timeout: 8000 })

    await nutzer.click(screen.getByRole('button', { name: 'Beenden' }))

    const admin = store.users.find((u) => u.username === 'admin')!
    await waitFor(() => expect(admin.tourCompletedAt).toBeTruthy())
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Rundgang' })).toBeNull())
  })

  it('ein Navigationsschritt schaltet von allein weiter, sobald seine Seite offen ist', { timeout: 20_000 }, async () => {
    const nutzer = userEvent.setup()
    await renderPage(<TourOverlay />, { route: ERSTER.route })
    await screen.findByRole('dialog', { name: 'Rundgang' })
    await bisZumNavigationsschritt(nutzer)

    // Keinen „Weiter"-Knopf gibt es mehr: Der Rundgang merkt selbst, dass die Seite offen ist.
    expect(screen.queryByRole('button', { name: 'Weiter' })).toBeNull()
    await waitFor(
      () => expect(screen.getByRole('heading', { name: TOUR_STEPS[NAVI + 1].title })).toBeTruthy(),
      { timeout: 8000 },
    )
  })

  // Der Fall braucht Luft: Schritt 1 hat seine Lesezeit, danach kommt erst der Klickschritt.
  it('ein Klickschritt schaltet weiter, sobald das Ziel angeklickt wurde', { timeout: 20_000 }, async () => {
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
    await bisZumNavigationsschritt(nutzer)
    await waitFor(() => expect(screen.getByRole('heading', { name: schritt.title })).toBeTruthy(), { timeout: 8000 })

    await nutzer.click(screen.getByRole('button', { name: schritt.target.text! }))
    await waitFor(
      () => expect(screen.getByRole('heading', { name: TOUR_STEPS[KLICKSCHRITT + 1].title })).toBeTruthy(),
      { timeout: 8000 },
    )
  })

  it('„Fertig" am letzten Schritt merkt den Rundgang ebenfalls am Konto', async () => {
    const nutzer = userEvent.setup()
    const store = testStore()
    await renderPage(<TourOverlay />, { store })
    await screen.findByRole('dialog', { name: 'Rundgang' })

    await bisZumNavigationsschritt(nutzer)
    await bisZumEnde(nutzer)
    expect(screen.getByRole('heading', { name: TOUR_STEPS[TOUR_STEPS.length - 1].title })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Überspringen' })).toBeNull()

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
