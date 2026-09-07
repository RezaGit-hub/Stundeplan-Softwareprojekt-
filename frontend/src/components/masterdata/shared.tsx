// Kleine Bausteine, die alle Grunddaten-Seiten teilen: Suche, Badges, Zeilenmenü, 409-„in-use"-Dialog, Formularzeile.
// Liegt im W2-Ordner (SPEZIFIKATION 8: Gemeinsames zuerst im eigenen Ordner, Wunsch in AGENT-NOTIZEN.md).
// Nicht-Komponenten (Beschriftungen, Filter, Lösch-Hook) stehen in helpers.ts.
import { useState, type ReactNode } from 'react'
import { AlertTriangle, MoreHorizontal, Search, SearchX, type LucideIcon } from 'lucide-react'
import type { ProblemDetail } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { EmptyState } from '@/components/states/EmptyState'
import { cn } from '@/lib/utils'

// ------------------------------------------------------------------ Suche
export function SearchField({ value, onChange, placeholder = 'Suchen…', className }: {
  value: string
  onChange(next: string): void
  placeholder?: string
  className?: string
}) {
  return (
    <div className={cn('relative', className)}>
      <Search className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-foreground-subtle" aria-hidden />
      <Input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className="w-56 bg-surface pl-8"
      />
    </div>
  )
}

export function NoMatches({ query }: { query: string }) {
  return <EmptyState icon={SearchX} title={`Keine Treffer für „${query}"`} description="Anderen Suchbegriff eingeben oder Suche leeren." />
}

// ------------------------------------------------------------------ Badges, Chips
export function ActiveBadge({ active }: { active: boolean }) {
  return active
    ? <Badge variant="outline" className="gap-1.5 text-foreground-muted"><span className="size-1.5 rounded-full bg-success" aria-hidden />aktiv</Badge>
    : <Badge variant="secondary" className="text-foreground-subtle">deaktiviert</Badge>
}

/** Grunddaten-Warnung: amber Chip mit „!" und dem Servertext (ui.md); Speichern bleibt möglich */
export function WarningChip({ message, short, className }: { message: string; short?: boolean; className?: string }) {
  return (
    <span
      title={message}
      className={cn('inline-flex max-w-full items-center gap-1 rounded-sm bg-warning-surface px-1.5 py-0.5 text-2xs font-medium text-foreground', className)}
    >
      <AlertTriangle className="size-3 shrink-0" aria-hidden />
      <span className={cn(short && 'truncate')}>{message}</span>
    </span>
  )
}

/** „3 / 25" mit dünnem Balken — Deputat einer Lehrkraft */
export function QuotaMeter({ planned, quota }: { planned: number; quota: number }) {
  const over = planned > quota
  const pct = quota > 0 ? Math.min(100, Math.round((planned / quota) * 100)) : 0
  return (
    <div className="flex min-w-28 items-center gap-2">
      <span className={cn('tabular-nums', over ? 'font-semibold text-warning' : 'text-foreground')}>
        {planned} <span className="text-foreground-subtle">/ {quota}</span>
      </span>
      <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-sunken" aria-hidden>
        <span className={cn('block h-full rounded-full', over ? 'bg-warning' : 'bg-primary/70')} style={{ width: `${pct}%` }} />
      </span>
    </div>
  )
}

/** Farbpunkt einer Fachfarbe (einzige erlaubte Inline-Farbe: subject.color) */
export function ColorDot({ color, className }: { color: string; className?: string }) {
  return <span className={cn('inline-block size-2.5 shrink-0 rounded-full ring-1 ring-black/10 ring-inset dark:ring-white/15', className)} style={{ background: color }} aria-hidden />
}

// ------------------------------------------------------------------ Zeilenmenü
export interface RowMenuItem { label: string; icon?: LucideIcon; onClick(): void; destructive?: boolean; separatorBefore?: boolean }

export function RowMenu({ items, label = 'Aktionen' }: { items: RowMenuItem[]; label?: string }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon-sm" aria-label={label} onClick={(e) => e.stopPropagation()}>
          <MoreHorizontal />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
        {items.map((it) => (
          <div key={it.label}>
            {it.separatorBefore && <DropdownMenuSeparator />}
            <DropdownMenuItem variant={it.destructive ? 'destructive' : 'default'} onSelect={it.onClick}>
              {it.icon && <it.icon />}
              {it.label}
            </DropdownMenuItem>
          </div>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

// ------------------------------------------------------------------ 409 „in-use"
/** „Wird noch verwendet — deaktivieren?": Titel und detail vom Server, optional Knopf „Deaktivieren" (T-W1.3) */
export function InUseDialog({ problem, onOpenChange, onDeactivate, deactivateLabel = 'Deaktivieren' }: {
  problem: ProblemDetail | null
  onOpenChange(open: boolean): void
  onDeactivate?(): void | Promise<void>
  deactivateLabel?: string
}) {
  const [pending, setPending] = useState(false)
  const deactivate = async () => {
    if (!onDeactivate) return
    setPending(true)
    try { await onDeactivate(); onOpenChange(false) } finally { setPending(false) }
  }
  return (
    <Dialog open={problem != null} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2"><AlertTriangle className="size-4 text-warning" aria-hidden />{problem?.title}</DialogTitle>
          <DialogDescription>{problem?.detail}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>{onDeactivate ? 'Abbrechen' : 'Verstanden'}</Button>
          {onDeactivate && <Button onClick={deactivate} disabled={pending} autoFocus>{deactivateLabel}</Button>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ------------------------------------------------------------------ Formularzeile, Karte
export function Field({ label, htmlFor, hint, error, children, className }: {
  label: string
  htmlFor?: string
  hint?: string
  error?: string
  children: ReactNode
  className?: string
}) {
  return (
    <div className={cn('grid content-start gap-1.5', className)}>
      <label htmlFor={htmlFor} className="text-xs font-medium text-foreground-muted">{label}</label>
      {children}
      {error ? <p role="alert" className="text-xs text-destructive">{error}</p> : hint ? <p className="text-xs text-foreground-subtle">{hint}</p> : null}
    </div>
  )
}

/** Karte mit Titel, Beschreibung und Inhalt (Einstellungen, Detailseiten) */
export function SectionCard({ title, description, actions, children, className }: {
  title: string
  description?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={cn('rounded-lg border bg-card shadow-xs', className)}>
      <header className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div>
          <h2 className="text-base font-semibold">{title}</h2>
          {description && <p className="mt-0.5 text-xs text-foreground-subtle">{description}</p>}
        </div>
        {actions}
      </header>
      <div className="px-5 py-4">{children}</div>
    </section>
  )
}
