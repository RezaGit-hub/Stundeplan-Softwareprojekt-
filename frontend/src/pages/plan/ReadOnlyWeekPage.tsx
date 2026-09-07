// Die Nur-Lese-Woche: der Rumpf hinter /mein-plan (Lehrkraft, E-38) und /klassenplan (Klassenkonto, E-50).
//
// Beide Seiten zeigen dasselbe: Kopfzeile mit Wochenwahl, Hinweis auf heutige Änderungen, das Wochenraster
// (oder Skeleton bzw. Fehler), Legende und Detail-Popover — nur Titel, Sicht und Abfrage unterscheiden sich.
// Bis zum 05.09. stand das zweimal fast wortgleich da (Befund FRONTEND-8): eine Änderung an der Druckzeile,
// der Legende oder dem Ladeverhalten musste an zwei Stellen nachgezogen werden, und der Tag-Umschalter aus
// PFLICHTENHEFT-8 zeigte, wie das ausläuft — er landete nur in einer der beiden.
//
// Die Abfrage kommt als Ergebnis herein, nicht als Hook: so ruft jede Seite ihren eigenen Hook auf oberster
// Ebene auf (Regeln der Hooks), und diese Komponente muss nicht wissen, welcher es ist.
import { useMemo, useState } from 'react'
import { useTimegrid } from '@/api/hooks'
import type { EntryDto, View, WeekDto } from '@/api/types'
import { useAuth } from '@/app/auth'
import { PageHeader } from '@/components/layout/PageHeader'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { WeekGrid } from '@/components/week-grid/WeekGrid'
import { WeekGridSkeleton } from '@/components/week-grid/WeekGridSkeleton'
import { isLesson } from '@/lib/grid-entry'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ChangedTodayBar } from '@/components/plan/ChangedTodayBar'
import { DayList } from '@/components/plan/DayList'
import { EntryDetailPopover } from '@/components/plan/EntryDetailPopover'
import { Legend } from '@/components/plan/Legend'
import { WeekPicker } from '@/components/plan/WeekPicker'
import { todayDayIn, type UrlSetter } from '@/components/plan/plan-params'
import { addDaysIso, between, formatDayDate, weekLabel } from '@/lib/dates'
import { today } from '@/lib/today'
import { changedOn, weekMeta } from '@/lib/week-meta'
import { cn } from '@/lib/utils'

/** Die fünf Schultage der Woche ab montag — die Tagesansicht wählt daraus. */
const weekDates = (monday: string) => [0, 1, 2, 3, 4].map((i) => addDaysIso(monday, i))

/** Was die Abfrage einer Wochensicht liefert — nur die Felder, die diese Seite liest. */
export interface WeekQuery {
  data: WeekDto | undefined
  isPending: boolean
  isError: boolean
  error: unknown
  refetch(): unknown
}

export function ReadOnlyWeekPage({ title, view, monday, week, params, set, dayView = false }: {
  title: string
  view: View
  monday: string
  week: WeekQuery
  params: URLSearchParams
  set: UrlSetter
  /**
   * Umschalter Tag | Woche. Nur /mein-plan hat ihn: das Musskriterium verlangt „dieselbe Tages- und
   * Wochenansicht des eigenen Plans wie in der App" für die Lehrkraft (zielbestimmungen.tex, Befund
   * PFLICHTENHEFT-8). Für den Klassenplan steht das nirgends — er bekommt ihn nicht nebenbei.
   */
  dayView?: boolean
}) {
  const { user } = useAuth()
  const timegrid = useTimegrid()
  const [detail, setDetail] = useState<{ entry: EntryDto; anchor: HTMLElement } | null>(null)

  // Tag oder Woche; der gewählte Tag liegt immer in der angezeigten Woche (sonst heute bzw. Montag).
  const dayMode = dayView && params.get('ansicht') === 'tag'
  const dates = weekDates(monday)
  const urlDate = params.get('tag')
  const date = urlDate && dates.includes(urlDate)
    ? urlDate
    : (between(today(), dates[0], dates[4]) ? today() : dates[0])

  const days = useMemo(() => week.data?.days ?? [], [week.data])
  const { entries, dateByDay, closedDays } = useMemo(() => weekMeta(days), [days])
  const changedToday = changedOn(entries, today())

  if (timegrid.isPending) return <PageSkeleton />
  if (timegrid.isError) return <ErrorState problem={timegrid.error} retry={() => void timegrid.refetch()} />

  const openDetail = (entry: Parameters<typeof isLesson>[0], anchor: HTMLElement) => {
    if (!isLesson(entry)) setDetail({ entry, anchor })
  }

  return (
    <>
      <PageHeader
        title={title}
        description={user?.displayName ? `${user.displayName} · nur lesend` : 'nur lesend'}
        actions={
          <div className="print-hide flex items-center gap-2">
            {dayView && (
              <div className="inline-flex rounded-md border p-0.5" role="group" aria-label="Ansicht">
                <Button size="xs" variant={dayMode ? 'ghost' : 'secondary'} aria-pressed={!dayMode} onClick={() => set({ ansicht: null, tag: null })}>Woche</Button>
                <Button size="xs" variant={dayMode ? 'secondary' : 'ghost'} aria-pressed={dayMode} onClick={() => set({ ansicht: 'tag', tag: date })}>Tag</Button>
              </div>
            )}
            <WeekPicker monday={monday} onChange={(m) => set({ woche: m })} />
          </div>
        }
      />
      {/* Nur im Druck: welche Woche das Blatt zeigt (die Wochenwahl selbst wird nicht gedruckt) */}
      <p className="mb-3 hidden text-sm print:block">{weekLabel(monday)}</p>
      <div className="space-y-3">
        <ChangedTodayBar count={changedToday} />
        {dayMode && (
          <div className="print-hide flex flex-wrap gap-1.5" role="group" aria-label="Tag wählen">
            {dates.map((d) => (
              <Button
                key={d}
                size="xs"
                variant={d === date ? 'secondary' : 'ghost'}
                aria-pressed={d === date}
                className={cn('tabular-nums', d === today() && 'font-bold')}
                onClick={() => set({ tag: d })}
              >
                {formatDayDate(d)}
              </Button>
            ))}
          </div>
        )}
        {week.isPending ? (
          dayMode ? (
            <div className="space-y-3" aria-busy="true" aria-label="Lädt den Tag">
              {[0, 1, 2].map((i) => <Skeleton key={i} className="h-16 w-full bg-surface-sunken" />)}
            </div>
          ) : (
            <WeekGridSkeleton slots={timegrid.data.slots} />
          )
        ) : week.isError ? (
          <ErrorState problem={week.error} retry={() => void week.refetch()} />
        ) : dayMode ? (
          <DayList day={days.find((d) => d.date === date)} slots={timegrid.data.slots} onEntryClick={openDetail} />
        ) : (
          <WeekGrid
            slots={timegrid.data.slots}
            entries={entries}
            view={view}
            todayDay={todayDayIn(monday)}
            dateByDay={dateByDay}
            closedDays={closedDays}
            onEntryClick={openDetail}
          />
        )}
        <Legend defaultOpen className="print-hide" />
      </div>
      {/*
        lookup={false}: das Popover lädt keine Grunddaten nach. Für die Lehrkraft ist das E-45 (mehr darf
        die Rolle nicht), für das Klassenkonto zusätzlich Datenschutz — Klarnamen von Lehrkräften gibt es
        in dieser Sicht nicht, und der Server lehnt die Grunddaten ohnehin mit 403 ab.
      */}
      <EntryDetailPopover entry={detail?.entry ?? null} anchor={detail?.anchor ?? null} onClose={() => setDetail(null)} slots={timegrid.data.slots} lookup={false} />
    </>
  )
}
