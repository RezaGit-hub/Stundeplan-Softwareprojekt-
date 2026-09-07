// Geltungsbeginn eines angenommenen Plans: erste Schulwoche und Hinweis „vorheriger Stand".
import { describe, expect, it } from 'vitest'
import { firstWeekMonday, planValidFrom } from './dates'
import { defaultPlanMonday, showsPreviousPlan } from './plan-validity'

describe('firstWeekMonday', () => {
  it('nimmt an Werktagen den Montag derselben Woche', () => {
    expect(firstWeekMonday('2026-09-07')).toBe('2026-09-07') // Mo
    expect(firstWeekMonday('2026-09-09')).toBe('2026-09-07') // Mi
    expect(firstWeekMonday('2026-09-11')).toBe('2026-09-07') // Fr
  })

  it('springt am Wochenende auf den nächsten Montag', () => {
    expect(firstWeekMonday('2026-09-05')).toBe('2026-09-07') // Sa
    expect(firstWeekMonday('2026-09-06')).toBe('2026-09-07') // So
  })
})

describe('planValidFrom', () => {
  it('nimmt den Kalendertag in Berlin, nicht den in UTC', () => {
    expect(planValidFrom('2026-09-05T10:12:00Z')).toBe('2026-09-05')
    expect(planValidFrom('2026-09-05T23:30:00Z')).toBe('2026-09-06')
  })
})

describe('defaultPlanMonday', () => {
  it('zeigt die heutige Woche, solange der Plan schon gilt', () => {
    expect(defaultPlanMonday('2026-09-05', '2026-08-24')).toBe('2026-08-31')
  })

  it('zeigt die erste gültige Woche, wenn der Plan erst später gilt', () => {
    // Annahme am Samstag: die Woche von heute (Mo 31.08.) liegt ganz vor dem Geltungsbeginn.
    expect(defaultPlanMonday('2026-09-05', '2026-09-05')).toBe('2026-09-07')
  })

  it('bleibt bei der heutigen Woche, wenn kein Geltungsbeginn bekannt ist', () => {
    expect(defaultPlanMonday('2026-09-05', null)).toBe('2026-08-31')
  })
})

describe('showsPreviousPlan', () => {
  const base = { monday: '2026-08-31', validFrom: '2026-09-07', weekEntryCount: 0, planLessonCount: 360 }

  it('meldet die Woche vor dem Geltungsbeginn', () => {
    expect(showsPreviousPlan(base)).toBe(true)
  })

  it('schweigt, sobald die Woche Einträge hat (Attrappe zeigt den Plan in jeder Woche)', () => {
    expect(showsPreviousPlan({ ...base, weekEntryCount: 34 })).toBe(false)
  })

  it('schweigt bei leerem Plan — dann ist die leere Woche die Wahrheit', () => {
    expect(showsPreviousPlan({ ...base, planLessonCount: 0 })).toBe(false)
  })

  it('schweigt ohne bekannten Geltungsbeginn', () => {
    expect(showsPreviousPlan({ ...base, validFrom: null })).toBe(false)
  })

  it('schweigt, sobald der Freitag der Woche den Geltungsbeginn erreicht', () => {
    expect(showsPreviousPlan({ ...base, monday: '2026-09-07' })).toBe(false)
    // Geltungsbeginn mitten in der Woche (Mi): die Woche ist teilweise gültig, kein Hinweis.
    expect(showsPreviousPlan({ ...base, monday: '2026-08-31', validFrom: '2026-09-02' })).toBe(false)
  })
})
