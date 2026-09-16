// W2: Anwendungsfälle W-1 und W-2 gegen die Attrappe, so wie die Seiten sie aufrufen (npm test).
// T-W1.1 (Schulz mit fünf Werten), T-W1.3 (Krause löschen / Meyer 409 → deaktivieren), T-W1.4, T-W1.5 (Warnungen,
// Speichern bleibt möglich), Testdurchlauf W-1 Schritt 8 (zwei Feldfehler) und 9 (Mathematik löschen → 409), T-W2.1.
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/types'
import { emptyStore } from '@/mock/store'
import { loadSmall } from '@/mock/data-small'
import { createMockApiForTests } from '@/mock/mock-api'
import { RULE_PRESETS, STATE_NAMES } from '@/mock/rule-presets'
import { STATE_LABEL } from './helpers'

function api() {
  const s = emptyStore()
  loadSmall(s)
  return createMockApiForTests(s, 'admin')
}
async function fails<T>(p: Promise<T>): Promise<ApiError> {
  try { await p } catch (e) { if (e instanceof ApiError) return e; throw e }
  throw new Error('kein Fehler')
}

describe('W-1 Grunddaten (W2-Seiten)', () => {
  it('T-W1.1: Schulz trägt Deputat 2, Fach Mathematik, Fr 5./6. gesperrt und ist nur in 9c', async () => {
    const a = api()
    const schulz = (await a.listTeachers()).find((t) => t.shortName === 'SCZ')!
    const grid = await a.getTimegrid()
    const fr = grid.slots.filter((s) => s.day === 'FR' && !s.isBreak).sort((x, y) => x.position - y.position)
    expect(schulz.weeklyHoursQuota).toBe(2)
    expect(schulz.subjectIds).toEqual([1])
    expect(schulz.blockedTimeslotIds.sort()).toEqual([fr[4].id, fr[5].id].sort())
    const classes = await a.listClasses()
    expect(classes.filter((c) => c.subjects.some((r) => r.teacherId === schulz.id)).map((c) => c.name)).toEqual(['9c'])
  })

  it('Lehrkraft anlegen: leere Pflichtfelder → 400 mit Feldfehlern name/shortName', async () => {
    const err = await fails(api().createTeacher({ name: '', shortName: '', weeklyHoursQuota: 0, subjectIds: [], preferredTimeslotIds: [], blockedTimeslotIds: [], active: true }))
    expect(err.status).toBe(400)
    expect(err.problem.errors?.map((e) => e.field).sort()).toEqual(['name', 'shortName', 'weeklyHoursQuota'])
  })

  it('T-W1.3: Krause anlegen und löschen; Meyer löschen → 409 in-use mit Hinweis, deaktivieren geht', async () => {
    const a = api()
    const krause = await a.createTeacher({ name: 'Krause', shortName: 'KRA', weeklyHoursQuota: 20, subjectIds: [2], preferredTimeslotIds: [], blockedTimeslotIds: [], active: true })
    await a.deleteTeacher(krause.id)
    expect((await a.listTeachers()).some((t) => t.id === krause.id)).toBe(false)

    const err = await fails(a.deleteTeacher(1))
    expect(err.status).toBe(409)
    expect(err.problem.type).toBe('urn:pushnpull:problem:in-use')
    expect(err.problem.title).toBe('Lehrkraft wird noch verwendet')
    expect(err.problem.detail).toContain('Deaktivieren statt löschen')

    const meyer = await a.getTeacher(1)
    const off = await a.updateTeacher(1, { name: meyer.name, shortName: meyer.shortName, weeklyHoursQuota: meyer.weeklyHoursQuota, subjectIds: meyer.subjectIds, preferredTimeslotIds: meyer.preferredTimeslotIds, blockedTimeslotIds: meyer.blockedTimeslotIds, active: false })
    expect(off.active).toBe(false)
    // Schritt 5: in der Lehrkraft-Auswahl der Klasse fehlt Meyer (Seite filtert t.active)
    expect((await a.listTeachers()).filter((t) => t.active).map((t) => t.shortName)).toEqual(['SCZ', 'WEB'])
  })

  it('T-W1.4: 9c Mathematik auf Weber → Warnung SUBJECT_NOT_TAUGHT, Speichern bleibt möglich', async () => {
    const a = api()
    const c = await a.replaceClassSubjects(3, [{ subjectId: 1, weeklyHours: 1, teacherId: 3 }])
    expect(c.subjects[0].teacherShort).toBe('WEB')
    expect(c.warnings.map((w) => w.code)).toContain('SUBJECT_NOT_TAUGHT')
    expect(c.warnings.find((w) => w.code === 'SUBJECT_NOT_TAUGHT')?.message).toBe('Weber unterrichtet das Fach Mathematik nicht.')
    const back = await a.replaceClassSubjects(3, [{ subjectId: 1, weeklyHours: 1, teacherId: 2 }])
    expect(back.warnings).toHaveLength(0)
  })

  it('T-W1.5: 7a Mathematik auf Schulz → QUOTA_EXCEEDED „3 Wochenstunden über dem Deputat von 2"', async () => {
    const a = api()
    const c7a = await a.getClass(1)
    const rows = c7a.subjects.map((r) => ({ subjectId: r.subjectId, weeklyHours: r.weeklyHours, teacherId: r.subjectId === 1 ? 2 : r.teacherId }))
    const c = await a.replaceClassSubjects(1, rows)
    const w = c.warnings.find((x) => x.code === 'QUOTA_EXCEEDED')
    expect(w?.message).toBe('Schulz ist mit 3 Wochenstunden über dem Deputat von 2.')
    expect(w?.teacherId).toBe(2)
    expect((await a.getTeacher(2)).plannedHours).toBe(3)
  })

  it('W-1 Schritt 8: Zeile Deutsch ohne Stunden und Lehrkraft → zwei Feldfehler an der Zeile', async () => {
    const err = await fails(api().replaceClassSubjects(3, [{ subjectId: 1, weeklyHours: 1, teacherId: 2 }, { subjectId: 2, weeklyHours: null, teacherId: null }]))
    expect(err.status).toBe(400)
    expect(err.problem.errors).toEqual([
      { field: 'subjects[1].weeklyHours', message: 'Wochenstunden fehlen' },
      { field: 'subjects[1].teacherId', message: 'Lehrkraft fehlt' },
    ])
  })

  it('W-1 Schritt 9: Mathematik löschen → 409 mit Klassenliste', async () => {
    const err = await fails(api().deleteSubject(1))
    expect(err.status).toBe(409)
    expect(err.problem.detail).toBe('Mathematik wird in 7a, 8b, 9c unterrichtet.')
  })

  it('Stundenraster: Vorlage speichern ist idempotent; belegtes Zeitfenster entfernen → 409', async () => {
    const a = api()
    const grid = await a.getTimegrid()
    const same = await a.putTimegrid({ slots: grid.slots.map((s) => ({ id: s.id, day: s.day, position: s.position, startTime: s.startTime, endTime: s.endTime, isBreak: s.isBreak })) })
    expect(same.slots).toHaveLength(grid.slots.length)
    const withoutMonday = grid.slots.filter((s) => s.day !== 'MO')
    const err = await fails(a.putTimegrid({ slots: withoutMonday }))
    expect(err.status).toBe(409)
    expect(err.problem.type).toBe('urn:pushnpull:problem:in-use')
  })
})

describe('W-2 Regelwerk (W2-Seite)', () => {
  it('T-W2.1: Wert 0 → 400 am Feld value, 6 bleibt; Wert 3 → gespeichert', async () => {
    const a = api()
    const rule = (await a.listRules()).find((r) => r.type === 'MAX_LESSONS_PER_DAY' && r.scope === 'CLASS')!
    const err = await fails(a.updateRule(rule.id, { value: 0, severity: rule.severity, weight: rule.weight, active: rule.active }))
    expect(err.status).toBe(400)
    expect(err.problem.errors).toEqual([{ field: 'value', message: 'muss mindestens 1 sein' }])
    expect((await a.listRules()).find((r) => r.id === rule.id)?.value).toBe(6)
    const saved = await a.updateRule(rule.id, { value: 3, severity: rule.severity, weight: rule.weight, active: rule.active })
    expect(saved.value).toBe(3)
  })

  it('Beispielsatz laden setzt zurück; Jahrgangsregel anlegen und löschen; schulweite Regel nicht löschbar', async () => {
    const a = api()
    const rule = (await a.listRules()).find((r) => r.type === 'MAX_LESSONS_PER_DAY' && r.scope === 'CLASS')!
    await a.updateRule(rule.id, { value: 3, severity: 'HARD', weight: 1, active: true })
    const reset = await a.resetRules()
    expect(reset.find((r) => r.id === rule.id)?.value).toBe(6)
    const grade = await a.createRule({ type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: 5, value: 5, severity: 'SOFT', weight: 1, active: true })
    expect(grade.grade).toBe(5)
    expect(grade.label).toBe('Maximale Stunden pro Tag')
    await a.deleteRule(grade.id)
    const err = await fails(a.deleteRule(rule.id))
    expect(err.status).toBe(409)
  })

  it('Beispielsatz lädt die Werte des eingestellten Bundeslands (SH → SH, BY → BY mit Jahrgangszeilen)', async () => {
    const a = api()
    const werte = (rules: { type: string; scope: string | null; grade: number | null; value: number }[]) =>
      rules.map((r) => `${r.type}|${r.scope}|${r.grade}|${r.value}`).sort()
    const ausPreset = (state: string) => werte(RULE_PRESETS[state])

    // Vorbelegung ist Schleswig-Holstein (E-37) — ohne Jahrgangszeilen, weil SH nur Blocksummen kennt.
    const config = await a.getConfig()
    expect(config.state).toBe('SH')
    expect(werte(await a.resetRules())).toEqual(ausPreset('SH'))
    expect((await a.listRules()).every((r) => r.grade == null)).toBe(true)

    // Bundesland umstellen → derselbe Knopf lädt die bayerischen Werte samt Jahrgangszeilen 5..10.
    await a.putConfig({ ...config, state: 'BY' })
    const bayern = await a.resetRules()
    expect(werte(bayern)).toEqual(ausPreset('BY'))
    expect(bayern.map((r) => r.grade).filter((g) => g != null).sort((x, y) => x - y)).toEqual([5, 6, 7, 8, 9, 10])
    expect(bayern.find((r) => r.type === 'MAX_LESSONS_PER_WEEK' && r.scope === 'CLASS' && r.grade === 5)?.value).toBe(30)
    expect(bayern.find((r) => r.type === 'MAX_LESSONS_PER_WEEK' && r.scope === 'TEACHER' && r.grade == null)?.value).toBe(23)
    // Beschriftungen kommen weiter aus dem Regelkatalog, nicht aus der Voreinstellung.
    expect(bayern.every((r) => r.label.length > 0)).toBe(true)

    // Alle sechzehn Länder haben einen Satz; ein unbekanntes Kürzel lehnt schon PUT /config ab (state ist im Vertrag ein Enum).
    expect(Object.keys(RULE_PRESETS).sort()).toEqual(Object.keys(STATE_NAMES).sort())
    expect(Object.keys(RULE_PRESETS)).toHaveLength(16)
    // Die Auswahlliste der Oberfläche und die Namen der Recherche dürfen nicht auseinanderlaufen.
    expect(STATE_LABEL).toEqual(STATE_NAMES)
    const err = await fails(a.putConfig({ ...config, state: 'XX' }))
    expect(err.status).toBe(400)
    expect(err.problem.errors?.[0].field).toBe('state')
    // Nach dem Zurücksetzen sind nur die schulweiten Lehrkraft-Zeilen aus (wie Seed V2, VERTRAG R-M2)
    const again = await a.resetRules()
    expect(again.filter((r) => !r.active).every((r) => r.scope === 'TEACHER' && r.grade == null)).toBe(true)
    // Dieselbe Jahrgangszeile zweimal → 409 data-exists (VERTRAG R-M1)
    await a.createRule({ type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: 7, value: 5, severity: 'SOFT', weight: 1, active: true })
    const dup = await fails(a.createRule({ type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: 7, value: 4, severity: 'SOFT', weight: 1, active: true }))
    expect(dup.status).toBe(409)
    expect(dup.problem.type).toBe('urn:pushnpull:problem:data-exists')
  })
})

describe('Einstellungen (W2-Seite)', () => {
  it('Konfiguration speichern; maxSolveSeconds 0 → 400', async () => {
    const a = api()
    const cfg = await a.getConfig()
    const saved = await a.putConfig({ ...cfg, schoolName: 'Gymnasium Testhausen', maxSolveSeconds: 30 })
    expect(saved.schoolName).toBe('Gymnasium Testhausen')
    const err = await fails(a.putConfig({ ...cfg, maxSolveSeconds: 0 }))
    expect(err.problem.errors?.[0].field).toBe('maxSolveSeconds')
  })

  it('Beispieldaten: 409 data-exists ohne replace, 12/24/18 mit replace', async () => {
    const a = api()
    const err = await fails(a.loadSampleData({}))
    expect(err.problem.type).toBe('urn:pushnpull:problem:data-exists')
    expect(await a.loadSampleData({ replace: true })).toEqual({ classes: 12, teachers: 24, rooms: 18 })
  })

  it('Konto für Lehrkraft: TEACHER ohne Lehrkraft → 400; Lehrkraft mit Konto → 400; Passwort setzen', async () => {
    const a = api()
    let err = await fails(a.createUser({ username: 'neu', password: 'geheim-1', role: 'TEACHER' }))
    expect(err.problem.errors?.[0].field).toBe('teacherId')
    err = await fails(a.createUser({ username: 'neu', password: 'geheim-1', role: 'TEACHER', teacherId: 1 }))
    expect(err.problem.errors?.[0].message).toBe('Diese Lehrkraft hat bereits ein Konto')
    // Passwort unter 8 Zeichen → 400 (wie der Server)
    err = await fails(a.createUser({ username: 'neu2', password: 'kurz', role: 'PLANNING' }))
    expect(err.problem.errors?.[0]).toEqual({ field: 'password', message: 'mindestens 8 Zeichen' })
    const krause = await a.createTeacher({ name: 'Krause', shortName: 'KRA', weeklyHoursQuota: 20, subjectIds: [], preferredTimeslotIds: [], blockedTimeslotIds: [], active: true })
    const u = await a.createUser({ username: 'kra', password: 'geheim-1', role: 'TEACHER', teacherId: krause.id })
    await a.setUserPassword(u.id, { password: 'neu12345' })
    expect((await a.listUsers()).some((x) => x.username === 'kra')).toBe(true)
  })

  it('Eigenes Passwort: falsches altes → 400 oldPassword', async () => {
    const err = await fails(api().changeMyPassword({ oldPassword: 'falsch', newPassword: 'neu12345' }))
    expect(err.problem.errors?.[0].field).toBe('oldPassword')
  })

  it('Aktiven Plan leeren: leerer Entwurf + annehmen → Plan ohne Stunden, Grunddaten bleiben', async () => {
    const a = api()
    const entwurf = await a.createDraft({ source: 'EMPTY' })
    const t = await a.acceptDraft(entwurf.id)
    expect(t.lessons).toHaveLength(0)
    expect((await a.listTeachers())).toHaveLength(3)
  })
})
