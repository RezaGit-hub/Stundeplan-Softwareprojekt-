// Fach anlegen oder ändern: Name, Kürzel, Raumanforderung, Fachfarbe (12 Swatches aus lib/subject-colors).
import { useState, type CSSProperties } from 'react'
import { toast } from 'sonner'
import { Check } from 'lucide-react'
import { useCreateSubject, useRooms, useSubjects, useUpdateSubject } from '@/api/hooks'
import type { SubjectDto, SubjectInput } from '@/api/types'
import { Input } from '@/components/ui/input'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { useFieldErrors } from '@/lib/form-errors'
import { SUBJECT_COLORS, subjectColorByIndex } from '@/lib/subject-colors'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { cn } from '@/lib/utils'
import { Field } from './shared'

/** Nächste Farbe, die noch kein Fach benutzt (sonst Reihum) */
function nextFreeColor(existing: SubjectDto[]): string {
  const used = new Set(existing.map((s) => s.color.toUpperCase()))
  const free = SUBJECT_COLORS.find((c) => !used.has(c.toUpperCase()))
  return free ?? subjectColorByIndex(existing.length)
}

export function SubjectDialog({ open, subject, onOpenChange }: { open: boolean; subject?: SubjectDto; onOpenChange(open: boolean): void }) {
  const subjects = useSubjects({ enabled: open })
  const rooms = useRooms({ enabled: open })
  const create = useCreateSubject()
  const update = useUpdateSubject()
  const fields = useFieldErrors()
  // Die Seite gibt beim Öffnen einen neuen `key` → frisches Formular ohne Effekt
  const [form, setForm] = useState<SubjectInput>(() => subject
    ? { name: subject.name, shortName: subject.shortName, requiredRoomType: subject.requiredRoomType, color: subject.color }
    : { name: '', shortName: '', requiredRoomType: null, color: nextFreeColor(subjects.data ?? []) })

  const set = (patch: Partial<SubjectInput>) => setForm((f) => ({ ...f, ...patch }))
  const roomTypes = [...new Set((rooms.data ?? []).map((r) => r.roomType))].sort()

  const save = async () => {
    fields.clear()
    const body: SubjectInput = { ...form, requiredRoomType: form.requiredRoomType?.trim() || null }
    try {
      if (subject) await update.mutateAsync({ id: subject.id, body })
      else await create.mutateAsync(body)
      toast.success('Fach gespeichert')
      onOpenChange(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title={subject ? `Fach ${subject.name} bearbeiten` : 'Neues Fach'} onSubmit={save} pending={create.isPending || update.isPending}>
      <div className="grid grid-cols-[1fr_6rem] gap-3">
        <Field label="Name" htmlFor="subject-name" error={fields.errors.name}>
          <Input id="subject-name" value={form.name} onChange={(e) => set({ name: e.target.value })} autoFocus placeholder="z. B. Mathematik" aria-invalid={!!fields.errors.name} />
        </Field>
        <Field label="Kürzel" htmlFor="subject-short" error={fields.errors.shortName}>
          <Input id="subject-short" value={form.shortName} onChange={(e) => set({ shortName: e.target.value })} placeholder="Ma" maxLength={5} aria-invalid={!!fields.errors.shortName} />
        </Field>
      </div>
      <Field label="Raumanforderung" htmlFor="subject-roomtype" error={fields.errors.requiredRoomType} hint={'Leer = jeder Raum. Sonst nur Räume dieser Raumart, z. B. „Fachraum Physik".'}>
        <Input id="subject-roomtype" list="subject-roomtype-list" value={form.requiredRoomType ?? ''} onChange={(e) => set({ requiredRoomType: e.target.value })} placeholder="keine" />
        <datalist id="subject-roomtype-list">
          {roomTypes.map((t) => <option key={t} value={t} />)}
        </datalist>
      </Field>
      <Field label="Fachfarbe" error={fields.errors.color}>
        <RadioGroup
          value={form.color.toUpperCase()}
          onValueChange={(c) => set({ color: c })}
          aria-label="Fachfarbe"
          className="flex flex-wrap gap-2"
        >
          {SUBJECT_COLORS.map((c, i) => {
            const on = form.color.toUpperCase() === c.toUpperCase()
            return (
              <RadioGroupItem
                key={c}
                value={c.toUpperCase()}
                aria-label={`Farbe ${i + 1}`}
                className={cn('flex size-7 items-center justify-center rounded-md hover:scale-110', on && 'ring-2 ring-ring ring-offset-2 ring-offset-background')}
                style={{ background: c }}
              >
                {on && <Check className="size-4 text-white" strokeWidth={3} aria-hidden />}
              </RadioGroupItem>
            )
          })}
        </RadioGroup>
        <div className="mt-1 flex items-center gap-2 text-xs text-foreground-subtle">
          Vorschau:
          <span className="lesson-tile inline-flex min-w-16 items-center rounded-sm px-2 py-1 text-base font-semibold" style={{ '--subject': form.color } as CSSProperties}>
            {form.shortName || 'Ma'}
          </span>
        </div>
      </Field>
    </FormDialog>
  )
}
