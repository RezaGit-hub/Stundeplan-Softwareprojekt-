// Ereignis erfassen (linke Spalte der Vertretungsseite): Ausfall Lehrkraft · Raumsperrung · Zusatzstunde → POST /events.
// Antwort steht inline unter dem Formular: "n betroffene Stunden" oder der hint des Servers.
//
// Das Formular hängt an fünf Grunddaten-Abfragen. Fehlt eine, wären die Auswahllisten einfach leer und das
// Formular liefe in einen Serverfehler: deshalb ein gemeinsamer Lade- und Fehlerzustand, der es sperrt.
import { useState } from 'react'
import { toast } from 'sonner'
import { CalendarPlus, Info } from 'lucide-react'
import { useClasses, useCreateEvent, useRooms, useSubjects, useTeachers, useTimegrid } from '@/api/hooks'
import type { EventDto, EventInput, EventKind, ExtraLessonInput } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { ErrorState } from '@/components/states/ErrorState'
import { FieldError } from '@/components/forms/FieldError'
import { useFieldErrors } from '@/lib/form-errors'
import { formatDay } from '@/lib/dates'
import { DAY_SHORT, dayOfDate, slotLabel, slotsOfDay } from '@/lib/timegrid'
import { DateField } from '@/components/forms/DateField'

const KIND_LABEL: Record<EventKind, string> = {
  TEACHER_ABSENCE: 'Ausfall Lehrkraft',
  ROOM_BLOCKED: 'Raumsperrung',
  EXTRA_LESSON: 'Zusatzstunde',
}

interface Form {
  kind: EventKind
  teacherId: number | null
  roomId: number | null
  dateFrom: string
  dateTo: string
  reason: string
  extra: { timeslotId: number | null; classId: number | null; subjectId: number | null; teacherId: number | null; roomId: number | null }
}

const numOrNull = (v: string) => (v ? Number(v) : null)
const str = (v: number | null) => (v == null ? '' : String(v))

export function EventForm({ defaultDate, onCreated }: { defaultDate: string; onCreated(ev: EventDto): void }) {
  const teachers = useTeachers()
  const rooms = useRooms()
  const classes = useClasses()
  const subjects = useSubjects()
  const timegrid = useTimegrid()
  const create = useCreateEvent()
  const fields = useFieldErrors()
  const [form, setForm] = useState<Form>({
    kind: 'TEACHER_ABSENCE', teacherId: null, roomId: null, dateFrom: defaultDate, dateTo: defaultDate, reason: '',
    extra: { timeslotId: null, classId: null, subjectId: null, teacherId: null, roomId: null },
  })
  const [result, setResult] = useState<EventDto | null>(null)
  const set = (patch: Partial<Form>) => setForm((f) => ({ ...f, ...patch }))
  const setExtra = (patch: Partial<Form['extra']>) => setForm((f) => ({ ...f, extra: { ...f.extra, ...patch } }))

  // Gemeinsamer Zustand der Grunddaten: geladen wird alles, bevor das Formular bedienbar ist.
  const sources = [teachers, rooms, classes, subjects, timegrid]
  const loading = sources.some((q) => q.isPending)
  const failed = sources.find((q) => q.isError)

  const activeTeachers = (teachers.data ?? []).filter((t) => t.active)
  const activeRooms = (rooms.data ?? []).filter((r) => r.active)
  const activeClasses = (classes.data ?? []).filter((c) => c.active)
  const extraClass = activeClasses.find((c) => c.id === form.extra.classId)
  const extraDay = dayOfDate(form.dateFrom)
  const extraSlots = extraDay && timegrid.data ? slotsOfDay(timegrid.data.slots, extraDay).filter((s) => !s.isBreak) : []

  /** Fach der Zusatzstunde: Lehrkraft aus der Klassenzuordnung vorbelegen */
  const pickExtraSubject = (subjectId: number | null) => {
    const row = extraClass?.subjects.find((r) => r.subjectId === subjectId)
    setExtra({ subjectId, teacherId: row?.teacherId ?? form.extra.teacherId })
  }

  const submit = async () => {
    fields.clear()
    const body: EventInput = {
      kind: form.kind,
      dateFrom: form.dateFrom,
      dateTo: form.kind === 'EXTRA_LESSON' ? form.dateFrom : form.dateTo,
      reason: form.reason,
      teacherId: form.kind === 'TEACHER_ABSENCE' ? form.teacherId : null,
      roomId: form.kind === 'ROOM_BLOCKED' ? form.roomId : null,
      extra: form.kind === 'EXTRA_LESSON'
        ? ({ date: form.dateFrom, timeslotId: form.extra.timeslotId ?? 0, classId: form.extra.classId ?? 0, subjectId: form.extra.subjectId ?? 0, teacherId: form.extra.teacherId ?? 0, roomId: form.extra.roomId ?? 0 } satisfies ExtraLessonInput)
        : null,
    }
    try {
      const ev = await create.mutateAsync(body)
      toast.success('Ereignis erfasst')
      setResult(ev)
      set({ reason: '' })
      onCreated(ev)
    } catch (err) {
      fields.handle(err)
    }
  }

  const header = (
    <div className="flex items-center gap-2">
      <CalendarPlus className="size-4 text-foreground-subtle" aria-hidden />
      <h2 className="text-lg font-semibold">Ereignis erfassen</h2>
    </div>
  )

  if (failed) {
    return (
      <section className="flex flex-col gap-4 rounded-lg border bg-card p-4 shadow-xs" aria-label="Ereignis erfassen">
        {header}
        <p className="text-sm text-foreground-subtle">Ohne Lehrkräfte, Räume, Klassen, Fächer und Stundenraster lässt sich kein Ereignis erfassen.</p>
        <ErrorState problem={failed.error} retry={() => void failed.refetch()} />
      </section>
    )
  }
  if (loading) {
    return (
      <section className="flex flex-col gap-4 rounded-lg border bg-card p-4 shadow-xs" aria-busy="true" aria-label="Ereignis erfassen">
        {header}
        <Skeleton className="h-9 w-full bg-surface-sunken" />
        <Skeleton className="h-9 w-full bg-surface-sunken" />
        <Skeleton className="h-16 w-full bg-surface-sunken" />
        <Skeleton className="h-9 w-full bg-surface-sunken" />
      </section>
    )
  }

  return (
    <form
      onSubmit={(e) => { e.preventDefault(); void submit() }}
      className="flex flex-col gap-4 rounded-lg border bg-card p-4 shadow-xs"
      aria-label="Ereignis erfassen"
    >
      {header}

      <div>
        <Label htmlFor="ev-kind">Art</Label>
        <Select value={form.kind} onValueChange={(v) => set({ kind: v as EventKind })}>
          <SelectTrigger id="ev-kind" className="mt-1 w-full"><SelectValue /></SelectTrigger>
          <SelectContent>
            {(Object.keys(KIND_LABEL) as EventKind[]).map((k) => <SelectItem key={k} value={k}>{KIND_LABEL[k]}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>

      {form.kind === 'TEACHER_ABSENCE' && (
        <div>
          <Label htmlFor="ev-teacher">Lehrkraft</Label>
          <Select value={str(form.teacherId)} onValueChange={(v) => set({ teacherId: numOrNull(v) })}>
            <SelectTrigger id="ev-teacher" className="mt-1 w-full"><SelectValue placeholder="Lehrkraft wählen" /></SelectTrigger>
            <SelectContent>
              {activeTeachers.map((t) => <SelectItem key={t.id} value={String(t.id)}>{t.name} ({t.shortName})</SelectItem>)}
            </SelectContent>
          </Select>
          <FieldError message={fields.errors.teacherId} />
        </div>
      )}

      {form.kind === 'ROOM_BLOCKED' && (
        <div>
          <Label htmlFor="ev-room">Raum</Label>
          <Select value={str(form.roomId)} onValueChange={(v) => set({ roomId: numOrNull(v) })}>
            <SelectTrigger id="ev-room" className="mt-1 w-full"><SelectValue placeholder="Raum wählen" /></SelectTrigger>
            <SelectContent>
              {activeRooms.map((r) => <SelectItem key={r.id} value={String(r.id)}>{r.name} · {r.roomType}</SelectItem>)}
            </SelectContent>
          </Select>
          <FieldError message={fields.errors.roomId} />
        </div>
      )}

      {form.kind === 'EXTRA_LESSON' ? (
        <>
          <div>
            <Label htmlFor="ev-date">Datum</Label>
            <DateField id="ev-date" value={form.dateFrom} onChange={(d) => set({ dateFrom: d, dateTo: d })} className="mt-1 w-full" />
            <FieldError message={fields.errors['extra.date'] ?? fields.errors.dateFrom} />
          </div>
          <div>
            <Label htmlFor="ev-slot">Zeitfenster</Label>
            <Select value={str(form.extra.timeslotId)} onValueChange={(v) => setExtra({ timeslotId: numOrNull(v) })}>
              <SelectTrigger id="ev-slot" className="mt-1 w-full"><SelectValue placeholder={extraDay ? 'Stunde wählen' : 'Kein Unterrichtstag'} /></SelectTrigger>
              <SelectContent>
                {extraSlots.map((s) => (
                  <SelectItem key={s.id} value={String(s.id)}>{DAY_SHORT[s.day]} {slotLabel(s, timegrid.data?.slots ?? [])} · {s.startTime}–{s.endTime}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FieldError message={fields.errors['extra.timeslotId']} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="ev-class">Klasse</Label>
              <Select value={str(form.extra.classId)} onValueChange={(v) => setExtra({ classId: numOrNull(v), subjectId: null })}>
                <SelectTrigger id="ev-class" className="mt-1 w-full"><SelectValue placeholder="Klasse" /></SelectTrigger>
                <SelectContent>{activeClasses.map((c) => <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>)}</SelectContent>
              </Select>
              <FieldError message={fields.errors['extra.classId']} />
            </div>
            <div>
              <Label htmlFor="ev-subject">Fach</Label>
              <Select value={str(form.extra.subjectId)} onValueChange={(v) => pickExtraSubject(numOrNull(v))}>
                <SelectTrigger id="ev-subject" className="mt-1 w-full"><SelectValue placeholder="Fach" /></SelectTrigger>
                <SelectContent>
                  {(extraClass ? extraClass.subjects.map((r) => (subjects.data ?? []).find((s) => s.id === r.subjectId)).filter((s) => s != null) : subjects.data ?? [])
                    .map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.name}</SelectItem>)}
                </SelectContent>
              </Select>
              <FieldError message={fields.errors['extra.subjectId']} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="ev-extra-teacher">Lehrkraft</Label>
              <Select value={str(form.extra.teacherId)} onValueChange={(v) => setExtra({ teacherId: numOrNull(v) })}>
                <SelectTrigger id="ev-extra-teacher" className="mt-1 w-full"><SelectValue placeholder="Lehrkraft" /></SelectTrigger>
                <SelectContent>{activeTeachers.map((t) => <SelectItem key={t.id} value={String(t.id)}>{t.name}</SelectItem>)}</SelectContent>
              </Select>
              <FieldError message={fields.errors['extra.teacherId']} />
            </div>
            <div>
              <Label htmlFor="ev-extra-room">Raum</Label>
              <Select value={str(form.extra.roomId)} onValueChange={(v) => setExtra({ roomId: numOrNull(v) })}>
                <SelectTrigger id="ev-extra-room" className="mt-1 w-full"><SelectValue placeholder="Raum" /></SelectTrigger>
                <SelectContent>{activeRooms.map((r) => <SelectItem key={r.id} value={String(r.id)}>{r.name}</SelectItem>)}</SelectContent>
              </Select>
              <FieldError message={fields.errors['extra.roomId']} />
            </div>
          </div>
          <FieldError message={fields.errors.extra} />
        </>
      ) : (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Label htmlFor="ev-from">Von</Label>
            <DateField id="ev-from" value={form.dateFrom} onChange={(d) => set({ dateFrom: d, dateTo: form.dateTo < d ? d : form.dateTo })} className="mt-1 w-full" />
            <FieldError message={fields.errors.dateFrom} />
          </div>
          <div>
            <Label htmlFor="ev-to">Bis</Label>
            <DateField id="ev-to" value={form.dateTo} onChange={(d) => set({ dateTo: d })} className="mt-1 w-full" />
            <FieldError message={fields.errors.dateTo} />
          </div>
        </div>
      )}

      <div>
        <Label htmlFor="ev-reason">Anlass</Label>
        <Textarea
          id="ev-reason"
          value={form.reason}
          onChange={(e) => set({ reason: e.target.value })}
          placeholder="z. B. Krankmeldung, Wasserschaden, Exkursion"
          rows={2}
          className="mt-1"
          aria-invalid={!!fields.errors.reason}
        />
        <FieldError message={fields.errors.reason} />
      </div>

      <Button type="submit" disabled={create.isPending} className="w-full">
        {create.isPending ? 'Wird gespeichert…' : 'Ereignis speichern'}
      </Button>

      {result && (
        <div
          className={result.affected.length > 0 ? 'rounded-md bg-substitution-soft px-3 py-2 text-xs text-substitution-fg' : 'flex items-start gap-2 rounded-md bg-surface-sunken px-3 py-2 text-xs text-foreground-muted'}
          role="status"
        >
          {result.affected.length > 0 ? (
            <>
              <span className="font-semibold">{result.affected.length} betroffene {result.affected.length === 1 ? 'Stunde' : 'Stunden'}</span>
              <span className="text-substitution-fg/80"> · {KIND_LABEL[result.kind]}, {result.dateFrom === result.dateTo ? formatDay(result.dateFrom) : `${formatDay(result.dateFrom)} – ${formatDay(result.dateTo)}`}</span>
            </>
          ) : (
            <>
              <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
              <span>{result.hint ?? 'Das Ereignis betrifft keine eingeplante Stunde.'}</span>
            </>
          )}
        </div>
      )}
    </form>
  )
}
