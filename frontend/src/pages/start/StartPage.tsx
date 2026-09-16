// / — Übersicht (Tremor-Vorbild): vier Kennzahlen, aktiver Plan dieser Woche (Sicht Schule, read-only),
// nächste offene Stunden, Entwurf-Hinweis. Lehrkraft-Konten landen auf /mein-plan (RequireRole macht das schon; hier doppelt sicher).
import { Link, Navigate } from 'react-router-dom'
import { motion } from 'motion/react'
import { format } from 'date-fns'
import { AlertTriangle, ArrowRight, CalendarDays, CheckCircle2, PencilRuler, Plus, Replace, Users } from 'lucide-react'
import { useDrafts, usePending, useTeachers, useTimegrid, useTimetable, useWeek } from '@/api/hooks'

import { useAuth } from '@/app/auth'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { StatTile } from '@/components/stats/StatTile'
import { positionLabel } from '@/components/substitution/position-label'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { WeekGrid } from '@/components/week-grid/WeekGrid'
import { addDaysIso, de, formatDay, fromIso, startOfIsoWeek, weekLabel, weekRange } from '@/lib/dates'
import { listItem } from '@/lib/motion'
import { dayOfDate } from '@/lib/timegrid'
import { conflictsByLesson } from '@/lib/conflicts'
import { weekMeta } from '@/lib/week-meta'
import { today } from '@/lib/today'

const NEXT_DAYS = 14
const NEXT_MAX = 5

export function StartPage() {
  const { role } = useAuth()
  const now = today()
  const monday = startOfIsoWeek(now)
  const thisWeek = weekRange(now)

  const timetable = useTimetable()
  const timegrid = useTimegrid()
  const week = useWeek({ monday, view: 'SCHOOL' })
  const pendingWeek = usePending(thisWeek.from, thisWeek.to)
  const pendingNext = usePending(now, addDaysIso(now, NEXT_DAYS - 1))
  const drafts = useDrafts()
  const teachers = useTeachers()

  if (role === 'TEACHER') return <Navigate to="/mein-plan" replace />
  if (timetable.isPending || timegrid.isPending) return <PageSkeleton />
  if (timetable.isError) return <ErrorState problem={timetable.error} retry={() => void timetable.refetch()} />
  if (timegrid.isError) return <ErrorState problem={timegrid.error} retry={() => void timegrid.refetch()} />

  const slots = timegrid.data.slots
  const hasPlan = timetable.data.id != null
  const conflicts = timetable.data.conflicts
  const hard = conflicts.filter((c) => c.severity === 'HARD').length
  const conflictsByLessonId = conflictsByLesson(conflicts)

  const { entries, dateByDay } = weekMeta(week.data?.days ?? [])
  const todayEntries = week.data?.days.find((d) => d.date === now)?.entries.filter((e) => e.kind !== 'CANCELLED' && e.kind !== 'GIVEN_AWAY') ?? []

  const activeTeachers = teachers.data?.filter((t) => t.active).length
  // Seit #103 stehen mehrere Entwürfe nebeneinander. Die Kennzahl zählt die, die noch in Arbeit sind —
  // was in der Ablage liegt (#116), ist absichtlich beiseitegelegt und soll nicht mahnen.
  const offeneEntwuerfe = (drafts.data ?? []).filter((d) => !d.finished)
  const draftHardConflicts = offeneEntwuerfe.reduce((sum, d) => sum + d.hardConflicts, 0)
  const pendingCount = pendingWeek.data?.length
  // Fehlgeschlagene Abfragen sagen es: „kein Entwurf", „…" oder eine leere Liste wären hier falsche
  // Aussagen (Befund FRONTEND-1, aus web übernommen).
  const FAILED = 'Fehler'
  const failedHint = 'konnte nicht geladen werden'
  const nextRows = (pendingNext.data ?? []).slice(0, NEXT_MAX)

  return (
    <>
      <PageHeader
        title="Übersicht"
        description={`${format(fromIso(now), 'EEEE, dd.MM.yyyy', { locale: de })} · ${todayEntries.length} ${todayEntries.length === 1 ? 'Stunde' : 'Stunden'} heute`}
        actions={
          <Button asChild variant="outline">
            <Link to={`/vertretung?datum=${now}`}><Replace /> Neue Vertretung</Link>
          </Button>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatTile
          label="Offene Vertretungen"
          value={pendingWeek.isError ? FAILED : pendingCount ?? '…'}
          hint={pendingWeek.isError ? `Offene Stunden ${failedHint}` : `diese Woche · ${weekLabel(monday)}`}
          tone={pendingWeek.isError ? 'destructive' : pendingCount ? 'warning' : 'default'}
          icon={Replace}
          to={`/vertretung?datum=${now}`}
        />
        <StatTile
          label="Konflikte im aktiven Plan"
          value={hasPlan ? conflicts.length : '—'}
          hint={hasPlan ? `${hard} hart · ${conflicts.length - hard} weich` : 'noch kein Plan angenommen'}
          tone={hard > 0 ? 'destructive' : conflicts.length > 0 ? 'warning' : 'default'}
          icon={AlertTriangle}
          to="/plan"
        />
        <StatTile
          label="Entwürfe in Arbeit"
          value={drafts.isError ? FAILED : offeneEntwuerfe.length}
          hint={drafts.isError ? `Entwürfe ${failedHint}` : offeneEntwuerfe.length === 0 ? 'Entwurf anlegen unter Planung' : `${draftHardConflicts} harte Konflikte`}
          tone={drafts.isError ? 'destructive' : draftHardConflicts > 0 ? 'warning' : 'default'}
          icon={PencilRuler}
          to="/entwurf"
        />
        <StatTile
          label="Lehrkräfte"
          value={teachers.isError ? FAILED : activeTeachers != null ? `${activeTeachers} / ${teachers.data?.length ?? 0}` : '…'}
          hint={teachers.isError ? `Lehrkräfte ${failedHint}` : 'aktiv / gesamt'}
          tone={teachers.isError ? 'destructive' : 'default'}
          icon={Users}
          to="/grunddaten/lehrkraefte"
        />
      </div>

      <div className="mt-5 grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_20rem]">
        <section aria-label="Aktiver Plan dieser Woche" className="min-w-0">
          <div className="mb-2 flex items-center justify-between gap-2">
            <h2 className="flex items-center gap-2 text-lg font-semibold">
              <CalendarDays className="size-4 text-foreground-subtle" aria-hidden /> Aktiver Plan · Schule
              <span className="text-xs font-normal text-foreground-subtle tabular-nums">{weekLabel(monday)}</span>
            </h2>
            {hasPlan && (
              <Button asChild variant="ghost" size="sm">
                <Link to={`/plan?woche=${monday}&sicht=SCHOOL`}>Zum Plan <ArrowRight /></Link>
              </Button>
            )}
          </div>
          {!hasPlan ? (
            <EmptyState
              icon={CalendarDays}
              title="Noch kein Plan angenommen"
              description="Ein Entwurf wird berechnet oder von Hand aufgebaut und dann angenommen — erst dann gibt es einen aktiven Plan."
              action={<Button asChild><Link to="/entwurf"><Plus /> Entwurf anlegen</Link></Button>}
              className="min-h-72"
            />
          ) : week.isError ? (
            <ErrorState problem={week.error} retry={() => void week.refetch()} />
          ) : (
            <WeekGrid
              slots={slots}
              entries={entries}
              view="SCHOOL"
              todayDay={dayOfDate(now) ?? undefined}
              dateByDay={dateByDay}
              conflictsByLessonId={conflictsByLessonId}
            />
          )}
        </section>

        <aside className="space-y-5">
          <section aria-label="Nächste offene Stunden" className="rounded-lg border bg-card shadow-xs">
            <div className="flex items-center justify-between border-b px-4 py-3">
              <h2 className="text-lg font-semibold">Nächste offene Stunden</h2>
              <span className="text-xs text-foreground-subtle">{NEXT_DAYS} Tage</span>
            </div>
            {pendingNext.isPending ? (
              <div className="space-y-2 p-4"><Skeleton className="h-8 bg-surface-sunken" /><Skeleton className="h-8 bg-surface-sunken" /></div>
            ) : pendingNext.isError ? (
              <div className="p-4"><ErrorState problem={pendingNext.error} retry={() => void pendingNext.refetch()} /></div>
            ) : nextRows.length === 0 ? (
              <div className="flex items-center gap-2 px-4 py-5 text-sm text-foreground-subtle">
                <CheckCircle2 className="size-4 text-success" aria-hidden /> Alle Stunden sind besetzt.
              </div>
            ) : (
              <ul className="divide-y">
                {nextRows.map((r, i) => (
                  <motion.li key={`${r.date}:${r.lessonId}`} {...listItem(i)}>
                    <Link
                      to={`/vertretung?datum=${r.date}`}
                      className="flex items-center gap-3 border-l-2 border-l-substitution px-4 py-2.5 transition-colors [border-left-style:dashed] hover:bg-substitution-soft/60"
                    >
                      <span className="w-16 shrink-0 text-xs whitespace-nowrap text-foreground-subtle tabular-nums">{formatDay(r.date)}</span>
                      <span className="w-6 shrink-0 font-semibold tabular-nums">{positionLabel(slots, r.date, r.position)}</span>
                      <span className="min-w-0 flex-1 truncate">
                        <span className="font-medium">{r.classShort}</span> {r.subjectShort}
                        <span className="text-foreground-subtle"> · {r.teacherShort} · {r.roomShort}</span>
                      </span>
                      <ArrowRight className="size-3.5 shrink-0 text-foreground-subtle" aria-hidden />
                    </Link>
                  </motion.li>
                ))}
              </ul>
            )}
            {(pendingNext.data?.length ?? 0) > NEXT_MAX && (
              <div className="border-t px-4 py-2 text-xs text-foreground-subtle">
                <Link to={`/vertretung?datum=${now}`} className="hover:underline">Alle {pendingNext.data?.length} offenen Stunden</Link>
              </div>
            )}
          </section>

          <section aria-label="Entwürfe" className={offeneEntwuerfe.length > 0 ? 'rounded-lg border border-draft-border bg-draft-bg p-4 text-draft-fg' : 'rounded-lg border bg-card p-4 shadow-xs'}>
            <div className="flex items-center gap-2">
              <PencilRuler className="size-4" aria-hidden />
              <h2 className="text-lg font-semibold">
                {drafts.isError ? 'Entwürfe' : offeneEntwuerfe.length === 0 ? 'Kein Entwurf' : offeneEntwuerfe.length === 1 ? 'Entwurf — nicht aktiv' : `${offeneEntwuerfe.length} Entwürfe — keiner ist aktiv`}
              </h2>
            </div>
            {drafts.isError ? (
              // GET /drafts fehlgeschlagen — ob es Entwürfe gibt, ist unbekannt (eine leere Liste wäre „keine", das ist ein Zustand).
              <div className="mt-3"><ErrorState problem={drafts.error} retry={() => void drafts.refetch()} /></div>
            ) : offeneEntwuerfe.length > 0 ? (
              <>
                <ul className="mt-1 space-y-0.5 text-sm">
                  {offeneEntwuerfe.slice(0, 3).map((d) => (
                    <li key={d.id} className="flex items-center justify-between gap-2">
                      <Link to={`/entwurf/${d.id}`} className="min-w-0 truncate font-medium underline-offset-2 hover:underline">{d.name}</Link>
                      <span className="shrink-0 text-xs">{d.status === 'SOLVING' ? 'rechnet …' : d.status === 'FAILED' ? 'fehlgeschlagen' : `${d.hardConflicts} hart`}</span>
                    </li>
                  ))}
                </ul>
                <Button asChild size="sm" variant="outline" className="mt-3 border-draft-border bg-background/60 text-foreground">
                  <Link to="/entwurf">Zu den Entwürfen <ArrowRight /></Link>
                </Button>
              </>
            ) : (
              <>
                <p className="mt-1 text-sm text-foreground-subtle">Ein Entwurf ist die Arbeitskopie: berechnen, von Hand nachbessern, dann annehmen.</p>
                <Button asChild size="sm" className="mt-3">
                  <Link to="/entwurf"><Plus /> Entwurf anlegen</Link>
                </Button>
              </>
            )}
          </section>
        </aside>
      </div>
    </>
  )
}
