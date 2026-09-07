// Eigenes Passwort ändern — die eine Stelle für beide Wege dorthin: das Benutzermenü oben rechts und
// Einstellungen › Eigenes Passwort. Bis zum 05.09. gab es zwei Formulare mit zwei Verhalten: das
// Menü ohne Wiederholung und ohne den Hinweis „mindestens 8 Zeichen", die Einstellungen mit beidem
// (Befund FRONTEND-8). Jetzt prüfen beide gleich, und die Regel steht einmal im Code.
import { useState } from 'react'
import { toast } from 'sonner'
import { useChangeMyPassword } from '@/api/hooks'
import { useFieldErrors } from '@/lib/form-errors'

/** Kürzeste Länge, die der Server annimmt (PUT /api/me/password, 400 bei weniger). */
export const MIN_PASSWORD_LENGTH = 8

export function usePasswordChange(onDone?: () => void) {
  const change = useChangeMyPassword()
  const fields = useFieldErrors()
  const [form, setForm] = useState({ oldPassword: '', newPassword: '', confirm: '' })
  const set = (patch: Partial<typeof form>) => setForm((f) => ({ ...f, ...patch }))
  const reset = () => setForm({ oldPassword: '', newPassword: '', confirm: '' })

  const save = async () => {
    fields.clear()
    if (form.newPassword !== form.confirm) {
      fields.setFromProblem({
        type: 'urn:pushnpull:problem:validation',
        title: 'Eingaben unvollständig',
        status: 400,
        errors: [{ field: 'confirm', message: 'Die Wiederholung stimmt nicht überein' }],
      })
      return
    }
    try {
      await change.mutateAsync({ oldPassword: form.oldPassword, newPassword: form.newPassword })
      toast.success('Passwort geändert')
      reset()
      onDone?.()
    } catch (err) {
      fields.handle(err)
    }
  }

  return {
    form,
    set,
    save,
    fields,
    pending: change.isPending,
    /** Beide Felder gefüllt — der Absendeknopf bleibt sonst gesperrt. */
    ready: form.oldPassword.length > 0 && form.newPassword.length > 0,
  }
}
