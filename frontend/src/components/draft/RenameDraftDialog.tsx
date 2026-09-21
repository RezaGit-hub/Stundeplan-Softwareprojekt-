// Entwurf umbenennen (#103): PATCH /api/drafts/{id} {name}. Auch während einer Berechnung erlaubt —
// der Name geht den Solver nichts an. Aufrufer geben beim Öffnen einen neuen `key`, damit das Feld
// ohne Effekt mit dem aktuellen Namen startet.
import { useState } from 'react'
import { toast } from 'sonner'
import { usePatchDraft } from '@/api/hooks'
import { Input } from '@/components/ui/input'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { Field } from '@/components/masterdata/shared'
import { useFieldErrors } from '@/lib/form-errors'

export function RenameDraftDialog({ open, onOpenChange, draftId, currentName }: {
  open: boolean
  onOpenChange(open: boolean): void
  draftId: number
  currentName: string
}) {
  const patch = usePatchDraft(draftId)
  const fields = useFieldErrors()
  const [name, setName] = useState(currentName)

  const save = async () => {
    fields.clear()
    try {
      await patch.mutateAsync({ name })
      toast.success('Entwurf umbenannt')
      onOpenChange(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title="Entwurf umbenennen" onSubmit={save} pending={patch.isPending}>
      <Field label="Name" htmlFor="rename-draft-name" error={fields.errors.name} hint="Höchstens 80 Zeichen.">
        <Input id="rename-draft-name" value={name} onChange={(e) => setName(e.target.value)} autoFocus aria-invalid={!!fields.errors.name} />
      </Field>
    </FormDialog>
  )
}
