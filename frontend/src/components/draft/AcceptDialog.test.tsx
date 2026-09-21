// @vitest-environment jsdom
//
// Der Annehmen-Dialog ist die letzte Bremse vor einem Schritt, den niemand rückgängig machen kann
// (Befund FRONTEND-6 — bis 05.09. war kein Dialog dieser Anwendung gerendert getestet).
//
// Seit #116 stellt er drei Wege zur Wahl: jetzt aktivieren, ab einem Datum aktivieren, in die Ablage
// legen. Geprüft wird hier (a) dass alle drei da sind und nur der gewählte seine Felder zeigt, (b) dass
// die Ablage wirklich nichts annimmt, sondern nur `finished` setzt, (c) dass ein belegter Zeitraum
// (409 `plan-overlap`) IM Dialog steht und nicht als Toast verschwindet, und (d) die alte Sperrlogik:
// verfallende Vertretungen verlangen ein Häkchen, sonst nicht. Diese hängt seit dem 05.09. an
// `expiringExceptions` aus dem Entwurf statt an einer eigenen Zählung im Dialog (Befund PFLICHTENHEFT-9).
import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError, type OpenLessonDto } from '@/api/types'
import { api, setApi } from '@/api/api'
import { newTimetable, nextId, type MockTimetable, type Store } from '@/mock/store'
import { addDaysIso } from '@/lib/dates'
import { nowIso, today } from '@/lib/today'
import { renderPage, testStore } from '@/test-utils'
import { AcceptDialog } from './AcceptDialog'

/** Beispielbestand mit einem Entwurf, der den aktiven Plan kopiert — konfliktfrei, also annehmbar. */
function storeMitEntwurf(activeValidFrom?: string): { s: Store; draft: MockTimetable } {
  const s = testStore()
  const active = s.timetables.find((t) => t.status === 'ACTIVE')!
  if (activeValidFrom) active.validFrom = activeValidFrom
  const draft = newTimetable('DRAFT', nowIso(), { name: 'Variante B', source: 'COPY_ACTIVE' })
  draft.lessons = active.lessons.map((l) => ({ ...l, id: nextId(), timetableId: draft.id }))
  s.timetables.push(draft)
  return { s, draft }
}

async function zeige(props: {
  expiringExceptions?: number
  warnings?: string[]
  openLessons?: OpenLessonDto[]
  hardConflicts?: number
  activeValidFrom?: string
  /** Statt anzunehmen antwortet der Server mit diesem Fehler (für die 409-Fälle). */
  acceptFails?: ApiError
  onShowConflicts?(): void
} = {}) {
  const { s, draft } = storeMitEntwurf(props.activeValidFrom)
  await renderPage(
    <AcceptDialog
      open
      onOpenChange={() => {}}
      draftId={draft.id}
      warnings={props.warnings ?? []}
      openLessons={props.openLessons ?? []}
      expiringExceptions={props.expiringExceptions ?? 0}
      hardConflicts={props.hardConflicts ?? 0}
      onShowConflicts={props.onShowConflicts}
    />,
    { store: s },
  )
  if (props.acceptFails) {
    const echt = api
    setApi({ ...echt, acceptDraft: () => Promise.reject(props.acceptFails) })
  }
  return {
    store: s,
    draft,
    annehmen: () => screen.getByRole('button', { name: /Annehmen|In die Ablage/ }) as HTMLButtonElement,
    weg: (name: string) => screen.getByRole('radio', { name: new RegExp(name) }),
  }
}

describe('AcceptDialog — die drei Wege (#116)', () => {
  it('stellt alle drei Wege zur Wahl, „Jetzt aktivieren" ist vorausgewählt', async () => {
    const { weg } = await zeige()

    expect(weg('Jetzt aktivieren').getAttribute('aria-checked')).toBe('true')
    expect(weg('Ab Datum aktivieren')).toBeTruthy()
    expect(weg('In die Ablage legen')).toBeTruthy()
    // Ohne den Weg „ab Datum" gibt es kein Datumsfeld — es wäre eine Frage, die niemand gestellt hat (E-40).
    expect(screen.queryByLabelText('Gültig ab')).toBeNull()
  })

  it('„Ab Datum aktivieren" zeigt Beginn und auf Wunsch ein Enddatum', async () => {
    const nutzer = userEvent.setup()
    const { weg } = await zeige()

    await nutzer.click(weg('Ab Datum aktivieren'))
    expect(screen.getByLabelText('Gültig ab')).toBeTruthy()
    expect(screen.getByText('Der bisherige Plan gilt bis zum Vortag weiter.')).toBeTruthy()
    // Das Enddatum ist Kann: erst das Häkchen holt das zweite Feld.
    expect(screen.queryByLabelText('Gültig bis')).toBeNull()
    await nutzer.click(screen.getByLabelText('Auch ein Enddatum setzen'))
    expect(screen.getByLabelText('Gültig bis')).toBeTruthy()
  })

  it('„In die Ablage legen" nimmt nichts an, sondern friert den Entwurf ein', async () => {
    const nutzer = userEvent.setup()
    const { weg, annehmen, draft } = await zeige()

    await nutzer.click(weg('In die Ablage legen'))
    expect(annehmen().textContent).toContain('In die Ablage')
    // Warnungen des Annehmens gelten hier nicht — es wird ja nichts gültig.
    expect(screen.queryByText(/bleibt unverplant/)).toBeNull()

    await nutzer.click(annehmen())
    await waitFor(() => expect(draft.finished).toBe(true))
    expect(draft.status).toBe('DRAFT')
    expect(draft.acceptedAt).toBeNull()
  })

  it('harte Konflikte sperren das Annehmen, die Ablage bleibt offen', async () => {
    const nutzer = userEvent.setup()
    const { weg, annehmen } = await zeige({ hardConflicts: 2 })

    expect(screen.getByText(/2 harte Konflikte blockieren das Annehmen/)).toBeTruthy()
    expect(annehmen().disabled).toBe(true)

    await nutzer.click(weg('In die Ablage legen'))
    expect(annehmen().disabled).toBe(false)
  })
})

describe('AcceptDialog — Folgen vor dem Klick', () => {
  it('ohne verfallende Vertretungen fragt er nichts und ist sofort bereit', async () => {
    const { annehmen } = await zeige({ expiringExceptions: 0 })

    expect(annehmen().disabled).toBe(false)
    expect(screen.queryByText(/verfäll/i)).toBeNull()
    expect(screen.queryByLabelText('Verstanden — trotzdem annehmen')).toBeNull()
  })

  it('verfallen Vertretungen, bleibt „Annehmen" gesperrt, bis das Häkchen sitzt', async () => {
    const nutzer = userEvent.setup()
    const { annehmen } = await zeige({ expiringExceptions: 2 })

    expect(screen.getByText(/2 Vertretungen ab heute/)).toBeTruthy()
    expect(annehmen().disabled).toBe(true)

    await nutzer.click(screen.getByLabelText('Verstanden — trotzdem annehmen'))
    expect(annehmen().disabled).toBe(false)
  })

  it('eine einzelne Vertretung wird im Singular genannt', async () => {
    await zeige({ expiringExceptions: 1 })
    expect(screen.getByText(/Eine Vertretung ab heute/)).toBeTruthy()
  })

  it('in der Ablage schweigen die Warnungen — es wird nichts gültig', async () => {
    const nutzer = userEvent.setup()
    const { weg } = await zeige({ expiringExceptions: 2 })

    await nutzer.click(weg('In die Ablage legen'))
    expect(screen.queryByText(/2 Vertretungen ab heute/)).toBeNull()
  })

  it('offene Stunden stehen als eigene Warnung da — sie verschwinden aus jeder Wochensicht', async () => {
    await zeige({ openLessons: [{ classId: 3, classShort: '9c', subjectShort: 'Ma', missing: 2 }] })
    expect(screen.getByText(/2 Stunden sind noch offen/)).toBeTruthy()
  })

  it('Warnungen des Entwurfs werden unverändert durchgereicht', async () => {
    await zeige({ warnings: ['9c Ma: 2 Stunden geplant, 1 vorgesehen'] })
    expect(screen.getByText('9c Ma: 2 Stunden geplant, 1 vorgesehen')).toBeTruthy()
  })
})

describe('AcceptDialog — belegter Zeitraum (409 plan-overlap, #115)', () => {
  // Der Weg zu diesem Fehler führt über ein künftiges Datum im Kalender-Popover; dessen Bedienung mit
  // Zeigergesten läuft in jsdom nicht. Die Regel selbst steht deshalb im Attrappentest (mock.test.ts,
  // „Zeitraum belegt"); hier zählt nur, dass der Dialog die Antwort des Servers zeigt, statt sie als
  // Toast wegfliegen zu lassen — weit weg von dem Datumsfeld, um das es geht.
  it('zeigt den Servertext im Dialog, statt ihn als Toast verschwinden zu lassen', async () => {
    const nutzer = userEvent.setup()
    const kuenftig = addDaysIso(today(), 10)
    const detail = `Ein angenommener Plan gilt bereits ab ${kuenftig}. Enddatum vor ${kuenftig} wählen oder den Zeitraum ändern.`
    const { annehmen } = await zeige({
      acceptFails: new ApiError({ type: 'urn:pushnpull:problem:plan-overlap', title: 'Zeitraum ist belegt', status: 409, detail }),
    })

    await nutzer.click(annehmen())

    expect(await screen.findByText(detail)).toBeTruthy()
    expect(screen.getByText('Zeitraum ist belegt')).toBeTruthy()
  })

  it('harte Konflikte des Servers führen zur Konfliktliste', async () => {
    const nutzer = userEvent.setup()
    const { annehmen } = await zeige({
      hardConflicts: 0, // die Oberfläche weiß noch nichts davon — erst die Antwort des Servers sagt es
      acceptFails: new ApiError({
        type: 'urn:pushnpull:problem:draft-has-conflicts', title: 'Harte Konflikte im Entwurf', status: 409,
        detail: 'Der Entwurf hat harte Konflikte. Bitte erst lösen, dann annehmen.',
      }),
      onShowConflicts: () => {},
    })

    await nutzer.click(annehmen())
    expect(await screen.findByRole('button', { name: 'Zur Konfliktliste' })).toBeTruthy()
  })
})
