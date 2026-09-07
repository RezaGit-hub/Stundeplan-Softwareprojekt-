// Karte „Vollständige Schule laden" (POST /sample-data): 409 data-exists → „Vorhandene Daten ersetzen?" → { replace: true }.
// Im Mock zusätzlich: Demo zurücksetzen auf Testdaten (SMALL) oder vollständige Schule (FULL).
import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Database, FlaskConical, School } from 'lucide-react'
import { IS_MOCK } from '@/api/api'
import { useClasses, useLoadSampleData, useRooms, useTeachers } from '@/api/hooks'
import { ApiError, type SampleDataResult } from '@/api/types'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { SectionCard } from './shared'

const isDataExists = (err: unknown) => err instanceof ApiError && err.status === 409 && err.problem.type.endsWith('data-exists')
const resultText = (r: SampleDataResult) => `${r.classes} Klassen, ${r.teachers} Lehrkräfte, ${r.rooms} Räume`

export function SampleDataCard() {
  const qc = useQueryClient()
  const teachers = useTeachers()
  const classes = useClasses()
  const rooms = useRooms()
  const [confirmReplace, setConfirmReplace] = useState(false)
  const [mockReset, setMockReset] = useState<'SMALL' | 'FULL' | null>(null)

  // 409 data-exists öffnet den Ersetzen-Dialog statt eines Toasts; alle anderen Fehler toastet der Hook
  const load = useLoadSampleData({ quiet: isDataExists })
  const run = async (replace = false) => {
    try {
      const r = await load.mutateAsync(replace ? { replace } : {})
      toast.success('Beispieldaten geladen', { description: resultText(r) })
    } catch (err) {
      if (isDataExists(err)) setConfirmReplace(true)
    }
  }

  const counts = teachers.data && classes.data && rooms.data
    ? `${classes.data.length} Klassen · ${teachers.data.length} Lehrkräfte · ${rooms.data.length} Räume`
    : '…'

  return (
    <SectionCard title="Beispieldaten" description="Ein vollständiger Schulbestand zum Ausprobieren von Berechnung und Sichten.">
      <div className="grid gap-4 md:grid-cols-[1fr_auto] md:items-center">
        <div className="flex items-start gap-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary-soft text-primary-soft-foreground"><School className="size-5" aria-hidden /></span>
          <div>
            <div className="font-medium">Vollständige Schule laden</div>
            <p className="mt-0.5 text-xs text-foreground-subtle">
              12 Klassen (5a–10b), 24 Lehrkräfte, 18 Räume, 10 Fächer, Regelwerk und ein berechneter aktiver Plan.
              Aktueller Bestand: <span className="tabular-nums">{counts}</span>.
            </p>
          </div>
        </div>
        <Button onClick={() => void run()} disabled={load.isPending}><Database /> Vollständige Schule laden</Button>
      </div>

      {IS_MOCK && (
        <div className="mt-4 flex flex-wrap items-center gap-2 rounded-md border border-dashed bg-surface-sunken/60 px-3 py-2 text-xs text-foreground-subtle">
          <FlaskConical className="size-3.5" aria-hidden />
          <span>Nur in der Demo: Daten auf den Ausgangszustand zurücksetzen.</span>
          <div className="ml-auto flex gap-2">
            <Button size="sm" variant="outline" onClick={() => setMockReset('SMALL')}>Demo zurücksetzen: Testdaten</Button>
            <Button size="sm" variant="outline" onClick={() => setMockReset('FULL')}>Demo zurücksetzen: Vollständige Schule</Button>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={confirmReplace}
        onOpenChange={setConfirmReplace}
        title="Vorhandene Daten ersetzen?"
        description={'Es sind bereits Grunddaten vorhanden. Mit „Ersetzen" werden Lehrkräfte, Klassen, Räume, Fächer, Regeln und Pläne durch den Beispielbestand ersetzt.'}
        confirmLabel="Ersetzen"
        destructive
        onConfirm={() => run(true)}
      />

      <ConfirmDialog
        open={mockReset != null}
        onOpenChange={(o) => { if (!o) setMockReset(null) }}
        title={mockReset === 'FULL' ? 'Demo auf die vollständige Schule zurücksetzen?' : 'Demo auf die Testdaten zurücksetzen?'}
        description="Alle Änderungen dieser Sitzung gehen verloren. Die Anmeldung bleibt bestehen."
        confirmLabel="Zurücksetzen"
        onConfirm={async () => {
          if (!mockReset) return
          // Erst hier laden: ein statischer Import zoege die ganze Attrappe in den Produktionsbuild (FRONTEND-3).
          const { mockApi } = await import('@/mock/mock-api')
          mockApi.reset(mockReset)
          void qc.invalidateQueries()
          toast.success(mockReset === 'FULL' ? 'Demo zurückgesetzt: vollständige Schule' : 'Demo zurückgesetzt: Testdaten')
        }}
      />
    </SectionCard>
  )
}
