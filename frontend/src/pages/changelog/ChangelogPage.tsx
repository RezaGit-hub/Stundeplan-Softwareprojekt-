// /verlauf?von=&bis=&klasse=&lehrkraft=&stunde=&seite= — Änderungsverlauf als Zeitachse, nur lesend (W-7, T-W7.1).
import { useSearchParams } from 'react-router-dom'
import { motion } from 'motion/react'
import { ChevronLeft, ChevronRight, History } from 'lucide-react'
import { useChangelog, useTimegrid } from '@/api/hooks'
import { ChangelogFilters, type Filters } from '@/components/changelog/ChangelogFilters'
import { EventChip } from '@/components/changelog/EventChip'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { Button } from '@/components/ui/button'
import { listItem } from '@/lib/motion'

const PAGE_SIZE = 50
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

function dateParam(v: string | null): string | null {
  return v && ISO_DATE.test(v) ? v : null
}
function intParam(v: string | null): number | null {
  const n = v ? Number(v) : NaN
  return Number.isInteger(n) && n >= 0 ? n : null
}

/** URL-Parameter (deutsch) ↔ Filter (englisch). Leere Werte verschwinden aus der URL. */
const PARAM: Record<keyof Filters | 'page', string> = { from: 'von', to: 'bis', classId: 'klasse', teacherId: 'lehrkraft', lessonId: 'stunde', page: 'seite' }

export function ChangelogPage() {
  const [params, setParams] = useSearchParams()
  const filters: Filters = {
    from: dateParam(params.get(PARAM.from)),
    to: dateParam(params.get(PARAM.to)),
    classId: intParam(params.get(PARAM.classId)),
    teacherId: intParam(params.get(PARAM.teacherId)),
    lessonId: intParam(params.get(PARAM.lessonId)),
  }
  const page = intParam(params.get(PARAM.page)) ?? 0

  const write = (next: Filters, nextPage: number) =>
    setParams((prev) => {
      const values: Record<string, string | number | null> = { ...next, page: nextPage > 0 ? nextPage : null }
      for (const key of Object.keys(PARAM) as (keyof typeof PARAM)[]) {
        const v = values[key]
        if (v == null) prev.delete(PARAM[key])
        else prev.set(PARAM[key], String(v))
      }
      return prev
    }, { replace: true })

  const log = useChangelog({
    from: filters.from ?? undefined, to: filters.to ?? undefined, classId: filters.classId ?? undefined,
    teacherId: filters.teacherId ?? undefined, lessonId: filters.lessonId ?? undefined, page, size: PAGE_SIZE,
  })
  const timegrid = useTimegrid()

  const total = log.data?.total ?? 0
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const first = total === 0 ? 0 : page * PAGE_SIZE + 1
  const lastIdx = Math.min(total, (page + 1) * PAGE_SIZE)

  return (
    <>
      <PageHeader title="Änderungsverlauf" description="Jede Vertretung, jeder Entfall, jede Planannahme — nur lesend." />
      <ChangelogFilters value={filters} onChange={(f) => write(f, 0)} />

      <div className="mt-5">
        {log.isPending && <PageSkeleton />}
        {log.isError && <ErrorState problem={log.error} retry={() => void log.refetch()} />}
        {log.data && log.data.items.length === 0 && (
          <EmptyState icon={History} title="Keine Einträge im Änderungsverlauf" description="Sobald eine Vertretung eingetragen oder ein Plan angenommen wird, steht es hier." />
        )}
        {log.data && log.data.items.length > 0 && (
          <div className="rounded-lg border bg-card px-5 py-4 shadow-xs">
            <ol className="ml-1.5 space-y-5 border-l border-border-strong">
              {log.data.items.map((item, i) => (
                <motion.div key={item.id} {...listItem(i)} className="contents">
                  <EventChip item={item} slots={timegrid.data?.slots} />
                </motion.div>
              ))}
            </ol>
          </div>
        )}
      </div>

      {log.data && total > 0 && (
        <div className="mt-4 flex items-center justify-between text-xs text-foreground-subtle tabular-nums">
          <span>Einträge {first}–{lastIdx} von {total}</span>
          <div className="flex items-center gap-1">
            <Button variant="outline" size="sm" disabled={page === 0} onClick={() => write(filters, page - 1)} aria-label="Vorige Seite"><ChevronLeft /> Zurück</Button>
            <span className="px-2">Seite {page + 1} von {pages}</span>
            <Button variant="outline" size="sm" disabled={page + 1 >= pages} onClick={() => write(filters, page + 1)} aria-label="Nächste Seite">Weiter <ChevronRight /></Button>
          </div>
        </div>
      )}
    </>
  )
}
