// Konflikt per Knopf lösen (#110, E-53): zeigt die Vorschläge aus POST /draft/repair als Vorschau — je Vorschlag
// die Züge als Tabelle (Stunde · Bisher · Neu) und ob danach noch harte Konflikte bleiben. Sortiert: erst was alles löst,
// dann die wenigsten Züge; der erste vollständige Vorschlag ist als „Empfohlen" markiert.
// „Übernehmen" ruft je Zug PATCH /draft/lessons/{id} mit Zeitfenster, Raum und pinned:false — nacheinander, weil der
// Server je Entwurf nur eine Änderung zugleich annimmt — und schließt den Dialog. Die Anfrage selbst startet die
// Entwurfsseite mit dem Klick auf „Lösen"; der Dialog zeigt nur ihren Zustand (rechnet, gescheitert, Ergebnis).
import { useState } from 'react'
import { toast } from 'sonner'
import { CircleCheck, LoaderCircle, TriangleAlert } from 'lucide-react'
import { usePatchDraftLesson } from '@/api/hooks'
import type { LessonDto, RepairMove, RepairProposal, RoomDto, TimeslotDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DAY_SHORT, slotById, slotLabel } from '@/lib/timegrid'

export function RepairDialog({ open, onOpenChange, pending, failed, proposals, lessons, slots, rooms }: {
  open: boolean
  onOpenChange(open: boolean): void
  /** Der Server rechnet noch (drei Stufen je 5 s) */
  pending: boolean
  failed: boolean
  /** null, solange keine Antwort da ist */
  proposals: RepairProposal[] | null
  lessons: LessonDto[]
  slots: TimeslotDto[]
  rooms: RoomDto[]
}) {
  const patch = usePatchDraftLesson()
  const [applying, setApplying] = useState<number | null>(null)

  const place = (timeslotId: number | null, roomId: number | null) => {
    const slot = slotById(slots, timeslotId)
    const room = rooms.find((r) => r.id === roomId)
    return `${slot ? `${DAY_SHORT[slot.day]} ${slotLabel(slot, slots)}` : '—'} · ${room?.name ?? '—'}`
  }
  const describe = (move: RepairMove) => {
    const lesson = lessons.find((l) => l.id === move.lessonId)
    return {
      lesson: lesson ? `${lesson.subjectShort} ${lesson.classShort}` : `Stunde ${move.lessonId}`,
      from: lesson ? place(lesson.timeslotId, lesson.roomId) : '—',
      to: place(move.timeslotId, move.roomId),
    }
  }

  const apply = async (proposal: RepairProposal) => {
    setApplying(proposal.level)
    let done = 0
    try {
      for (const move of proposal.moves) {
        await patch.mutateAsync({ id: move.lessonId, body: { timeslotId: move.timeslotId, roomId: move.roomId, pinned: false } })
        done += 1
      }
      toast.success(proposal.moves.length === 1 ? 'Vorschlag übernommen — eine Stunde verschoben' : `Vorschlag übernommen — ${proposal.moves.length} Stunden verschoben`)
      onOpenChange(false)
    } catch {
      // Toast kam aus dem Standard-onError. Scheitert ein Zug nach anderen, ist der Entwurf schon halb
      // umgestellt — dann passen die übrigen Vorschläge nicht mehr zum Stand, und der Dialog geht zu.
      if (done > 0) {
        toast.warning(`Nur ${done} von ${proposal.moves.length} Stunden verschoben — den Konflikt erneut lösen lassen.`)
        onOpenChange(false)
      }
    } finally {
      setApplying(null)
    }
  }

  // Reihenfolge: erst Vorschläge, die alle Konflikte lösen, dann die mit den wenigsten Zügen. Der erste ist die Empfehlung.
  const sorted = [...(proposals ?? [])].sort((a, b) =>
    (a.hardConflictsAfter - b.hardConflictsAfter) || (a.moves.length - b.moves.length) || (a.level - b.level))
  const recommended = sorted[0]?.hardConflictsAfter === 0 ? sorted[0].level : null

  const outcome = (p: RepairProposal) =>
    p.hardConflictsAfter === 0 ? 'löst alle Konflikte' : p.hardConflictsAfter === 1 ? '1 Konflikt bleibt' : `${p.hardConflictsAfter} Konflikte bleiben`

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o && applying != null) return; onOpenChange(o) }}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Konflikt lösen</DialogTitle>
          <DialogDescription>
            Wähle einen Vorschlag. Erst „Übernehmen" ändert den Entwurf.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-3 py-2">
          {pending && (
            <p className="flex items-center gap-2 text-sm text-foreground-subtle" role="status">
              <LoaderCircle className="size-4 animate-spin" aria-hidden />
              Vorschläge werden berechnet — bis zu 15 Sekunden.
            </p>
          )}
          {!pending && failed && (
            <p className="text-sm text-destructive" role="alert">Die Vorschläge konnten nicht berechnet werden.</p>
          )}
          {!pending && !failed && proposals?.length === 0 && (
            <p className="text-sm text-foreground-subtle">Kein Vorschlag gefunden. Verschiebe die Stunde von Hand oder lass den ganzen Plan neu berechnen.</p>
          )}
          {!pending && !failed && sorted.map((p) => {
            const solves = p.hardConflictsAfter === 0
            return (
              <section
                key={p.level}
                className={`rounded-md border p-3 ${p.level === recommended ? 'border-primary/60 bg-primary/5' : 'bg-surface-sunken/50'}`}
                aria-label={`Vorschlag ${p.level}`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="flex flex-wrap items-center gap-2 text-sm font-medium">
                      <span>Vorschlag {p.level}</span>
                      {p.level === recommended && (
                        <span className="rounded-full bg-primary px-2 py-0.5 text-[11px] font-medium text-primary-foreground">Empfohlen</span>
                      )}
                    </p>
                    <p className="mt-0.5 text-xs text-foreground-subtle">{p.label}</p>
                    <p className={`mt-1 flex items-center gap-1 text-xs font-medium ${solves ? 'text-success' : 'text-warning'}`}>
                      {solves ? <CircleCheck className="size-3.5" aria-hidden /> : <TriangleAlert className="size-3.5" aria-hidden />}
                      {p.moves.length === 1 ? '1 Stunde verschieben' : `${p.moves.length} Stunden verschieben`} · {outcome(p)}
                    </p>
                  </div>
                  <Button size="sm" onClick={() => void apply(p)} disabled={applying != null} aria-label={`Vorschlag ${p.level} übernehmen`}>
                    {applying === p.level ? <LoaderCircle data-icon="inline-start" className="animate-spin" /> : null} Übernehmen
                  </Button>
                </div>
                <table className="mt-2 w-full text-xs">
                  <thead>
                    <tr className="text-left text-foreground-subtle">
                      <th className="py-1 pr-2 font-normal">Stunde</th>
                      <th className="py-1 pr-2 font-normal">Bisher</th>
                      <th className="py-1 font-normal">Neu</th>
                    </tr>
                  </thead>
                  <tbody>
                    {p.moves.map((m) => {
                      const row = describe(m)
                      return (
                        <tr key={m.lessonId} className="border-t border-border/60">
                          <td className="py-1 pr-2 font-semibold">{row.lesson}</td>
                          <td className="py-1 pr-2 text-foreground-subtle">{row.from}</td>
                          <td className="py-1 font-medium">{row.to}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </section>
            )
          })}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={applying != null}>Schließen</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
