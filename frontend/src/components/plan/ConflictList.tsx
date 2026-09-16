// Konfliktliste rechts neben dem Raster: Meldung vom Server, Objekte als Chips; Klick springt zur Stunde (kein Modal).
// Mit onRepair steht an jedem Konflikt ein Knopf „Lösen" (#110) — nur im Entwurf, solange er bearbeitbar ist.
import { CircleCheck, Wrench } from 'lucide-react'
import type { ConflictDto } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { ConflictItem } from '@/components/week-grid/ConflictPopover'
import { cn } from '@/lib/utils'

export function ConflictList({ conflicts, onJump, onRepair, title = 'Konflikte', className }: { conflicts: ConflictDto[]; onJump(lessonId: number): void; onRepair?(conflict: ConflictDto): void; title?: string; className?: string }) {
  const hard = conflicts.filter((c) => c.severity === 'HARD').length
  const soft = conflicts.length - hard
  return (
    <section className={cn('flex flex-col rounded-lg border bg-card shadow-xs', className)} aria-label={title}>
      <header className="flex items-center justify-between gap-2 border-b px-3 py-2">
        <h2 className="text-sm font-semibold">{title}</h2>
        <div className="flex items-center gap-1">
          <Badge variant="outline" className={cn('h-5 tabular-nums', hard > 0 ? 'border-conflict/50 text-conflict' : 'text-foreground-subtle')}>{hard} hart</Badge>
          <Badge variant="outline" className={cn('h-5 border-dashed tabular-nums', soft > 0 ? 'border-conflict/50 text-conflict' : 'text-foreground-subtle')}>{soft} weich</Badge>
        </div>
      </header>
      {conflicts.length === 0 ? (
        <div className="flex flex-col items-center gap-1 px-3 py-6 text-center text-xs text-foreground-subtle">
          <CircleCheck className="size-5 text-success" aria-hidden />
          Keine Konflikte
        </div>
      ) : (
        <ScrollArea className="[&>[data-slot=scroll-area-viewport]]:max-h-[440px]">
          <ul className="divide-y">
            {conflicts.map((c, i) => (
              <li key={`${c.type}-${c.lessonIds.join('.')}-${i}`} className="flex items-start">
                <button
                  type="button"
                  className="min-w-0 flex-1 px-3 py-2 text-left transition-colors hover:bg-accent/60 focus-visible:bg-accent/60 focus-visible:outline-none"
                  onClick={() => c.lessonIds[0] != null && onJump(c.lessonIds[0])}
                  title="Zur Stunde springen"
                >
                  <ConflictItem conflict={c} />
                </button>
                {onRepair && c.lessonIds.length > 0 && (
                  <Button size="xs" variant="ghost" className="mr-2 mt-1.5 shrink-0" onClick={() => onRepair(c)} title="Vorschläge zum Auflösen berechnen">
                    <Wrench data-icon="inline-start" /> Lösen
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </ScrollArea>
      )}
    </section>
  )
}
