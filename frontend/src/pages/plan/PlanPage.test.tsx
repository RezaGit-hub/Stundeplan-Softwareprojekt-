// @vitest-environment jsdom
//
// Aktiver Plan: der Seitentitel nennt das gewählte Objekt, sobald nach Klasse, Lehrkraft oder Raum
// gefiltert wird („Aktiver Plan - 7a"). In der Schulsicht bleibt es beim schlichten „Aktiver Plan".
import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderPage } from '@/test-utils'
import { PlanPage } from '@/pages/plan/PlanPage'

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
