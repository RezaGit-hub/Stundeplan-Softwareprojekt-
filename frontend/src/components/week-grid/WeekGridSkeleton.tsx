// Ladezustand des Rasters in denselben Maßen (36 px Kopf, 72 px Zeilen). Kein Spinner.
import type { TimeslotDto } from '@/api/types'
import { Skeleton } from '@/components/ui/skeleton'
import { DAY_SHORT, daysOf, maxPositions } from '@/lib/timegrid'
import { cn } from '@/lib/utils'

export function WeekGridSkeleton({ slots, className }: { slots?: TimeslotDto[]; className?: string }) {
  const days = slots ? daysOf(slots) : (['MO', 'TU', 'WE', 'TH', 'FR'] as const)
  const rows = slots ? maxPositions(slots) : 7
  return (
    <div className={cn('overflow-x-auto rounded-lg border bg-card shadow-xs', className)} aria-busy="true" aria-label="Wochenraster lädt">
      <div
        className="week-grid grid min-w-fit"
        style={{ gridTemplateColumns: `64px repeat(${Math.max(days.length, 1)}, minmax(140px, 1fr))`, gridTemplateRows: `36px repeat(${rows}, 72px)` }}
      >
        <div className="border-b border-grid-line bg-background" />
        {days.map((d) => (
          <div key={d} className="flex items-center justify-center border-b border-l border-grid-line bg-background text-xs font-medium text-foreground-subtle">{DAY_SHORT[d]}</div>
        ))}
        {Array.from({ length: rows }, (_, r) => (
          <div key={`row-${r}`} className="contents">
            <div className="flex items-center justify-center border-b border-grid-line"><Skeleton className="h-4 w-8 bg-surface-sunken" /></div>
            {days.map((d) => (
              <div key={`${d}-${r}`} className="border-b border-l border-grid-line p-1">
                <Skeleton className="h-full w-full bg-surface-sunken" />
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}
