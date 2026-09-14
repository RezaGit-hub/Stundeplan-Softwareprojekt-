// Regelwerk: eine Zeile = eine Regel = eine Mutation (PUT /rules/{id}). Wert < 1 → Feldfehler, alter Wert bleibt (T-W2.1).
import { useState } from 'react'
import { toast } from 'sonner'
import { Save, Trash2 } from 'lucide-react'
import { useDeleteRule, useUpdateRule } from '@/api/hooks'
import type { RuleDto, RuleInput, Severity } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { FieldError } from '@/components/forms/FieldError'
import { useFieldErrors } from '@/lib/form-errors'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { cn } from '@/lib/utils'
import { SCOPE_LABEL } from './helpers'


const toInput = (r: RuleDto): RuleInput => ({ value: r.value, severity: r.severity, weight: r.weight, active: r.active })
const sig = (r: RuleInput) => `${r.value}|${r.severity}|${r.weight}|${r.active}`

export function RulesTable({ rules }: { rules: RuleDto[] }) {
  const sorted = [...rules].sort((a, b) => (a.grade ?? 0) - (b.grade ?? 0) || (a.scope ?? 'Z').localeCompare(b.scope ?? 'Z') || a.id - b.id)
  return (
    <div className="overflow-x-auto overflow-y-visible rounded-lg border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="text-xs">Regel</TableHead>
            <TableHead className="w-28 text-xs">Zähleinheit</TableHead>
            <TableHead className="w-24 text-xs">Jahrgang</TableHead>
            <TableHead className="w-28 text-xs">Wert</TableHead>
            <TableHead className="w-36 text-xs">Hart / weich</TableHead>
            <TableHead className="w-24 text-xs">Gewicht</TableHead>
            <TableHead className="w-20 text-xs">Aktiv</TableHead>
            <TableHead className="w-32 text-right text-xs"><span className="sr-only">Aktionen</span></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((r) => <RuleRow key={`${r.id}-${sig(toInput(r))}`} rule={r} />)}
        </TableBody>
      </Table>
    </div>
  )
}

function RuleRow({ rule }: { rule: RuleDto }) {
  const update = useUpdateRule()
  const remove = useDeleteRule()
  const fields = useFieldErrors()
  const [form, setForm] = useState<RuleInput>(() => toInput(rule))
  const [confirmDelete, setConfirmDelete] = useState(false)
  const dirty = sig(form) !== sig(toInput(rule))
  const fixedValue = rule.type === 'NO_LESSONS_IN_BREAKS'
  const set = (p: Partial<RuleInput>) => setForm((f) => ({ ...f, ...p }))

  const save = async () => {
    fields.clear()
    try {
      await update.mutateAsync({ id: rule.id, body: form })
      toast.success('Regel gespeichert')
    } catch (err) {
      fields.handle(err) // Wert < 1 → Feldfehler; der gespeicherte Wert bleibt
    }
  }

  return (
    <TableRow className={cn('align-top', !rule.active && !dirty && 'text-foreground-subtle')}>
      <TableCell className="pt-3">
        <div className="font-medium">{rule.label}</div>
        <div className="text-2xs text-foreground-subtle">{rule.type === 'NO_LESSONS_IN_BREAKS' ? 'Harte Regel des Rasters — Wert ohne Bedeutung' : `Verstoß: mehr als ${form.value} Stunden ${SCOPE_LABEL[rule.scope ?? ''] ?? ''}`}</div>
      </TableCell>
      <TableCell className="pt-3">{rule.scope ? SCOPE_LABEL[rule.scope] : <span className="text-foreground-subtle">schulweit</span>}</TableCell>
      <TableCell className="pt-3">
        {rule.grade != null ? <Badge variant="secondary" className="tabular-nums">Jahrgang {rule.grade}</Badge> : <span className="text-foreground-subtle">alle</span>}
      </TableCell>
      <TableCell>
        <Input
          type="number"
          min={1}
          value={form.value}
          disabled={fixedValue}
          onChange={(e) => set({ value: Number(e.target.value) })}
          onKeyDown={(e) => { if (e.key === 'Enter' && dirty) void save() }}
          aria-label={`Wert für ${rule.label}`}
          aria-invalid={!!fields.errors.value}
          className="w-20 tabular-nums"
        />
        <FieldError message={fields.errors.value} />
      </TableCell>
      <TableCell>
        <SeveritySwitch value={form.severity} onChange={(severity) => set({ severity })} disabled={fixedValue} />
        <FieldError message={fields.errors.severity} />
      </TableCell>
      <TableCell>
        <Input
          type="number"
          min={1}
          value={form.weight}
          disabled={form.severity === 'HARD'}
          onChange={(e) => set({ weight: Number(e.target.value) })}
          aria-label={`Gewicht für ${rule.label}`}
          aria-invalid={!!fields.errors.weight}
          className="w-16 tabular-nums"
          title={form.severity === 'HARD' ? 'Harte Regeln haben kein Gewicht' : undefined}
        />
        <FieldError message={fields.errors.weight} />
      </TableCell>
      <TableCell className="pt-3">
        <Switch checked={form.active} onCheckedChange={(active) => set({ active })} aria-label={`${rule.label} aktiv`} />
      </TableCell>
      <TableCell className="text-right">
        <div className="flex items-center justify-end gap-1">
          <Button size="sm" variant={dirty ? 'default' : 'outline'} disabled={!dirty || update.isPending} onClick={() => void save()}>
            <Save /> Speichern
          </Button>
          {rule.grade != null && (
            <Button size="icon-sm" variant="ghost" aria-label="Jahrgangsregel löschen" onClick={() => setConfirmDelete(true)}><Trash2 /></Button>
          )}
        </div>
        <ConfirmDialog
          open={confirmDelete}
          onOpenChange={setConfirmDelete}
          title={`Jahrgangsregel „${rule.label}" für Jahrgang ${rule.grade} löschen?`}
          description="Danach gilt für diesen Jahrgang wieder der schulweite Wert."
          confirmLabel="Löschen"
          destructive
          onConfirm={async () => { await remove.mutateAsync(rule.id); toast.success('Jahrgangsregel gelöscht') }}
        />
      </TableCell>
    </TableRow>
  )
}

/** Segmentschalter hart/weich. Radiogruppe von Radix: Pfeiltasten wechseln, die Gruppe hat einen Tabstopp. */
export function SeveritySwitch({ value, onChange, disabled }: { value: Severity; onChange(v: Severity): void; disabled?: boolean }) {
  const opt = (v: Severity, label: string, hint: string) => (
    <RadioGroupItem
      value={v}
      disabled={disabled}
      title={hint}
      className={cn(
        'h-6 rounded-md px-2.5 text-xs font-medium',
        value === v ? 'bg-card text-foreground shadow-xs' : 'text-foreground-subtle hover:text-foreground',
      )}
    >
      {label}
    </RadioGroupItem>
  )
  return (
    <RadioGroup
      value={value}
      onValueChange={(v) => onChange(v as Severity)}
      disabled={disabled}
      aria-label="Hart oder weich"
      className="inline-flex h-8 items-center gap-0.5 rounded-lg border bg-surface-sunken p-0.5"
    >
      {opt('HARD', 'hart', 'Harte Regel: ein Verstoß macht den Plan ungültig')}
      {opt('SOFT', 'weich', 'Weiche Regel: Verstöße werden gezählt und mit Gewicht bewertet')}
    </RadioGroup>
  )
}
