// Verlegen: neues Datum + Zeitfenster → POST /exceptions {kind:'MOVE'}. Antwort mit conflicts[] geht an onDone.
import { useState } from 'react'
import { useCreateException, useTimegrid } from '@/api/hooks'
import type { ExceptionDto, PendingRow } from '@/api/types'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { FieldError } from '@/components/forms/FieldError'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useFieldErrors } from '@/lib/form-errors'
import { DAY_SHORT, dayOfDate, lessonNumber, slotLabel, slotsOfDay } from '@/lib/timegrid'
import { DateField } from '@/components/forms/DateField'

export function MoveDialog({ open, row, onOpenChange, onDone }: {
  open: boolean
  row: PendingRow
  onOpenChange(open: boolean): void
  onDone(exception: ExceptionDto, target: string): void
}) {
  const timegrid = useTimegrid()
  const create = useCreateException()
  const fields = useFieldErrors()
  const [newDate, setNewDate] = useState(row.date)
  const [timeslotId, setTimeslotId] = useState<number | null>(null)
  const slots = timegrid.data?.slots ?? []
  const day = dayOfDate(newDate)
  const options = day ? slotsOfDay(slots, day).filter((s) => !s.isBreak) : []

  const submit = async () => {
    fields.clear()
    try {
      const ex = await create.mutateAsync({ date: row.date, lessonId: row.lessonId, kind: 'MOVE', newDate, newTimeslotId: timeslotId, eventIds: row.eventIds })
      const slot = slots.find((s) => s.id === timeslotId)
      const target = slot ? `${DAY_SHORT[slot.day]} ${lessonNumber(slot, slots) ?? slot.position}.` : ''
      onOpenChange(false)
      onDone(ex, target)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title={`${row.classShort} ${row.subjectShort} verlegen`} description={'Die Stunde entfällt am ursprünglichen Termin und erscheint am neuen Termin als „verlegt".'} onSubmit={submit} pending={create.isPending} submitLabel="Verlegen">
      <div>
        <Label htmlFor="move-date">Neues Datum</Label>
        <DateField id="move-date" value={newDate} onChange={(d) => { setNewDate(d); setTimeslotId(null) }} className="mt-1 w-full" />
        <FieldError message={fields.errors.newDate} />
      </div>
      <div>
        <Label htmlFor="move-slot">Zeitfenster</Label>
        <Select value={timeslotId == null ? '' : String(timeslotId)} onValueChange={(v) => setTimeslotId(v ? Number(v) : null)}>
          <SelectTrigger id="move-slot" className="mt-1 w-full"><SelectValue placeholder={day ? 'Stunde wählen' : 'Kein Unterrichtstag'} /></SelectTrigger>
          <SelectContent>
            {options.map((s) => <SelectItem key={s.id} value={String(s.id)}>{DAY_SHORT[s.day]} {slotLabel(s, slots)} · {s.startTime}–{s.endTime}</SelectItem>)}
          </SelectContent>
        </Select>
        <FieldError message={fields.errors.newTimeslotId} />
      </div>
    </FormDialog>
  )
}
