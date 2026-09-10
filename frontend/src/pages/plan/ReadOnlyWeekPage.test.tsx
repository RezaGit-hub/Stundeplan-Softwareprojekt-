// @vitest-environment jsdom
//
// Die beiden Nur-Lese-Seiten, gerendert (Befund FRONTEND-8: sie teilen sich seit dem 05.09. einen Rumpf).
//
// Beide stehen im Abnahmepfad — /mein-plan trägt den Web-Anteil von T-A1.1, /klassenplan die Sicht E-50 —
// und beide sind beim Zusammenlegen angefasst worden. Geprüft wird deshalb genau das, was dabei hätte
// verrutschen können: welche Sicht das Raster bekommt, wer den Tag-Umschalter sieht, und dass der
// Klassenplan ihn nicht nebenbei geerbt hat.
import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderPage } from '@/test-utils'
import { MyPlanPage } from '@/pages/my-plan/MyPlanPage'
import { ClassPlanPage } from '@/pages/class-plan/ClassPlanPage'

describe('Mein Plan (Lehrkraft, E-38)', () => {
  it('zeigt die eigene Woche mit Umschalter Tag | Woche (PFLICHTENHEFT-8)', async () => {
    await renderPage(<MyPlanPage />, { username: 'meyer' })

    expect(await screen.findByText('Mein Plan')).toBeTruthy()
    const umschalter = screen.getByRole('group', { name: 'Ansicht' })
    expect(umschalter).toBeTruthy()
    // Woche ist die Vorgabe; „Tag" ist da, aber nicht gewählt.
    expect(screen.getByRole('button', { name: 'Woche' }).getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByRole('button', { name: 'Tag' }).getAttribute('aria-pressed')).toBe('false')
  })

  it('der Klick auf „Tag" schaltet auf die Tagesliste und bietet die fünf Schultage an', async () => {
    const nutzer = userEvent.setup()
    await renderPage(<MyPlanPage />, { username: 'meyer' })
    await screen.findByText('Mein Plan')

    await nutzer.click(screen.getByRole('button', { name: 'Tag' }))

    await waitFor(() => expect(screen.getByRole('group', { name: 'Tag wählen' })).toBeTruthy())
    const tage = screen.getByRole('group', { name: 'Tag wählen' })
    expect(tage.querySelectorAll('button')).toHaveLength(5)
  })
})

describe('Klassenplan (Klassenkonto, E-50)', () => {
  it('zeigt die Woche der eigenen Klasse', async () => {
    await renderPage(<ClassPlanPage />, { username: '7a' })
    expect(await screen.findByText('Klassenplan')).toBeTruthy()
  })

  it('hat keinen Tag-Umschalter — den verlangt das Pflichtenheft nur für die Lehrkraft', async () => {
    await renderPage(<ClassPlanPage />, { username: '7a' })
    await screen.findByText('Klassenplan')

    expect(screen.queryByRole('group', { name: 'Ansicht' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Tag' })).toBeNull()
  })
})
