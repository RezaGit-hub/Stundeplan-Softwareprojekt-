// „Heute geändert: n Stunden" (Soll E-40) — Einträge des heutigen Tages mit kind != REGULAR. Bei 0 unsichtbar.
import { BellRing } from 'lucide-react'
import { cn } from '@/lib/utils'

export function ChangedTodayBar({ count, className }: { count: number; className?: string }) {
  if (count <= 0) return null
  return (
    <div className={cn('flex items-center gap-2 rounded-lg bg-substitution-soft px-3 py-2 text-sm font-medium text-substitution-fg', className)} role="status">
      <BellRing className="size-4" aria-hidden />
      Heute geändert: {count} {count === 1 ? 'Stunde' : 'Stunden'}
    </div>
  )
}
