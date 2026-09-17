// Ticket #160: Alle Grunddaten auf einmal löschen (force). Ersetzt den bisherigen Tab "Grunddaten
// löschen" (#148): statt Räume/Klassen/Lehrkräfte/Fächer einzeln über die vorhandenen Einzel-Endpunkte
// zu löschen (die bei Verwendung mit 409 scheitern), nutzt dies Anthonys Backend-Route
// DELETE /clear-data/all — die löscht serverseitig mit Kaskade. Der bisherige "Plan/Entwurf vorher
// leeren"-Schritt entfällt: die Kaskade entfernt die Stunden mit, der Plan selbst bleibt (leer) bestehen.
// Zweistufige Bestätigung bleibt: erst Warnung, dann Schulnamen eintippen (wie beim Löschen eines Git-Repos).
import { useState } from 'react'
import { toast } from 'sonner'
import { AlertTriangle, Trash2 } from 'lucide-react'
import { useClearAllMasterData, useConfig } from '@/api/hooks'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { SectionCard } from './shared'

export function ClearAllDataCard() {
  const config = useConfig()
  const clearAll = useClearAllMasterData()

  const run = async () => {
    await clearAll.mutateAsync()
    toast.success('Alle Grunddaten wurden gelöscht.')
  }

  return (
    <SectionCard
      title="Grunddaten löschen"
      description="Löscht Lehrkräfte, Klassen, Räume und Fächer zusammen mit allem, was noch darauf beruht — unabhängig davon, ob etwas noch verwendet wird."
    >
      <div className="grid gap-4 md:grid-cols-[1fr_auto] md:items-center">
        <div className="flex items-start gap-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-destructive/10 text-destructive"><AlertTriangle className="size-5" aria-hidden /></span>
          <div>
            <div className="font-medium text-destructive">Alle Grunddaten zusammen löschen</div>
            <p className="mt-0.5 text-xs text-foreground-subtle">
              Lehrkräfte, Klassen, Räume und Fächer werden endgültig gelöscht — inklusive aller darauf beruhenden Stunden. Diese Aktion kann nicht zurückgenommen werden.
            </p>
          </div>
        </div>
        <ClearAllButton schoolName={config.data?.schoolName ?? ''} onConfirm={run} pending={clearAll.isPending} />
      </div>
    </SectionCard>
  )
}

/** Zweistufige Bestätigung für die riskanteste Aktion: erst Warnung, dann Schulname eintippen. */
function ClearAllButton({ schoolName, onConfirm, pending }: { schoolName: string; onConfirm(): Promise<void>; pending: boolean }) {
  const [warnOpen, setWarnOpen] = useState(false)
  const [typeOpen, setTypeOpen] = useState(false)
  const [typed, setTyped] = useState('')
  const matches = typed.trim() === schoolName && schoolName !== ''

  const run = async () => {
    await onConfirm()
    setTypeOpen(false)
    setTyped('')
  }

  return (
    <>
      <Button variant="destructive" onClick={() => setWarnOpen(true)}><Trash2 /> Alles löschen</Button>

      <ConfirmDialog
        open={warnOpen}
        onOpenChange={setWarnOpen}
        title="Alle Grunddaten löschen?"
        description="Lehrkräfte, Klassen, Räume und Fächer werden endgültig gelöscht, ebenso alle darauf beruhenden Stunden — unabhängig davon, ob etwas noch verwendet wird."
        confirmLabel="Weiter"
        destructive
        onConfirm={() => setTypeOpen(true)}
      />

      <Dialog open={typeOpen} onOpenChange={(o) => { setTypeOpen(o); if (!o) setTyped('') }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Zur Bestätigung: Schulnamen eintippen</DialogTitle>
            <DialogDescription>Tippe „{schoolName}" ein, um das Löschen endgültig zu bestätigen.</DialogDescription>
          </DialogHeader>
          <div>
            <Label htmlFor="confirm-school-name">Name der Schule</Label>
            <Input id="confirm-school-name" value={typed} onChange={(e) => setTyped(e.target.value)} autoFocus className="mt-1" />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setTypeOpen(false); setTyped('') }} disabled={pending}>Abbrechen</Button>
            <Button variant="destructive" disabled={!matches || pending} onClick={() => void run()}>
              Endgültig alles löschen
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}