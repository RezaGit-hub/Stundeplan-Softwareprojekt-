// Offene Stunden (kein Zeitfenster) stehen nicht im Raster, sondern hier: Klasse · Fach · fehlt n. Klick → Klassensicht.
import { CircleCheck } from 'lucide-react'
import type { DraftDto } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

export function OpenLessonsPanel({ open, onPick, className }: { open: DraftDto['openLessons']; onPick?(classId: number): void; className?: string }) {
  const total = open.reduce((n, o) => n + o.missing, 0)
  return (
    <section className={cn('flex h-full min-h-0 flex-col rounded-lg border bg-card shadow-xs', className)} aria-label="Offene Stunden">
      <header className="flex items-center justify-between gap-2 border-b px-3 py-2">
        <h2 className="text-sm font-semibold">Offene Stunden</h2>
        <Badge variant="outline" className={cn('h-5 tabular-nums', total > 0 ? 'border-warning/50 text-warning' : 'text-foreground-subtle')}>{total}</Badge>
      </header>
      {open.length === 0 ? (
        <p className="flex items-center gap-1.5 px-3 py-4 text-xs text-foreground-subtle">
          <CircleCheck className="size-4 text-success" aria-hidden />
          Alle Stunden sind eingeplant.
        </p>
      ) : (
        <div className="min-h-0 flex-1 overflow-y-auto">
          <table className="w-full text-xs">
            <thead className="sticky top-0 bg-card">
              <tr className="text-left text-foreground-subtle">
                <th className="px-3 py-1.5 font-medium">Klasse</th>
                <th className="py-1.5 font-medium">Fach</th>
                <th className="px-3 py-1.5 text-right font-medium">fehlt</th>
              </tr>
            </thead>
            <tbody>
              {open.map((o) => (
                <tr
                  key={`${o.classId}-${o.subjectShort}`}
                  className={cn('border-t', onPick && 'cursor-pointer transition-colors hover:bg-accent/60')}
                  onClick={onPick ? () => onPick(o.classId) : undefined}
                  title={onPick ? `Klassensicht ${o.classShort} öffnen` : undefined}
                >
                  <td className="px-3 py-1.5 font-medium">{o.classShort}</td>
                  <td className="py-1.5">{o.subjectShort}</td>
                  <td className="px-3 py-1.5 text-right font-semibold tabular-nums">{o.missing}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}