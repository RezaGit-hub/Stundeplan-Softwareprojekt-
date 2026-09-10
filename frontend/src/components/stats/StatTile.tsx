import type { LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { cn } from '@/lib/utils'

const TONE: Record<'default' | 'warning' | 'destructive', string> = {
  default: 'text-foreground',
  warning: 'text-warning',
  destructive: 'text-destructive',
}

/** Kennzahl-Kachel (Tremor-Stil). Als Link, wenn `to` gesetzt ist. Zahlen werden nicht animiert. */
export function StatTile({ label, value, hint, tone = 'default', icon: Icon, to, className }: {
  label: string
  value: number | string
  hint?: string
  tone?: 'default' | 'warning' | 'destructive'
  icon?: LucideIcon
  to?: string
  className?: string
}) {
  const body = (
    <>
      <div className="flex items-start justify-between gap-2">
        <span className="text-2xs font-medium tracking-wide text-foreground-subtle uppercase">{label}</span>
        {Icon && <Icon className="size-4 text-foreground-subtle" aria-hidden />}
      </div>
      <div className={cn('mt-2 text-2xl font-semibold tabular-nums', TONE[tone])}>{value}</div>
      {hint && <div className="mt-1 text-xs text-foreground-subtle">{hint}</div>}
    </>
  )
  const classes = cn('stat-tile block rounded-lg border bg-card p-4 shadow-xs', to && 'transition-colors hover:bg-accent/60', className)
  return to ? <Link to={to} className={classes}>{body}</Link> : <div className={classes}>{body}</div>
}
