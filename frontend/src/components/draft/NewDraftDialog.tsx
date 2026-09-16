// Neuer Entwurf (#103): Name (Kann) und Quelle — leer beginnen oder den aktiven Plan kopieren.
// Seit 1.3.0 stehen Entwürfe nebeneinander; „Es gibt schon einen Entwurf" (409 `draft-exists`) gibt es nicht mehr.
// Ohne Namen vergibt der Server „Entwurf n" — das Feld bleibt deshalb leer statt einen Vorschlag zu raten.
import { useState } from 'react'
import { toast } from 'sonner'
import { Copy, FilePlus } from 'lucide-react'
import { useCreateDraft } from '@/api/hooks'
import type { DraftDto, DraftSource } from '@/api/types'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { Field } from '@/components/masterdata/shared'
import { useFieldErrors } from '@/lib/form-errors'
import { cn } from '@/lib/utils'

const SOURCES: { value: DraftSource; icon: typeof FilePlus; label: string; hint: string }[] = [
  { value: 'EMPTY', icon: FilePlus, label: 'Leer beginnen', hint: 'Kein Stundenplan — berechnen lassen oder von Hand aufbauen.' },
  { value: 'COPY_ACTIVE', icon: Copy, label: 'Aktiven Plan kopieren', hint: 'Alle Stunden des aktiven Plans; konfliktfreie Stunden sind festgelegt.' },
]

export function NewDraftDialog({ open, onOpenChange, onCreated }: {
  open: boolean
  onOpenChange(open: boolean): void
  onCreated?(draft: DraftDto): void
}) {
  const create = useCreateDraft()
  const fields = useFieldErrors()
  const [name, setName] = useState('')
  const [source, setSource] = useState<DraftSource>('EMPTY')

  const save = async () => {
    fields.clear()
    try {
      const draft = await create.mutateAsync({ source, ...(name.trim() ? { name: name.trim() } : {}) })
      toast.success(`Entwurf „${draft.name}" angelegt`)
      onOpenChange(false)
      onCreated?.(draft)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Neuer Entwurf"
      description="Ein Entwurf ist eine Arbeitskopie neben dem aktiven Plan. Erst das Annehmen macht ihn gültig."
      onSubmit={save}
      pending={create.isPending}
      submitLabel="Anlegen"
    >
      <Field label="Name" htmlFor="draft-name" error={fields.errors.name} hint={'Kann frei bleiben — dann heißt er „Entwurf 1", „Entwurf 2" …'}>
        <Input id="draft-name" value={name} onChange={(e) => setName(e.target.value)} autoFocus placeholder="z. B. Variante B" aria-invalid={!!fields.errors.name} />
      </Field>
      <RadioGroup value={source} onValueChange={(v) => setSource(v as DraftSource)} className="grid gap-2" aria-label="Quelle">
        {SOURCES.map((s) => (
          <div key={s.value} className={cn('rounded-md border p-3 transition-colors', source === s.value ? 'border-primary/50 bg-primary-soft/40' : 'bg-surface-sunken/40')}>
            <div className="flex items-center gap-2">
              <RadioGroupItem value={s.value} id={`draft-source-${s.value}`} className="size-4 shrink-0 rounded-full border data-[state=checked]:border-[5px] data-[state=checked]:border-primary" />
              <Label htmlFor={`draft-source-${s.value}`} className="font-medium"><s.icon className="size-4 text-foreground-subtle" aria-hidden /> {s.label}</Label>
            </div>
            <p className="mt-1 pl-6 text-xs text-foreground-subtle">{s.hint}</p>
          </div>
        ))}
      </RadioGroup>
    </FormDialog>
  )
}
