// Übersicht (Issue "Regelwert je Jahrgang"): Zeilen = Jahrgänge, Spalten = jahrgangsfähige Regeltypen.
// Jede Zelle zeigt den effektiv geltenden Wert (Jahrgang schlägt schulweit, E-47) und woher er kommt.
// Klick auf eine Zelle ohne eigenen Jahrgangswert legt ihn an (mit dem schulweiten Wert vorbelegt);
// Klick auf eine Zelle mit Jahrgangswert entfernt ihn wieder. Den Wert selbst bearbeitet man weiterhin
// unten in der Regeltabelle — die Matrix ist nur die Übersicht plus An/Aus-Schalter.
import { useState } from 'react'
import { toast } from 'sonner'
import { Plus, Trash2 } from 'lucide-react'
import { useClasses, useCreateRule, useDeleteRule } from '@/api/hooks'
import type { RuleDto, RuleType } from '@/api/types'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { RULE_TYPE_LABEL } from './helpers'
import { cn } from '@/lib/utils'

const MATRIX_TYPES: RuleType[] = ['MAX_LESSONS_PER_DAY', 'MAX_LESSONS_PER_WEEK']

export function RulesMatrix({ rules }: { rules: RuleDto[] }) {
  const classes = useClasses()
  if (classes.isPending || classes.isError || !classes.data) return null // Fehler/Ladezustand zeigt die Regelseite selbst schon

  const grades = [...new Set(classes.data.map((c) => c.grade))].sort((a, b) => a - b)
  if (grades.length === 0) return null

  return (
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
              {MATRIX_TYPES.map((t) => <MatrixCell key={t} grade={g} type={t} rules={rules} />)}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

function MatrixCell({ grade, type, rules }: { grade: number; type: RuleType; rules: RuleDto[] }) {
  const create = useCreateRule()
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
          className={cn(
            'group inline-flex items-center gap-1.5 rounded-md border border-primary/40 bg-primary-soft px-2 py-1 text-sm font-medium text-primary-soft-foreground tabular-nums transition-colors hover:border-destructive/50 hover:bg-destructive/10 hover:text-destructive',
            !gradeRule.active && 'opacity-60',
          )}
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

  const addGradeRule = async () => {
    if (!schoolRule) return
    try {
      await create.mutateAsync({
        type, scope: 'CLASS', grade, value: schoolRule.value, severity: schoolRule.severity, weight: schoolRule.weight, active: true,
      })
      toast.success(`Jahrgangswert für Jahrgang ${grade} angelegt`)
    } catch {
      toast.error('Jahrgangswert konnte nicht angelegt werden')
    }
  }

  return (
    <TableCell>
      <button
        type="button"
        onClick={() => void addGradeRule()}
        disabled={create.isPending}
        className={cn(
          'inline-flex items-center gap-1.5 rounded-md border border-dashed px-2 py-1 text-sm text-foreground-subtle tabular-nums transition-colors hover:border-primary/50 hover:text-foreground',
          !schoolRule?.active && 'opacity-60',
        )}
        title="Schulweiter Wert — Klick, um für diesen Jahrgang einen eigenen Wert anzulegen"
      >
        {effective.value}
        <Plus className="size-3.5" aria-hidden />
      </button>
    </TableCell>
  )
}