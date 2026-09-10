// Lehrkraft anlegen oder ändern: Name, Kürzel, Deputat, Fächer, aktiv, bevorzugte/gesperrte Zeiten (W-1, T-W1.1).
import { useState } from 'react'
import { toast } from 'sonner'
import { useCreateTeacher, useSubjects, useTimegrid, useUpdateTeacher } from '@/api/hooks'
import type { TeacherDto, TeacherInput } from '@/api/types'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { useFieldErrors } from '@/lib/form-errors'
import { ColorDot, Field } from './shared'
import { TeacherTimeGrid } from './TeacherTimeGrid'

const EMPTY: TeacherInput = { name: '', shortName: '', weeklyHoursQuota: 25, subjectIds: [], preferredTimeslotIds: [], blockedTimeslotIds: [], active: true }

function toInput(t: TeacherDto): TeacherInput {
  return {
    name: t.name, shortName: t.shortName, weeklyHoursQuota: t.weeklyHoursQuota, subjectIds: [...t.subjectIds],
    preferredTimeslotIds: [...t.preferredTimeslotIds], blockedTimeslotIds: [...t.blockedTimeslotIds], active: t.active,
  }
}

export function TeacherDialog({ open, teacher, onOpenChange }: { open: boolean; teacher?: TeacherDto; onOpenChange(open: boolean): void }) {
  const subjects = useSubjects({ enabled: open })
  const timegrid = useTimegrid()
  const create = useCreateTeacher()
  const update = useUpdateTeacher()
  const fields = useFieldErrors()
  // Die Seite gibt beim Öffnen einen neuen `key` → frisches Formular ohne Effekt
  const [form, setForm] = useState<TeacherInput>(() => (teacher ? toInput(teacher) : EMPTY))

  const pending = create.isPending || update.isPending
  const set = (patch: Partial<TeacherInput>) => setForm((f) => ({ ...f, ...patch }))

  const save = async () => {
    fields.clear()
    try {
      if (teacher) await update.mutateAsync({ id: teacher.id, body: form })
      else await create.mutateAsync(form)
      toast.success('Lehrkraft gespeichert')
      onOpenChange(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  const toggleSubject = (id: number, on: boolean) =>
    set({ subjectIds: on ? [...new Set([...form.subjectIds, id])] : form.subjectIds.filter((x) => x !== id) })

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title={teacher ? `Lehrkraft ${teacher.name} bearbeiten` : 'Neue Lehrkraft'} onSubmit={save} pending={pending}>
      <div className="grid grid-cols-[1fr_6rem_6rem] gap-3">
        <Field label="Name" htmlFor="teacher-name" error={fields.errors.name}>
          <Input id="teacher-name" value={form.name} onChange={(e) => set({ name: e.target.value })} autoFocus placeholder="z. B. Schulz" aria-invalid={!!fields.errors.name} />
        </Field>
        <Field label="Kürzel" htmlFor="teacher-short" error={fields.errors.shortName}>
          <Input id="teacher-short" value={form.shortName} onChange={(e) => set({ shortName: e.target.value.toUpperCase() })} placeholder="SCZ" maxLength={5} className="uppercase" aria-invalid={!!fields.errors.shortName} />
        </Field>
        <Field label="Deputat" htmlFor="teacher-quota" error={fields.errors.weeklyHoursQuota} hint="Std./Woche">
          <Input id="teacher-quota" type="number" min={1} value={form.weeklyHoursQuota} onChange={(e) => set({ weeklyHoursQuota: Number(e.target.value) })} className="tabular-nums" aria-invalid={!!fields.errors.weeklyHoursQuota} />
        </Field>
      </div>

      <Field label="Fächer" error={fields.errors.subjectIds} hint={subjects.data?.length === 0 ? 'Noch keine Fächer angelegt (Grunddaten → Fächer).' : undefined}>
        <div className="flex flex-wrap gap-1.5">
          {(subjects.data ?? []).map((s) => {
            const on = form.subjectIds.includes(s.id)
            return (
              <label
                key={s.id}
                className={`inline-flex cursor-pointer items-center gap-2 rounded-md border px-2.5 py-1.5 text-sm transition-colors ${on ? 'border-primary/40 bg-primary-soft text-primary-soft-foreground' : 'bg-surface hover:bg-accent'}`}
              >
                <Checkbox checked={on} onCheckedChange={(v) => toggleSubject(s.id, v === true)} aria-label={s.name} />
                <ColorDot color={s.color} />
                {s.name}
              </label>
            )
          })}
        </div>
      </Field>

      <Field label="Bevorzugte und gesperrte Zeiten" hint="Gesperrte Zeiten sind eine harte Regel für Entwurf und Berechnung, bevorzugte Zeiten ein weicher Wunsch.">
        <TeacherTimeGrid
          slots={timegrid.data?.slots ?? []}
          preferred={form.preferredTimeslotIds}
          blocked={form.blockedTimeslotIds}
          onChange={(next) => set({ preferredTimeslotIds: next.preferred, blockedTimeslotIds: next.blocked })}
        />
      </Field>

      <label className="flex items-center justify-between rounded-md border bg-surface px-3 py-2 text-sm">
        <span>
          <span className="font-medium">Aktiv</span>
          <span className="block text-xs text-foreground-subtle">Deaktivierte Lehrkräfte bleiben in alten Plänen, sind aber nicht mehr wählbar.</span>
        </span>
        <Switch checked={form.active} onCheckedChange={(v) => set({ active: v })} aria-label="Aktiv" />
      </label>
    </FormDialog>
  )
}
