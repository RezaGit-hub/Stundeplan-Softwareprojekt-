// Benutzerkonto anlegen: Kontotyp (Lehrkraft · Klasse · Planung), dazu die Lehrkraft bzw. die Klasse
// (jeweils nur die ohne Konto), Benutzername und Passwort.
//
// Kontotyp „Klasse“ = Rolle STUDENT (E-50): ein Konto je Klasse, das nur den eigenen Klassenplan sieht.
// Für alle Klassen auf einmal gibt es den Knopf „Konten für alle Klassen anlegen“ in den Einstellungen;
// dieser Dialog ist der Einzelfall (Nachzügler, eigener Benutzername).
import { useState } from 'react'
import { toast } from 'sonner'
import { useClasses, useCreateUser } from '@/api/hooks'
import type { ClassDto, CreateUser, Role, TeacherDto, UserDto } from '@/api/types'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { useFieldErrors } from '@/lib/form-errors'
import { Field } from './shared'
import { ROLE_LABEL } from './helpers'

const ROLE_HINT: Record<Role, string> = {
  TEACHER: 'Lehrkräfte sehen im Web nur ihren eigenen Plan (lesend) und nutzen die App.',
  STUDENT: 'Ein Konto je Klasse. Es zeigt nur den Plan dieser Klasse, Lehrkräfte als Kürzel und keine Abwesenheitsgründe.',
  PLANNING: 'Planung darf alles: Grunddaten, Entwurf, Vertretung, Einstellungen.',
}

export function UserDialog({ open, onOpenChange, users, teachers }: { open: boolean; onOpenChange(open: boolean): void; users: UserDto[]; teachers: TeacherDto[] }) {
  const create = useCreateUser()
  const fields = useFieldErrors()
  const [form, setForm] = useState<CreateUser>({ username: '', password: '', role: 'TEACHER', teacherId: null, classId: null })
  const set = (p: Partial<CreateUser>) => setForm((f) => ({ ...f, ...p }))
  // Klassen erst laden, wenn der Kontotyp „Klasse“ gewählt ist — die Liste braucht sonst niemand.
  const classes = useClasses({ enabled: form.role === 'STUDENT' })

  const withAccount = new Set(users.map((u) => u.teacherId).filter((id): id is number => id != null))
  const available = teachers.filter((t) => t.active && !withAccount.has(t.id))
  const classesWithAccount = new Set(users.map((u) => u.classId).filter((id): id is number => id != null))
  const availableClasses: ClassDto[] = (classes.data ?? []).filter((c) => c.active && !classesWithAccount.has(c.id))

  const pickTeacher = (id: number) => {
    const t = teachers.find((x) => x.id === id)
    // Benutzername vorschlagen: Kürzel klein, solange noch nichts eingetippt wurde
    set({ teacherId: id, username: form.username || (t ? t.shortName.toLowerCase() : '') })
  }
  const pickClass = (id: number) => {
    const c = availableClasses.find((x) => x.id === id)
    set({ classId: id, username: form.username || (c ? c.name.toLowerCase() : '') })
  }
  // Kontotyp wechseln: die Bindung der anderen Rolle darf nicht stehen bleiben, der Server lehnt sie ab.
  const pickRole = (role: Role) => set({ role, teacherId: null, classId: null })

  const save = async () => {
    fields.clear()
    try {
      await create.mutateAsync({
        ...form,
        teacherId: form.role === 'TEACHER' ? form.teacherId : null,
        classId: form.role === 'STUDENT' ? form.classId : null,
      })
      toast.success('Benutzerkonto angelegt')
      onOpenChange(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title="Neues Benutzerkonto" onSubmit={save} pending={create.isPending} submitLabel="Konto anlegen">
      <Field label="Kontotyp" error={fields.errors.role} hint={ROLE_HINT[form.role]}>
        <Select value={form.role} onValueChange={(v) => pickRole(v as Role)}>
          <SelectTrigger className="w-full" aria-label="Kontotyp"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="TEACHER">{ROLE_LABEL.TEACHER}</SelectItem>
            <SelectItem value="STUDENT">{ROLE_LABEL.STUDENT}</SelectItem>
            <SelectItem value="PLANNING">{ROLE_LABEL.PLANNING}</SelectItem>
          </SelectContent>
        </Select>
      </Field>
      {form.role === 'TEACHER' && (
        <Field label="Lehrkraft" error={fields.errors.teacherId} hint={available.length === 0 ? 'Alle aktiven Lehrkräfte haben bereits ein Konto.' : undefined}>
          <Select value={form.teacherId != null ? String(form.teacherId) : ''} onValueChange={(v) => pickTeacher(Number(v))}>
            <SelectTrigger className="w-full" aria-label="Lehrkraft" aria-invalid={!!fields.errors.teacherId}><SelectValue placeholder="Lehrkraft wählen" /></SelectTrigger>
            <SelectContent>
              {available.map((t) => <SelectItem key={t.id} value={String(t.id)}>{t.name} <span className="text-xs text-foreground-subtle">({t.shortName})</span></SelectItem>)}
            </SelectContent>
          </Select>
        </Field>
      )}
      {form.role === 'STUDENT' && (
        <Field label="Klasse" error={fields.errors.classId} hint={!classes.isPending && availableClasses.length === 0 ? 'Alle aktiven Klassen haben bereits ein Konto.' : undefined}>
          <Select value={form.classId != null ? String(form.classId) : ''} onValueChange={(v) => pickClass(Number(v))}>
            <SelectTrigger className="w-full" aria-label="Klasse" aria-invalid={!!fields.errors.classId}><SelectValue placeholder="Klasse wählen" /></SelectTrigger>
            <SelectContent>
              {availableClasses.map((c) => <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>)}
            </SelectContent>
          </Select>
        </Field>
      )}
      <div className="grid grid-cols-2 gap-3">
        <Field label="Benutzername" htmlFor="user-name" error={fields.errors.username}>
          <Input id="user-name" value={form.username} autoComplete="off" onChange={(e) => set({ username: e.target.value })} aria-invalid={!!fields.errors.username} autoFocus={form.role === 'PLANNING'} />
        </Field>
        <Field label="Passwort" htmlFor="user-password" error={fields.errors.password} hint="mindestens 8 Zeichen">
          <Input id="user-password" type="password" autoComplete="new-password" value={form.password} onChange={(e) => set({ password: e.target.value })} aria-invalid={!!fields.errors.password} />
        </Field>
      </div>
    </FormDialog>
  )
}
