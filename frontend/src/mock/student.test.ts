// Schülersicht gegen die Attrappe (E-50), so wie die Seite /klassenplan sie aufruft.
// Geprüft wird dasselbe wie im Backend-Test ClassWeekIT: nur die eigene Klasse, nie ein Anlass, Kürzelwechsel
// statt Klarnamen, 403 auf allem anderen — und die Klassenkonten aus einem Knopf, idempotent.
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/types'
import { startOfIsoWeek } from '@/lib/dates'
import { today } from '@/lib/today'
import { emptyStore, type Store } from './store'
import { loadSmall } from './data-small'
import { createMockApiForTests } from './mock-api'

function store(): Store {
  const s = emptyStore()
  loadSmall(s)
  return s
}
async function fails<T>(p: Promise<T>): Promise<ApiError> {
  try { await p } catch (e) { if (e instanceof ApiError) return e; throw e }
  throw new Error('kein Fehler')
}
const MONTAG = startOfIsoWeek(today())

describe('Klassenplan des Klassenkontos', () => {
  it('das Konto 7a sieht nur Stunden der 7a', async () => {
    const a = createMockApiForTests(store(), '7a')
    const week = await a.getMyClassWeek({ date: MONTAG })
    const entries = week.days.flatMap((d) => d.entries)
    expect(entries.length).toBeGreaterThan(0)
    expect(entries.every((e) => e.classShort === '7a')).toBe(true)
  })

  it('reasons bleibt leer, auch wenn ein Ausfall mit Anlass eingetragen ist', async () => {
    const s = store()
    const admin = createMockApiForTests(s, 'admin')
    await admin.createEvent({ kind: 'TEACHER_ABSENCE', teacherId: 1, dateFrom: MONTAG, dateTo: MONTAG, reason: 'Krankmeldung' })

    // Die Planung sieht den Anlass …
    const fuerDiePlanung = await admin.getWeek({ date: MONTAG, view: 'CLASS', id: 1 })
    expect(fuerDiePlanung.days.flatMap((d) => d.entries).some((e) => e.reasons.length > 0)).toBe(true)

    // … die Klasse sieht nur, DASS eine Vertretung offen ist.
    const klasse = createMockApiForTests(s, '7a')
    const entries = (await klasse.getMyClassWeek({ date: MONTAG })).days.flatMap((d) => d.entries)
    expect(entries.every((e) => e.reasons.length === 0)).toBe(true)
    expect(entries.some((e) => e.kind === 'NEEDS_SUBSTITUTION')).toBe(true)
  })

  it('eine Vertretung zeigt den Kürzelwechsel, aber keine Kennung der alten Lehrkraft', async () => {
    const s = store()
    const admin = createMockApiForTests(s, 'admin')
    const event = await admin.createEvent({ kind: 'TEACHER_ABSENCE', teacherId: 1, dateFrom: MONTAG, dateTo: MONTAG, reason: 'Krankmeldung' })
    const mo1 = (await admin.getWeek({ date: MONTAG, view: 'CLASS', id: 1 })).days[0].entries.find((e) => e.position === 1)!
    await admin.createException({ date: MONTAG, lessonId: mo1.lessonId!, kind: 'SUBSTITUTION', substituteTeacherId: 2, eventIds: [event.id] })

    const klasse = createMockApiForTests(s, '7a')
    const vertreten = (await klasse.getMyClassWeek({ date: MONTAG })).days[0].entries.find((e) => e.kind === 'SUBSTITUTED')!
    expect(vertreten.teacherShort).toBe('SCZ')
    expect(vertreten.original?.teacherShort).toBe('MEY')
    expect(vertreten.original?.teacherId).toBeUndefined()
    expect(vertreten.original?.roomId).toBeUndefined()
    expect(vertreten.reasons).toEqual([])
  })

  it('alles außer der eigenen Klassenwoche ist 403', async () => {
    const a = createMockApiForTests(store(), '7a')
    expect((await fails(a.getTimetable())).status).toBe(403)
    expect((await fails(a.getWeek({ date: MONTAG, view: 'CLASS', id: 2 }))).status).toBe(403)
    expect((await fails(a.listTeachers())).status).toBe(403)
    expect((await fails(a.listClasses())).status).toBe(403)
    expect((await fails(a.listUsers())).status).toBe(403)
    expect((await fails(a.getMyWeek({ date: MONTAG }))).status).toBe(403)
    expect((await fails(a.changeMyPassword({ oldPassword: '7a', newPassword: 'neues-passwort' }))).status).toBe(403)
  })

  it('das Stundenraster und die eigene Identität bleiben erlaubt', async () => {
    const a = createMockApiForTests(store(), '7a')
    expect((await a.getTimegrid()).slots.length).toBeGreaterThan(0)
    const me = await a.me()
    expect(me.role).toBe('STUDENT')
    expect(me.displayName).toBe('Klasse 7a')
    expect(me.teacherId).toBeNull()
  })

  it('ein Konto ohne Klasse bekommt 404', async () => {
    const a = createMockApiForTests(store(), 'meyer')
    expect((await fails(a.getMyClassWeek({ date: MONTAG }))).status).toBe(404)
  })
})

describe('Konten für alle Klassen', () => {
  it('legt nur für Klassen ohne Konto an und ist beim zweiten Mal leer', async () => {
    const s = store()
    const a = createMockApiForTests(s, 'admin')

    const ersterLauf = await a.createClassAccounts()
    // Die 7a hat im Testbestand bereits ein Konto; 8b und 9c bekommen eines.
    expect(ersterLauf.map((r) => r.classShort).sort()).toEqual(['8b', '9c'])
    expect(ersterLauf.every((r) => r.username === r.classShort.toLowerCase())).toBe(true)
    expect(ersterLauf.every((r) => r.password.length === 10)).toBe(true)

    expect(await a.createClassAccounts()).toEqual([])
  })

  it('mit dem ausgegebenen Passwort meldet sich das Klassenkonto an', async () => {
    const s = store()
    const a = createMockApiForTests(s, 'admin')
    const zeile = (await a.createClassAccounts()).find((r) => r.classShort === '8b')!

    const angemeldet = await a.login({ username: zeile.username, password: zeile.password })
    expect(angemeldet.role).toBe('STUDENT')
    expect(angemeldet.displayName).toBe('Klasse 8b')
  })

  it('die Kontenliste nennt Klasse und Kürzel', async () => {
    const s = store()
    const a = createMockApiForTests(s, 'admin')
    await a.createClassAccounts()
    const konten = await a.listUsers()
    const achtB = konten.find((u) => u.username === '8b')!
    expect(achtB.role).toBe('STUDENT')
    expect(achtB.classShort).toBe('8b')
    expect(achtB.teacherId).toBeNull()
  })
})
