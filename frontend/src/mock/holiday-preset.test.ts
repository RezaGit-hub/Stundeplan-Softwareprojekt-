// Mitgelieferte Ferien- und Feiertagsvorlage (E-51): Schuljahr aus dem Datum, Laden aus der Attrappe.
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/types'
import { schoolYearOf } from '@/lib/dates'
import { presetToast } from '@/components/masterdata/helpers'
import { emptyStore } from './store'
import { loadSmall } from './data-small'
import { createMockApiForTests } from './mock-api'
import { HOLIDAY_PRESETS } from './holiday-presets'

describe('schoolYearOf', () => {
  it('schneidet am 1. August', () => {
    expect(schoolYearOf('2026-08-01')).toBe('2026/27')
    expect(schoolYearOf('2026-09-05')).toBe('2026/27')
    expect(schoolYearOf('2027-01-01')).toBe('2026/27')
    expect(schoolYearOf('2027-07-31')).toBe('2026/27')
    expect(schoolYearOf('2027-08-01')).toBe('2027/28')
  })

  it('nennt das Schuljahr in der Schreibweise der KMK', () => {
    expect(schoolYearOf('2028-12-24')).toBe('2028/29')
    expect(schoolYearOf('2030-03-01')).toBe('2029/30')
  })
})

describe('presetToast', () => {
  it('zählt übernommen und schon vorhanden', () => {
    expect(presetToast(14, 0)).toBe('14 übernommen')
    expect(presetToast(3, 11)).toBe('3 übernommen, 11 schon vorhanden')
    expect(presetToast(0, 14)).toBe('Nichts übernommen — 14 Zeiträume standen schon')
    expect(presetToast(0, 1)).toBe('Nichts übernommen — 1 Zeitraum stand schon')
  })
})

describe('Vorlage in der Attrappe', () => {
  it('kennt alle 16 Bundesländer und beide Schuljahre', () => {
    expect(Object.keys(HOLIDAY_PRESETS)).toHaveLength(16)
    for (const [state, jahre] of Object.entries(HOLIDAY_PRESETS)) {
      expect(Object.keys(jahre), state).toEqual(['2026/27', '2027/28'])
      for (const [jahr, zeilen] of Object.entries(jahre)) {
        expect(zeilen.length, `${state} ${jahr}`).toBeGreaterThanOrEqual(12)
        expect(zeilen.filter((z) => z.kind === 'VACATION').length, `${state} ${jahr}`).toBeGreaterThanOrEqual(3)
      }
    }
  })

  it('enthält keinen Feiertag, der nur in einem Teilgebiet gilt (R1)', () => {
    const alle = Object.values(HOLIDAY_PRESETS).flatMap((j) => Object.values(j).flat())
    expect(alle.filter((z) => z.label.includes('Friedensfest'))).toHaveLength(0)
    expect(alle.filter((z) => z.label.includes('nur kath'))).toHaveLength(0)
    // Mariä Himmelfahrt gilt landesweit nur im Saarland; in Bayern nur in katholischen Gemeinden.
    expect(HOLIDAY_PRESETS.SL['2026/27'].some((z) => z.label === 'Mariä Himmelfahrt')).toBe(true)
    expect(HOLIDAY_PRESETS.BY['2026/27'].some((z) => z.label.startsWith('Mariä'))).toBe(false)
  })

  it('führt Sommerferien im Schuljahr, in dem sie beginnen (R4)', () => {
    // BW: 29.07.2027 liegt zwei Tage vor dem Schuljahreswechsel — der Block bleibt bei 2026/27.
    const bw = HOLIDAY_PRESETS.BW['2026/27'].find((z) => z.label === 'Sommerferien')
    expect(bw).toMatchObject({ from: '2027-07-29', to: '2027-09-11' })
  })
})

describe('POST /api/holidays/preset (Attrappe)', () => {
  const api = () => {
    const s = emptyStore()
    loadSmall(s)
    s.holidays = []
    return { s, api: createMockApiForTests(s) }
  }

  it('nennt die Schuljahre des eingestellten Bundeslands', async () => {
    const { s, api: a } = api()
    expect(await a.listHolidayPresetYears()).toEqual({ state: s.config.state, schoolYears: ['2026/27', '2027/28'] })
  })

  it('übernimmt die Zeiträume und legt beim zweiten Aufruf nichts mehr an', async () => {
    const { s, api: a } = api()
    const erwartet = HOLIDAY_PRESETS[s.config.state]['2026/27'].length

    const erster = await a.loadHolidayPreset({ schoolYear: '2026/27' })
    expect(erster).toEqual({ added: erwartet, skipped: 0, state: s.config.state, schoolYear: '2026/27' })
    expect(await a.listHolidays()).toHaveLength(erwartet)

    const zweiter = await a.loadHolidayPreset({ schoolYear: '2026/27' })
    expect(zweiter.added).toBe(0)
    expect(zweiter.skipped).toBe(erwartet)
    expect(await a.listHolidays()).toHaveLength(erwartet)
  })

  it('folgt dem Bundesland aus der Systemkonfiguration', async () => {
    const { s, api: a } = api()
    await a.putConfig({ ...s.config, state: 'BY' })
    await a.loadHolidayPreset({ schoolYear: '2026/27' })
    const namen = (await a.listHolidays()).map((h) => h.name)
    // Bayern hat als einziges Land Ferien um Aschermittwoch.
    expect(namen.some((n) => n.startsWith('Winterferien'))).toBe(true)
    expect(namen).not.toContain('Augsburger Friedensfest')
  })

  it('meldet ein unbekanntes Schuljahr als 409 preset-missing', async () => {
    const { api: a } = api()
    await expect(a.loadHolidayPreset({ schoolYear: '2030/31' })).rejects.toSatisfy(
      (err) => err instanceof ApiError && err.problem.status === 409
        && err.problem.type === 'urn:pushnpull:problem:preset-missing')
    expect(await a.listHolidays()).toHaveLength(0)
  })
})
