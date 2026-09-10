// Objektwahl zur Sicht: Klasse, Lehrkraft oder Raum (nur aktive). In der Schulsicht unsichtbar.
import type { View } from '@/api/types'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useObjectOptions, VIEW_LABEL } from './object-options'

export function ObjectSelect({ view, value, onChange }: { view: View; value: number | null; onChange(id: number): void }) {
  const { options, isPending, isError } = useObjectOptions(view)
  if (view === 'SCHOOL') return null
  const label = VIEW_LABEL[view]
  return (
    <Select value={value != null ? String(value) : ''} onValueChange={(v) => v && onChange(Number(v))} disabled={isPending || isError}>
      <SelectTrigger className="min-w-44 bg-surface" aria-label={`${label} wählen`}>
        <SelectValue placeholder={isError ? 'Nicht geladen' : isPending ? 'Lädt…' : `${label} wählen`} />
      </SelectTrigger>
      <SelectContent>
        {(options ?? []).map((o) => (
          <SelectItem key={o.id} value={String(o.id)}>{o.label}</SelectItem>
        ))}
        {options && options.length === 0 && <div className="px-2 py-1.5 text-xs text-foreground-subtle">Keine aktiven Einträge</div>}
      </SelectContent>
    </Select>
  )
}
