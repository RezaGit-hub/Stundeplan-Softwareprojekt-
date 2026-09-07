// Konflikte einer Stunde: Popover am ⚠-Badge (Text kommt vom Server). ConflictItem wird auch in der Konfliktliste benutzt.
import type { ReactNode } from 'react'
import { TriangleAlert } from 'lucide-react'
import type { ConflictDto } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Popover, PopoverContent, PopoverHeader, PopoverTitle, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

/** Eine Konfliktzeile: ⚠ rot (durchgezogen = hart, gestrichelt = weich) + Meldung + Objekte als Chips */
export function ConflictItem({ conflict, compact = false }: { conflict: ConflictDto; compact?: boolean }) {
  const hard = conflict.severity === 'HARD'
  return (
    <div className="flex items-start gap-2">
      <span
        className={cn('mt-px flex size-5 shrink-0 items-center justify-center rounded-sm border-2 border-conflict text-conflict', !hard && 'border-dashed')}
        aria-label={hard ? 'Konflikt' : 'Warnung'}
        title={hard ? 'Konflikt (hart)' : 'Warnung (weich)'}
      >
        <TriangleAlert className="size-3" aria-hidden />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-xs leading-snug">{conflict.message}</p>
        {!compact && conflict.objects.length > 0 && (
          <div className="mt-1 flex flex-wrap gap-1">
            {conflict.objects.map((o) => (
              <Badge key={`${o.kind}-${o.id}`} variant="outline" className="h-4 px-1.5 text-2xs font-medium">{o.label}</Badge>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

export function ConflictPopover({ conflicts, children }: { conflicts: ConflictDto[]; children: ReactNode }) {
  const hard = conflicts.filter((c) => c.severity === 'HARD').length
  const soft = conflicts.length - hard
  const title = [hard > 0 && `${hard} ${hard === 1 ? 'Konflikt' : 'Konflikte'}`, soft > 0 && `${soft} ${soft === 1 ? 'Warnung' : 'Warnungen'}`].filter(Boolean).join(' · ')
  return (
    <Popover>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent align="start" side="bottom" className="w-80" onClick={(e) => e.stopPropagation()} onPointerDown={(e) => e.stopPropagation()}>
        <PopoverHeader>
          <PopoverTitle>{title}</PopoverTitle>
        </PopoverHeader>
        <ul className="space-y-2.5">
          {conflicts.map((c, i) => (
            <li key={`${c.type}-${i}`}><ConflictItem conflict={c} /></li>
          ))}
        </ul>
      </PopoverContent>
    </Popover>
  )
}
