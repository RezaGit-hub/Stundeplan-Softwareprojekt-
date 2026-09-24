// Entwurfsbanner (ui.md): vollbreit amber, „ENTWURF — nicht aktiv", Kennzahlen, Aktionen je Status. Der Unterschied
// Entwurf/aktiv ist zentrale Fachlogik — deshalb Text, nicht nur ein Symbol.
import { useEffect, useState } from 'react'
import { motion } from 'motion/react'
import { Check, PencilRuler, Play, Square, Trash2 } from 'lucide-react'
import type { DraftDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { EASE, SLOW } from '@/lib/motion'
import { now } from '@/lib/today'
import { cn } from '@/lib/utils'
import { openCount } from '@/lib/draft'

/** Sekunden seit startedAt, Ticker 1 s; 0 ohne Start */
function useElapsedSeconds(startedAt: string | null): number {
  const [elapsed, setElapsed] = useState(0)
  useEffect(() => {
    if (!startedAt) return
    const started = new Date(startedAt).getTime()
    const tick = () => setElapsed(Math.max(0, Math.round((now().getTime() - started) / 1000)))
    const id = setInterval(tick, 1000)
    tick()
    return () => clearInterval(id)
  }, [startedAt])
  return startedAt ? elapsed : 0
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: 'conflict' | 'warn' }) {
  return (
    <span className="inline-flex items-baseline gap-1 whitespace-nowrap">
      <span className={cn('text-base font-semibold tabular-nums', tone === 'conflict' && value > 0 && 'text-conflict', tone === 'warn' && value > 0 && 'text-warning')}>{value}</span>
      <span className="text-xs">{label}</span>
    </span>
  )
}

export function DraftBanner({ draft, onSolve, onCancel, onAccept, onDiscard, pending = false, className }: {
  draft: DraftDto
  onSolve(): void
  onCancel(): void
  onAccept(): void
  onDiscard(): void
  /** eine Aktion läuft gerade (Knöpfe gesperrt) */
  pending?: boolean
  className?: string
}) {
  const placed = draft.lessons.filter((l) => l.timeslotId != null).length
  const open = openCount(draft.openLessons)
  const hard = draft.conflicts.filter((c) => c.severity === 'HARD').length
  const soft = draft.conflicts.length - hard
  const solving = draft.status === 'SOLVING'
  const elapsed = useElapsedSeconds(solving ? draft.solve.startedAt : null)
  const limit = draft.solve.limitSeconds
  const pct = limit ? Math.min(100, (elapsed / limit) * 100) : 0

  return (
    <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} transition={{ duration: SLOW, ease: EASE }} className={cn('overflow-hidden', className)}>
      <div className="rounded-lg border border-draft-border bg-draft-bg px-4 py-3 text-draft-fg" role="status" aria-live="polite" aria-busy={solving || undefined}>
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
          <div className="flex items-center gap-2">
            <PencilRuler className="size-4" aria-hidden />
            <span className="font-semibold tracking-wide">ENTWURF</span>
            <span>— nicht aktiv</span>
          </div>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
            <Stat label="belegt" value={placed} />
            <Stat label="offen" value={open} tone="warn" />
            <Stat label={hard === 1 ? 'Konflikt hart' : 'Konflikte hart'} value={hard} tone="conflict" />
            <Stat label="weich" value={soft} tone="conflict" />
          </div>
          <div className="ml-auto flex flex-wrap items-center gap-2">
            {draft.status === 'EDITING' && (
              <>
                {/* In der Ablage (#116) ist Berechnen serverseitig gesperrt (409 draft-finished) — der Knopf
                    verschwindet, statt in einen Fehler zu führen. Annehmen und Verwerfen bleiben erlaubt. */}
                {!draft.finished && <Button size="sm" onClick={onSolve} disabled={pending}><Play data-icon="inline-start" /> Berechnen</Button>}
                <Button size="sm" variant="outline" onClick={onAccept} disabled={pending}><Check data-icon="inline-start" /> Annehmen</Button>
                <Button size="sm" variant="outline" onClick={onDiscard} disabled={pending}><Trash2 data-icon="inline-start" /> Verwerfen</Button>
              </>
            )}
            {solving && <Button size="sm" variant="outline" onClick={onCancel} disabled={pending}><Square data-icon="inline-start" /> Abbrechen</Button>}
            {draft.status === 'FAILED' && (
              <>
                {/* Dieselbe Sperre wie oben: in der Ablage (#116) ist Berechnen serverseitig gesperrt
                    (409 `draft-finished`) — auch die Wiederholung nach einem Fehlschlag. */}
                {!draft.finished && <Button size="sm" onClick={onSolve} disabled={pending}><Play data-icon="inline-start" /> Erneut berechnen</Button>}
                <Button size="sm" variant="outline" onClick={onDiscard} disabled={pending}><Trash2 data-icon="inline-start" /> Verwerfen</Button>
              </>
            )}
          </div>
        </div>
        {solving && (
          <div className="mt-2.5">
            <div className="flex items-center justify-between text-xs">
              <span>Berechnung läuft — Stunden werden belegt, festgelegte Stunden bleiben.</span>
              <span className="tabular-nums">{elapsed} s{limit ? ` / ${limit} s` : ''}</span>
            </div>
            <Progress value={limit ? pct : undefined} className="mt-1.5 h-1.5 bg-draft-border [&>[data-slot=progress-indicator]]:bg-draft-fg" aria-label="Fortschritt der Berechnung" />
          </div>
        )}
        {draft.status === 'FAILED' && (
          <p className="mt-2 text-xs">Berechnung fehlgeschlagen{draft.solve.error ? `: ${draft.solve.error}` : '.'}</p>
        )}
        {/* Nur der Hinweis, dass gerechnet wurde. Der rohe Timefold-Score („0hard/-1medium/-6soft") ist keine Schulsprache,
            und seine drei Zahlen stehen als „offen", „Konflikte hart" und „weich" schon oben — dort aus dem aktuellen Stand
            gerechnet, hier aus dem Ende des Laufs: zwei Zahlenpaare, die sich widersprechen koennen. */}
        {draft.status === 'EDITING' && draft.solve.lastScore && (
          <p className="mt-1.5 text-xs opacity-80">Letzte Berechnung abgeschlossen.</p>
        )}
      </div>
    </motion.div>
  )
}
