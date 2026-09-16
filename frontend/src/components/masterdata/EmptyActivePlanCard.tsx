// Karte „Aktiven Plan leeren": leerer Entwurf + sofort annehmen (W-8 Schritt 1). Grunddaten bleiben erhalten.
// Ablauf: POST /api/drafts {EMPTY}; POST /api/drafts/{id}/accept {}. Die Hooks invalidieren ['timetable'],
// ['timetables'], ['week'] und die Entwurfsliste selbst.
//
// Seit #103 stehen Entwürfe nebeneinander: der Ablauf legt einen zusätzlichen an und rührt die vorhandenen
// nicht mehr an. „Jetzt annehmen" beendet jeden Plan, der heute gilt, am Vortag (#115) — der leere Plan
// übernimmt ab heute.
import { useState } from 'react'
import { toast } from 'sonner'
import { CalendarX2, Eraser } from 'lucide-react'
import { api } from '@/api/api'
import { useQueryClient } from '@tanstack/react-query'
import { qk } from '@/api/query-keys'
import { toastProblem } from '@/api/errors'
import { useTimetable } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { formatDateTime } from '@/lib/dates'
import { SectionCard } from './shared'

export function EmptyActivePlanCard() {
  const timetable = useTimetable()
  const qc = useQueryClient()
  const [confirm, setConfirm] = useState(false)
  const [pending, setPending] = useState(false)

  // Zwei Aufrufe auf einem Entwurf, dessen Kennung erst der erste liefert — dafür gibt es keinen
  // Mutations-Hook je Kennung. Deshalb hier direkt über `api`, mit derselben Auffrischung wie die Hooks.
  const run = async () => {
    setPending(true)
    try {
      const draft = await api.createDraft({ source: 'EMPTY' })
      await api.acceptDraft(draft.id, {})
      await Promise.all([qk.drafts, qk.draftAll, qk.timetable, qk.timetables, qk.weeks, qk.myWeeks, qk.classWeeks, qk.changelogAll, qk.pendingAll, qk.optionsAll]
        .map((queryKey) => qc.invalidateQueries({ queryKey })))
      toast.success('Aktiver Plan geleert')
    } catch (err) {
      toastProblem(err)
    } finally {
      setPending(false)
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
              Die Grunddaten bleiben erhalten, der aktive Plan wird durch einen leeren Plan ersetzt. {status} Vorhandene Entwürfe bleiben stehen.
            </p>
          </div>
        </div>
        <Button variant="outline" onClick={() => setConfirm(true)} disabled={pending || timetable.isPending}><Eraser /> Aktiven Plan leeren</Button>
      </div>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        title="Aktiven Plan leeren?"
        description={<>Die Grunddaten bleiben erhalten, der aktive Plan wird durch einen leeren Plan ersetzt. Vorhandene Entwürfe bleiben stehen. Lehrkräfte sehen danach keine Stunden mehr, bis ein neuer Plan angenommen ist.</>}
        confirmLabel="Plan leeren"
        destructive
        onConfirm={run}
      />
    </SectionCard>
  )
}
