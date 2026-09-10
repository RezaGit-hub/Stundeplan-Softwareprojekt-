import { useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'

/** Bestätigung für Löschen, Verwerfen, Beispielsatz laden. onConfirm darf async sein; der Dialog wartet. */
export function ConfirmDialog({ open, onOpenChange, title, description, confirmLabel = 'Bestätigen', cancelLabel = 'Abbrechen', destructive = false, onConfirm }: {
  open: boolean
  onOpenChange(open: boolean): void
  title: string
  description?: ReactNode
  confirmLabel?: string
  /** Beschriftung des Abbruchs; „Später", wo die Rückfrage nur ein Angebot ist */
  cancelLabel?: string
  destructive?: boolean
  onConfirm(): void | Promise<void>
}) {
  const [pending, setPending] = useState(false)
  const confirm = async () => {
    setPending(true)
    try {
      await onConfirm()
      onOpenChange(false)
    } finally {
      setPending(false)
    }
  }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>{cancelLabel}</Button>
          <Button variant={destructive ? 'destructive' : 'default'} onClick={confirm} disabled={pending} autoFocus>
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
