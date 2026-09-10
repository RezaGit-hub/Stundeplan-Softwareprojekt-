// Karte „Aktiven Plan leeren": leerer Entwurf + sofort annehmen (W-8 Schritt 1). Grunddaten bleiben erhalten.
// Ablauf: vorhandener Entwurf → DELETE /draft; POST /draft {EMPTY}; POST /draft/accept. Die Hooks invalidieren
// ['timetable'], ['week'], ['draft'] selbst.
import { useState } from 'react'
import { toast } from 'sonner'
import { CalendarX2, Eraser } from 'lucide-react'
import { useAcceptDraft, useCreateDraft, useDeleteDraft, useDraft, useTimetable } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { formatDateTime } from '@/lib/dates'
import { SectionCard } from './shared'

export function EmptyActivePlanCard() {
  const timetable = useTimetable()
  const draft = useDraft()
  const createDraft = useCreateDraft()
  const deleteDraft = useDeleteDraft()
  const accept = useAcceptDraft()
  const [confirm, setConfirm] = useState(false)
  const pending = createDraft.isPending || deleteDraft.isPending || accept.isPending

  const run = async () => {
    try {
      if (draft.data) await deleteDraft.mutateAsync(undefined)
      await createDraft.mutateAsync({ source: 'EMPTY' })
      await accept.mutateAsync(undefined)
      toast.success('Aktiver Plan geleert')
    } catch {
      // Fehler-Toast kommt aus dem Standard-onError der Hooks
    }
  }

  const lessons = timetable.data?.lessons.length ?? 0
  const status = timetable.isPending
    ? '…'
    : timetable.data?.id == null
      ? 'Noch kein Plan angenommen.'
      : `${lessons} Stunden im aktiven Plan${timetable.data.acceptedAt ? `, angenommen am ${formatDateTime(timetable.data.acceptedAt)}` : ''}.`

  return (
    <SectionCard title="Aktiver Plan" description="Für einen Neustart der Planung, z. B. vor dem Berechnen aus dem Nichts.">
      <div className="grid gap-4 md:grid-cols-[1fr_auto] md:items-center">
        <div className="flex items-start gap-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-surface-sunken text-foreground-subtle"><CalendarX2 className="size-5" aria-hidden /></span>
          <div>
            <div className="font-medium">Aktiven Plan leeren</div>
            <p className="mt-0.5 text-xs text-foreground-subtle">
              Die Grunddaten bleiben erhalten, der aktive Plan wird durch einen leeren Plan ersetzt. {status}
              {draft.data && ' Der vorhandene Entwurf wird dabei verworfen.'}
            </p>
          </div>
        </div>
        <Button variant="outline" onClick={() => setConfirm(true)} disabled={pending || timetable.isPending}><Eraser /> Aktiven Plan leeren</Button>
      </div>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title="Aktiven Plan leeren?"
        description={<>Die Grunddaten bleiben erhalten, der aktive Plan wird durch einen leeren Plan ersetzt.{draft.data ? ' Der vorhandene Entwurf wird verworfen.' : ''} Lehrkräfte sehen danach keine Stunden mehr, bis ein neuer Plan angenommen ist.</>}
        confirmLabel="Plan leeren"
        destructive
        onConfirm={run}
      />
    </SectionCard>
  )
}
