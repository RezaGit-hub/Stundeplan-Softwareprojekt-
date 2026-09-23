// /vertretung?datum= — das Leitszenario in EINER Ansicht: links Ereignis erfassen, Mitte offene Stunden der Woche,
// rechts Optionen der markierten Stunde. Zuweisen speichert und markiert die nächste offene Stunde.
import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { useDeleteException, usePending } from '@/api/hooks'
import type { PendingRow } from '@/api/types'
import { PageHeader } from '@/components/layout/PageHeader'
import { ProblemBanner } from '@/components/states/ProblemBanner'
import { WeekPicker } from '@/components/plan/WeekPicker'
import { EventForm } from '@/components/substitution/EventForm'
import { AbsenceNoticeCard } from '@/components/substitution/AbsenceNoticeCard'
import { EventList } from '@/components/substitution/EventList'
import { OptionsPanel } from '@/components/substitution/OptionsPanel'
import { PendingTable } from '@/components/substitution/PendingTable'
import { rowKey, type ResolvedRow, type SelectedLesson } from '@/components/substitution/types'
import { between, formatDay, weekRange } from '@/lib/dates'
import { today } from '@/lib/today'


const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

export function SubstitutionPage() {
  const [params, setParams] = useSearchParams()
  const raw = params.get('datum')
  const datum = raw && ISO_DATE.test(raw) ? raw : today()
  const { from, to } = weekRange(datum)
  const setDatum = (d: string) => setParams((prev) => { prev.set('datum', d); return prev }, { replace: true })

  const pending = usePending(from, to)
  const removeException = useDeleteException()
  const [selected, setSelected] = useState<SelectedLesson | null>(null)
  const [resolved, setResolved] = useState<ResolvedRow[]>([])
  const [last, setLast] = useState<ResolvedRow | null>(null)

  const [leftEl, setLeftEl] = useState<HTMLDivElement | null>(null)
  const [leftHeight, setLeftHeight] = useState<number>()

  useEffect(() => {
    if (!leftEl) return
    const observer = new ResizeObserver(([entry]) => setLeftHeight(entry.contentRect.height))
    observer.observe(leftEl)
    return () => observer.disconnect()
  }, [leftEl])

  // Markierte Stunde: die gewählte, sonst die erste noch offene der Woche (abgeleitet, kein Effekt)
  const resolvedKeys = new Set(resolved.map((r) => rowKey(r.row)))
  const openRows = (pending.data ?? []).filter((r) => !resolvedKeys.has(rowKey(r)))
  const selectedRow: PendingRow | null = (selected && openRows.find((r) => rowKey(r) === rowKey(selected))) ?? openRows[0] ?? null

  const changeWeek = (d: string) => {
    setDatum(d)
    if (!between(d, from, to)) { setSelected(null); setResolved([]); setLast(null) }
  }

  const onAssigned = (result: ResolvedRow) => {
    const key = rowKey(result.row)
    const idx = openRows.findIndex((r) => rowKey(r) === key)
    const rest = openRows.filter((r) => rowKey(r) !== key)
    const next = rest[idx] ?? rest[0] ?? null    // nächste offene Stunde nach der erledigten, sonst die erste
    setResolved((list) => [...list.filter((r) => rowKey(r.row) !== key), result])
    setSelected(next ? { date: next.date, lessonId: next.lessonId } : null)
    setLast(result)
  }

  
  const relatedRows = selectedRow ? (pending.data ?? []).filter((r) => r.eventIds.some((id) => selectedRow.eventIds.includes(id))) : []
  

  /**
   * Eine falsche Zuweisung zuruecknehmen (FRONTEND-2): die Ausnahme loeschen, die Zeile wieder oeffnen
   * und sie gleich markieren. Ohne diesen Weg beantwortet der Server jede zweite Ausnahme derselben
   * Stunde mit 409 `exception-exists` und die Stunde bliebe im Web unkorrigierbar.
   */
  const onUndo = async (res: ResolvedRow) => {
    await removeException.mutateAsync(res.exceptionId)
    const key = rowKey(res.row)
    setResolved((list) => list.filter((r) => rowKey(r.row) !== key))
    setLast((l) => (l && rowKey(l.row) === key ? null : l))
    setSelected({ date: res.row.date, lessonId: res.row.lessonId })
    toast.success('Eintrag zurückgenommen — die Stunde ist wieder offen')
  }

  const onCreated = (ev: { dateFrom: string }) => {
    if (!between(ev.dateFrom, from, to)) changeWeek(ev.dateFrom)
  }

  return (
    <>
      <PageHeader
        title="Vertretung"
        description="Ausfälle erfassen, offene Stunden besetzen — alles auf einer Seite."
        actions={<WeekPicker monday={from} onChange={changeWeek} />}
      />
      <p className="-mt-3 mb-4 text-xs text-foreground-subtle tabular-nums">
        {openRows.length === 0 ? 'Keine offene Stunde in dieser Woche' : `${openRows.length} offene ${openRows.length === 1 ? 'Stunde' : 'Stunden'} in dieser Woche`}
      </p>

      <div className="grid items-start gap-5 xl:grid-cols-[17rem_minmax(0,1fr)_19rem]">
        <div ref={setLeftEl} className="space-y-4">
          <EventForm defaultDate={datum} onCreated={onCreated} />
          <AbsenceNoticeCard from={from} to={to} />
          <EventList from={from} to={to} />
        </div>
        
        <div className="min-w-0">
          <PendingTable
            from={from}
            to={to}
            selected={selectedRow ? { date: selectedRow.date, lessonId: selectedRow.lessonId } : null}
            onSelect={(r) => setSelected({ date: r.date, lessonId: r.lessonId })}
            resolved={resolved}
            onUndo={(res) => void onUndo(res)}
          />
        </div>
        
        <div
          className="min-h-0 space-y-3 overflow-y-auto xl:sticky xl:top-[68px]"
          style={leftHeight ? { maxHeight: leftHeight } : undefined}
        >
          <div className="min-h-64">
            <OptionsPanel
              key={selectedRow ? rowKey(selectedRow) : 'none'}
              row={selectedRow}
              onAssigned={onAssigned}
              relatedRows={relatedRows}
              
            />
          </div>
          {last && last.conflicts.length > 0 && (
            <ProblemBanner problem={{ type: 'urn:pushnpull:problem:follow-up-conflicts', title: `Folgekonflikte: ${last.row.classShort}, ${formatDay(last.row.date)}`, status: 200, conflicts: last.conflicts }} />
          )}
          {last && last.conflicts.length === 0 && (
            <p className="rounded-md bg-success-soft px-3 py-2 text-xs text-success" role="status">
              {last.row.classShort} {last.row.subjectShort}, {formatDay(last.row.date)}: {last.kind === 'CANCEL' ? 'Entfall eingetragen' : last.kind === 'MOVE' ? `verlegt → ${last.target ?? ''}` : 'Vertretung zugewiesen'} — ohne Folgekonflikt.
            </p>
          )}
        </div>
      </div>
    </>
  )
}
