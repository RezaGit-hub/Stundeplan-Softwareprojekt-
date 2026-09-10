// Benutzerkonten: Benutzername · Kontotyp · Lehrkraft oder Klasse; Zeilenaktion „Passwort setzen“ (PUT /users/{id}/password).
// Ein Klassenkonto (Rolle STUDENT, E-50) steht mit seiner Klasse in derselben Spalte wie die Lehrkraft — beide sagen,
// wozu das Konto gehört, und nur eines von beiden ist je Zeile gesetzt.
import { useState } from 'react'
import { toast } from 'sonner'
import { KeyRound, UserRound } from 'lucide-react'
import { useSetUserPassword } from '@/api/hooks'
import type { TeacherDto, UserDto } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { EmptyState } from '@/components/states/EmptyState'
import { DataTable } from '@/components/table/DataTable'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { useFieldErrors } from '@/lib/form-errors'
import { ROLE_LABEL } from './helpers'
import { Field, RowMenu } from './shared'

export function UsersTable({ users, teachers }: { users: UserDto[]; teachers: TeacherDto[] }) {
  const [target, setTarget] = useState<{ user: UserDto; key: number } | null>(null)
  const teacherById = new Map(teachers.map((t) => [t.id, t]))
  const ORDER: Record<UserDto['role'], number> = { PLANNING: 0, TEACHER: 1, STUDENT: 2 }
  const rows = [...users].sort((a, b) => (a.role === b.role ? a.username.localeCompare(b.username, 'de') : ORDER[a.role] - ORDER[b.role]))

  return (
    <>
      <DataTable<UserDto>
        rows={rows}
        rowKey={(u) => u.id}
        columns={[
          {
            key: 'name', header: 'Benutzername',
            cell: (u) => (
              <div className="flex items-center gap-2.5">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-surface-sunken text-foreground-subtle"><UserRound className="size-3.5" aria-hidden /></span>
                <span className="font-medium">{u.username}</span>
              </div>
            ),
          },
          { key: 'role', header: 'Kontotyp', className: 'w-32', cell: (u) => <Badge variant={u.role === 'PLANNING' ? 'default' : 'secondary'}>{ROLE_LABEL[u.role]}</Badge> },
          {
            key: 'teacher', header: 'Gehört zu',
            cell: (u) => {
              if (u.classId != null) return <span>Klasse {u.classShort ?? u.classId}</span>
              if (u.teacherId == null) return <span className="text-foreground-subtle">—</span>
              const t = teacherById.get(u.teacherId)
              return t ? <span>{t.name} <span className="text-xs text-foreground-subtle">({t.shortName})</span></span> : <span className="text-foreground-subtle">Lehrkraft {u.teacherId}</span>
            },
          },
          {
            key: 'actions', header: <span className="sr-only">Aktionen</span>, className: 'w-10 text-right',
            cell: (u) => <RowMenu items={[{ label: 'Passwort setzen', icon: KeyRound, onClick: () => setTarget((t) => ({ user: u, key: (t?.key ?? 0) + 1 })) }]} />,
          },
        ]}
        empty={<EmptyState icon={UserRound} title="Noch keine Benutzerkonten" description="Konten für die Planung, für Lehrkräfte (nur eigener Plan, lesend) und je Klasse (nur eigener Klassenplan)." />}
      />
      {target && <SetPasswordDialog key={target.key} user={target.user} onClose={() => setTarget(null)} />}
    </>
  )
}

function SetPasswordDialog({ user, onClose }: { user: UserDto; onClose(): void }) {
  const setPassword = useSetUserPassword()
  const fields = useFieldErrors()
  const [open, setOpen] = useState(true)
  const [password, setPasswordValue] = useState('')
  const close = (o: boolean) => { setOpen(o); if (!o) onClose() }

  const save = async () => {
    fields.clear()
    try {
      await setPassword.mutateAsync({ id: user.id, body: { password } })
      toast.success(`Passwort für ${user.username} gesetzt`)
      close(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog open={open} onOpenChange={close} title={`Passwort für ${user.username} setzen`} onSubmit={save} pending={setPassword.isPending} submitLabel="Passwort setzen">
      <Field label="Neues Passwort" htmlFor="set-password" error={fields.errors.password} hint="Mindestens 8 Zeichen. Die Person meldet sich danach mit dem neuen Passwort an.">
        <Input id="set-password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPasswordValue(e.target.value)} autoFocus aria-invalid={!!fields.errors.password} />
      </Field>
    </FormDialog>
  )
}
