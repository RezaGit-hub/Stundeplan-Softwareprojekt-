// Ein Entwurf (W-3, W-8, W-9) unter /entwurf/:id. Seit #103 gibt es mehrere Entwürfe nebeneinander; die
// Liste steht unter /entwurf, hier steht genau einer: Banner mit Berechnen/Annehmen/Verwerfen, Sichten wie
// /plan (Standard Klasse), Raster interaktiv nur in der Klassensicht (Drag-and-Drop + Klick-Klick, leere
// Zelle → Handaufbau, Pin), Panel offene Stunden und Konfliktliste rechts. Kein optimistisches Update:
// das Raster ändert sich mit der Serverantwort.
//
// Liegt der Entwurf in der Ablage (#116), ist die Bearbeitung gesperrt — der Server antwortet dann 409
// `draft-finished`. Damit niemand erst gegen die Sperre läuft, steht der Zustand als Leiste über dem
// Raster, und die Bedienelemente sind aus.
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Archive, ArchiveRestore, ArrowLeft, MousePointerClick, Pencil, PencilRuler } from 'lucide-react'
import { useCancelSolve, useClasses, useDeleteDraft, useDraft, usePatchDraft, usePatchDraftLesson, useRepairDraft, useRooms, useTimegrid } from '@/api/hooks'
import type { ConflictDto, LessonDto } from '@/api/types'
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
import { RenameDraftDialog } from '@/components/draft/RenameDraftDialog'
import { RepairDialog } from '@/components/draft/RepairDialog'
import { SolveDialog } from '@/components/draft/SolveDialog'
import { firstWeekMonday, formatDateTime } from '@/lib/dates'
import { conflictsByLesson } from '@/lib/conflicts'
import { useJumpToLesson } from '@/hooks/use-jump-to-lesson'

/** Anker der Konfliktliste — der Annehmen-Dialog springt hierher, wenn harte Konflikte blockieren. */
const CONFLICTS_ANCHOR = 'entwurf-konflikte'

export function DraftPage() {
  const params_ = useParams()
  const draftId = Number(params_.id)
  const { params, set } = useUrlState()
  const view = parseView(params.get('sicht'), 'CLASS')
  const urlId = parseId(params.get('id'))
  const aktion = params.get('aktion')
  const objects = useObjectOptions(view)
  const id = view === 'SCHOOL' ? null : (urlId ?? objects.options?.[0]?.id ?? null)
  const navigate = useNavigate()

  const draft = useDraft(Number.isFinite(draftId) ? draftId : null)
  const timegrid = useTimegrid()
  const classes = useClasses()
  const rooms = useRooms()
  const discard = useDeleteDraft(draftId)
  const patchDraft = usePatchDraft(draftId)
  const patch = usePatchDraftLesson(draftId)
  const cancel = useCancelSolve(draftId)
  const repair = useRepairDraft(draftId)

  // Aus der Befehlspalette („Entwurf berechnen", „Entwurf annehmen") kommt die Aktion als `aktion` in der
  // Adresse. Der Dialog steht deshalb offen, solange der Parameter da ist — abgeleitet statt in einem
  // Effekt gesetzt, damit auch eine Navigation auf die schon offene Seite wirkt. `closeAction` streicht
  // den Parameter beim Schließen, der Dialog geht also nicht von selbst wieder auf.
  const [solveOpen, setSolveOpen] = useState(false)
  const [acceptOpen, setAcceptOpen] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [renameKey, setRenameKey] = useState(0)
  const [addTarget, setAddTarget] = useState<{ classId: number; timeslotId: number } | null>(null)
  const [detail, setDetail] = useState<{ lesson: LessonDto; anchor: HTMLElement } | null>(null)
  const [moving, setMoving] = useState<number | null>(null)
  /** Die Konfliktstunde, für die Vorschläge gerechnet werden (#110); null = Dialog zu. */
  const [repairTarget, setRepairTarget] = useState<number | null>(null)

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
      await patch.mutateAsync({ lessonId, body: { timeslotId } })
    } catch { /* Toast kam aus dem Standard-onError */ }
  }
  const togglePin = async (lessonId: number, pinned: boolean) => {
    try {
      await patch.mutateAsync({ lessonId, body: { pinned } })
      toast.success(pinned ? 'Stunde festgelegt' : 'Festlegung aufgehoben')
    } catch { /* Toast kam */ }
  }
  // Lösen (#110): die Anfrage startet mit dem Klick, nicht erst im Dialog — so rechnet der Server genau einmal.
  // Bei einer Doppelbelegung wandert bevorzugt die Stunde der gerade gezeigten Klasse.
  const startRepair = (conflict: ConflictDto) => {
    const own = view === 'CLASS' && id != null ? conflict.lessonIds.find((lessonId) => d?.lessons.find((l) => l.id === lessonId)?.classId === id) : undefined
    const lessonId = own ?? conflict.lessonIds[0]
    if (lessonId == null) return
    setRepairTarget(lessonId)
    repair.mutate({ lessonId })
  }
  const reopen = async () => {
    try {
      await patchDraft.mutateAsync({ finished: false })
      toast.success('Entwurf wieder geöffnet')
    } catch { /* Toast kam */ }
  }

  if (!Number.isFinite(draftId)) return <NotFound />
  if (draft.isPending || timegrid.isPending) return <PageSkeleton />
  if (draft.isError) return <ErrorState problem={draft.error} retry={() => void draft.refetch()} />
  if (timegrid.isError) return <ErrorState problem={timegrid.error} retry={() => void timegrid.refetch()} />
  if (!d) return <NotFound />

  // Bearbeitbar heißt: keine Berechnung läuft UND der Entwurf liegt nicht in der Ablage (#116).
  const editing = d.status === 'EDITING' && !d.finished
  const hardConflicts = d.conflicts.filter((c) => c.severity === 'HARD').length
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
  const showConflicts = () => document.getElementById(CONFLICTS_ANCHOR)?.scrollIntoView({ behavior: 'smooth', block: 'center' })

  return (
    <>
      <PageHeader
        title={d.name}
        description={`Entwurf · angelegt am ${formatDateTime(d.createdAt)}`}
        actions={
          <>
            <Button variant="ghost" size="sm" asChild><Link to="/entwurf"><ArrowLeft data-icon="inline-start" /> Alle Entwürfe</Link></Button>
            <Button variant="outline" size="sm" onClick={() => setRenameKey((k) => k + 1)}><Pencil data-icon="inline-start" /> Umbenennen</Button>
          </>
        }
      />
      {d.finished && (
        <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg border bg-surface-sunken px-4 py-3 text-sm" role="status">
          <Archive className="size-4 text-foreground-subtle" aria-hidden />
          <span><b>In der Ablage.</b> Der Entwurf ist eingefroren: Stunden ändern und Berechnen sind gesperrt. Annehmen, Umbenennen und Verwerfen gehen weiter.</span>
          <Button size="sm" variant="outline" className="ml-auto" onClick={() => void reopen()} disabled={patchDraft.isPending}>
            <ArchiveRestore data-icon="inline-start" /> Wieder öffnen
          </Button>
        </div>
      )}
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
          {d.finished
            ? 'In der Ablage — nur ansehen'
            : view === 'CLASS'
              ? (editing ? 'Kachel ziehen oder anklicken · leere Zelle anklicken = Stunde eintragen' : 'Während der Berechnung nur ansehen')
              : 'Handaufbau nur in der Klassensicht'}
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
          id={CONFLICTS_ANCHOR}
          className="flex min-h-0 w-full shrink-0 flex-col gap-4 xl:w-80 max-xl:h-auto!"
          style={leftHeight ? { height: leftHeight } : undefined}
        >
          <OpenLessonsPanel
            open={d.openLessons}
            onPick={(classId) => set({ sicht: 'CLASS', id: String(classId) })}
            className="min-h-0 flex-1"
          />
          <ConflictList conflicts={d.conflicts} onJump={jump} onRepair={editing ? startRepair : undefined} />
        </aside>
      </div>

      {repairTarget != null && (
        <RepairDialog
          open
          onOpenChange={(o) => { if (!o) { setRepairTarget(null); repair.reset() } }}
          draftId={draftId}
          pending={repair.isPending}
          failed={repair.isError}
          proposals={repair.data?.proposals ?? null}
          lessons={d.lessons}
          slots={timegrid.data.slots}
          rooms={rooms.data ?? []}
        />
      )}
      {addTarget && <AddLessonDialog open draftId={draftId} classId={addTarget.classId} timeslotId={addTarget.timeslotId} suggestedSubjectId={suggestedSubjectId} onOpenChange={(o) => !o && setAddTarget(null)} />}
      <LessonDetailPopover
        draftId={draftId}
        lesson={detail?.lesson ?? null}
        conflicts={detailConflicts}
        anchor={detail?.anchor ?? null}
        onClose={() => setDetail(null)}
        onStartMove={view === 'CLASS' && editing ? (lessonId) => { setMoving(lessonId); setDetail(null) } : undefined}
        readOnly={!editing}
      />
      <SolveDialog open={solveOpen || aktion === 'berechnen'} onOpenChange={closeAction(setSolveOpen)} draftId={draftId} classes={classes.data ?? []} defaultClassId={view === 'CLASS' ? id : null} />
      {/* Nach dem Annehmen in die erste Woche, in der der Plan gilt — nicht in die Woche von heute, die noch
          den vorherigen Stand zeigt (Befund 05.09.). Der angenommene Entwurf behält seine Kennung, darum
          `plan=`: ohne das zeigt die Planseite bei einem erst künftig geltenden Plan zwar dessen Woche,
          Kopfzeile und Konflikte aber den heute gültigen. */}
      <AcceptDialog
        open={acceptOpen || aktion === 'annehmen'}
        onOpenChange={closeAction(setAcceptOpen)}
        draftId={draftId}
        warnings={d.warnings}
        openLessons={d.openLessons}
        expiringExceptions={d.expiringExceptions}
        hardConflicts={hardConflicts}
        onAccepted={(validFrom) => navigate(`/plan?plan=${draftId}&woche=${firstWeekMonday(validFrom)}`)}
        onShowConflicts={showConflicts}
      />
      {renameKey > 0 && <RenameDraftDialog key={renameKey} open onOpenChange={(o) => { if (!o) setRenameKey(0) }} draftId={draftId} currentName={d.name} />}
      <ConfirmDialog
        open={discardOpen}
        onOpenChange={setDiscardOpen}
        title={`Entwurf „${d.name}" verwerfen?`}
        description="Alle Änderungen in diesem Entwurf gehen verloren. Der aktive Plan und die übrigen Entwürfe bleiben unverändert."
        confirmLabel="Verwerfen"
        destructive
        onConfirm={async () => {
          try {
            await discard.mutateAsync()
            toast.success('Entwurf verworfen')
            navigate('/entwurf')
          } catch { /* Toast kam */ }
        }}
      />
    </>
  )
}

/** Der Entwurf ist weg (angenommen, verworfen — oder die Adresse stimmt nicht). Ein Zustand, kein Fehler. */
function NotFound() {
  return (
    <>
      <PageHeader title="Entwurf" />
      <EmptyState
        icon={PencilRuler}
        title="Diesen Entwurf gibt es nicht mehr"
        description="Er wurde angenommen oder verworfen. Die Liste zeigt, welche Entwürfe es noch gibt."
        action={<Button asChild><Link to="/entwurf"><ArrowLeft data-icon="inline-start" /> Zu den Entwürfen</Link></Button>}
      />
    </>
  )
}
