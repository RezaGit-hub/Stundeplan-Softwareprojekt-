// Berechnen (W-8): Rechenzeit aus den Einstellungen, Kann-Option „nur eine Klasse" (R1) → POST /draft/solve.
// 409 master-data-incomplete / pinned-contradiction: Toast kommt automatisch, die Liste (missing/conflicts) steht hier inline.
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { useConfig, useStartSolve } from '@/api/hooks'
import { ApiError, type ClassDto, type ProblemDetail } from '@/api/types'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { ProblemBanner } from '@/components/states/ProblemBanner'

export function SolveDialog({ open, onOpenChange, classes, defaultClassId }: { open: boolean; onOpenChange(open: boolean): void; classes: ClassDto[]; defaultClassId?: number | null }) {
  const config = useConfig()
  const start = useStartSolve()
  const [onlyClass, setOnlyClass] = useState(false)
  const [classId, setClassId] = useState<number | null>(null)
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const active = classes.filter((c) => c.active)
  const chosen = classId ?? defaultClassId ?? active[0]?.id ?? null

  const submit = async () => {
    setProblem(null)
    try {
      await start.mutateAsync(onlyClass && chosen != null ? { classId: chosen } : undefined)
      toast.success('Berechnung gestartet')
      onOpenChange(false)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) setProblem(err.problem)
    }
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={(o) => { if (!o) setProblem(null); onOpenChange(o) }}
      title="Plan berechnen"
      description="Alle offenen Stunden werden belegt. Festgelegte Stunden bleiben unverändert; nicht festgelegte können wandern."
      onSubmit={submit}
      pending={start.isPending}
      submitLabel="Berechnen"
    >
      <p className="text-sm">
        Rechenzeit: <span className="font-semibold tabular-nums">{config.data ? `${config.data.maxSolveSeconds} s` : '…'}</span>{' '}
        <Link to="/einstellungen" className="text-primary underline-offset-2 hover:underline">(Einstellungen)</Link>
      </p>
      <div className="rounded-md border bg-surface-sunken/50 p-3">
        <div className="flex items-center gap-2">
          <Checkbox id="solve-only-class" checked={onlyClass} onCheckedChange={(v) => setOnlyClass(v === true)} disabled={active.length === 0} />
          <Label htmlFor="solve-only-class" className="font-normal">Nur eine Klasse neu berechnen</Label>
        </div>
        {onlyClass && (
          <div className="mt-2">
            <Select value={chosen != null ? String(chosen) : ''} onValueChange={(v) => { if (v) setClassId(Number(v)) }}>
              <SelectTrigger className="w-full" aria-label="Klasse"><SelectValue placeholder="Klasse wählen" /></SelectTrigger>
              <SelectContent>{active.map((c) => <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>)}</SelectContent>
            </Select>
            <p className="mt-1.5 text-xs text-foreground-subtle">Die Stunden der anderen Klassen bleiben, wo sie sind.</p>
          </div>
        )}
      </div>
      {problem && <ProblemBanner problem={problem} />}
    </FormDialog>
  )
}
