// Legende (ui.md, Text aus lib/entry-kind.ts), auf jeder Plansicht sichtbar und einklappbar.
import { useState } from 'react'
import { ChevronRight, Pin, TriangleAlert } from 'lucide-react'
import { LEGEND } from '@/lib/entry-kind'
import { cn } from '@/lib/utils'

const SWATCH = 'flex size-5 shrink-0 items-center justify-center rounded-sm bg-card text-2xs font-bold'

function Swatch({ kind }: { kind: (typeof LEGEND)[number]['kind'] }) {
  switch (kind) {
    case 'SUBSTITUTED':
      return <span className={cn(SWATCH, 'border-2 border-substitution text-substitution-fg')} aria-hidden>V</span>
    case 'NEEDS_SUBSTITUTION':
      return <span className={cn(SWATCH, 'border-2 border-dashed border-substitution text-substitution-fg')} aria-hidden>!</span>
    case 'CANCELLED':
      return <span className={cn(SWATCH, 'bg-surface-sunken text-foreground-muted line-through')} aria-hidden>✕</span>
    case 'CONFLICT':
      return <span className={cn(SWATCH, 'border-2 border-conflict text-conflict')} aria-hidden><TriangleAlert className="size-3" /></span>
    case 'WARNING':
      return <span className={cn(SWATCH, 'border-2 border-dashed border-conflict text-conflict')} aria-hidden><TriangleAlert className="size-3" /></span>
    case 'PINNED':
      return <span className={cn(SWATCH, 'ring-1 ring-inset ring-pinned text-pinned')} aria-hidden><Pin className="size-3" /></span>
    case 'DRAFT':
      return <span className={cn(SWATCH, 'border border-draft-border bg-draft-bg')} aria-hidden />
    default:
      return <span className={SWATCH} aria-hidden />
  }
}

export function Legend({ defaultOpen = false, className }: { defaultOpen?: boolean; className?: string }) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div className={cn('rounded-lg border bg-card', className)}>
      <button
        type="button"
        className="flex w-full items-center gap-1.5 px-3 py-2 text-xs font-medium text-foreground-subtle transition-colors hover:text-foreground"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <ChevronRight className={cn('size-3.5 transition-transform', open && 'rotate-90')} aria-hidden />
        Legende
      </button>
      {open && (
        <ul className="flex flex-wrap gap-x-5 gap-y-2 border-t px-3 py-2.5 text-xs">
          {LEGEND.map((item) => (
            <li key={item.kind} className="flex items-center gap-2">
              <Swatch kind={item.kind} />
              <span>{item.text}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
