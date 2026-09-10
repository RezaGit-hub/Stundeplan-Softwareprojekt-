// /grunddaten/raster — TimegridEditor mit einem „Speichern" (PUT /timegrid); 409 bei belegten Zeitfenstern inline.
import { useState } from 'react'
import { toast } from 'sonner'
import { RotateCcw, Save } from 'lucide-react'
import { usePutTimegrid, useTimegrid } from '@/api/hooks'
import type { ProblemDetail, TimeslotDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/layout/PageHeader'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { ProblemBanner } from '@/components/states/ProblemBanner'
import { TimegridEditor } from '@/components/masterdata/TimegridEditor'
import { isInUse, normalize } from '@/components/masterdata/helpers'
import { useFieldErrors } from '@/lib/form-errors'
import { daysOf, DAY_SHORT } from '@/lib/timegrid'

const signature = (slots: TimeslotDto[]) => JSON.stringify(slots.map((s) => [s.day, s.position, s.startTime, s.endTime, s.isBreak]))

export function TimegridPage() {
  const timegrid = useTimegrid()
  if (timegrid.isPending) return <PageSkeleton />
  if (timegrid.isError) return <ErrorState problem={timegrid.error} retry={() => void timegrid.refetch()} />
  // key = Serverstand: nach dem Speichern startet das Formular vom neuen Stand, Bearbeitung bleibt sonst erhalten
  return <TimegridForm key={signature(timegrid.data.slots)} slots={timegrid.data.slots} />
}

function TimegridForm({ slots }: { slots: TimeslotDto[] }) {
  const fields = useFieldErrors()
  const [draft, setDraft] = useState<TimeslotDto[]>(() => normalize(slots))
  const [inUse, setInUse] = useState<ProblemDetail | null>(null)
  // 409 „in-use" landet als Banner auf der Seite (kein Toast), Rest wie überall
  const put = usePutTimegrid({ quiet: isInUse })

  const serverSig = signature(normalize(slots))
  const dirty = signature(draft) !== serverSig
  const days = daysOf(draft)
  const lessonsPerDay = days.map((d) => draft.filter((s) => s.day === d && !s.isBreak).length)
  const min = Math.min(...lessonsPerDay)
  const max = Math.max(...lessonsPerDay)
  const summary = days.length === 0
    ? 'Kein Unterrichtstag'
    : `${DAY_SHORT[days[0]]}–${DAY_SHORT[days[days.length - 1]]} · ${min}${min !== max ? `–${max}` : ''} Stunden je Tag · ${draft.filter((s) => s.isBreak).length} Pausen`

  const save = async () => {
    fields.clear()
    setInUse(null)
    try {
      await put.mutateAsync({ slots: draft.map((s) => ({ id: s.id > 0 ? s.id : undefined, day: s.day, position: s.position, startTime: s.startTime, endTime: s.endTime, isBreak: s.isBreak })) })
      toast.success('Stundenraster gespeichert')
    } catch (err) {
      if (isInUse(err)) setInUse(err.problem)
      else fields.handle(err)
    }
  }

  return (
    <>
      <PageHeader
        title="Stundenraster"
        description={`${summary} — gilt für alle Klassen; Stundennummern werden gezählt, nicht gespeichert.`}
        actions={
          <>
            {dirty && <Button variant="ghost" onClick={() => { setDraft(normalize(slots)); fields.clear(); setInUse(null) }} disabled={put.isPending}><RotateCcw /> Verwerfen</Button>}
            <Button onClick={() => void save()} disabled={!dirty || put.isPending}><Save /> Speichern</Button>
          </>
        }
      />
      {inUse && <ProblemBanner problem={inUse} className="mb-4" />}
      {Object.keys(fields.errors).length > 0 && (
        <p role="alert" className="mb-4 rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-xs text-destructive">
          Eingaben unvollständig — bitte die markierten Zeitfenster prüfen.
        </p>
      )}
      <TimegridEditor value={draft} onChange={(next) => { setDraft(next); fields.clear() }} errors={fields.errors} />
    </>
  )
}
