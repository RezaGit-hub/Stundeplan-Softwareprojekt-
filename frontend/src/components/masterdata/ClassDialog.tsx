// Klasse anlegen oder ändern: Name, Jahrgang (≥ 1, keine Obergrenze — Schulform ist Daten, nicht Code), aktiv.
import { useState } from 'react'
import { toast } from 'sonner'
import { useCreateClass, useUpdateClass } from '@/api/hooks'
import type { ClassDto, ClassInput } from '@/api/types'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { useFieldErrors } from '@/lib/form-errors'
import { Field } from './shared'

const EMPTY: ClassInput = { name: '', grade: 5, active: true }

export function ClassDialog({ open, schoolClass, onOpenChange, onCreated }: {
  open: boolean
  schoolClass?: ClassDto
  onOpenChange(open: boolean): void
  /** nach dem Anlegen, z. B. zur Detailseite springen */
  onCreated?(created: ClassDto): void
}) {
  const create = useCreateClass()
  const update = useUpdateClass()
  const fields = useFieldErrors()
  // Die Seite gibt beim Öffnen einen neuen `key` → frisches Formular ohne Effekt
  const [form, setForm] = useState<ClassInput>(() => (schoolClass ? { name: schoolClass.name, grade: schoolClass.grade, active: schoolClass.active } : EMPTY))

  const set = (patch: Partial<ClassInput>) => setForm((f) => ({ ...f, ...patch }))

  const save = async () => {
    fields.clear()
    try {
      if (schoolClass) {
        await update.mutateAsync({ id: schoolClass.id, body: form })
      } else {
        const created = await create.mutateAsync(form)
        onCreated?.(created)
      }
      toast.success('Klasse gespeichert')
      onOpenChange(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title={schoolClass ? `Klasse ${schoolClass.name} bearbeiten` : 'Neue Klasse'} onSubmit={save} pending={create.isPending || update.isPending}>
      <div className="grid grid-cols-[1fr_7rem] gap-3">
        <Field label="Name" htmlFor="class-name" error={fields.errors.name}>
          <Input
            id="class-name"
            value={form.name}
            onChange={(e) => {
              const name = e.target.value
              const grade = Number.parseInt(name, 10)
              set(Number.isFinite(grade) && grade >= 1 && !schoolClass ? { name, grade } : { name })
            }}
            autoFocus
            placeholder="z. B. 7a"
            aria-invalid={!!fields.errors.name}
          />
        </Field>
        <Field label="Jahrgang" htmlFor="class-grade" error={fields.errors.grade}>
          <Input id="class-grade" type="number" min={1} value={form.grade} onChange={(e) => set({ grade: Number(e.target.value) })} className="tabular-nums" aria-invalid={!!fields.errors.grade} />
        </Field>
      </div>
      <label className="flex items-center justify-between rounded-md border bg-surface px-3 py-2 text-sm">
        <span>
          <span className="font-medium">Aktiv</span>
          <span className="block text-xs text-foreground-subtle">Nur aktive Klassen werden berechnet und in Sichten angeboten.</span>
        </span>
        <Switch checked={form.active} onCheckedChange={(v) => set({ active: v })} aria-label="Aktiv" />
      </label>
    </FormDialog>
  )
}
