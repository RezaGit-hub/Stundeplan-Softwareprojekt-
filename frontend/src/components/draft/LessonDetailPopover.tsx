// Details einer Entwurfsstunde am Anker der Kachel: Raum ändern (PATCH roomId), Verschieben (Klick-Klick), Pin an/aus,
// aus dem Entwurf entfernen (ConfirmDialog → DELETE). Die Seite hält die gewählte Stunde und den Anker.
import { useState } from 'react'
import { toast } from 'sonner'
import { Move, Pin, PinOff, Trash2 } from 'lucide-react'
import { useDeleteDraftLesson, usePatchDraftLesson, useRooms, useSubjects, useTeachers, useTimegrid } from '@/api/hooks'
import type { ConflictDto, LessonDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Popover, PopoverAnchor, PopoverContent } from '@/components/ui/popover'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { ConflictItem } from '@/components/week-grid/ConflictPopover'
import { slotById, slotLongLabel } from '@/lib/timegrid'

export function LessonDetailPopover({ draftId, lesson, conflicts, anchor, onClose, onStartMove, readOnly = false }: {
  draftId: number
  lesson: LessonDto | null
  conflicts: ConflictDto[]
  anchor: HTMLElement | null
  onClose(): void
  /** Klick-Klick-Rückfall: Stunde markieren, dann Zielzelle anklicken */
  onStartMove?(lessonId: number): void
  /** Berechnung läuft: nur ansehen */
  readOnly?: boolean
}) {
  const subjects = useSubjects()
  const teachers = useTeachers()
  const rooms = useRooms()
  const timegrid = useTimegrid()
  const patch = usePatchDraftLesson(draftId)
  const remove = useDeleteDraftLesson(draftId)
  const [pendingDelete, setPendingDelete] = useState<LessonDto | null>(null)

  const open = lesson != null && anchor != null
  const subjectName = lesson ? (subjects.data?.find((s) => s.id === lesson.subjectId)?.name ?? lesson.subjectShort) : ''
  const teacherName = lesson ? (teachers.data?.find((t) => t.id === lesson.teacherId)?.name ?? lesson.teacherShort) : ''
  const slot = lesson && timegrid.data ? slotById(timegrid.data.slots, lesson.timeslotId) : undefined
  const where = slot && timegrid.data ? `${slotLongLabel(slot, timegrid.data.slots)} (${slot.startTime}–${slot.endTime})` : ''
  const busy = patch.isPending || readOnly

  const changeRoom = async (roomId: number) => {
    if (!lesson) return
    try {
      await patch.mutateAsync({ lessonId: lesson.id, body: { roomId } })
      toast.success('Raum geändert')
    } catch { /* Toast kam aus dem Standard-onError */ }
  }
  const togglePin = async () => {
    if (!lesson) return
    try {
      await patch.mutateAsync({ lessonId: lesson.id, body: { pinned: !lesson.pinned } })
      toast.success(lesson.pinned ? 'Festlegung aufgehoben' : 'Stunde festgelegt')
    } catch { /* Toast kam */ }
  }

  return (
    <>
      {open && (
        <Popover open onOpenChange={(o) => !o && onClose()}>
          <PopoverAnchor virtualRef={{ current: anchor }} />
          <PopoverContent align="start" side="right" sideOffset={8} className="w-80 gap-3" onOpenAutoFocus={(e) => e.preventDefault()}>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-base font-semibold">{subjectName}</span>
                <span className="text-sm text-foreground-subtle">{lesson.classShort}</span>
                {lesson.pinned && <span className="ml-auto inline-flex items-center gap-1 text-2xs font-medium text-pinned"><Pin className="size-3" aria-hidden /> festgelegt</span>}
              </div>
              <div className="text-xs text-foreground-subtle">{where}</div>
            </div>
            <dl className="space-y-1.5 text-sm">
              <div className="grid grid-cols-[5.5rem_1fr] gap-2"><dt className="text-foreground-subtle">Lehrkraft</dt><dd>{teacherName}</dd></div>
              <div className="grid grid-cols-[5.5rem_1fr] items-center gap-2">
                <dt><Label htmlFor="lesson-room" className="text-foreground-subtle font-normal">Raum</Label></dt>
                <dd>
                  <Select value={lesson.roomId != null ? String(lesson.roomId) : ''} onValueChange={(v) => { if (v && Number(v) !== lesson.roomId) void changeRoom(Number(v)) }} disabled={busy}>
                    <SelectTrigger id="lesson-room" size="sm" className="w-full"><SelectValue placeholder="Raum wählen" /></SelectTrigger>
                    <SelectContent>
                      {(rooms.data ?? []).filter((r) => r.active || r.id === lesson.roomId).map((r) => (
                        <SelectItem key={r.id} value={String(r.id)}>{r.name} <span className="text-foreground-subtle">· {r.roomType}</span></SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </dd>
              </div>
            </dl>
            {conflicts.length > 0 && (
              <ul className="space-y-2 rounded-md bg-conflict-soft/60 p-2">
                {conflicts.map((c, i) => <li key={i}><ConflictItem conflict={c} compact /></li>)}
              </ul>
            )}
            <div className="flex flex-wrap items-center gap-1.5 border-t pt-2.5">
              {onStartMove && (
                <Button size="sm" variant="outline" disabled={busy} onClick={() => onStartMove(lesson.id)} title="Danach die Zielzelle anklicken">
                  <Move data-icon="inline-start" /> Verschieben
                </Button>
              )}
              <Button size="sm" variant="outline" disabled={busy} onClick={() => void togglePin()}>
                {lesson.pinned ? <><PinOff data-icon="inline-start" /> Freigeben</> : <><Pin data-icon="inline-start" /> Festlegen</>}
              </Button>
              <Button size="sm" variant="ghost" className="ml-auto text-destructive hover:text-destructive" disabled={busy} onClick={() => { setPendingDelete(lesson); onClose() }}>
                <Trash2 data-icon="inline-start" /> Entfernen
              </Button>
            </div>
          </PopoverContent>
        </Popover>
      )}
      <ConfirmDialog
        open={pendingDelete != null}
        onOpenChange={(o) => !o && setPendingDelete(null)}
        title="Stunde aus dem Entwurf entfernen?"
        description={pendingDelete ? `${pendingDelete.subjectShort} ${pendingDelete.classShort} wird aus dem Entwurf entfernt. Die Stunde fehlt dann in „Offene Stunden", bis sie neu eingetragen oder berechnet wird.` : undefined}
        confirmLabel="Entfernen"
        destructive
        onConfirm={async () => {
          if (!pendingDelete) return
          try {
            await remove.mutateAsync(pendingDelete.id)
            toast.success('Stunde entfernt')
          } catch { /* Toast kam */ }
        }}
      />
    </>
  )
}
