// @vitest-environment jsdom
//
// Der Rundgang (#139, T2-3.1): erscheint für die Planung genau dann, wenn das Konto ihn noch nie beendet hat
// (`tourCompletedAt` null), und „Überspringen" wie „Fertig" merken das am Konto — danach ist er weg.
// Geprüft gegen die Attrappe mit eigenem Store, wie die anderen Seitentests (test-utils).
import { describe, expect, it } from 'vitest'
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderPage, testStore } from '@/test-utils'
import { TourOverlay } from './TourOverlay'
import { TOUR_STEPS } from './tour-steps'

/** Die Attrappe antwortet ohne Kunstlatenz; ein kurzer Tick reicht, damit /me durch ist. */
const abwarten = () => act(() => new Promise<void>((r) => setTimeout(r, 30)))

describe('TourOverlay (#139)', () => {
  it('erscheint, solange tourCompletedAt null ist; „Überspringen" merkt es am Konto und schließt ihn', async () => {
    const nutzer = userEvent.setup()
    const store = testStore()
    await renderPage(<TourOverlay />, { store })

    expect(await screen.findByRole('dialog', { name: 'Rundgang' })).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Grunddaten' })).toBeTruthy()
    expect(screen.getByText('Schritt 1 von 6')).toBeTruthy()

    await nutzer.click(screen.getByRole('button', { name: 'Weiter' }))
    expect(screen.getByRole('heading', { name: 'Stundenraster' })).toBeTruthy()

    await nutzer.click(screen.getByRole('button', { name: 'Überspringen' }))

    const admin = store.users.find((u) => u.username === 'admin')!
    await waitFor(() => expect(admin.tourCompletedAt).toBeTruthy())
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Rundgang' })).toBeNull())
  })

  it('„Fertig" am letzten Schritt merkt den Rundgang ebenfalls am Konto', async () => {
    const nutzer = userEvent.setup()
    const store = testStore()
    await renderPage(<TourOverlay />, { store })
    await screen.findByRole('dialog', { name: 'Rundgang' })

    for (let i = 1; i < TOUR_STEPS.length; i++) await nutzer.click(screen.getByRole('button', { name: 'Weiter' }))
    expect(screen.getByRole('heading', { name: 'Annehmen und Vertretung' })).toBeTruthy()
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
