// Aktiver Plan als Kalenderwoche (W-4, W-5): Sichten Schule/Klasse/Lehrkraft/Raum, Wochenwahl, Raster read-only mit
// allen Arten (Vertretung, entfällt, …), Konfliktliste rechts, Legende, Druckansicht per CSS (print-hide).
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarDays, PencilRuler, Printer } from 'lucide-react'
import { useTimegrid, useTimetable, useWeek } from '@/api/hooks'
import type { EntryDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { WeekGrid } from '@/components/week-grid/WeekGrid'
import { WeekGridSkeleton } from '@/components/week-grid/WeekGridSkeleton'
import { isLesson } from '@/lib/grid-entry'
import { ConflictList } from '@/components/plan/ConflictList'
import { EntryDetailPopover } from '@/components/plan/EntryDetailPopover'
import { Legend } from '@/components/plan/Legend'
import { ObjectSelect } from '@/components/plan/ObjectSelect'
import { ViewSwitch } from '@/components/plan/ViewSwitch'
import { WeekPicker } from '@/components/plan/WeekPicker'
import { useObjectOptions, VIEW_LABEL } from '@/components/plan/object-options'
import { ValidFromBanner } from '@/components/plan/ValidFromBanner'
import { parseId, parseMonday, parseView, todayDayIn, useUrlState } from '@/components/plan/plan-params'
import { firstWeekMonday, formatDateTime, formatDayDate, planValidFrom, weekLabel } from '@/lib/dates'
import { showsPreviousPlan } from '@/lib/plan-validity'
import { conflictsByLesson } from '@/lib/conflicts'
import { weekMeta } from '@/lib/week-meta'
import { useJumpToLesson } from '@/hooks/use-jump-to-lesson'

export function PlanPage() {
  const { params, set } = useUrlState()
  const view = parseView(params.get('sicht'), 'SCHOOL')
  const urlId = parseId(params.get('id'))
  const objects = useObjectOptions(view)
  const id = view === 'SCHOOL' ? null : (urlId ?? objects.options?.[0]?.id ?? null)

  const timegrid = useTimegrid()
  const timetable = useTimetable()
  // Ab wann der Plan gilt: sein Stichtag „Gültig ab" (E-40), sonst der Tag seiner Annahme. Ohne `woche`
  // deshalb nicht stur die Woche von heute zeigen: wer am Samstag annimmt, säße sonst vor der Woche davor —
  // dort gilt noch der vorherige, oft leere Plan. Bis 05.09. stand hier nur die Näherung aus `acceptedAt`;
  // bei einem Plan mit Stichtag war das Datum falsch (Befund PFLICHTENHEFT-6).
  const validFrom = timetable.data?.validFrom ?? (timetable.data?.acceptedAt ? planValidFrom(timetable.data.acceptedAt) : null)
  const monday = parseMonday(params.get('woche'), validFrom)
  const week = useWeek({ monday, view, id }, { enabled: !timetable.isPending })
  const [detail, setDetail] = useState<{ entry: EntryDto; anchor: HTMLElement } | null>(null)

  const conflictsByLessonId = useMemo(
    () => conflictsByLesson(timetable.data?.conflicts ?? []),
    [timetable.data],
  )

  const { entries, dateByDay, closedDays } = useMemo(
    () => weekMeta(week.data?.days ?? []),
    [week.data],
  )

  const { highlight, jump } = useJumpToLesson(
    (lessonId) => entries.some((e) => e.lessonId === lessonId),
    () => set({ sicht: 'SCHOOL', id: null }),
  )

  if (timegrid.isPending || timetable.isPending) return <PageSkeleton />
  if (timegrid.isError) return <ErrorState problem={timegrid.error} retry={() => void timegrid.refetch()} />
  if (timetable.isError) return <ErrorState problem={timetable.error} retry={() => void timetable.refetch()} />

  if (timetable.data.id == null) {
    return (
      <>
        <PageHeader title="Aktiver Plan" />
        <EmptyState
          icon={CalendarDays}
          title="Noch kein Plan angenommen"
          description="Ein Plan entsteht als Entwurf: leer beginnen oder berechnen lassen, prüfen, dann annehmen. Erst der angenommene Plan gilt für Lehrkräfte und App."
          action={<Button asChild><Link to="/entwurf"><PencilRuler data-icon="inline-start" /> Entwurf anlegen</Link></Button>}
        />
      </>
    )
  }

  const acceptedAt = timetable.data.acceptedAt
  const objectLabel = objects.options?.find((o) => o.id === id)?.label ?? null
  const previousStand = showsPreviousPlan({
    monday,
    validFrom,
    weekEntryCount: week.data ? entries.length : 1, // solange die Woche lädt oder fehlt: kein Hinweis
    planLessonCount: timetable.data.lessons.length,
  })
  return (
    <>
      <PageHeader
        title="Aktiver Plan"
        description={acceptedAt ? `Angenommen am ${formatDateTime(acceptedAt)} · ${timetable.data.lessons.length} Stunden${validFrom ? ` · gilt ab ${formatDayDate(validFrom)}` : ''}` : undefined}
        actions={<Button variant="outline" size="sm" className="print-hide" onClick={() => window.print()}><Printer data-icon="inline-start" /> Drucken</Button>}
      />
      {/* Nur im Druck: Sicht, Objekt und Woche stehen sonst allein in den Bedienelementen, die der Druck ausblendet */}
      <p className="mb-3 hidden text-sm print:block">
        {VIEW_LABEL[view]}{objectLabel ? ` ${objectLabel}` : ''} · {weekLabel(monday)}
      </p>
      <div className="print-hide mb-4 flex flex-wrap items-center gap-2.5">
        <ViewSwitch value={view} onChange={(v) => set({ sicht: v, id: null })} />
        <ObjectSelect view={view} value={id} onChange={(next) => set({ id: String(next) })} />
        <WeekPicker monday={monday} onChange={(m) => set({ woche: m })} className="ml-auto" />
      </div>
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start">
        <div className="min-w-0 flex-1 space-y-3">
          {previousStand && validFrom && <ValidFromBanner validFrom={validFrom} onJump={() => set({ woche: firstWeekMonday(validFrom) })} />}
          {objects.isError ? (
            // Die Liste der Klassen/Lehrkräfte/Räume fehlt — ohne sie ist unbekannt, ob es Grunddaten gibt.
            <ErrorState problem={objects.error} retry={objects.refetch} />
          ) : view !== 'SCHOOL' && objects.isPending ? (
            // Solange die Liste lädt, ist `id` null. Ohne diese Zeile stünde hier „Keine aktive Klasse",
            // obwohl noch gar nichts geprüft ist (Befund FRONTEND-5).
            <WeekGridSkeleton slots={timegrid.data.slots} />
          ) : week.isPending && view !== 'SCHOOL' && id == null ? (
            <EmptyState title={`Keine aktive ${view === 'CLASS' ? 'Klasse' : view === 'TEACHER' ? 'Lehrkraft' : 'Raum'}`} description="Lege zuerst Grunddaten an." />
          ) : week.isPending ? (
            <WeekGridSkeleton slots={timegrid.data.slots} />
          ) : week.isError ? (
            <ErrorState problem={week.error} retry={() => void week.refetch()} />
          ) : (
            <WeekGrid
              slots={timegrid.data.slots}
              entries={entries}
              view={view}
              todayDay={todayDayIn(monday)}
              dateByDay={dateByDay}
              closedDays={closedDays}
              conflictsByLessonId={conflictsByLessonId}
              highlightLessonId={highlight}
              onEntryClick={(entry, anchor) => { if (!isLesson(entry)) setDetail({ entry, anchor }) }}
            />
          )}
          <Legend className="print-hide" />
        </div>
        <aside className="print-hide w-full shrink-0 xl:w-80">
          <ConflictList conflicts={timetable.data.conflicts} onJump={jump} title="Konflikte im aktiven Plan" />
        </aside>
      </div>
      <EntryDetailPopover entry={detail?.entry ?? null} anchor={detail?.anchor ?? null} onClose={() => setDetail(null)} slots={timegrid.data.slots} />
    </>
  )
}
