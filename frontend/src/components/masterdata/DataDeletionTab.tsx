// Ticket #148: Grunddaten komplett löschen — einzeln je Typ oder alles zusammen. Es gibt keinen
// Bulk-Endpunkt im Backend; wir nutzen die vorhandenen Einzel-Endpunkte (DELETE /{typ}/{id}) in einer
// Schleife. Wer noch referenziert wird, liefert 409 "in-use" (wie beim Löschen einer einzelnen Zeile) —
// das fangen wir mit quiet auf und sammeln es zu einer Übersicht, statt für jeden Eintrag einen Toast
// zu zeigen. "Alles löschen" leert vorher automatisch Plan und Entwurf (häufigste Blockade); Konten,
// die noch an einer Lehrkraft/Klasse hängen, bleiben trotzdem eine Blockade (Ticket #108, noch offen) —
// das wird ehrlich gemeldet, nicht umgangen.
import { useState } from 'react'
import { toast } from 'sonner'
import { AlertTriangle, BookOpen, DoorOpen, GraduationCap, Trash2, Users } from 'lucide-react'
import {
  useAcceptDraft, useClasses, useConfig, useCreateDraft, useDeleteClass, useDeleteDraft, useDeleteRoom,
  useDeleteSubject, useDeleteTeacher, useDeleteUser, useDraft, useRooms, useSubjects, useTeachers, useUsers,
} from '@/api/hooks'
import type { UserDto } from '@/api/types'
import { problemOf } from '@/api/errors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { SectionCard } from './shared'

interface BulkResult { succeeded: number; failed: { label: string; message: string }[] }

function summarize(result: BulkResult, noun: string): string {
  if (result.failed.length === 0) return `${result.succeeded} ${noun} gelöscht.`
  return `${result.succeeded} ${noun} gelöscht. ${result.failed.length} nicht gelöscht (noch verwendet): ${result.failed.map((f) => f.label).join(', ')}.`
}

async function bulkDelete<T>(items: T[], idOf: (t: T) => number, labelOf: (t: T) => string, del: (id: number) => Promise<unknown>): Promise<BulkResult> {
  const result: BulkResult = { succeeded: 0, failed: [] }
  for (const item of items) {
    try {
      await del(idOf(item))
      result.succeeded += 1
    } catch (err) {
      result.failed.push({ label: labelOf(item), message: problemOf(err).detail ?? problemOf(err).title })
    }
  }
  return result
}

function DeleteAllRow({ icon: Icon, title, description, count, confirmTitle, confirmDescription, onConfirm }: {
  icon: typeof Trash2
  title: string
  description: string
  count: number
  confirmTitle: string
  confirmDescription: string
  onConfirm(): Promise<void>
}) {
  const [confirm, setConfirm] = useState(false)
  return (
    <div className="grid gap-4 border-t px-5 py-4 first:border-t-0 md:grid-cols-[1fr_auto] md:items-center">
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-surface-sunken text-foreground-subtle"><Icon className="size-5" aria-hidden /></span>
        <div>
          <div className="font-medium">{title}</div>
          <p className="mt-0.5 text-xs text-foreground-subtle">{description}</p>
        </div>
      </div>
      <Button variant="destructive" disabled={count === 0} onClick={() => setConfirm(true)}>
        <Trash2 /> Alle {count} löschen
      </Button>
      <ConfirmDialog open={confirm} onOpenChange={setConfirm} title={confirmTitle} description={confirmDescription} confirmLabel="Alle löschen" destructive onConfirm={onConfirm} />
    </div>
  )
}

export function DataDeletionTab() {
  const rooms = useRooms()
  const subjects = useSubjects()
  const teachers = useTeachers()
  const classes = useClasses()
  const users = useUsers()
  const draftQuery = useDraft()
  const config = useConfig()

  const deleteRoom = useDeleteRoom({ quiet: () => true })
  const deleteSubject = useDeleteSubject({ quiet: () => true })
  const deleteTeacher = useDeleteTeacher({ quiet: () => true })
  const deleteClass = useDeleteClass({ quiet: () => true })
  const deleteUser = useDeleteUser({ quiet: () => true })
  const deleteDraft = useDeleteDraft()
  const createDraft = useCreateDraft()
  const acceptDraft = useAcceptDraft()

  const notify = (res: BulkResult, noun: string) => {
    if (res.failed.length > 0) toast.warning(summarize(res, noun))
    else toast.success(summarize(res, noun))
  }

  /** Zugehöriges Benutzerkonto zuerst entfernen (Login-Zugang), bevor das Grunddatum selbst gelöscht wird. */
  const dropAccountFor = async (match: (u: UserDto) => boolean) => {
    const account = users.data?.find(match)
    if (account) await deleteUser.mutateAsync(account.id).catch(() => undefined)
  }
  const runRooms = async () => notify(await bulkDelete(rooms.data ?? [], (r) => r.id, (r) => r.name, (id) => deleteRoom.mutateAsync(id)), 'Räume')
  const runClasses = async () => notify(await bulkDelete(classes.data ?? [], (c) => c.id, (c) => c.name, async (id) => {
      await dropAccountFor((u) => u.classId === id)
      await deleteClass.mutateAsync(id)
    }), 'Klassen')
  const runTeachers = async () => notify(await bulkDelete(teachers.data ?? [], (t) => t.id, (t) => t.name, async (id) => {
      await dropAccountFor((u) => u.teacherId === id)
      await deleteTeacher.mutateAsync(id)
    }), 'Lehrkräfte')
  const runSubjects = async () => notify(await bulkDelete(subjects.data ?? [], (s) => s.id, (s) => s.name, (id) => deleteSubject.mutateAsync(id)), 'Fächer')

  // Häufigste Blockade zuerst aus dem Weg räumen: eingeplante Stunden verhindern sonst fast jedes
  // Löschen. Konten, die noch an einer Lehrkraft/Klasse hängen, bleiben eine eigene, echte Blockade
  // (Ticket #108) — das melden wir ehrlich, statt es zu verschleiern.
  const runAll = async () => {
    try {
      if (draftQuery.data) await deleteDraft.mutateAsync()
      await createDraft.mutateAsync({ source: 'EMPTY' })
      await acceptDraft.mutateAsync(undefined)
    } catch {
      toast.error('Plan/Entwurf konnte nicht geleert werden — Löschen wird trotzdem versucht.')
    }
    await runRooms(); await runClasses(); await runTeachers(); await runSubjects()
  }

  return (
    <SectionCard
      title="Grunddaten löschen"
      description="Löscht nur, was nicht mehr verwendet wird — jeder Eintrag wird einzeln geprüft, genau wie beim Löschen einer einzelnen Zeile auf den jeweiligen Seiten. Reihenfolge: oben am wenigsten Abhängigkeiten, unten am meisten."
    >
      <div className="-mx-5 -mb-4">
        <DeleteAllRow icon={DoorOpen} title="Alle Räume löschen" description="Räume ohne eingeplante Stunden." count={rooms.data?.length ?? 0}
          confirmTitle="Alle Räume löschen?" confirmDescription="Räume, die noch in Stunden eingeplant sind, bleiben erhalten und werden gemeldet."
          onConfirm={runRooms} />
        <DeleteAllRow icon={GraduationCap} title="Alle Klassen löschen" description="Klassen ohne eingeplante Stunden. Ein zugehöriges Klassenkonto wird mitgelöscht." count={classes.data?.length ?? 0}
             confirmTitle="Alle Klassen löschen?" confirmDescription="Klassen mit eingeplanten Stunden bleiben erhalten und werden gemeldet. Ein zugehöriges Klassenkonto wird mit der Klasse gelöscht — der Login-Zugang geht dabei verloren."
             onConfirm={runClasses} />
        <DeleteAllRow icon={Users} title="Alle Lehrkräfte löschen" description="Lehrkräfte ohne Klassenzuordnung und ohne eingeplante Stunden. Ein zugehöriges Benutzerkonto wird mitgelöscht." count={teachers.data?.length ?? 0}
             confirmTitle="Alle Lehrkräfte löschen?" confirmDescription="Lehrkräfte, die noch einer Klasse zugeordnet oder eingeplant sind, bleiben erhalten und werden gemeldet — für sie bietet sich das Deaktivieren an. Ein zugehöriges Benutzerkonto wird mit der Lehrkraft gelöscht — der Login-Zugang geht dabei verloren."
             onConfirm={runTeachers} />
        <DeleteAllRow icon={BookOpen} title="Alle Fächer löschen" description="Fächer ohne Zuordnung zu Klasse oder Lehrkraft." count={subjects.data?.length ?? 0}
          confirmTitle="Alle Fächer löschen?" confirmDescription="Fächer, die noch zugeordnet sind, bleiben erhalten und werden gemeldet."
          onConfirm={runSubjects} />
        <div className="border-t px-5 py-4">
          <div className="flex items-start gap-3 md:items-center">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-destructive/10 text-destructive"><AlertTriangle className="size-5" aria-hidden /></span>
            <div className="flex-1">
              <div className="font-medium text-destructive">Alle Grunddaten zusammen löschen</div>
              <p className="mt-0.5 text-xs text-foreground-subtle">
                Leert zuerst Plan und Entwurf, danach die obigen vier Schritte nacheinander. Zugehörige Benutzerkonten (außer „admin") werden dabei mitgelöscht.
              </p>
            </div>
            <DeleteAllButton schoolName={config.data?.schoolName ?? ''} onConfirm={runAll} />
          </div>
        </div>
      </div>
    </SectionCard>
  )
}

/** Zweistufige Bestätigung für die riskanteste Aktion: erst Warnung, dann Schulname eintippen. */
function DeleteAllButton({ schoolName, onConfirm }: { schoolName: string; onConfirm(): Promise<void> }) {
  const [warnOpen, setWarnOpen] = useState(false)
  const [typeOpen, setTypeOpen] = useState(false)
  const [typed, setTyped] = useState('')
  const [pending, setPending] = useState(false)
  const matches = typed.trim() === schoolName && schoolName !== ''

  const run = async () => {
    setPending(true)
    try {
      await onConfirm()
      setTypeOpen(false)
      setTyped('')
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <Button variant="destructive" onClick={() => setWarnOpen(true)}><Trash2 /> Alles löschen</Button>

      <ConfirmDialog
        open={warnOpen}
        onOpenChange={setWarnOpen}
        title="Alle Grunddaten löschen?"
        description="Räume, Klassen, Lehrkräfte und Fächer werden nacheinander gelöscht, soweit nicht mehr verwendet — Plan und Entwurf werden dafür zuerst geleert. Zugehörige Benutzerkonten werden mit gelöscht; das Konto 'admin' bleibt geschützt."
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