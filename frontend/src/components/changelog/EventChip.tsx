// Ein Eintrag der Zeitachse im Änderungsverlauf: 8-px-Punkt, Zeitpunkt, Aktion, Datum/Std., Klasse, alt → neu, Anlässe, Benutzer.
import { ArrowRight } from 'lucide-react'
import type { ChangelogAction, ChangelogItem, TimeslotDto } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { positionLabel } from '@/components/substitution/position-label'
import { formatDateTime, formatDay } from '@/lib/dates'
import { cn } from '@/lib/utils'

const ACTION_LABEL: Record<ChangelogAction, string> = {
  EXCEPTION_CREATED: 'Vertretung eingetragen',
  EXCEPTION_DELETED: 'Vertretung aufgehoben',
  TIMETABLE_ACCEPTED: 'Plan angenommen',
}
const DOT: Record<ChangelogAction, string> = {
  EXCEPTION_CREATED: 'bg-substitution',
  EXCEPTION_DELETED: 'bg-cancelled',
  TIMETABLE_ACCEPTED: 'bg-success',
}

export function EventChip({ item, slots, className }: { item: ChangelogItem; slots?: TimeslotDto[]; className?: string }) {
  const hasChange = item.oldAssignment || item.newAssignment
  return (
    <li className={cn('relative pl-6', className)}>
      <span className={cn('absolute top-1.5 -left-[5px] size-2 rounded-full ring-4 ring-background', DOT[item.action])} aria-hidden />
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <time dateTime={item.timestamp} className="text-xs text-foreground-subtle tabular-nums">{formatDateTime(item.timestamp)}</time>
        <span className="font-medium">{ACTION_LABEL[item.action]}</span>
        {item.date && (
          <span className="text-xs text-foreground-subtle tabular-nums">
            {formatDay(item.date)}{item.position != null && ` · ${positionLabel(slots, item.date, item.position)} Stunde`}
          </span>
        )}
        {item.classShort && <Badge variant="outline">{item.classShort}</Badge>}
      </div>
      {hasChange && (
        <div className="mt-1 flex flex-wrap items-center gap-2 text-sm">
          {item.oldAssignment && <s className="text-foreground-subtle">{item.oldAssignment}</s>}
          {item.oldAssignment && item.newAssignment && <ArrowRight className="size-3.5 text-substitution-fg" aria-hidden />}
          {item.newAssignment && <span className="font-semibold text-substitution-fg">{item.newAssignment}</span>}
        </div>
      )}
      <div className="mt-0.5 flex flex-wrap gap-x-3 text-xs text-foreground-subtle">
        {item.reasons && <span>Anlass: {item.reasons}</span>}
        <span>von {item.username}</span>
      </div>
    </li>
  )
}
