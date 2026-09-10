// Tagesansicht des eigenen Plans im Browser (Befund PFLICHTENHEFT-8, 05.09.).
//
// Das Musskriterium verlangt „dieselbe Tages- und Wochenansicht des eigenen Plans wie in der App"
// (zielbestimmungen.tex). Das Web hatte bis dahin nur das Wochenraster; die Tagesliste gab es allein in
// der App-Vorschau gegen die Attrappe. Damit „dieselbe" auch stimmt, benutzt diese Liste die Karten der
// App (PhoneEntryCard) und dieselbe Stundennummerierung wie das Raster (lib/timegrid).
import { motion } from 'motion/react'
import type { DayDto, EntryDto, TimeslotDto } from '@/api/types'
import { PhoneEntryCard } from '@/components/phone/PhoneEntryCard'
import { listItem } from '@/lib/motion'
import { lessonNumber, slotById } from '@/lib/timegrid'

export function DayList({ day, slots, onEntryClick }: {
  /** Der Tag aus der Wochenantwort; fehlt er, liegt das Datum ausserhalb der geladenen Woche. */
  day: DayDto | undefined
  slots: TimeslotDto[]
  onEntryClick(entry: EntryDto, anchor: HTMLElement): void
}) {
  if (!day) return <p className="px-6 py-12 text-center text-sm text-foreground-subtle">Kein Schultag</p>
  if (!day.schoolDay) return <p className="px-6 py-12 text-center text-sm text-foreground-subtle">Unterrichtsfrei</p>

  const entries = [...day.entries].sort((a, b) => a.position - b.position)
  if (entries.length === 0) return <p className="px-6 py-12 text-center text-sm text-foreground-subtle">Keine Stunden an diesem Tag</p>

  return (
    <ul className="space-y-3">
      {entries.map((e, i) => {
        const slot = slotById(slots, e.timeslotId)
        const n = slot ? lessonNumber(slot, slots) : null
        return (
          <motion.li
            key={`${e.date}-${e.timeslotId}-${e.lessonId ?? 'x'}-${e.kind}`}
            {...listItem(i)}
            className="flex items-stretch gap-3"
            // Der Klick kommt von der Karte darin; als Anker fuer das Detail-Popover dient ihr Knopf.
            onClick={(ev) => {
              const target = ev.target instanceof HTMLElement ? ev.target.closest('button') : null
              onEntryClick(e, target ?? (ev.currentTarget as HTMLElement))
            }}
          >
            <div className="flex w-12 shrink-0 flex-col items-center justify-center rounded-lg bg-surface-sunken py-1">
              <span className="text-lg leading-tight font-bold tabular-nums">{n ?? '–'}</span>
              <span className="text-2xs leading-tight text-foreground-subtle tabular-nums">{slot?.startTime ?? ''}</span>
              <span className="text-2xs leading-tight text-foreground-subtle tabular-nums">{slot?.endTime ?? ''}</span>
            </div>
            <PhoneEntryCard entry={e} onClick={() => { /* der Klick wird auf der Zeile behandelt (Anker) */ }} />
          </motion.li>
        )
      })}
    </ul>
  )
}
