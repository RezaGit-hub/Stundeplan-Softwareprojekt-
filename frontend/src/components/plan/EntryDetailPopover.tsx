// Details einer Wochenstunde: Langnamen (nur Planung — die Lehrkraft darf keine Grunddaten laden, E-45), Art, alle
// Anlässe, alt → neu; bei „noch nicht vertreten" Link zur Vertretung.
//
// Bei einer eingetragenen Ausnahme (Vertretung, Entfall, Verlegung, Zusatzstunde) steht hier auch der Weg
// zurück: „Ausnahme aufheben" → DELETE /api/exceptions/{id}. Ohne ihn wäre eine falsche Zuweisung im Web
// nicht mehr zu korrigieren — der Server beantwortet die zweite Ausnahme derselben Stunde mit 409
// `exception-exists` (Befund FRONTEND-2, 05.09.).
import { useState } from 'react'
import { toast } from 'sonner'
import { ArrowRight, Undo2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useClasses, useDeleteException, useRooms, useSubjects, useTeachers } from '@/api/hooks'
import type { EntryDto, TimeslotDto } from '@/api/types'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Popover, PopoverAnchor, PopoverContent } from '@/components/ui/popover'
import { formatDay } from '@/lib/dates'
import { ENTRY_KIND, entryLabel } from '@/lib/entry-kind'
import { DAY_SHORT, slotById, slotLabel } from '@/lib/timegrid'
import { cn } from '@/lib/utils'

const KIND_CHIP: Record<EntryDto['kind'], string> = {
  REGULAR: '',
  NEEDS_SUBSTITUTION: 'border-dashed border-substitution bg-substitution-soft text-substitution-fg',
  SUBSTITUTED: 'border-substitution bg-substitution-soft text-substitution-fg',
  GIVEN_AWAY: 'text-given-away italic',
  MOVED: 'border-substitution bg-substitution-soft text-substitution-fg',
  CANCELLED: 'bg-surface-sunken text-foreground-muted',
  ADDED: 'border-substitution bg-substitution-soft text-substitution-fg',
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[5.5rem_1fr] gap-2 text-sm">
      <dt className="text-foreground-subtle">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  )
}

function OldNew({ old, now }: { old?: string | null; now: string | null }) {
  if (!old || old === now) return <>{now ?? '–'}</>
  return <><s className="text-foreground-subtle">{old}</s> <span className="font-semibold text-substitution-fg">→ {now ?? '?'}</span></>
}

export function EntryDetailPopover({ entry, anchor, onClose, slots, lookup = true }: {
  entry: EntryDto | null
  anchor: HTMLElement | null
  onClose(): void
  slots?: TimeslotDto[]
  /** Langnamen aus den Grunddaten laden (nur Rolle Planung) */
  lookup?: boolean
}) {
  const subjects = useSubjects({ enabled: lookup })
  const teachers = useTeachers({ enabled: lookup })
  const rooms = useRooms({ enabled: lookup })
  const classes = useClasses({ enabled: lookup })
  const removeException = useDeleteException()
  // Merkt sich die Ausnahme, während die Rückfrage läuft: das Popover schließt beim Öffnen des Dialogs,
  // `entry` ist dann schon null.
  const [toRemove, setToRemove] = useState<{ id: number; label: string } | null>(null)
  const open = entry != null && anchor != null
  if (!open) {
    return (
      <RemoveDialog
        toRemove={toRemove}
        onOpenChange={(o) => { if (!o) setToRemove(null) }}
        onConfirm={async (id) => { await removeException.mutateAsync(id); toast.success('Ausnahme aufgehoben'); setToRemove(null) }}
      />
    )
  }

  const subjectName = subjects.data?.find((s) => s.shortName === entry.subjectShort)?.name ?? entry.subjectShort
  const teacherName = (short: string | null | undefined) => (short ? (teachers.data?.find((t) => t.shortName === short)?.name ?? short) : null)
  const roomName = (short: string | null | undefined) => (short ? (rooms.data?.find((r) => r.name === short)?.name ?? short) : null)
  const className = classes.data?.find((c) => c.name === entry.classShort)?.name ?? entry.classShort
  const slot = slots ? slotById(slots, entry.timeslotId) : undefined
  const when = `${formatDay(entry.date)} · ${slot && slots ? slotLabel(slot, slots) : `${entry.position}. Stunde`}${slot ? ` (${slot.startTime}–${slot.endTime})` : ''}`
  const label = entryLabel(entry)
  const ui = ENTRY_KIND[entry.kind]
  const originSlot = entry.original?.position != null && entry.original.date ? `${DAY_SHORT[entry.day]} ${formatDay(entry.original.date)}, ${entry.original.position}. Zeitfenster` : null

  return (
    <Popover open onOpenChange={(o) => !o && onClose()}>
      <PopoverAnchor virtualRef={{ current: anchor }} />
      <PopoverContent align="start" side="right" sideOffset={8} className="w-80 gap-3" onOpenAutoFocus={(e) => e.preventDefault()}>
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <div className="text-base font-semibold">{subjectName}</div>
            <div className="text-xs text-foreground-subtle">{when}</div>
          </div>
          {entry.kind !== 'REGULAR' && (
            <Badge variant="outline" className={cn('h-5 shrink-0', KIND_CHIP[entry.kind])}>
              {ui.badge && <span className="font-bold">{ui.badge}</span>}
              {label}
            </Badge>
          )}
        </div>
        <dl className="space-y-1.5">
          <Row label="Klasse">{className}</Row>
          <Row label="Lehrkraft"><OldNew old={teacherName(entry.original?.teacherShort) ?? undefined} now={teacherName(entry.teacherShort)} /></Row>
          <Row label="Raum"><OldNew old={roomName(entry.original?.roomShort) ?? undefined} now={roomName(entry.roomShort)} /></Row>
          {entry.kind === 'MOVED' && originSlot && <Row label="Ursprung">{originSlot}</Row>}
          {entry.reasons.length > 0 && (
            <Row label={entry.reasons.length === 1 ? 'Anlass' : 'Anlässe'}>
              <ul className="space-y-0.5">{entry.reasons.map((r, i) => <li key={i}>{r}</li>)}</ul>
            </Row>
          )}
        </dl>
        {entry.kind === 'NEEDS_SUBSTITUTION' && lookup && (
          <Button asChild size="sm" className="w-fit">
            <Link to={`/vertretung?datum=${entry.date}`}>Zur Vertretung <ArrowRight data-icon="inline-end" /></Link>
          </Button>
        )}
        {lookup && entry.exceptionId != null && entry.kind !== 'NEEDS_SUBSTITUTION' && (
          <Button
            size="sm"
            variant="outline"
            className="w-fit"
            onClick={() => {
              setToRemove({ id: entry.exceptionId!, label: `${className} ${subjectName}, ${when}` })
              onClose()
            }}
          >
            <Undo2 data-icon="inline-start" /> Ausnahme aufheben
          </Button>
        )}
      </PopoverContent>
      <RemoveDialog
        toRemove={toRemove}
        onOpenChange={(o) => { if (!o) setToRemove(null) }}
        onConfirm={async (id) => { await removeException.mutateAsync(id); toast.success('Ausnahme aufgehoben'); setToRemove(null) }}
      />
    </Popover>
  )
}

/** Rückfrage vor dem Aufheben — steht außerhalb des Popovers, damit sie dessen Schließen überlebt. */
function RemoveDialog({ toRemove, onOpenChange, onConfirm }: {
  toRemove: { id: number; label: string } | null
  onOpenChange(open: boolean): void
  onConfirm(id: number): Promise<void>
}) {
  return (
    <ConfirmDialog
      open={toRemove != null}
      onOpenChange={onOpenChange}
      title="Ausnahme aufheben?"
      description={toRemove ? `${toRemove.label}. Die Stunde steht danach wieder so im Plan wie vorher; war sie offen, ist sie wieder offen.` : undefined}
      confirmLabel="Aufheben"
      destructive
      onConfirm={() => (toRemove ? onConfirm(toRemove.id) : Promise.resolve())}
    />
  )
}
