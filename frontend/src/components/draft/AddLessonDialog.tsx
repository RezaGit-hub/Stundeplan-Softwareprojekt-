// Handaufbau (W-3): leere Zelle → Fach der Klasse (die Lehrkraft folgt aus der Klassenzuordnung, E-18) + Raum
// (passende Raumart zuerst) → POST /draft/lessons. Die Seite rendert den Dialog nur, solange eine Zelle gewählt ist.
import { useState } from 'react'
import { toast } from 'sonner'
import { useAddDraftLesson, useClass, useRooms, useSubjects, useTeachers, useTimegrid } from '@/api/hooks'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { FieldError } from '@/components/forms/FieldError'
import { useFieldErrors } from '@/lib/form-errors'
import { slotById, slotLongLabel } from '@/lib/timegrid'

export function AddLessonDialog({ open, classId, timeslotId, onOpenChange, suggestedSubjectId }: {
  open: boolean
  classId: number
  timeslotId: number
  onOpenChange(open: boolean): void
  /** z. B. das erste offene Fach der Klasse */
  suggestedSubjectId?: number | null
}) {
  const cls = useClass(classId)
  const subjects = useSubjects()
  const teachers = useTeachers()
  const rooms = useRooms()
  const timegrid = useTimegrid()
  const add = useAddDraftLesson()
  const fields = useFieldErrors()
  const [subjectId, setSubjectId] = useState<number | null>(suggestedSubjectId ?? null)
  const [roomId, setRoomId] = useState<number | null>(null)

  const rows = cls.data?.subjects ?? []
  const chosenSubjectId = subjectId ?? rows[0]?.subjectId ?? null
  const subject = subjects.data?.find((s) => s.id === chosenSubjectId)
  const requiredType = subject?.requiredRoomType ?? null
  const activeRooms = (rooms.data ?? []).filter((r) => r.active)
  const matching = requiredType ? activeRooms.filter((r) => r.roomType === requiredType) : activeRooms
  const others = requiredType ? activeRooms.filter((r) => r.roomType !== requiredType) : []
  const chosenRoomId = roomId ?? matching[0]?.id ?? activeRooms[0]?.id ?? null
  const slot = timegrid.data ? slotById(timegrid.data.slots, timeslotId) : undefined
  const where = slot && timegrid.data ? slotLongLabel(slot, timegrid.data.slots) : ''

  const save = async () => {
    fields.clear()
    if (chosenSubjectId == null || chosenRoomId == null) return
    try {
      await add.mutateAsync({ classId, subjectId: chosenSubjectId, timeslotId, roomId: chosenRoomId })
      toast.success('Stunde eingetragen')
      onOpenChange(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={`Stunde eintragen — ${cls.data?.name ?? ''}`}
      description={where ? `${where}. Die Lehrkraft folgt aus der Fächerliste der Klasse; die Stunde wird festgelegt.` : undefined}
      onSubmit={save}
      pending={add.isPending}
      submitLabel="Eintragen"
    >
      <div>
        <Label htmlFor="add-subject">Fach</Label>
        {/* Radix Select meldet beim Nachladen der Optionen einmal '' — deshalb nur echte Werte übernehmen */}
        <Select value={chosenSubjectId != null ? String(chosenSubjectId) : ''} onValueChange={(v) => { if (v) { setSubjectId(Number(v)); setRoomId(null) } }}>
          <SelectTrigger id="add-subject" className="mt-1 w-full">
            <SelectValue placeholder={rows.length === 0 ? 'Die Klasse hat noch kein Fach' : 'Fach wählen'} />
          </SelectTrigger>
          <SelectContent>
            {rows.map((r) => {
              const t = teachers.data?.find((x) => x.id === r.teacherId)
              const name = subjects.data?.find((s) => s.id === r.subjectId)?.name ?? r.subjectShort
              return (
                <SelectItem key={r.subjectId} value={String(r.subjectId)}>
                  <span>{name}</span>
                  <span className="text-foreground-subtle">— {t?.name ?? r.teacherShort}</span>
                  {t && !t.active && <Badge variant="outline" className="h-4 px-1.5 text-2xs">deaktiviert</Badge>}
                </SelectItem>
              )
            })}
          </SelectContent>
        </Select>
        <FieldError message={fields.errors.subjectId ?? fields.errors.classId} />
      </div>
      <div>
        <Label htmlFor="add-room">Raum</Label>
        <Select value={chosenRoomId != null ? String(chosenRoomId) : ''} onValueChange={(v) => { if (v) setRoomId(Number(v)) }}>
          <SelectTrigger id="add-room" className="mt-1 w-full"><SelectValue placeholder="Raum wählen" /></SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {requiredType && <SelectLabel>Passende Raumart: {requiredType}</SelectLabel>}
              {matching.map((r) => <SelectItem key={r.id} value={String(r.id)}>{r.name} <span className="text-foreground-subtle">· {r.roomType}</span></SelectItem>)}
              {requiredType && matching.length === 0 && <div className="px-2 py-1 text-xs text-foreground-subtle">Kein Raum dieser Art</div>}
            </SelectGroup>
            {others.length > 0 && (
              <SelectGroup>
                <SelectLabel>Weitere Räume</SelectLabel>
                {others.map((r) => <SelectItem key={r.id} value={String(r.id)}>{r.name} <span className="text-foreground-subtle">· {r.roomType}</span></SelectItem>)}
              </SelectGroup>
            )}
          </SelectContent>
        </Select>
        <FieldError message={fields.errors.roomId ?? fields.errors.timeslotId} />
      </div>
    </FormDialog>
  )
}
