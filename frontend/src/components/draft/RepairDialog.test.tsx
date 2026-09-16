// @vitest-environment jsdom
//
// Konflikt per Knopf lösen (#110, T2-3.2): der Dialog zeigt die Vorschläge des Servers lesbar und übernimmt einen
// Vorschlag Zug für Zug über PATCH /drafts/{id}/lessons/{lessonId} — mit Zeitfenster, Raum und pinned:false. Genau dieser
// Weg wird hier gegen die Attrappe geprüft: nach „Übernehmen" stehen die Stunden im Entwurf der Attrappe dort,
// wohin der Vorschlag sie schickt.
import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { api, setApi } from '@/api/api'
import type { LessonDto, PatchLessonInput, RepairProposal } from '@/api/types'
import { activeTimetable, newTimetable, nextId, slotId, type MockLesson, type Store } from '@/mock/store'
import { nowIso } from '@/lib/today'
import { renderPage, testStore } from '@/test-utils'
import { RepairDialog } from './RepairDialog'

/** Ein Entwurf als Kopie des aktiven Plans der Attrappe; liefert die Kopie und ihre Stunden als LessonDto. */
function mitEntwurf(store: Store) {
  const active = activeTimetable(store)!
  const draft = newTimetable('DRAFT', nowIso(), { name: 'Entwurf 1', source: 'COPY_ACTIVE' })
  draft.lessons = active.lessons.map((l): MockLesson => ({ ...l, id: nextId(), timetableId: draft.id }))
  store.timetables.push(draft)
  const lessons: LessonDto[] = draft.lessons.map((l) => {
    const slot = store.timeslots.find((t) => t.id === l.timeslotId)!
    return {
      id: l.id, classId: l.classId, classShort: store.classes.find((c) => c.id === l.classId)!.name,
      subjectId: l.subjectId, subjectShort: store.subjects.find((s) => s.id === l.subjectId)!.shortName, subjectColor: '#000',
      teacherId: l.teacherId, teacherShort: store.teachers.find((t) => t.id === l.teacherId)!.shortName,
      roomId: l.roomId, roomShort: store.rooms.find((r) => r.id === l.roomId)!.name,
      timeslotId: l.timeslotId, day: slot.day, position: slot.position, pinned: l.pinned,
    }
  })
  return { draft, lessons }
}

describe('RepairDialog (#110)', () => {
  it('zeigt je Vorschlag die Züge lesbar: Fach/Klasse, von → nach Zeitfenster und Raum, verbleibende harte Konflikte', async () => {
    const store = testStore()
    const { draft, lessons } = mitEntwurf(store)
    // 7a Ma Mo 1. Stunde in R101 → Mo 5. Stunde (Position 6, die Pause zählt nicht mit) in R204
    const ma7a = lessons.find((l) => l.classShort === '7a' && l.subjectShort === 'Ma' && l.day === 'MO' && l.position === 1)!
    const proposals: RepairProposal[] = [
      { level: 1, label: 'Nur diese Stunde verschieben', moves: [{ lessonId: ma7a.id, timeslotId: slotId(store.timeslots, 'MO', 6), roomId: 2 }], hardConflictsAfter: 0, score: '0hard/0medium/0soft' },
      { level: 3, label: 'Auch Stunden der Lehrkraft und des Raums am selben Tag umstellen', moves: [
        { lessonId: ma7a.id, timeslotId: slotId(store.timeslots, 'TU', 2), roomId: 1 },
        { lessonId: lessons.find((l) => l.classShort === '8b' && l.day === 'MO' && l.position === 2)!.id, timeslotId: slotId(store.timeslots, 'MO', 7), roomId: 2 },
      ], hardConflictsAfter: 1, score: '-1hard/0medium/0soft' },
    ]
    await renderPage(<RepairDialog open onOpenChange={() => {}} draftId={draft.id} pending={false} failed={false} proposals={proposals} lessons={lessons} slots={store.timeslots} rooms={store.rooms} />, { store })

    expect(await screen.findByText('Nur diese Stunde verschieben')).toBeTruthy()
    expect(screen.getAllByText('Ma 7a').length).toBe(2)
    expect(screen.getAllByText('Mo 1. Stunde · R101').length).toBe(2)
    expect(screen.getByText('Mo 5. Stunde · R204')).toBeTruthy()
    expect(screen.getByText('Ma 8b')).toBeTruthy()
    expect(screen.getByText('Di 2. Stunde · R101')).toBeTruthy()
    expect(screen.getByText(/1 Stunde verschieben · löst alle Konflikte/)).toBeTruthy()
    expect(screen.getByText(/2 Stunden verschieben · 1 Konflikt bleibt/)).toBeTruthy()
  })

  it('„Übernehmen" ruft PATCH je Zug nacheinander (Zeitfenster, Raum, pinned:false) und schließt den Dialog', async () => {
    const nutzer = userEvent.setup()
    const store = testStore()
    const { draft, lessons } = mitEntwurf(store)
    const ma7a = lessons.find((l) => l.classShort === '7a' && l.subjectShort === 'Ma' && l.day === 'MO' && l.position === 1)!
    const ma8b = lessons.find((l) => l.classShort === '8b' && l.day === 'MO' && l.position === 2)!
    const zielA = slotId(store.timeslots, 'TU', 2)
    const zielB = slotId(store.timeslots, 'MO', 7)
    const proposals: RepairProposal[] = [{
      level: 3, label: 'Auch Stunden der Lehrkraft und des Raums am selben Tag umstellen',
      moves: [{ lessonId: ma7a.id, timeslotId: zielA, roomId: 1 }, { lessonId: ma8b.id, timeslotId: zielB, roomId: 2 }],
      hardConflictsAfter: 0, score: '0hard/0medium/0soft',
    }]
    const onOpenChange = vi.fn()
    await renderPage(<RepairDialog open onOpenChange={onOpenChange} draftId={draft.id} pending={false} failed={false} proposals={proposals} lessons={lessons} slots={store.timeslots} rooms={store.rooms} />, { store })
    // Die Attrappe mitschreiben lassen: welche PATCHes in welcher Reihenfolge mit welchem Rumpf.
    const calls: [number, PatchLessonInput][] = []
    const original = api
    setApi({ ...original, patchDraftLesson: (draftId, lessonId, body) => { calls.push([lessonId, body]); return original.patchDraftLesson(draftId, lessonId, body) } })

    await nutzer.click(await screen.findByRole('button', { name: 'Vorschlag 3 übernehmen' }))

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
    expect(calls).toEqual([
      [ma7a.id, { timeslotId: zielA, roomId: 1, pinned: false }],
      [ma8b.id, { timeslotId: zielB, roomId: 2, pinned: false }],
    ])
    const a = draft.lessons.find((l) => l.id === ma7a.id)!
    const b = draft.lessons.find((l) => l.id === ma8b.id)!
    expect([a.timeslotId, a.roomId, a.pinned]).toEqual([zielA, 1, false])
    expect([b.timeslotId, b.roomId, b.pinned]).toEqual([zielB, 2, false])
    // Nur die beiden Züge — die übrigen Stunden des Entwurfs stehen unverändert.
    expect(draft.lessons.filter((l) => l.id !== ma7a.id && l.id !== ma8b.id).every((l) => l.pinned)).toBe(true)
  })

  it('solange der Server rechnet, steht der Hinweis auf die Wartezeit; ohne Vorschlag der Hinweis auf den Handweg', async () => {
    const store = testStore()
    const { draft, lessons } = mitEntwurf(store)
    const { rerender } = await renderPage(<RepairDialog open onOpenChange={() => {}} draftId={draft.id} pending failed={false} proposals={null} lessons={lessons} slots={store.timeslots} rooms={store.rooms} />, { store })
    expect(await screen.findByText(/bis zu 15 Sekunden/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: /übernehmen/ })).toBeNull()

    rerender(<RepairDialog open onOpenChange={() => {}} draftId={draft.id} pending={false} failed={false} proposals={[]} lessons={lessons} slots={store.timeslots} rooms={store.rooms} />)
    expect(await screen.findByText(/Kein Vorschlag gefunden/)).toBeTruthy()
  })
})
