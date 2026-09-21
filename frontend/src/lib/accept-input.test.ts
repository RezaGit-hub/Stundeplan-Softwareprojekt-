// Die Regel hinter dem Annehmen-Dialog (#116): welcher Körper geht an den Server, und wann ist der
// Zeitraum verdreht? Der Dialog selbst ist gerendert getestet (AcceptDialog.test.tsx); hier stehen die
// Fälle, die dort nur mühsam zu erreichen wären.
import { describe, expect, it } from 'vitest'
import { acceptBody, acceptRangeError, type AcceptInput } from './accept-input'

/** Felder wie frisch geöffnet: beide Datumsangaben auf demselben Tag, kein Enddatum. */
function felder(over: Partial<AcceptInput> = {}): AcceptInput {
  return { way: 'now', validFrom: '2026-09-16', useValidUntil: false, validUntil: '2026-09-16', ...over }
}

describe('acceptBody', () => {
  it('schickt bei „Jetzt aktivieren" kein Datum mit — der Server rechnet ab heute', () => {
    expect(acceptBody(felder({ way: 'now', validFrom: '2026-10-01' }))).toEqual({})
  })

  it('schickt bei „Ab Datum" den Beginn', () => {
    expect(acceptBody(felder({ way: 'from', validFrom: '2026-10-01' }))).toEqual({ validFrom: '2026-10-01' })
  })

  it('schickt mit Häkchen auch das Ende', () => {
    expect(acceptBody(felder({ way: 'from', validFrom: '2026-10-01', useValidUntil: true, validUntil: '2027-01-31' })))
      .toEqual({ validFrom: '2026-10-01', validUntil: '2027-01-31' })
  })

  it('lässt das Ende weg, solange das Häkchen nicht steht', () => {
    expect(acceptBody(felder({ way: 'from', validFrom: '2026-10-01', validUntil: '2027-01-31' })))
      .toEqual({ validFrom: '2026-10-01' })
  })

  it('schickt für die Ablage nichts — dieser Weg nimmt gar nicht an', () => {
    expect(acceptBody(felder({ way: 'park', validFrom: '2026-10-01', useValidUntil: true }))).toEqual({})
  })
})

describe('acceptRangeError', () => {
  it('meldet den verdrehten Zeitraum', () => {
    expect(acceptRangeError(felder({ way: 'from', validFrom: '2026-10-01', useValidUntil: true, validUntil: '2026-09-30' })))
      .toBe('Das Ende darf nicht vor dem Beginn liegen.')
  })

  it('lässt Beginn gleich Ende zu — ein Plan darf einen Tag gelten', () => {
    expect(acceptRangeError(felder({ way: 'from', validFrom: '2026-10-01', useValidUntil: true, validUntil: '2026-10-01' })))
      .toBeUndefined()
  })

  it('schweigt ohne Enddatum und auf den anderen Wegen', () => {
    expect(acceptRangeError(felder({ way: 'from', validFrom: '2026-10-01', validUntil: '2026-09-30' }))).toBeUndefined()
    expect(acceptRangeError(felder({ way: 'now', useValidUntil: true, validFrom: '2026-10-01', validUntil: '2026-09-30' }))).toBeUndefined()
    expect(acceptRangeError(felder({ way: 'park', useValidUntil: true, validFrom: '2026-10-01', validUntil: '2026-09-30' }))).toBeUndefined()
  })
})
