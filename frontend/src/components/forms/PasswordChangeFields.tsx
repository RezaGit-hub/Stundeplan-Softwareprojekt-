// Die drei Felder des Passwortwechsels. Zustand und Pruefung stehen im Hook `usePasswordChange`
// (lib/password-change.ts) — beide Wege ins Formular teilen sich beides: das Benutzermenue der
// Kopfleiste und Einstellungen › Eigenes Passwort (Befund FRONTEND-8, 05.09.).
import { Field } from '@/components/masterdata/shared'
import { Input } from '@/components/ui/input'
import { MIN_PASSWORD_LENGTH, type usePasswordChange } from '@/lib/password-change'

export type PasswordChangeState = ReturnType<typeof usePasswordChange>

/** Die drei Felder; `idPrefix` trennt sie, wenn Dialog und Seite gleichzeitig im Baum stehen. */
export function PasswordChangeFields({ state, idPrefix = 'pw' }: {
  state: PasswordChangeState
  idPrefix?: string
}) {
  const { form, set, fields } = state
  return (
    <>
      <Field label="Bisheriges Passwort" htmlFor={`${idPrefix}-old`} error={fields.errors.oldPassword}>
        <Input
          id={`${idPrefix}-old`}
          type="password"
          autoComplete="current-password"
          value={form.oldPassword}
          onChange={(e) => set({ oldPassword: e.target.value })}
          aria-invalid={!!fields.errors.oldPassword}
        />
      </Field>
      <Field
        label="Neues Passwort"
        htmlFor={`${idPrefix}-new`}
        error={fields.errors.newPassword}
        hint={`mindestens ${MIN_PASSWORD_LENGTH} Zeichen`}
      >
        <Input
          id={`${idPrefix}-new`}
          type="password"
          autoComplete="new-password"
          value={form.newPassword}
          onChange={(e) => set({ newPassword: e.target.value })}
          aria-invalid={!!fields.errors.newPassword}
        />
      </Field>
      <Field label="Neues Passwort wiederholen" htmlFor={`${idPrefix}-confirm`} error={fields.errors.confirm}>
        <Input
          id={`${idPrefix}-confirm`}
          type="password"
          autoComplete="new-password"
          value={form.confirm}
          onChange={(e) => set({ confirm: e.target.value })}
          aria-invalid={!!fields.errors.confirm}
        />
      </Field>
    </>
  )
}
