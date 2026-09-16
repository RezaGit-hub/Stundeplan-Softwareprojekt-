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
  //
  // Scheitert der zweite Aufruf (z. B. 409 `plan-overlap`, weil ein künftiger Plan den Zeitraum belegt),
  // steht der leere Entwurf schon in der Datenbank — sichtbar wäre er nur in der Entwurfsliste, und jeder
  // weitere Klick legte „Entwurf 2", „Entwurf 3" … nach. Deshalb wird er wieder gelöscht. Die Auffrischung
  // gehört ins `finally`: auch der Fehlerweg hat den Bestand verändert.
  const run = async () => {
    setPending(true)
    let angelegt: number | null = null
    try {
      const draft = await api.createDraft({ source: 'EMPTY' })
      angelegt = draft.id
      await api.acceptDraft(draft.id, {})
      angelegt = null
      toast.success('Aktiver Plan geleert')
    } catch (err) {
      // Zuerst melden, was wirklich schiefging — das Aufräumen ist Nebensache und darf die Meldung nicht
      // überschreiben. Scheitert auch das Löschen, bleibt der Entwurf stehen, ist aber nach der
      // Auffrischung wenigstens in der Liste sichtbar.
      toastProblem(err)
      if (angelegt != null) await api.deleteDraftById(angelegt).catch(() => undefined)
    } finally {
      await Promise.all([qk.drafts, qk.draftAll, qk.timetable, qk.timetables, qk.weeks, qk.myWeeks, qk.classWeeks, qk.changelogAll, qk.pendingAll, qk.optionsAll]
        .map((queryKey) => qc.invalidateQueries({ queryKey })))
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
