// Der eine Entwurf (W-3, W-8, W-9): Banner mit Berechnen/Annehmen/Verwerfen, Sichten wie /plan (Standard Klasse),
// Raster interaktiv nur in der Klassensicht (Drag-and-Drop + Klick-Klick, leere Zelle → Handaufbau, Pin), Panel
// offene Stunden und Konfliktliste rechts. Kein optimistisches Update: das Raster ändert sich mit der Serverantwort.
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Copy, FilePlus, MousePointerClick, PencilRuler } from 'lucide-react'
import { useCancelSolve, useClasses, useCreateDraft, useDeleteDraft, useDraft, usePatchDraftLesson, useTimegrid } from '@/api/hooks'
import type { LessonDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { WeekGrid } from '@/components/week-grid/WeekGrid'
import { WeekGridSkeleton } from '@/components/week-grid/WeekGridSkeleton'
import { isLesson } from '@/lib/grid-entry'
import { ConflictList } from '@/components/plan/ConflictList'
import { Legend } from '@/components/plan/Legend'
import { ObjectSelect } from '@/components/plan/ObjectSelect'
import { ViewSwitch } from '@/components/plan/ViewSwitch'
import { useObjectOptions } from '@/components/plan/object-options'
import { parseId, parseView, useUrlState } from '@/components/plan/plan-params'
import { AcceptDialog } from '@/components/draft/AcceptDialog'
import { AddLessonDialog } from '@/components/draft/AddLessonDialog'
import { DraftBanner } from '@/components/draft/DraftBanner'
import { LessonDetailPopover } from '@/components/draft/LessonDetailPopover'
import { OpenLessonsPanel } from '@/components/draft/OpenLessonsPanel'
import { SolveDialog } from '@/components/draft/SolveDialog'
import { firstWeekMonday, formatDateTime } from '@/lib/dates'
import { conflictsByLesson } from '@/lib/conflicts'
import { useJumpToLesson } from '@/hooks/use-jump-to-lesson'

export function DraftPage() {
  const { params, set } = useUrlState()
  const view = parseView(params.get('sicht'), 'CLASS')
  const urlId = parseId(params.get('id'))
  const aktion = params.get('aktion')
  const objects = useObjectOptions(view)
  const id = view === 'SCHOOL' ? null : (urlId ?? objects.options?.[0]?.id ?? null)
  const navigate = useNavigate()

  const draft = useDraft()
  const timegrid = useTimegrid()
  const classes = useClasses()
  const create = useCreateDraft()
  const discard = useDeleteDraft()
  const patch = usePatchDraftLesson()
  const cancel = useCancelSolve()

  const [solveOpen, setSolveOpen] = useState(aktion === 'berechnen')
  const [acceptOpen, setAcceptOpen] = useState(aktion === 'annehmen')
  const [discardOpen, setDiscardOpen] = useState(false)
  const [addTarget, setAddTarget] = useState<{ classId: number; timeslotId: number } | null>(null)
  const [detail, setDetail] = useState<{ lesson: LessonDto; anchor: HTMLElement } | null>(null)
  const [moving, setMoving] = useState<number | null>(null)

  const [leftEl, setLeftEl] = useState<HTMLDivElement | null>(null)
  const [leftHeight, setLeftHeight] = useState<number>()

  useEffect(() => {
    if (!leftEl) return
    const observer = new ResizeObserver(([entry]) => setLeftHeight(entry.contentRect.height))
    observer.observe(leftEl)
    return () => observer.disconnect()
  }, [leftEl])

  // Esc hebt den Klick-Klick-Modus auf
  useEffect(() => {
    if (moving == null) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMoving(null) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [moving])

  const d = draft.data ?? null
  const conflictsByLessonId = useMemo(() => conflictsByLesson(d?.conflicts ?? []), [d])
  const lessons = useMemo(() => {
    const all = (d?.lessons ?? []).filter((l) => l.timeslotId != null)
    if (view === 'SCHOOL') return all
    if (id == null) return []
    return all.filter((l) => (view === 'CLASS' ? l.classId : view === 'TEACHER' ? l.teacherId : l.roomId) === id)
  }, [d, view, id])

  const { highlight, jump } = useJumpToLesson(
    (lessonId) => lessons.some((l) => l.id === lessonId),
    () => set({ sicht: 'SCHOOL', id: null }),
  )

  const closeAction = (setter: (o: boolean) => void) => (o: boolean) => {
    setter(o)
    if (!o && aktion) set({ aktion: null })
  }

  const move = async (lessonId: number, timeslotId: number) => {
    setMoving(null)
    try {
      await patch.mutateAsync({ id: lessonId, body: { timeslotId } })
    } catch { /* Toast kam aus dem Standard-onError */ }
  }
  const togglePin = async (lessonId: number, pinned: boolean) => {
    try {
      await patch.mutateAsync({ id: lessonId, body: { pinned } })
      toast.success(pinned ? 'Stunde festgelegt' : 'Festlegung aufgehoben')
    } catch { /* Toast kam */ }
  }
  const createDraft = async (source: 'EMPTY' | 'COPY_ACTIVE') => {
    try {
      await create.mutateAsync({ source })
      toast.success(source === 'EMPTY' ? 'Leerer Entwurf angelegt' : 'Entwurf aus dem aktiven Plan angelegt')
    } catch { /* Toast kam */ }
  }

  if (draft.isPending || timegrid.isPending) return <PageSkeleton />
  if (draft.isError) return <ErrorState problem={draft.error} retry={() => void draft.refetch()} />
  if (timegrid.isError) return <ErrorState problem={timegrid.error} retry={() => void timegrid.refetch()} />

  if (!d) {
    return (
      <>
        <PageHeader title="Entwurf" description={'Eine Arbeitskopie neben dem aktiven Plan. Erst „Annehmen" macht sie gültig.'} />
        <EmptyState
          icon={PencilRuler}
          title="Kein Entwurf vorhanden"
          description="Leer beginnen und berechnen lassen, oder den aktiven Plan kopieren und anpassen. Es gibt immer höchstens einen Entwurf."
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <Button variant="outline" onClick={() => void createDraft('EMPTY')} disabled={create.isPending}><FilePlus data-icon="inline-start" /> Leer beginnen</Button>
              <Button onClick={() => void createDraft('COPY_ACTIVE')} disabled={create.isPending}><Copy data-icon="inline-start" /> Aktiven Plan kopieren</Button>
            </div>
          }
        />
      </>
    )
  }

  const editing = d.status === 'EDITING'
  const interactive = editing && view === 'CLASS' && id != null
    ? {
        onMove: (lessonId: number, timeslotId: number) => void move(lessonId, timeslotId),
        onEmptyCell: (timeslotId: number) => setAddTarget({ classId: id, timeslotId }),
        onTogglePin: (lessonId: number, pinned: boolean) => void togglePin(lessonId, pinned),
      }
    : undefined
  const movingLesson = moving != null ? d.lessons.find((l) => l.id === moving) : undefined
  const detailConflicts = detail ? (conflictsByLessonId.get(detail.lesson.id) ?? []) : []
  const suggestedSubject = addTarget ? (d.openLessons.find((o) => o.classId === addTarget.classId) ?? null) : null
  const suggestedSubjectId = suggestedSubject ? (classes.data?.find((c) => c.id === addTarget!.classId)?.subjects.find((s) => s.subjectShort === suggestedSubject.subjectShort)?.subjectId ?? null) : null

  return (
    <>
      <PageHeader title="Entwurf" description={`Angelegt am ${formatDateTime(d.createdAt)}`} />
      <DraftBanner
        draft={d}
        className="mb-4"
        pending={cancel.isPending || discard.isPending}
        onSolve={() => setSolveOpen(true)}
        onCancel={() => { cancel.mutateAsync().then(() => toast.success('Berechnung abgebrochen — Zwischenstand übernommen')).catch(() => undefined) }}
        onAccept={() => setAcceptOpen(true)}
        onDiscard={() => setDiscardOpen(true)}
      />
      <div className="mb-4 flex flex-wrap items-center gap-2.5">
        <ViewSwitch value={view} onChange={(v) => { setMoving(null); set({ sicht: v, id: null }) }} layoutId="draft-view-switch" />
        <ObjectSelect view={view} value={id} onChange={(next) => { setMoving(null); set({ id: String(next) }) }} />
        <span className="text-xs text-foreground-subtle">
          {view === 'CLASS' ? (editing ? 'Kachel ziehen oder anklicken · leere Zelle anklicken = Stunde eintragen' : 'Während der Berechnung nur ansehen') : 'Handaufbau nur in der Klassensicht'}
        </span>
      </div>
      {movingLesson && (
        <div className="mb-3 flex items-center gap-2 rounded-lg border border-primary/40 bg-primary-soft px-3 py-2 text-sm text-primary-soft-foreground" role="status">
          <MousePointerClick className="size-4" aria-hidden />
          <span>Zielzelle anklicken, um <b>{movingLesson.subjectShort} {movingLesson.classShort}</b> zu verschieben.</span>
          <Button size="xs" variant="ghost" className="ml-auto" onClick={() => setMoving(null)}>Abbrechen (Esc)</Button>
        </div>
      )}
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start">
        <div ref={setLeftEl} className="min-w-0 flex-1 space-y-3">
          {objects.isError ? (
            // Die Liste der Klassen/Lehrkräfte/Räume fehlt — ohne sie ist unbekannt, ob es Grunddaten gibt.
            <ErrorState problem={objects.error} retry={objects.refetch} />
          ) : view !== 'SCHOOL' && objects.isPending ? (
            // Solange die Liste lädt, ist `id` null — sonst stünde hier „Keine aktive Klasse" (FRONTEND-5).
            <WeekGridSkeleton slots={timegrid.data.slots} />
          ) : view !== 'SCHOOL' && id == null ? (
            <EmptyState title={`Keine aktive ${view === 'CLASS' ? 'Klasse' : view === 'TEACHER' ? 'Lehrkraft' : 'Raum'}`} description="Lege zuerst Grunddaten an." />
          ) : (
            <WeekGrid
              slots={timegrid.data.slots}
              entries={lessons}
              view={view}
              conflictsByLessonId={conflictsByLessonId}
              selectedLessonId={moving}
              highlightLessonId={highlight}
              interactive={interactive}
              busy={d.status === 'SOLVING'}
              className="bg-draft-tint"
              onEntryClick={(entry, anchor) => { if (isLesson(entry)) setDetail({ lesson: entry, anchor }) }}
            />
          )}
          <Legend />
        </div>
        <aside
          className="flex min-h-0 w-full shrink-0 flex-col gap-4 xl:w-80 max-xl:h-auto!"
          style={leftHeight ? { height: leftHeight } : undefined}
        >
          <OpenLessonsPanel
            open={d.openLessons}
            onPick={(classId) => set({ sicht: 'CLASS', id: String(classId) })}
            className="min-h-0 flex-1"
          />
          <ConflictList conflicts={d.conflicts} onJump={jump} />
        </aside>
      </div>

      {addTarget && <AddLessonDialog open classId={addTarget.classId} timeslotId={addTarget.timeslotId} suggestedSubjectId={suggestedSubjectId} onOpenChange={(o) => !o && setAddTarget(null)} />}
      <LessonDetailPopover
        lesson={detail?.lesson ?? null}
        conflicts={detailConflicts}
        anchor={detail?.anchor ?? null}
        onClose={() => setDetail(null)}
        onStartMove={view === 'CLASS' && editing ? (lessonId) => { setMoving(lessonId); setDetail(null) } : undefined}
        readOnly={!editing}
      />
      <SolveDialog open={solveOpen} onOpenChange={closeAction(setSolveOpen)} classes={classes.data ?? []} defaultClassId={view === 'CLASS' ? id : null} />
      {/* Nach dem Annehmen in die erste Woche, in der der Plan gilt — nicht in die Woche von heute, die noch
          den vorherigen Stand zeigt (Befund 05.09.). */}
      <AcceptDialog open={acceptOpen} onOpenChange={closeAction(setAcceptOpen)} warnings={d.warnings} openLessons={d.openLessons} expiringExceptions={d.expiringExceptions} onAccepted={(validFrom) => navigate(`/plan?woche=${firstWeekMonday(validFrom)}`)} />
      <ConfirmDialog
        open={discardOpen}
        onOpenChange={setDiscardOpen}
        title="Entwurf verwerfen?"
        description="Alle Änderungen im Entwurf gehen verloren. Der aktive Plan bleibt unverändert."
        confirmLabel="Verwerfen"
        destructive
        onConfirm={async () => {
          try {
            await discard.mutateAsync()
            toast.success('Entwurf verworfen')
          } catch { /* Toast kam */ }
        }}
      />
    </>
  )
}
