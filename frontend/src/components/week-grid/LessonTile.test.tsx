// @vitest-environment jsdom
//
// Die Design-Sprache der Kachel, gerendert statt gelesen (Karte T-509, Befund FRONTEND-6).
//
// Der Kern ist eine Zusage aus ui.md, die kein Logiktest prüfen kann: **Farbe ist nie der einzige Träger.**
// Wer nicht zwischen Orange und Grau unterscheidet — Rot-Grün-Schwäche, Ausdruck in Graustufen, Beamer im
// hellen Raum —, muss trotzdem sehen, dass eine Stunde vertreten ist oder entfällt. Deshalb prüft der erste
// Fall jeden der sieben `kind`-Werte auf einen Träger, den ein Screenreader vorliest oder ein Graustufendruck
// zeigt: ein Badge mit `aria-label` oder ein Text in der Kachel.
import { describe, expect, it } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import type { EntryDto, EntryKind } from '@/api/types'
import { normalizeEntry } from '@/lib/grid-entry'
import { ENTRY_KIND, entryLabel } from '@/lib/entry-kind'
import { LessonTile } from './LessonTile'

const KINDS = Object.keys(ENTRY_KIND) as EntryKind[]

/** Ein Wocheneintrag, wie ihn GET /api/timetable/week liefert — die Felder, die die Kachel liest. */
function entry(kind: EntryKind, extra: Partial<EntryDto> = {}): EntryDto {
  return {
    date: '2026-09-21', lessonId: 1, timeslotId: 1, day: 'MO', position: 1,
    classShort: '7a', subjectShort: 'Ma', subjectColor: '#1976D2',
    teacherShort: 'MEY', roomShort: 'R101',
    kind, original: null, reasons: [], exceptionId: null, ...extra,
  } as EntryDto
}

function renderTile(e: EntryDto) {
  const normalized = normalizeEntry(e, 0)!
  return render(<LessonTile entry={normalized} view="CLASS" />)
}

describe('LessonTile — Farbe ist nie der einzige Träger (ui.md)', () => {
  it.each(KINDS.filter((k) => k !== 'REGULAR'))('%s trägt Badge oder Text, nicht nur Farbe', (kind) => {
    const e = entry(kind)
    const { container } = renderTile(e)
    // Die Kachel beschriftet kontextabhängig: `entryLabel` macht aus SUBSTITUTED ohne Lehrkraftwechsel
    // „Raumwechsel" und aus CANCELLED mit Zieltermin „verlegt → Di 3.". Geprüft wird der Träger, den die
    // Kachel wirklich zeigt — nicht der Vorgabewert der Tabelle.
    const erwartet = entryLabel(e)
    expect(erwartet, `${kind} hat gar keine Beschriftung in entry-kind.ts`).not.toBe('')

    const alsBadge = screen.queryAllByLabelText(erwartet, { exact: false }).length > 0
    const alsText = (container.textContent ?? '').includes(erwartet)
    expect(
      alsBadge || alsText,
      `${kind} („${erwartet}") hat weder Badge mit aria-label noch sichtbaren Text — nur die Farbklasse "${ENTRY_KIND[kind].className}"`,
    ).toBe(true)
  })

  it('die reguläre Stunde bleibt schmucklos: Fach, Lehrkraft, Raum', () => {
    const { container } = renderTile(entry('REGULAR'))
    expect(container.textContent).toContain('Ma')
    expect(container.textContent).toContain('MEY')
    expect(container.textContent).toContain('R101')
    expect(screen.queryByLabelText(/festgelegt/)).toBeNull()
  })

  it('eine Vertretung zeigt die alte Lehrkraft durchgestrichen neben der neuen', () => {
    const { container } = renderTile(entry('SUBSTITUTED', {
      teacherShort: 'SCZ',
      original: { teacherShort: 'MEY', roomShort: 'R101' },
    } as Partial<EntryDto>))

    // Der Vorher-Wert steht in <s>, der Nachher-Wert daneben: ohne beides wüsste die Lehrkraft nicht,
    // wessen Stunde sie übernimmt (T-W5.1).
    const alt = container.querySelector('s')
    expect(alt?.textContent).toBe('MEY')
    expect(container.textContent).toContain('SCZ')
  })

  it('festgelegte Stunden nennen den Pin beim Namen (W-9)', () => {
    const lesson = {
      id: 7, classId: 1, classShort: '7a', subjectId: 1, subjectShort: 'Ma', subjectColor: '#1976D2',
      teacherId: 1, teacherShort: 'MEY', roomId: 1, roomShort: 'R101',
      timeslotId: 1, day: 'MO', position: 1, pinned: true,
    }
    render(<LessonTile entry={normalizeEntry(lesson as never, 0)!} view="CLASS" />)
    expect(screen.getByLabelText('festgelegt')).toBeTruthy()
  })

  it('der Konflikt-Badge sagt, wie viele es sind — nicht nur rot', () => {
    const normalized = normalizeEntry(entry('REGULAR'), 0)!
    render(
      <LessonTile
        entry={normalized}
        view="CLASS"
        conflicts={[{
          type: 'ROOM_DOUBLE_BOOKED', severity: 'HARD',
          message: 'Raum R101 ist am Montag in der 1. Stunde doppelt belegt (7a, 9c).',
          lessonIds: [1, 2], ruleId: null, objects: [],
        } as never]}
      />,
    )
    const badge = screen.getByLabelText('1 Konflikt anzeigen')
    expect(within(badge).getByText('1')).toBeTruthy()
  })
})
