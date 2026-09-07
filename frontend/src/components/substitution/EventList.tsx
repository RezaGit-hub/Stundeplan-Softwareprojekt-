// Ereignisse der angezeigten Woche mit dem Weg zurück (Vertrag V-6, Befund FRONTEND-2, 05.09.).
//
// Bis dahin war ein versehentlich erfasster Ausfall im Web nicht mehr zu entfernen: `useDeleteEvent`
// gab es, aber keinen Knopf. Zurücknehmen löscht das Ereignis und mit ihm die Zusatzstunde eines
// EXTRA_LESSON; bereits eingetragene Vertretungen bleiben stehen (so beschreibt es der Vertrag).
import { useState } from 'react'
import { toast } from 'sonner'
import { CalendarX2, Undo2 } from 'lucide-react'
import { useDeleteEvent, useEvents } from '@/api/hooks'
import type { EventDto } from '@/api/types'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { ErrorState } from '@/components/states/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDay } from '@/lib/dates'

const KIND_LABEL: Record<EventDto['kind'], string> = {
  TEACHER_ABSENCE: 'Ausfall',
  ROOM_BLOCKED: 'Raumsperrung',
  EXTRA_LESSON: 'Zusatzstunde',
}

export function EventList({ from, to }: { from: string; to: string }) {
  const events = useEvents({ from, to })
  const remove = useDeleteEvent()
  const [toRemove, setToRemove] = useState<EventDto | null>(null)

  const frame = 'flex flex-col gap-3 rounded-lg border bg-card p-4 shadow-xs'
  const header = (
    <div className="flex items-center gap-2">
      <CalendarX2 className="size-4 text-foreground-subtle" aria-hidden />
      <h2 className="text-lg font-semibold">Ereignisse dieser Woche</h2>
    </div>
  )

  if (events.isError) {
    return <section className={frame} aria-label="Ereignisse dieser Woche">{header}<ErrorState problem={events.error} retry={() => void events.refetch()} /></section>
  }
  if (events.isPending) {
    return (
      <section className={frame} aria-busy="true" aria-label="Ereignisse dieser Woche">
        {header}
        <Skeleton className="h-8 w-full bg-surface-sunken" />
        <Skeleton className="h-8 w-2/3 bg-surface-sunken" />
      </section>
    )
  }

  return (
    <section className={frame} aria-label="Ereignisse dieser Woche">
      {header}
      {events.data.length === 0 ? (
        <p className="text-sm text-foreground-subtle">Kein Ereignis in dieser Woche.</p>
      ) : (
        <ul className="space-y-2">
          {events.data.map((ev) => (
            <li key={ev.id} className="flex items-start gap-2 border-b pb-2 text-sm last:border-b-0 last:pb-0">
              <div className="min-w-0 flex-1">
                <div className="font-medium">{KIND_LABEL[ev.kind]} · {formatDay(ev.dateFrom)}{ev.dateTo !== ev.dateFrom ? `–${formatDay(ev.dateTo)}` : ''}</div>
                <div className="text-xs text-foreground-subtle">
                  {ev.reason}
                  {ev.affected.length > 0 && ` · ${ev.affected.length} ${ev.affected.length === 1 ? 'Stunde' : 'Stunden'}`}
                </div>
              </div>
              <Button size="xs" variant="ghost" className="shrink-0 text-foreground-subtle" onClick={() => setToRemove(ev)}>
                <Undo2 data-icon="inline-start" /> Zurücknehmen
              </Button>
            </li>
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={toRemove != null}
        onOpenChange={(o) => { if (!o) setToRemove(null) }}
        title="Ereignis zurücknehmen?"
        description={toRemove
          ? `${KIND_LABEL[toRemove.kind]} vom ${formatDay(toRemove.dateFrom)}: „${toRemove.reason}". Die betroffenen Stunden sind danach nicht mehr offen; eine Zusatzstunde wird mit gelöscht.`
          : undefined}
        confirmLabel="Zurücknehmen"
        destructive
        onConfirm={async () => {
          if (!toRemove) return
          await remove.mutateAsync(toRemove.id)
          toast.success('Ereignis zurückgenommen')
          setToRemove(null)
        }}
      />
    </section>
  )
}
