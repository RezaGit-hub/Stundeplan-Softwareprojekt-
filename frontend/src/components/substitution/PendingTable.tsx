// Mitte der Vertretungsseite: offene Stunden der Woche (GET /substitutions/pending), nach Tag gruppiert.
// Offene Zeile: orange gestrichelter Linksrand. Aufgelöste Zeile (resolved): orange durchgezogen, alte Werte durchgestrichen.
import { Fragment, useMemo } from 'react'
import { motion } from 'motion/react'
import { ArrowRight, CheckCircle2, Mail, Undo2 } from 'lucide-react'
import { usePending, useTimegrid, useSubstitutionRequests } from '@/api/hooks'
import type { PendingRow } from '@/api/types'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDay } from '@/lib/dates'
import { listItem } from '@/lib/motion'
import { cn } from '@/lib/utils'
import { positionLabel, slotTime } from './position-label'
import { rowKey, type ResolvedRow, type SelectedLesson } from './types'

type Line = { key: string; row: PendingRow; resolved: ResolvedRow | null }

/** Offene und aufgelöste Zeilen in Tabellenreihenfolge (Datum, Position, Klasse) */
function mergeLines(pending: PendingRow[], resolved: ResolvedRow[]): Line[] {
  const byKey = new Map(resolved.map((r) => [rowKey(r.row), r]))
  const lines: Line[] = pending.filter((r) => !byKey.has(rowKey(r))).map((r) => ({ key: rowKey(r), row: r, resolved: null }))
  for (const r of resolved) lines.push({ key: rowKey(r.row), row: r.row, resolved: r })
  return lines.sort((a, b) => a.row.date.localeCompare(b.row.date) || a.row.position - b.row.position || a.row.classShort.localeCompare(b.row.classShort))
}

function Old({ children }: { children: string }) {
  return <s className="text-foreground-subtle">{children}</s>
}
function New({ children }: { children: string }) {
  return <span className="font-bold text-substitution-fg">{children}</span>
}

export function PendingTable({ from, to, selected, onSelect, resolved = [], onUndo }: {
  from: string
  to: string
  selected: SelectedLesson | null
  onSelect(row: PendingRow): void
  resolved?: ResolvedRow[]
  /** Aufgeloeste Zeile wieder oeffnen: DELETE /api/exceptions/{id} (FRONTEND-2). Fehlt der Rueckruf, bleibt die Zeile stehen. */
  onUndo?(resolved: ResolvedRow): void
}) {
  const pending = usePending(from, to)
  const timegrid = useTimegrid()
  const requests = useSubstitutionRequests()
  // Nur ob überhaupt eine Anfrage existiert, nicht welcher Status — ein zurückgezogener Wunsch
  // (Hard-Delete) hinterlässt keine Zeile und ist damit ununterscheidbar von "nie angefragt".
  const requestedKeys = useMemo(() => new Set((requests.data ?? []).map((r) => `${r.date}|${r.lessonId}`)), [requests.data])
  const slots = timegrid.data?.slots

  if (pending.isPending) {
    return (
      <div className="space-y-2 rounded-lg border bg-card p-4" aria-busy="true" aria-label="Lädt offene Stunden">
        <Skeleton className="h-4 w-40 bg-surface-sunken" />
        <Skeleton className="h-9 w-full bg-surface-sunken" />
        <Skeleton className="h-9 w-full bg-surface-sunken" />
        <Skeleton className="h-9 w-2/3 bg-surface-sunken" />
      </div>
    )
  }
  if (pending.isError) return <ErrorState problem={pending.error} retry={() => void pending.refetch()} />

  const lines = mergeLines(pending.data, resolved)
  if (lines.length === 0) {
    return (
      <EmptyState
        icon={CheckCircle2}
        title="Keine offenen Stunden in dieser Woche"
        description="Ein Ausfall oder eine Raumsperrung links erzeugt hier die betroffenen Stunden."
        className="h-full min-h-64"
      />
    )
  }

  const days = [...new Set(lines.map((l) => l.row.date))]
  let i = 0
  return (
    <div className="overflow-hidden rounded-lg border bg-card shadow-xs">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-14 text-xs">Std.</TableHead>
            <TableHead className="w-16 text-xs">Klasse</TableHead>
            <TableHead className="w-16 text-xs">Fach</TableHead>
            <TableHead className="text-xs">Lehrkraft</TableHead>
            <TableHead className="text-xs">Raum</TableHead>
            <TableHead className="text-xs">Anlass</TableHead>
            <TableHead className="w-24 text-xs"><span className="sr-only">Rückgängig</span></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {days.map((day) => {
            const ofDay = lines.filter((l) => l.row.date === day)
            const open = ofDay.filter((l) => !l.resolved).length
            return (
              <Fragment key={day}>
                <TableRow className="bg-surface-sunken/70 hover:bg-surface-sunken/70">
                  <TableCell colSpan={7} className="py-1.5 text-2xs font-medium tracking-wide text-foreground-subtle uppercase">
                    {formatDay(day)} · {open === 0 ? 'alles besetzt' : `${open} offen`}
                  </TableCell>
                </TableRow>
                {ofDay.map((l) => {
                  const r = l.row
                  const res = l.resolved
                  const isSelected = !res && selected != null && rowKey(selected) === l.key
                  const idx = i++
                  return (
                    <motion.tr
                      key={l.key}
                      {...listItem(idx)}
                      // Offene Zeile ist auswählbar — auch mit Tastatur (Tab, dann Enter oder Leertaste).
                      onClick={res ? undefined : () => onSelect(r)}
                      onKeyDown={res ? undefined : (e) => {
                        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(r) }
                      }}
                      tabIndex={res ? undefined : 0}
                      aria-selected={isSelected}
                      // Kein aria-label: der Name der Zeile ist ihr Inhalt (Stunde, Klasse, Fach, Lehrkraft, Raum, Anlass).
                      className={cn(
                        'border-b border-l-2 border-l-substitution transition-colors last:border-b-0',
                        res ? '[border-left-style:solid]' : 'cursor-pointer [border-left-style:dashed] hover:bg-substitution-soft/60 focus-visible:bg-substitution-soft/60 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring',
                        isSelected && 'bg-substitution-soft ring-1 ring-substitution/40 ring-inset',
                      )}
                    >
                      <TableCell className="tabular-nums">
                        <span className="font-semibold">{positionLabel(slots, r.date, r.position)}</span>
                        <span className="ml-1 text-2xs text-foreground-subtle">{slotTime(slots, r.date, r.position)}</span>
                      </TableCell>
                      <TableCell className="font-medium">{r.classShort}</TableCell>
                      <TableCell>{res?.kind === 'CANCEL' ? <Old>{r.subjectShort}</Old> : r.subjectShort}</TableCell>
                      <TableCell>
                        {res?.teacherShort ? <><Old>{r.teacherShort}</Old> <ArrowRight className="inline size-3 text-substitution-fg" aria-hidden /> <New>{res.teacherShort}</New></> : r.teacherShort}
                      </TableCell>
                      <TableCell>
                        {res?.roomShort ? <><Old>{r.roomShort}</Old> <ArrowRight className="inline size-3 text-substitution-fg" aria-hidden /> <New>{res.roomShort}</New></> : r.roomShort}
                      </TableCell>
                                            <TableCell className="max-w-56 text-xs whitespace-normal text-foreground-muted">
                        {res?.kind === 'CANCEL' && <span className="mr-2 font-semibold text-cancelled">✕ entfällt</span>}
                        {res?.kind === 'MOVE' && <New>{`verlegt → ${res.target ?? ''}`}</New>}
                        {res?.kind === 'SUBSTITUTION' && <span className="mr-2 rounded-sm border border-substitution px-1 text-2xs font-semibold text-substitution-fg">V</span>}
                        {!res && requestedKeys.has(`${r.date}|${r.lessonId}`) && (
                          <span className="mr-2 inline-flex items-center gap-0.5 rounded-sm border border-warning/40 bg-warning-soft px-1 text-2xs font-medium text-warning">
                            <Mail className="size-2.5" aria-hidden /> angefragt
                          </span>
                        )}
                        {!res && <span className="mr-2 rounded-sm border border-dashed border-substitution px-1 text-2xs font-semibold text-substitution-fg">!</span>}
                        {r.reasons.join('; ')}
                      </TableCell>
                      <TableCell className="py-1 text-right">
                        {res && onUndo && (
                          // Ohne diesen Weg bliebe eine falsche Zuweisung nur per curl korrigierbar: eine zweite
                          // Ausnahme auf dieselbe Stunde beantwortet der Server mit 409 (FRONTEND-2).
                          <Button
                            size="xs"
                            variant="ghost"
                            className="text-foreground-subtle"
                            onClick={(e) => { e.stopPropagation(); onUndo(res) }}
                          >
                            <Undo2 data-icon="inline-start" /> Rückgängig
                          </Button>
                        )}
                      </TableCell>
                    </motion.tr>
                  )
                })}
              </Fragment>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}
