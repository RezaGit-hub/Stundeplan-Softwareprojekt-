// Rechte Spalte: Optionen der markierten Stunde (GET /substitutions/options). Lehrkräfte fachgleich zuerst (Chip mit Fach);
// Räume passende Raumart zuerst. Kein Belastungshinweis (am 04.09. gestrichen, VERTRAG V-5).
// Zuweisen (Lehrkraft und/oder Raum in EINER POST /exceptions) · Entfall · Verlegen…
import { useState } from 'react'
import { toast } from 'sonner'
import { ArrowRightLeft, Ban, Check, DoorOpen, MessageCircleQuestion, MousePointerClick, Users } from 'lucide-react'
import { TeacherRequestPanel } from './TeacherRequestPanel'
import { useTeacherRequestGroups } from './teacher-requests'
import { toastValidation } from '@/api/errors'
import { useCreateException, useCreateSubstitutionRequests, useOptions, useSubjects, useTimegrid, useWithdrawSubstitutionRequest } from '@/api/hooks'
import type { PendingRow, RoomOption, TeacherOption } from '@/api/types'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { ErrorState } from '@/components/states/ErrorState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDay } from '@/lib/dates'
import { cn } from '@/lib/utils'
import { MoveDialog } from './MoveDialog'
import { positionLabel } from './position-label'
import type { ResolvedRow } from './types'
import { RequestTeacherDialog } from './RequestTeacherDialog'



// Umschalter, kein Radio: die Auswahl lässt sich wieder aufheben (Lehrkraft ODER Raum ODER beides zuweisen),
// und die Liste hat keine Pfeiltasten-Steuerung. Deshalb aria-pressed statt role="radio"/aria-checked —
// Screenreader melden „nicht gedrückt/gedrückt" statt einer Radiogruppe, die es nicht gibt.
function OptionButton({ selected, onClick, children }: { selected: boolean; onClick(): void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-2 rounded-md border px-2.5 py-2 text-left transition-colors hover:bg-accent',
        selected && 'border-primary bg-primary-soft/50 ring-1 ring-primary ring-inset hover:bg-primary-soft/50',
      )}
    >
      {children}
      <Check className={cn('ml-auto size-4 shrink-0 text-primary', !selected && 'invisible')} aria-hidden />
    </button>
  )
}

export function OptionsPanel({ row, onAssigned, relatedRows = [] }: {
  row: PendingRow | null
  onAssigned(result: ResolvedRow): void
  relatedRows?: PendingRow[]
}) {
  const options = useOptions(row?.date, row?.lessonId)
  const subjects = useSubjects()
  const timegrid = useTimegrid()
  const create = useCreateException()
  const [teacher, setTeacher] = useState<TeacherOption | null>(null)
  const [room, setRoom] = useState<RoomOption | null>(null)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [moveOpen, setMoveOpen] = useState(false)
  const relatedLessonIds = relatedRows.map((r) => r.lessonId)
  const { groups } = useTeacherRequestGroups(relatedLessonIds)
  const requestedTeacherIds = new Set(groups.map((g) => g.teacherId))
  const createRequests = useCreateSubstitutionRequests()
  const withdrawRequest = useWithdrawSubstitutionRequest()
  const [requestTarget, setRequestTarget] = useState<TeacherOption | null>(null)


  const frame = 'flex h-full flex-col rounded-lg border bg-card shadow-xs'

  if (!row) {
    return (
      <div className={cn(frame, 'items-center justify-center p-6 text-center')}>
        <MousePointerClick className="mb-3 size-8 text-foreground-subtle" strokeWidth={1.5} aria-hidden />
        <p className="text-sm font-medium">Keine Stunde markiert</p>
        <p className="mt-1 text-xs text-foreground-subtle">Eine Zeile in der Tabelle anklicken — hier erscheinen freie Lehrkräfte und Räume.</p>
      </div>
    )
  }

  const subjectName = subjects.data?.find((s) => s.shortName === row.subjectShort)?.name ?? row.subjectShort
  const title = `${row.classShort} · ${subjectName}`
  const when = `${formatDay(row.date)}, ${positionLabel(timegrid.data?.slots, row.date, row.position)} Stunde`

  const assign = async () => {
    try {
      const ex = await create.mutateAsync({
        date: row.date, lessonId: row.lessonId, kind: 'SUBSTITUTION',
        substituteTeacherId: teacher?.id ?? null, substituteRoomId: room?.id ?? null, eventIds: row.eventIds,
      })
      toast.success('Vertretung zugewiesen')
      onAssigned({ row, kind: 'SUBSTITUTION', exceptionId: ex.id, teacherShort: teacher?.shortName, roomShort: room?.name, conflicts: ex.conflicts })
    } catch (err) {
      // Der Vertrag kennt hier 400 mit Feldnamen (unbekannte Kennung, Vertretung ohne Aenderung, Lehrkraft faellt
      // selbst aus, Raum gesperrt). Diese Seite hat kein Eingabefeld — ohne den Aufruf bliebe der Knopf stumm.
      toastValidation(err)
    }
  }
  const cancel = async () => {
    try {
      const ex = await create.mutateAsync({ date: row.date, lessonId: row.lessonId, kind: 'CANCEL', eventIds: row.eventIds })
      toast.success('Entfall eingetragen')
      onAssigned({ row, kind: 'CANCEL', exceptionId: ex.id, conflicts: ex.conflicts })
    } catch (err) {
      toastValidation(err)
    }
  }

  return (
    <div className={frame}>
      <div className="border-b px-4 py-3">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-lg font-semibold">{title}</h2>
          <span className="rounded-sm border border-dashed border-substitution px-1.5 text-2xs font-semibold text-substitution-fg">offen</span>
        </div>
        <p className="mt-0.5 text-xs text-foreground-subtle">{when} · bisher {row.teacherShort} in {row.roomShort}</p>
        {row.reasons.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
            {row.reasons.map((r, i) => <Badge key={i} variant="secondary" className="font-normal">{r}</Badge>)}
          </div>
        )}
      </div>

      <div className="flex flex-1 flex-col divide-y overflow-y-auto">
        {options.isPending && (
          <div className="space-y-2 px-4 py-3" aria-busy="true" aria-label="Lädt Optionen">
            <Skeleton className="h-9 w-full bg-surface-sunken" />
            <Skeleton className="h-9 w-full bg-surface-sunken" />
            <Skeleton className="h-9 w-3/4 bg-surface-sunken" />
          </div>
        )}
        {options.isError && <div className="px-4 py-3"><ErrorState problem={options.error} retry={() => void options.refetch()} /></div>}
        {options.data && (
          <>
            <section aria-label="Lehrkräfte" role="group" className="flex max-h-56 flex-col overflow-hidden px-4 py-3">
              <h3 className="mb-1.5 flex shrink-0 items-center gap-1.5 text-2xs font-medium tracking-wide text-foreground-subtle uppercase">
                <Users className="size-3.5" aria-hidden /> Lehrkräfte · {options.data.teachers.length}
              </h3>
              {options.data.teachers.length === 0 && <p className="text-xs text-foreground-subtle">Keine freie Lehrkraft in dieser Stunde.</p>}
              <div className="space-y-1.5 overflow-y-auto">
                {options.data.teachers.map((t) => {
                  const requested = requestedTeacherIds.has(t.id)
                  return (
                    <div key={t.id} className="flex items-center gap-1.5">
                      <div className="flex-1">
                        <OptionButton selected={teacher?.id === t.id} onClick={() => setTeacher(teacher?.id === t.id ? null : t)}>
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-1.5">
                              <span className="font-medium">{t.name}</span>
                              <span className="text-xs text-foreground-subtle">{t.shortName}</span>
                              {t.teachesSubject && <Badge variant="outline" className="border-primary/40 bg-primary-soft text-primary-soft-foreground">{subjectName}</Badge>}
                            </div>
                          </div>
                        </OptionButton>
                      </div>
                      <button
                        type="button"
                        onClick={() => setRequestTarget(t)}
                        disabled={requested}
                        aria-label={`${t.name} anfragen`}
                        title="Lehrkraft anfragen (echte Anfrage, Backend V14/V15)"
                        className={cn(
                          'flex size-9 shrink-0 items-center justify-center rounded-md border text-foreground-subtle transition-colors hover:border-primary/50 hover:text-foreground',
                          requested && 'border-transparent text-success',
                        )}
                      >
                        {requested ? <Check className="size-4" aria-hidden /> : <MessageCircleQuestion className="size-4" aria-hidden />}
                      </button>
                    </div>
                  )
                })}
              </div>
            </section>
              
            <section aria-label="Räume" role="group" className="flex max-h-56 flex-col overflow-hidden px-4 py-3">
              <h3 className="mb-1.5 flex shrink-0 items-center gap-1.5 text-2xs font-medium tracking-wide text-foreground-subtle uppercase">
                <DoorOpen className="size-3.5" aria-hidden /> Räume · {options.data.rooms.length}
              </h3>
              {options.data.rooms.length === 0 && <p className="text-xs text-foreground-subtle">Kein freier Raum in dieser Stunde.</p>}
              <div className="space-y-1.5 overflow-y-auto">
                {options.data.rooms.map((r) => (
                  <OptionButton key={r.id} selected={room?.id === r.id} onClick={() => setRoom(room?.id === r.id ? null : r)}>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5">
                        <span className="font-medium">{r.name}</span>
                        {r.matchesRequirement && <Badge variant="outline" className="border-success/40 bg-success-soft text-success">passend</Badge>}
                      </div>
                      <div className="text-xs text-foreground-subtle">{r.roomType}</div>
                    </div>
                  </OptionButton>
                ))}
              </div>
            </section>
              
            <TeacherRequestPanel
              groups={groups}
              teacherNames={Object.fromEntries((options.data?.teachers ?? []).map((t) => [t.id, t.name]))}
              timeslots={timegrid.data?.slots}
              onWithdraw={(id) => withdrawRequest.mutate(id)}
            />
          </>
        )}
      </div>

      <div className="space-y-2 border-t px-4 py-3">
        <Button className="w-full" disabled={(!teacher && !room) || create.isPending} onClick={() => void assign()}>
          <Check /> Zuweisen{teacher ? ` · ${teacher.shortName}` : ''}{room ? ` · ${room.name}` : ''}
        </Button>
        <div className="grid grid-cols-2 gap-2">
          <Button variant="outline" disabled={create.isPending} onClick={() => setConfirmCancel(true)}><Ban /> Entfall</Button>
          <Button variant="outline" disabled={create.isPending} onClick={() => setMoveOpen(true)}><ArrowRightLeft /> Verlegen…</Button>
        </div>
      </div>

      <ConfirmDialog
        open={confirmCancel}
        onOpenChange={setConfirmCancel}
        title="Stunde entfallen lassen?"
        description={`${title}, ${when}. Die Klasse hat in dieser Stunde keinen Unterricht.`}
        confirmLabel="Entfall eintragen"
        destructive
        onConfirm={cancel}
      />
      {moveOpen && (
        <MoveDialog
          open={moveOpen}
          row={row}
          onOpenChange={setMoveOpen}
          onDone={(ex, target) => {
            toast.success('Stunde verlegt')
            onAssigned({ row, kind: 'MOVE', exceptionId: ex.id, target, conflicts: ex.conflicts })
          }}
        />
      )}
      {requestTarget && (
        <RequestTeacherDialog
          open
          onOpenChange={(o) => { if (!o) setRequestTarget(null) }}
          teacherName={requestTarget.name}
          rows={relatedRows}
          timeslots={timegrid.data?.slots}
          onConfirm={(lessonIds) => {
            const chosen = relatedRows.filter((r) => lessonIds.includes(r.lessonId))
            createRequests.mutate(chosen.map((r) => ({ teacherId: requestTarget.id, lessonId: r.lessonId, date: r.date })))
            setRequestTarget(null)
          }}
        />
      )}
    </div>
  )
}
