// Absicherung des Mock-Stores: Doppelbelegung → Konflikt, Deputat-Überschreitung → Warnung (npm test).
import { describe, expect, it } from 'vitest'
import { emptyStore } from './store'
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
    await api.createDraft({ source: 'COPY_ACTIVE' })
    const mo1 = s.timeslots.find((t) => t.day === 'MO' && t.position === 1)!.id
    const mo6 = s.timeslots.find((t) => t.day === 'MO' && t.position === 6)!.id
    const d1 = await api.addDraftLesson({ classId: 3, subjectId: 1, timeslotId: mo1, roomId: 1 })
    expect(d1.conflicts.map((c) => c.type)).toEqual(['ROOM_DOUBLE_BOOKED'])
    const added = d1.lessons.find((l) => l.classId === 3)!
    const d2 = await api.patchDraftLesson(added.id, { timeslotId: mo6 })
    expect(d2.conflicts).toHaveLength(0)
    expect(d2.lessons.find((l) => l.id === added.id)?.pinned).toBe(true)
  })

  it('Berechnen platziert alle offenen Stunden ohne Doppelbelegung (T-W8.1)', async () => {
    const s = emptyStore()
    loadSmall(s)
    const api = createMockApiForTests(s)
    await api.createDraft({ source: 'EMPTY' })
    await api.startSolve()
    await api.cancelSolve() // platziert sofort statt nach 3 s
    const d = await api.getDraft()
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
