// @vitest-environment jsdom
//
// Aktiver Plan: der Seitentitel nennt das gewählte Objekt, sobald nach Klasse, Lehrkraft oder Raum
// gefiltert wird („Aktiver Plan - 7a"). In der Schulsicht bleibt es beim schlichten „Aktiver Plan".
import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderPage, testStore } from '@/test-utils'
import { PlanPage } from '@/pages/plan/PlanPage'
import { newTimetable, nextId, type MockTimetable, type Store } from '@/mock/store'
import { addDaysIso, formatDayDate } from '@/lib/dates'
import { nowIso, today } from '@/lib/today'

describe('Aktiver Plan – Seitentitel', () => {
  it('zeigt in der Schulsicht nur „Aktiver Plan"', async () => {
    await renderPage(<PlanPage />, { route: '/plan' })
    expect(await screen.findByRole('heading', { level: 1, name: 'Aktiver Plan' })).toBeTruthy()
  })

  it('hängt in der Klassensicht die Klasse an den Titel', async () => {
    await renderPage(<PlanPage />, { route: '/plan?sicht=CLASS' })
    expect(await screen.findByRole('heading', { level: 1, name: 'Aktiver Plan - 7a' })).toBeTruthy()
  })

  it('hängt in der Lehrkraftsicht Name und Kürzel an den Titel', async () => {
    await renderPage(<PlanPage />, { route: '/plan?sicht=TEACHER' })
    expect(await screen.findByRole('heading', { level: 1, name: /^Aktiver Plan - .+ \(\w+\)$/ })).toBeTruthy()
  })
})

// ------------------------------------------------------------------ Gültigkeitszeitraum und Planwahl (#115)
//
// Seit 1.4.0 darf es mehrere angenommene Pläne mit anschließenden Zeiträumen geben. Die Plansicht muss
// deshalb zwei Dinge können: den Zeitraum des gezeigten Plans nennen (sonst rät die Planung, welcher Stand
// vor ihr liegt) und einen anderen Plan wählen lassen. Die Wahl steht als `plan` in der URL — der
// Auswahlknopf von Radix lässt sich in jsdom nicht mit Zeigergesten öffnen, geprüft wird deshalb die
// Verdrahtung über die Adresse.
describe('Aktiver Plan – Gültigkeitszeitraum (#115)', () => {
  const heute = today()
  const endeAlt = addDaysIso(heute, 29)
  const beginnNeu = addDaysIso(heute, 30)

  /** Zwei angenommene Pläne, deren Zeiträume aneinander anschließen. */
  function storeMitZweiPlaenen(): { s: Store; neu: MockTimetable } {
    const s = testStore()
    const alt = s.timetables.find((x) => x.status === 'ACTIVE')!
    alt.validUntil = endeAlt
    const neu = newTimetable('ACTIVE', nowIso(), { name: 'Zweiter Plan' })
    neu.acceptedAt = nowIso()
    neu.validFrom = beginnNeu
    neu.lessons = alt.lessons.map((l) => ({ ...l, id: nextId(), timetableId: neu.id }))
    s.timetables.push(neu)
    return { s, neu }
  }

  it('nennt bei einem einzigen Plan nur den Beginn und bietet keine Wahl an', async () => {
    await renderPage(<PlanPage />, { route: '/plan' })
    expect(await screen.findByRole('heading', { level: 1, name: 'Aktiver Plan' })).toBeTruthy()
    expect(screen.getByText(/gilt ab /)).toBeTruthy()
    expect(screen.queryByLabelText('Plan')).toBeNull()
  })

  it('nennt bei einem Plan mit Ende beide Ränder und lässt den Plan wählen', async () => {
    const { s } = storeMitZweiPlaenen()
    await renderPage(<PlanPage />, { route: '/plan', store: s })

    expect(await screen.findByRole('heading', { level: 1, name: 'Aktiver Plan' })).toBeTruthy()
    expect(screen.getByText(new RegExp(`gilt .* – ${formatDayDate(endeAlt)}`))).toBeTruthy()
    expect(screen.getByLabelText('Plan')).toBeTruthy()
  })

  it('zeigt über `plan` in der Adresse den künftigen Plan mit seinem Beginn', async () => {
    const { s, neu } = storeMitZweiPlaenen()
    await renderPage(<PlanPage />, { route: `/plan?plan=${neu.id}`, store: s })

    expect(await screen.findByText(new RegExp(`7 Stunden · gilt ab ${formatDayDate(beginnNeu)}$`))).toBeTruthy()
  })
})
