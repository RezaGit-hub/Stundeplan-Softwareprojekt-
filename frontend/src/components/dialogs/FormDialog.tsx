import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'

/**
 * Rahmen aller CRUD-Dialoge: Titel, Felder (children), Abbrechen/Speichern. Enter sendet (echtes <form>).
 * onSubmit erledigt Speichern + Feldfehler selbst und schließt bei Erfolg über onOpenChange(false).
 */
export function FormDialog({ open, onOpenChange, title, description, children, onSubmit, submitLabel = 'Speichern', pending = false, submitDisabled = false }: {
  open: boolean
  onOpenChange(open: boolean): void
  title: string
  description?: ReactNode
  children: ReactNode
  onSubmit(): Promise<void>
  submitLabel?: string
  pending?: boolean
  /** Senden sperren, solange eine Rückfrage offen ist (z. B. Bestätigung im Annehmen-Dialog) */
  submitDisabled?: boolean
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            void onSubmit()
          }}
          className="contents"
        >
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            {description ? <DialogDescription>{description}</DialogDescription> : <DialogDescription className="sr-only">{title}</DialogDescription>}
          </DialogHeader>
          <div className="grid gap-4 py-2">{children}</div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>Abbrechen</Button>
            <Button type="submit" disabled={pending || submitDisabled}>{submitLabel}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
