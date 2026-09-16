// Absicherung des Mock-Stores: Doppelbelegung → Konflikt, Deputat-Überschreitung → Warnung (npm test).
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/types'
import { addDaysIso } from '@/lib/dates'
import { today } from '@/lib/today'
import { emptyStore } from './store'
import { planValidAt } from './logic/week-resolver'
import { loadSmall } from './data-small'
import { loadFull } from './data-full'
import { checkConflicts } from './logic/conflicts'
import { classWarnings } from './logic/warnings'
import { createMockApiForTests } from './mock-api'

describe('Mock-Store (Testdaten SMALL)', () => {
  it('aktiver Plan der Testdaten ist konfliktfrei', () => {
    const s = emptyStore()
    loadSmall(s)
    expect(checkConflicts(s.timetables[0].lessons, s)).toHaveLength(0)
  })

  it('Doppelbelegung eines Raums erzeugt genau einen ROOM_DOUBLE_BOOKED (T-W3.1)', () => {
    const s = emptyStore()
    loadSmall(s)
    const active = s.timetables[0]
    const mo1 = s.timeslots.find((t) => t.day === 'MO' && t.position === 1)!.id
    const lessons = [...active.lessons, { id: 999, timetableId: active.id, classId: 3, subjectId: 1, teacherId: 2, timeslotId: mo1, roomId: 1, pinned: true }]
    const conflicts = checkConflicts(lessons, s)
    expect(conflicts.map((c) => c.type)).toEqual(['ROOM_DOUBLE_BOOKED'])
    expect(conflicts[0].severity).toBe('HARD')
    expect(conflicts[0].message).toBe('Raum R101 ist am Montag in der 1. Stunde doppelt belegt (7a, 9c).')
    expect(conflicts[0].lessonIds).toHaveLength(2)
    expect(conflicts[0].objects).toEqual([{ kind: 'ROOM', id: 1, label: 'R101' }])
  })

  it('Stunde in der Sperrzeit eines Raums erzeugt genau einen ROOM_BLOCKED_TIME (#133)', () => {
    const s = emptyStore()
    loadSmall(s)
    const active = s.timetables[0]
    // 7a Mathematik liegt Montag 1. Stunde in R101 — R101 dort sperren.
    const mo1 = s.timeslots.find((t) => t.day === 'MO' && t.position === 1)!.id
    s.rooms[0].blockedTimeslotIds = [mo1]
    const conflicts = checkConflicts(active.lessons, s)
    expect(conflicts.map((c) => c.type)).toEqual(['ROOM_BLOCKED_TIME'])
    expect(conflicts[0].severity).toBe('HARD')
    expect(conflicts[0].message).toBe('Raum R101 ist am Montag in der 1. Stunde gesperrt.')
    expect(conflicts[0].objects).toEqual([{ kind: 'ROOM', id: 1, label: 'R101' }])
  })

  it('Deputat-Überschreitung erzeugt QUOTA_EXCEEDED (T-W1.5)', () => {
    const s = emptyStore()
    loadSmall(s)
    const c7a = s.classes[0]
    c7a.subjects[0].teacherId = 2 // 7a Mathematik (2 h) auf Schulz: 2 + 1 (9c) = 3 > Deputat 2
    const warnings = classWarnings(c7a, s)
    expect(warnings.some((w) => w.code === 'QUOTA_EXCEEDED' && w.message === 'Schulz ist mit 3 Wochenstunden über dem Deputat von 2.')).toBe(true)
  })

  it('Fach nicht unterrichtet erzeugt SUBJECT_NOT_TAUGHT (T-W1.4)', () => {
    const s = emptyStore()
    loadSmall(s)
    const c9c = s.classes[2]
    c9c.subjects[0].teacherId = 3 // 9c Mathematik auf Weber (Deutsch)
    expect(classWarnings(c9c, s).map((w) => w.message)).toContain('Weber unterrichtet das Fach Mathematik nicht.')
  })

  it('über die Api: Entwurf kopieren, 9c Ma Mo 1. in R101 → ein Raumkonflikt; verschieben löst ihn', async () => {
    const s = emptyStore()
    loadSmall(s)
    const api = createMockApiForTests(s)
    const entwurf = await api.createDraft({ source: 'COPY_ACTIVE' })
    const mo1 = s.timeslots.find((t) => t.day === 'MO' && t.position === 1)!.id
    const mo6 = s.timeslots.find((t) => t.day === 'MO' && t.position === 6)!.id
    const d1 = await api.addDraftLesson(entwurf.id, { classId: 3, subjectId: 1, timeslotId: mo1, roomId: 1 })
    expect(d1.conflicts.map((c) => c.type)).toEqual(['ROOM_DOUBLE_BOOKED'])
    const added = d1.lessons.find((l) => l.classId === 3)!
    const d2 = await api.patchDraftLesson(entwurf.id, added.id, { timeslotId: mo6 })
    expect(d2.conflicts).toHaveLength(0)
    expect(d2.lessons.find((l) => l.id === added.id)?.pinned).toBe(true)
  })

  it('Berechnen platziert alle offenen Stunden ohne Doppelbelegung (T-W8.1)', async () => {
    const s = emptyStore()
    loadSmall(s)
    const api = createMockApiForTests(s)
    const entwurf = await api.createDraft({ source: 'EMPTY' })
    await api.startSolve(entwurf.id)
    await api.cancelSolve(entwurf.id) // platziert sofort statt nach 3 s
    const d = await api.getDraftById(entwurf.id)
    expect(d.status).toBe('EDITING')
    expect(d.openLessons).toHaveLength(0)
    expect(d.conflicts.filter((c) => c.type.endsWith('DOUBLE_BOOKED'))).toHaveLength(0)
    expect(d.lessons.filter((l) => l.timeslotId != null)).toHaveLength(8)
  })

  it('Vertretung: Ausfall Meyer am Montag → vier offene Stunden, Optionen nennen Schulz und R204, nicht Meyer (T-W5.1, T-W6.1)', async () => {
    const s = emptyStore()
    loadSmall(s)
    const api = createMockApiForTests(s)
    const monday = '2026-09-21'
    const ev = await api.createEvent({ kind: 'TEACHER_ABSENCE', teacherId: 1, dateFrom: monday, dateTo: monday, reason: 'Krankmeldung' })
    expect(ev.affected).toHaveLength(4)
    const pending = await api.listPending({ from: monday, to: '2026-09-22' })
    // sortiert nach Datum, dann Position (Spezifikation 7.7)
    expect(pending.map((p) => `${p.classShort}:${p.position}`)).toEqual(['7a:1', '8b:2', '7a:4', '8b:5'])
    const first = pending[0]
    const options = await api.getOptions({ date: monday, lessonId: first.lessonId })
    expect(options.teachers.map((t) => t.shortName)).toContain('SCZ')
    expect(options.teachers.map((t) => t.shortName)).not.toContain('MEY')
    expect(options.rooms.map((r) => r.name)).toContain('R204')
    const x = await api.createException({ date: monday, lessonId: first.lessonId, kind: 'SUBSTITUTION', substituteTeacherId: 2, substituteRoomId: 2 })
    expect(x.conflicts).toHaveLength(0)
    const week = await api.getWeek({ date: monday, view: 'CLASS', id: 1 })
    const entry = week.days[0].entries.find((e) => e.position === 1)!
    expect(entry.kind).toBe('SUBSTITUTED')
    expect(entry.teacherShort).toBe('SCZ')
    expect(entry.original).toMatchObject({ teacherShort: 'MEY', roomShort: 'R101' })
    const meyer = await api.getWeek({ date: monday, view: 'TEACHER', id: 1 })
    expect(meyer.days[0].entries.find((e) => e.position === 1)?.kind).toBe('GIVEN_AWAY')
    const log = await api.getChangelog({ classId: 1 })
    expect(log.total).toBe(1)
    expect(log.items[0]).toMatchObject({ oldAssignment: 'Meyer / R101', newAssignment: 'Schulz / R204', reasons: 'Krankmeldung', username: 'admin' })
  })

  it('Vertretungsoptionen: fachgleiche Lehrkraft zuerst, dann geringere Belastung, dann Name; passender Raum zuerst (W-6)', async () => {
    const s = emptyStore()
    loadSmall(s)
    // vierte Lehrkraft ohne Stunden, Name sortiert hinter Schulz — macht die Belastungsreihenfolge sichtbar
    s.teachers.push({ id: 4, name: 'Zimmer', shortName: 'ZIM', weeklyHoursQuota: 10, subjectIds: [2], preferredTimeslotIds: [], blockedTimeslotIds: [], active: true })
    const api = createMockApiForTests(s)
    const monday = '2026-09-21'
    await api.createEvent({ kind: 'TEACHER_ABSENCE', teacherId: 1, dateFrom: monday, dateTo: monday, reason: 'Krankmeldung' })
    const pending = await api.listPending({ from: monday, to: monday })
    const ma7a = pending.find((p) => p.classShort === '7a' && p.subjectShort === 'Ma')!
    const ph7a = pending.find((p) => p.classShort === '7a' && p.subjectShort === 'Ph')!
    // 7a Ma: Schulz unterrichtet Mathematik → zuerst; Weber und Zimmer danach nach Name
    const o1 = await api.getOptions({ date: monday, lessonId: ma7a.lessonId })
    expect(o1.teachers.map((t) => t.shortName)).toEqual(['SCZ', 'WEB', 'ZIM'])
    expect(o1.teachers.map((t) => t.teachesSubject)).toEqual([true, false, false])
    // 7a Ph (Fachraum Physik): P1 passend zuerst, R101 danach; R204 ist in der Stunde belegt (8b De); Weber ebenfalls
    const o2 = await api.getOptions({ date: monday, lessonId: ph7a.lessonId })
    expect(o2.rooms.map((r) => [r.name, r.matchesRequirement])).toEqual([['P1', true], ['R101', false]])
    expect(o2.teachers.map((t) => t.shortName)).toEqual(['SCZ', 'ZIM'])
    // Ohne Fachlehrkraft entscheidet die Belastung: nach einer Vertretung durch Schulz steht Zimmer (0) vor Schulz (1)
    await api.createException({ date: monday, lessonId: ma7a.lessonId, kind: 'SUBSTITUTION', substituteTeacherId: 2, substituteRoomId: 1 })
    const o3 = await api.getOptions({ date: monday, lessonId: ph7a.lessonId })
    expect(o3.teachers.map((t) => t.shortName)).toEqual(['ZIM', 'SCZ'])
    expect(o3.teachers.some((t) => 'substitutionsThisWeek' in t)).toBe(false)
  })

  it('Rolle Lehrkraft: eigener Plan ja, Grunddaten 403 (E-38)', async () => {
    const s = emptyStore()
    loadSmall(s)
    const meyer = createMockApiForTests(s, 'meyer')
    const week = await meyer.getMyWeek({ date: '2026-09-21' })
    expect(week.days[0].entries).toHaveLength(4)
    await expect(meyer.listTeachers()).rejects.toMatchObject({ status: 403 })
  })
})

describe('Mock-Store (Datensatz FULL)', () => {
  it('12 Klassen / 24 Lehrkräfte / 18 Räume, aktiver Plan ohne Doppelbelegung', () => {
    const s = emptyStore()
    loadFull(s)
    expect([s.classes.length, s.teachers.length, s.rooms.length]).toEqual([12, 24, 18])
    expect(new Set(s.teachers.map((t) => t.shortName)).size).toBe(24)
    const active = s.timetables[0]
    const placed = active.lessons.filter((l) => l.timeslotId != null).length
    expect(placed).toBeGreaterThan(300)
    expect(checkConflicts(active.lessons, s).filter((c) => c.type.endsWith('DOUBLE_BOOKED'))).toHaveLength(0)
  })
})

describe('Grunddaten-Sicherung (Transfer)', () => {
  it('Roundtrip: exportieren und importieren erhält den Bestand, Konto admin bleibt', async () => {
    const s = emptyStore()
    loadSmall(s)
    const api = createMockApiForTests(s, 'admin')

    const doc = await api.exportMasterData()
    expect(doc.version).toBe('1')
    expect(doc.subjects).toHaveLength(3)
    expect(doc.teachers).toHaveLength(3)
    expect(doc.rooms).toHaveLength(3)
    expect(doc.classes).toHaveLength(3)
    expect(doc.timeslots).toHaveLength(35)
    expect(doc.rules).toHaveLength(5)
    expect(doc.holidays).toHaveLength(1)

    const ergebnis = await api.importMasterData({ ...doc, replace: true })
    expect(ergebnis).toMatchObject({ subjects: 3, teachers: 3, rooms: 3, classes: 3, timeslots: 35, rules: 5, holidays: 1 })
    expect(ergebnis.warnings).toEqual([])

    // Konto admin lebt weiter.
    expect(s.users.some((u) => u.username === 'admin' && u.role === 'PLANNING')).toBe(true)
    // Die Lehrkraft-Konten sind weg — wie im Backend (sie verweisen auf geloeschte Stammdaten).
    expect(s.users.filter((u) => u.role === 'TEACHER')).toHaveLength(0)
  })

  it('Ohne replace und bei vorhandenen Daten: 409 data-exists', async () => {
    const s = emptyStore()
    loadSmall(s)
    const api = createMockApiForTests(s, 'admin')
    const doc = await api.exportMasterData()

    await expect(api.importMasterData({ ...doc, replace: false }))
      .rejects.toMatchObject({ status: 409, problem: { type: 'urn:pushnpull:problem:data-exists' } })
  })

  it('Unbekannte Version: 400, Bestand bleibt', async () => {
    const s = emptyStore()
    loadSmall(s)
    const api = createMockApiForTests(s, 'admin')
    const doc = await api.exportMasterData()

    await expect(api.importMasterData({ ...doc, version: '99' as never, replace: true }))
      .rejects.toMatchObject({ status: 400 })
    expect(s.teachers).toHaveLength(3)
  })

  it('Verweis ins Leere: 400, nichts geändert', async () => {
    const s = emptyStore()
    loadSmall(s)
    const api = createMockApiForTests(s, 'admin')
    const doc = await api.exportMasterData()
    const kaputt = JSON.parse(JSON.stringify(doc)) as typeof doc
    kaputt.classes[0].subjects[0].teacherId = 999_999

    await expect(api.importMasterData({ ...kaputt, replace: true }))
      .rejects.toMatchObject({ status: 400 })
    expect(s.teachers).toHaveLength(3)
    expect(s.classes).toHaveLength(3)
  })
})

describe('Bereichsweise Sicherung (Transfer, Version 2)', () => {
  it('Lehrkräfte exportieren und in einen gleichen Bestand importieren: nur Lehrkräfte betroffen', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s, 'admin')
    const doc = await api.exportMasterDataSection('teachers')
    expect(doc.version).toBe('2')
    expect(doc.scope).toBe('teachers')
    expect(doc.teachers).toHaveLength(3)
    expect(doc.subjects).toBeNull()
    expect(doc.classes).toBeNull()
    // Referenzen für die Fach- und Sperrzeiten-Zuordnung:
    expect(doc.refs?.subjects?.map((r) => r.shortName).sort()).toEqual(['De', 'Ma', 'Ph'])
    expect(doc.refs?.timeslots?.map((r) => `${r.day}${r.position}`).sort()).toEqual(['FR6', 'FR7'])

    const vorher = { subjects: s.subjects.length, rooms: s.rooms.length, classes: s.classes.length }
    const res = await api.importMasterDataSection(doc)
    expect(res).toMatchObject({ scope: 'teachers', updated: 3, created: 0 })
    expect(s.subjects.length).toBe(vorher.subjects)
    expect(s.rooms.length).toBe(vorher.rooms)
    expect(s.classes.length).toBe(vorher.classes)
    expect(s.teachers).toHaveLength(3)
  })

  it('Neue Lehrkraft in der Datei wird angelegt, bestehende aktualisiert', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s, 'admin')
    const doc = await api.exportMasterDataSection('teachers')
    // Meyer umbenennen, vierte Lehrkraft ergänzen (Kürzel KRA existiert noch nicht)
    doc.teachers![0].name = 'Meyer-van-der-Berg'
    doc.teachers!.push({ id: 99, name: 'Krause', shortName: 'KRA', weeklyHoursQuota: 20,
      subjectIds: [doc.refs!.subjects![0].id], preferredTimeslotIds: [], blockedTimeslotIds: [], active: true,
      plannedHours: 0, warnings: [] })

    const res = await api.importMasterDataSection(doc)
    expect(res).toMatchObject({ created: 1, updated: 3 })
    expect(s.teachers.find((t) => t.shortName === 'MEY')?.name).toBe('Meyer-van-der-Berg')
    expect(s.teachers.find((t) => t.shortName === 'KRA')).toBeDefined()
  })

  it('Fach-Referenz ins Leere: 400 mit Pfad, Bestand bleibt', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s, 'admin')
    const doc = await api.exportMasterDataSection('teachers')
    // Ein neues Fach in die Referenzliste, das es im Zielbestand nicht gibt
    doc.refs!.subjects!.push({ id: 999, shortName: 'Xy' })
    doc.teachers![0].subjectIds.push(999)

    await expect(api.importMasterDataSection(doc)).rejects.toMatchObject({
      status: 400,
      problem: { type: 'urn:pushnpull:problem:validation' },
    })
    expect(s.teachers).toHaveLength(3)
  })

  it('Klassen ohne passende Lehrkräfte im Zielbestand: 400, nichts angelegt', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s, 'admin')
    const doc = await api.exportMasterDataSection('classes')
    // Im leeren Zielbestand gibt es keine der Kurzzeichen — die Referenzauflösung scheitert.
    // (Für dieses Beispiel: Store komplett leeren, außer Planungs-Konto.)
    const leer = emptyStore()
    // Ohne das Konto gäbe es 401 statt 400 — planning() prüft die Rolle vor jeder Aktion.
    leer.users.push({ id: 1, username: 'admin', password: 'admin', role: 'PLANNING', teacherId: null, classId: null })
    const apiLeer = createMockApiForTests(leer, 'admin')

    await expect(apiLeer.importMasterDataSection(doc)).rejects.toMatchObject({ status: 400 })
    expect(leer.classes).toHaveLength(0)
  })

  it('Zeitfenster-Bereich ersetzt das komplette Raster', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s, 'admin')
    const doc = await api.exportMasterDataSection('timeslots')
    doc.timeslots = doc.timeslots!.slice(0, 2)
    await api.importMasterDataSection(doc)
    expect(s.timeslots).toHaveLength(2)
  })
})

// ------------------------------------------------------------------ #103 / #115 / #116
//
// Drei Regeln, die Attrappe und Server gleich halten müssen, weil die Oberfläche auf ihnen aufbaut:
// mehrere Entwürfe nebeneinander (#103), Gültigkeitszeiträume ohne Überschneidung (#115) und die
// Ablage als Sperre (#116). Die Wochenauflösung wird direkt über `planValidAt` geprüft — sie ist die
// Stelle, an der ein Tag seinen Plan bekommt.
describe('Mehrere Entwürfe (#103)', () => {
  it('stehen nebeneinander, jüngster zuerst, mit automatischem Namen', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s)
    const a = await api.createDraft({ source: 'EMPTY' })
    const b = await api.createDraft({ source: 'COPY_ACTIVE', name: 'Variante B' })

    expect(a.name).toBe('Entwurf 1')
    expect(b.name).toBe('Variante B')
    const liste = await api.listDrafts()
    expect(liste.map((d) => d.name)).toEqual(['Variante B', 'Entwurf 1'])
    expect(liste.map((d) => d.source)).toEqual(['COPY_ACTIVE', 'EMPTY'])
    // Ein Entwurf annehmen lässt die anderen stehen (Vertrag 1.3.0).
    await api.acceptDraft(b.id)
    expect((await api.listDrafts()).map((d) => d.id)).toEqual([a.id])
  })

  it('leerer oder zu langer Name ist 400 mit Feld name', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s)
    await expect(api.createDraft({ source: 'EMPTY', name: '  ' })).rejects.toMatchObject({ status: 400 })
    await expect(api.createDraft({ source: 'EMPTY', name: 'x'.repeat(81) })).rejects.toMatchObject({ status: 400 })
  })
})

describe('Ablage (#116)', () => {
  it('sperrt Stunden und Berechnung mit 409 draft-finished; Annehmen und Wiederöffnen gehen', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s)
    const d = await api.createDraft({ source: 'COPY_ACTIVE' })
    const mo6 = s.timeslots.find((t) => t.day === 'MO' && t.position === 6)!.id

    const abgelegt = await api.patchDraft(d.id, { finished: true })
    expect(abgelegt.finished).toBe(true)
    expect((await api.listDrafts())[0].finished).toBe(true)

    const erwartet = { status: 409, problem: { type: 'urn:pushnpull:problem:draft-finished' } }
    await expect(api.addDraftLesson(d.id, { classId: 3, subjectId: 1, timeslotId: mo6, roomId: 1 })).rejects.toMatchObject(erwartet)
    await expect(api.patchDraftLesson(d.id, d.lessons[0].id, { pinned: false })).rejects.toMatchObject(erwartet)
    await expect(api.deleteDraftLesson(d.id, d.lessons[0].id)).rejects.toMatchObject(erwartet)
    await expect(api.startSolve(d.id)).rejects.toMatchObject(erwartet)

    // Umbenennen geht weiter, Wiederöffnen hebt die Sperre auf.
    await api.patchDraft(d.id, { name: 'Später' })
    const offen = await api.patchDraft(d.id, { finished: false })
    expect(offen.finished).toBe(false)
    expect(offen.name).toBe('Später')
    await api.patchDraftLesson(d.id, d.lessons[0].id, { pinned: false })
  })

  it('leerer Rumpf ist 400 — ein PATCH ohne Feld ändert nichts und soll das sagen', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s)
    const d = await api.createDraft({ source: 'EMPTY' })
    await expect(api.patchDraft(d.id, {})).rejects.toMatchObject({ status: 400 })
  })
})

describe('Gültigkeitszeitraum (#115)', () => {
  const heute = today()

  it('ein künftiger Beginn lässt den Vorgänger ACTIVE bis zum Vortag; jeder Tag trifft seinen Plan', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s)
    const alt = s.timetables[0]
    const ab = addDaysIso(heute, 7)
    const d = await api.createDraft({ source: 'COPY_ACTIVE' })
    const neu = await api.acceptDraft(d.id, { validFrom: ab })

    expect(neu.validFrom).toBe(ab)
    expect(neu.validUntil).toBeNull()
    expect(alt.status).toBe('ACTIVE')
    expect(alt.validUntil).toBe(addDaysIso(ab, -1))
    // An der Datumsgrenze: letzter Tag alt, erster Tag neu.
    expect(planValidAt(addDaysIso(ab, -1), s)?.id).toBe(alt.id)
    expect(planValidAt(ab, s)?.id).toBe(neu.id)
    // Beide Pläne stehen in der Liste, heute gilt der alte.
    const plaene = await api.listTimetables()
    expect(plaene.map((p) => [p.id, p.current])).toEqual([[alt.id, true], [neu.id, false]])
  })

  it('„jetzt annehmen" archiviert den Vorgänger — er endet gestern', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s)
    const alt = s.timetables[0]
    const d = await api.createDraft({ source: 'COPY_ACTIVE' })
    const neu = await api.acceptDraft(d.id)

    expect(alt.status).toBe('ARCHIVED')
    expect(alt.validUntil).toBe(addDaysIso(heute, -1))
    expect(planValidAt(heute, s)?.id).toBe(neu.id)
    // Der archivierte Plan bleibt für vergangene Wochen auflösbar.
    expect(planValidAt(addDaysIso(heute, -1), s)?.id).toBe(alt.id)
    expect(await api.listTimetables()).toHaveLength(1)
  })

  it('ein Zeitraum, den ein künftiger Plan schon belegt, ist 409 plan-overlap — und nichts ändert sich', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s)
    const alt = s.timetables[0]
    const spaet = addDaysIso(heute, 30)
    const a = await api.createDraft({ source: 'COPY_ACTIVE' })
    const kuenftig = await api.acceptDraft(a.id, { validFrom: spaet })
    expect(alt.validUntil).toBe(addDaysIso(spaet, -1))

    const b = await api.createDraft({ source: 'COPY_ACTIVE' })
    await expect(api.acceptDraft(b.id, { validFrom: addDaysIso(heute, 10) })).rejects.toMatchObject({
      status: 409,
      problem: { type: 'urn:pushnpull:problem:plan-overlap' },
    })
    // Unverändert: der künftige Plan gilt weiter, der Vorgänger endet wie zuvor, der Entwurf bleibt Entwurf.
    expect(s.timetables.find((t) => t.id === kuenftig.id)?.status).toBe('ACTIVE')
    expect(alt.validUntil).toBe(addDaysIso(spaet, -1))
    expect(alt.status).toBe('ACTIVE')
    expect(s.timetables.find((t) => t.id === b.id)?.status).toBe('DRAFT')
  })

  it('ein Ende vor dem Beginn ist 400 mit Feld validUntil', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s)
    const d = await api.createDraft({ source: 'COPY_ACTIVE' })
    const err = await api.acceptDraft(d.id, { validFrom: heute, validUntil: addDaysIso(heute, -1) }).catch((e: unknown) => e)
    expect(err).toMatchObject({ status: 400 })
    expect((err as ApiError).problem.errors?.[0].field).toBe('validUntil')
  })

  it('ein Plan mit Ende lässt die Zeit danach leer, bis ein weiterer angenommen wird', async () => {
    const s = emptyStore(); loadSmall(s)
    const api = createMockApiForTests(s)
    const bis = addDaysIso(heute, 20)
    const d = await api.createDraft({ source: 'COPY_ACTIVE' })
    const neu = await api.acceptDraft(d.id, { validFrom: heute, validUntil: bis })

    expect(neu.validUntil).toBe(bis)
    expect(planValidAt(bis, s)?.id).toBe(neu.id)
    expect(planValidAt(addDaysIso(bis, 1), s)).toBeUndefined()
  })
})
