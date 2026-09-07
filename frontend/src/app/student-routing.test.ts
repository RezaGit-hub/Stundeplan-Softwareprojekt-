// Schülersicht, Wegweiser (E-50): ein Klassenkonto landet nach dem Anmelden auf /klassenplan, sieht in der
// Seitenleiste nur diese eine Seite und wird von jeder anderen Route dorthin zurückgeschickt.
//
// Geprüft werden die reinen Funktionen aus nav.ts — genau die, die LoginPage (targetFor), RequireRole
// (falsche Rolle → homeFor) und AppSidebar/CommandPalette (navFor) benutzen. Kein DOM nötig: die Tests laufen
// in der Node-Umgebung wie alle anderen (vitest.config.ts).
import { describe, expect, it } from 'vitest'
import { findNav, homeFor, navFor, PLANNING_NAV, ROLE_LABEL, STUDENT_NAV, TEACHER_NAV } from './nav'

describe('Startseite je Rolle', () => {
  it('ein Klassenkonto startet auf /klassenplan', () => {
    expect(homeFor('STUDENT')).toBe('/klassenplan')
  })

  it('Lehrkraft und Planung bleiben unverändert', () => {
    expect(homeFor('TEACHER')).toBe('/mein-plan')
    expect(homeFor('PLANNING')).toBe('/')
    expect(homeFor(null)).toBe('/')
  })
})

describe('Seitenleiste je Rolle', () => {
  it('ein Klassenkonto sieht genau einen Eintrag: Klassenplan', () => {
    const items = navFor('STUDENT', false)
    expect(items).toHaveLength(1)
    expect(items[0].label).toBe('Klassenplan')
    expect(items[0].to).toBe('/klassenplan')
    expect(items).toEqual(STUDENT_NAV)
  })

  it('die Einträge der Planung tauchen beim Klassenkonto nirgends auf', () => {
    const ziele = new Set(navFor('STUDENT', true).map((n) => n.to))
    for (const n of PLANNING_NAV) expect(ziele.has(n.to)).toBe(false)
    for (const n of TEACHER_NAV) expect(ziele.has(n.to)).toBe(false)
  })

  it('die Brotkrume findet den Klassenplan nur für das Klassenkonto', () => {
    expect(findNav('/klassenplan', 'STUDENT')?.label).toBe('Klassenplan')
    expect(findNav('/klassenplan', 'PLANNING')).toBeUndefined()
    // Umgekehrt: eine Planungsseite gehört nicht zur Schülersicht — RequireRole leitet sie ohnehin um.
    expect(findNav('/grunddaten/lehrkraefte', 'STUDENT')).toBeUndefined()
  })

  it('die Rolle heißt in der Oberfläche „Klasse“', () => {
    expect(ROLE_LABEL.STUDENT).toBe('Klasse')
  })
})
