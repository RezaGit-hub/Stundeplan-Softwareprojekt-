// Übersicht (Issue "Regelwert je Jahrgang"): Zeilen = Jahrgänge, Spalten = jahrgangsfähige Regeltypen.
// Jede Zelle zeigt den effektiv geltenden Wert (Jahrgang schlägt schulweit, E-47) und woher er kommt.
// Klick auf eine Zelle ohne eigenen Jahrgangswert öffnet einen Dialog zum Anlegen; Klick auf eine
// Zelle mit Jahrgangswert entfernt ihn wieder (Rückfrage). Den Wert selbst bearbeitet man weiterhin
// in der Regeltabelle unten — die Matrix ist nur die Übersicht plus An/Aus-Schalter.
import { useState } from 'react'
import { toast } from 'sonner'
import { Plus, Trash2 } from 'lucide-react'
import { useClasses, useCreateRule, useDeleteRule } from '@/api/hooks'
import type { RuleDto, RuleInput, RuleType, Severity } from '@/api/types'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { useFieldErrors } from '@/lib/form-errors'
import { RULE_TYPE_LABEL } from './helpers'
import { Field } from './shared'
import { SeveritySwitch } from './RulesTable'

const MATRIX_TYPES: RuleType[] = ['MAX_LESSONS_PER_DAY', 'MAX_LESSONS_PER_WEEK']

export function RulesMatrix({ rules }: { rules: RuleDto[] }) {
  const classes = useClasses()
  const [target, setTarget] = useState<{ grade: number; type: RuleType } | null>(null)
  if (classes.isPending || classes.isError || !classes.data) return null // Fehler/Ladezustand zeigt die Regelseite selbst schon

  const grades = [...new Set(classes.data.map((c) => c.grade))].sort((a, b) => a - b)
  if (grades.length === 0) return null

  return (
    <>
      <div className="mb-4 overflow-x-auto rounded-lg border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-28 text-xs">Jahrgang</TableHead>
              {MATRIX_TYPES.map((t) => <TableHead key={t} className="text-xs">{RULE_TYPE_LABEL[t]}</TableHead>)}
            </TableRow>
          </TableHeader>
          <TableBody>
            {grades.map((g) => (
              <TableRow key={g}>
                <TableCell className="font-medium tabular-nums">Jahrgang {g}</TableCell>
                {MATRIX_TYPES.map((t) => (
                  <MatrixCell
                    key={t}
                    grade={g}
                    type={t}
                    rules={rules}
                    onAdd={() => setTarget({ grade: g, type: t })}
                  />
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {target && (
        <GradeRuleDialog
          open
          onOpenChange={(o) => { if (!o) setTarget(null) }}
          grade={target.grade}
          type={target.type}
          rules={rules}
        />
      )}
    </>
  )
}

function MatrixCell({ grade, type, rules, onAdd }: { grade: number; type: RuleType; rules: RuleDto[]; onAdd(): void }) {
  const remove = useDeleteRule()
  const [confirmDelete, setConfirmDelete] = useState(false)

  const gradeRule = rules.find((r) => r.type === type && r.scope === 'CLASS' && r.grade === grade)
  const schoolRule = rules.find((r) => r.type === type && r.scope === 'CLASS' && r.grade == null)
  const effective = gradeRule ?? schoolRule

  if (!effective) {
    return <TableCell className="text-center text-foreground-subtle">–</TableCell>
  }

  if (gradeRule) {
    return (
      <TableCell>
        <button
          type="button"
          onClick={() => setConfirmDelete(true)}
          className={`group inline-flex items-center gap-1.5 rounded-md border border-primary/40 bg-primary-soft px-2 py-1 text-sm font-medium text-primary-soft-foreground tabular-nums transition-colors hover:border-destructive/50 hover:bg-destructive/10 hover:text-destructive${!gradeRule.active ? ' opacity-60' : ''}`}
          title={`Jahrgangswert${!gradeRule.active ? ' (inaktiv)' : ''} — Klick zum Entfernen`}
        >
          {gradeRule.value}
          <Trash2 className="size-3.5 opacity-0 transition-opacity group-hover:opacity-100" aria-hidden />
        </button>
        <ConfirmDialog
          open={confirmDelete}
          onOpenChange={setConfirmDelete}
          title={`Jahrgangswert für Jahrgang ${grade} entfernen?`}
          description={`Danach gilt für Jahrgang ${grade} wieder der schulweite Wert${schoolRule ? ` (${schoolRule.value})` : ''}.`}
          confirmLabel="Entfernen"
          destructive
          onConfirm={async () => { await remove.mutateAsync(gradeRule.id); toast.success('Jahrgangswert entfernt') }}
        />
      </TableCell>
    )
  }

  return (
    <TableCell>
      <button
        type="button"
        onClick={onAdd}
        className="inline-flex size-7 items-center justify-center rounded-md border border-dashed text-foreground-subtle transition-colors hover:border-primary/50 hover:text-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
        title={schoolRule
          ? `Schulweiter Wert: ${schoolRule.value} — Klick, um für diesen Jahrgang einen eigenen Wert anzulegen`
          : 'Jahrgangswert anlegen'}
        aria-label={`Jahrgangswert für Jahrgang ${grade} anlegen (${RULE_TYPE_LABEL[type]})`}
      >
        <Plus className="size-3.5" aria-hidden />
      </button>
    </TableCell>
  )
}

function GradeRuleDialog({ open, onOpenChange, grade, type, rules }: {
  open: boolean
  onOpenChange(open: boolean): void
  grade: number
  type: RuleType
  rules: RuleDto[]
}) {
  const create = useCreateRule()
  const fields = useFieldErrors()
  const schoolRule = rules.find((r) => r.type === type && r.scope === 'CLASS' && r.grade == null)
  // Vorbelegt mit dem schulweiten Wert, damit der neue Jahrgangswert dort ansetzt, wo die Schule steht.
  const [form, setForm] = useState<RuleInput>(() => ({
    type,
    scope: 'CLASS',
    grade,
    value: schoolRule?.value ?? 1,
    severity: schoolRule?.severity ?? ('SOFT' as Severity),
    weight: schoolRule?.weight ?? 1,
    active: true,
  }))
  const set = (p: Partial<RuleInput>) => setForm((f) => ({ ...f, ...p }))

  const save = async () => {
    fields.clear()
    try {
      await create.mutateAsync(form)
      toast.success(`Jahrgangswert für Jahrgang ${grade} angelegt`)
      onOpenChange(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={`Jahrgangswert: ${RULE_TYPE_LABEL[type]}`}
      description={`Gilt nur für Klassen des Jahrgangs ${grade} und überschreibt dort den schulweiten Wert.`}
      onSubmit={save}
      pending={create.isPending}
    >
      <div className="grid grid-cols-2 gap-3">
        <Field
          label="Wert"
          htmlFor="matrix-value"
          error={fields.errors.value}
          hint={schoolRule ? `Schulweiter Wert: ${schoolRule.value}` : undefined}
        >
          <Input
            id="matrix-value"
            type="number"
            min={1}
            value={form.value}
            onChange={(e) => set({ value: Number(e.target.value) })}
            className="tabular-nums"
            aria-invalid={!!fields.errors.value}
            autoFocus
          />
        </Field>
        <Field label="Gewicht" htmlFor="matrix-weight" error={fields.errors.weight}>
          <Input
            id="matrix-weight"
            type="number"
            min={1}
            value={form.weight}
            disabled={form.severity === 'HARD'}
            onChange={(e) => set({ weight: Number(e.target.value) })}
            className="tabular-nums"
          />
        </Field>
      </div>
      <Field label="Hart / weich" error={fields.errors.severity}>
        <SeveritySwitch value={form.severity} onChange={(severity) => set({ severity })} />
      </Field>
      <label className="flex items-center justify-between rounded-md border bg-surface px-3 py-2 text-sm">
        <span className="font-medium">Sofort aktiv</span>
        <Switch checked={form.active} onCheckedChange={(active) => set({ active })} aria-label="Aktiv" />
      </label>
    </FormDialog>
  )
}