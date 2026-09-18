// /regelwerk — Regeltabelle (Zeile speichert einzeln), Beispielsatz laden, Kann: Jahrgangswert hinzufügen (E-47).
import { useState } from 'react'
import { toast } from 'sonner'
import { Info, Plus, RotateCcw, SlidersHorizontal } from 'lucide-react'
import { useConfig, useCreateRule, useResetRules, useRules } from '@/api/hooks'
import type { RuleInput, RuleType } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { RulesTable, SeveritySwitch } from '@/components/masterdata/RulesTable'
import { RulesMatrix } from '@/components/masterdata/RulesMatrix'
import { RULE_TYPE_LABEL, STATE_LABEL } from '@/components/masterdata/helpers'
import { Field } from '@/components/masterdata/shared'
import { useFieldErrors } from '@/lib/form-errors'
import { TransferButtons } from '@/components/masterdata/TransferButtons'

export function RulesPage() {
  const rules = useRules()
  const config = useConfig()
  const reset = useResetRules()
  const [confirmReset, setConfirmReset] = useState(false)
  const [addOpen, setAddOpen] = useState(false)
  const [addKey, setAddKey] = useState(0)

  if (rules.isPending) return <PageSkeleton />
  if (rules.isError) return <ErrorState problem={rules.error} retry={() => void rules.refetch()} />

  const stateName = config.data ? (STATE_LABEL[config.data.state] ?? config.data.state) : ''
  const active = rules.data.filter((r) => r.active).length
  const hard = rules.data.filter((r) => r.active && r.severity === 'HARD').length

  return (
    <>
      <PageHeader
        title="Regelwerk"
        description={`${active} von ${rules.data.length} Regeln aktiv · ${hard} hart · ${active - hard} weich`}
        actions={
          <>
            <TransferButtons scope="rules"/>
            <Button variant="outline" onClick={() => { setAddKey((k) => k + 1); setAddOpen(true) }}><Plus /> Jahrgangswert hinzufügen</Button>
            <Button variant="outline" onClick={() => setConfirmReset(true)}><RotateCcw /> Beispielsatz {stateName} laden</Button>
          </>
        }
      />

      {rules.data.length === 0 ? (
        <EmptyState
          icon={SlidersHorizontal}
          title="Noch keine Regeln vorhanden"
          description="Der Beispielsatz legt die Grundregeln an: maximale Stunden pro Tag und Woche, keine Stunden in Pausen."
          action={<Button onClick={() => setConfirmReset(true)}><RotateCcw /> Beispielsatz {stateName} laden</Button>}
        />
      ) : (
        <>
          <RulesMatrix rules={rules.data} />
          <RulesTable rules={rules.data} />
        </>  
      )}

      <p className="mt-4 flex items-start gap-2 text-xs text-foreground-subtle">
        <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
        <span>
          <strong className="font-medium text-foreground-muted">Hart</strong> heißt: ein Verstoß macht den Plan ungültig (roter Rahmen, durchgezogen).{' '}
          <strong className="font-medium text-foreground-muted">Weich</strong> heißt: Verstöße werden gezählt und mit dem Gewicht bewertet (roter Rahmen, gestrichelt).
          Ein Jahrgangswert überschreibt den schulweiten Wert nur für diesen Jahrgang. Änderungen wirken auf Entwurf und aktiven Plan sofort bei der nächsten Prüfung.
        </span>
      </p>

      <ConfirmDialog
        open={confirmReset}
        onOpenChange={setConfirmReset}
        title={`Beispielsatz ${stateName} laden?`}
        description="Alle Regeln werden auf die Standardwerte zurückgesetzt. Eigene Jahrgangswerte gehen verloren."
        confirmLabel="Beispielsatz laden"
        onConfirm={async () => { await reset.mutateAsync(undefined); toast.success('Beispielsatz geladen') }}
      />

      <AddGradeRuleDialog key={addKey} open={addOpen} onOpenChange={setAddOpen} />
    </>
  )
}

const GRADE_RULE_TYPES: RuleType[] = ['MAX_LESSONS_PER_DAY', 'MAX_LESSONS_PER_WEEK']

function AddGradeRuleDialog({ open, onOpenChange }: { open: boolean; onOpenChange(open: boolean): void }) {
  const create = useCreateRule()
  const fields = useFieldErrors()
  const [form, setForm] = useState<RuleInput>({ type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: 5, value: 6, severity: 'SOFT', weight: 1, active: true })
  const set = (p: Partial<RuleInput>) => setForm((f) => ({ ...f, ...p }))

  const save = async () => {
    fields.clear()
    try {
      await create.mutateAsync(form)
      toast.success('Jahrgangsregel gespeichert')
      onOpenChange(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title="Jahrgangswert hinzufügen" description="Gilt nur für Klassen dieses Jahrgangs und überschreibt dort den schulweiten Wert." onSubmit={save} pending={create.isPending}>
      <Field label="Regel" error={fields.errors.type}>
        <Select value={form.type} onValueChange={(v) => set({ type: v as RuleType })}>
          <SelectTrigger className="w-full" aria-label="Regel"><SelectValue /></SelectTrigger>
          <SelectContent>
            {GRADE_RULE_TYPES.map((t) => <SelectItem key={t} value={t}>{RULE_TYPE_LABEL[t]}</SelectItem>)}
          </SelectContent>
        </Select>
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Jahrgang" htmlFor="rule-grade" error={fields.errors.grade}>
          <Input id="rule-grade" type="number" min={1} value={form.grade ?? ''} onChange={(e) => set({ grade: e.target.value === '' ? null : Number(e.target.value) })} className="tabular-nums" aria-invalid={!!fields.errors.grade} autoFocus />
        </Field>
        <Field label="Wert" htmlFor="rule-value" error={fields.errors.value}>
          <Input id="rule-value" type="number" min={1} value={form.value} onChange={(e) => set({ value: Number(e.target.value) })} className="tabular-nums" aria-invalid={!!fields.errors.value} />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
      <Field label="Hart / weich" error={fields.errors.severity}>
        <SeveritySwitch value={form.severity} onChange={(severity) => set({ severity })} fullWidth />
      </Field>
        <Field label="Gewicht" htmlFor="rule-weight" error={fields.errors.weight}>
          <Input id="rule-weight" type="number" min={1} value={form.weight} disabled={form.severity === 'HARD'} onChange={(e) => set({ weight: Number(e.target.value) })} className="tabular-nums" />
        </Field>
      </div>
      <label className="flex items-center justify-between rounded-md border bg-surface px-3 py-2 text-sm">
        <span className="font-medium">Sofort aktiv</span>
        <Switch checked={form.active} onCheckedChange={(active) => set({ active })} aria-label="Aktiv" />
      </label>
    </FormDialog>
  )
}
