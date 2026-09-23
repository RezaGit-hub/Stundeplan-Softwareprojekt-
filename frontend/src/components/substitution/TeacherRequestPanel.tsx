// Zeigt bereits gesendete Vertretungsanfragen und ihren Antwortstatus je Lehrkraft. Die Antwort selbst
// kommt aus der App (PATCH /me/{id}, Rolle TEACHER); hier wird nur gelesen und zurückgezogen.
import { Hourglass, Mail, MessageCircleQuestion, X } from 'lucide-react'
import type {SubstitutionRequestStatus, TimeslotDto } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { formatDay } from '@/lib/dates'
import { cn } from '@/lib/utils'
import { positionLabel } from './position-label'
import type { TeacherRequestGroup } from './teacher-requests'

const STATUS_LABEL: Record<SubstitutionRequestStatus, string> = {
  UNKNOWN: 'wartet auf Antwort',
  ACCEPTED: 'zugesagt',
  DECLINED: 'abgesagt',
}
const STATUS_TONE: Record<SubstitutionRequestStatus, string> = {
  UNKNOWN: 'bg-warning-soft text-warning',
  ACCEPTED: 'bg-success-soft text-success',
  DECLINED: 'bg-destructive/10 text-destructive',
}

function lessonLabel(row: { date: string; position: number; classShort: string; subjectShort: string }, slots: TimeslotDto[] | undefined) {
  return `${formatDay(row.date)}, ${positionLabel(slots, row.date, row.position)} Std. · ${row.classShort} ${row.subjectShort}`
}

export function TeacherRequestPanel({ groups, teacherNames, timeslots, onWithdraw }: {
  groups: TeacherRequestGroup[]
  /** teacherId → Anzeigename; die einzelne Anfrage-Zeile kennt nur teacherId, keinen Namen */
  teacherNames: Record<number, string>
  timeslots: TimeslotDto[] | undefined
  onWithdraw(requestId: number): void
}) {
  if (groups.length === 0) {
    return (
      <p className="rounded-md border border-dashed px-3 py-2 text-xs text-foreground-subtle">
        Noch niemand angefragt. Bei einer Lehrkraft oben auf <MessageCircleQuestion className="inline size-3.5 align-text-bottom" aria-hidden /> klicken.
      </p>
    )
  }

  return (
    <section aria-label="Angefragte Lehrkräfte">
      <h3 className="mb-1.5 flex items-center gap-1.5 text-2xs font-medium tracking-wide text-foreground-subtle uppercase">
        <Mail className="size-3.5" aria-hidden /> Angefragt · {groups.length}
      </h3>
      <div className="space-y-2">
        {groups.map((group) => {
          const total = group.rows.length
          const accepted = group.rows.filter((r) => r.status === 'ACCEPTED')
          const declined = group.rows.every((r) => r.status === 'DECLINED')
          // Übergreifender Status der Gruppe: erst wenn alle geantwortet haben, zeigt der Chip
          // "zugesagt"/"abgesagt" statt "wartet" — solange auch nur eine Stunde offen ist, wartet man noch.
          const stillWaiting = group.rows.some((r) => r.status === 'UNKNOWN')
          const overall: SubstitutionRequestStatus = stillWaiting ? 'UNKNOWN' : declined ? 'DECLINED' : 'ACCEPTED'
          const name = teacherNames[group.teacherId] ?? `Lehrkraft ${group.teacherId}`

          return (
            <div key={group.teacherId} className="rounded-md border px-2.5 py-2">
              <div className="flex items-center gap-1.5">
                <span className="min-w-0 flex-1 truncate font-medium">{name}</span>
                <Badge className={cn('shrink-0 font-normal', STATUS_TONE[overall])}>
                  {overall === 'ACCEPTED' && accepted.length < total ? `${accepted.length} von ${total} zugesagt` : STATUS_LABEL[overall]}
                </Badge>
              </div>

              <p className="mt-1 text-xs text-foreground-subtle">{total} {total === 1 ? 'Stunde' : 'Stunden'} angeboten</p>

              {stillWaiting && (
                <p className="mt-1.5 flex items-center gap-1 text-2xs text-foreground-subtle">
                  <Hourglass className="size-3" aria-hidden /> Antwort steht noch aus — die Lehrkraft antwortet über die App.
                </p>
              )}

              {accepted.length > 0 && (
                <ul className="mt-1.5 space-y-0.5 border-l-2 border-success/40 pl-2">
                  {accepted
                    .slice()
                    .sort((a, b) => a.date.localeCompare(b.date) || a.position - b.position)
                    .map((r) => (
                      <li key={r.id} className="text-2xs text-foreground-subtle">{lessonLabel(r, timeslots)}</li>
                    ))}
                </ul>
              )}

              <div className="mt-2 flex flex-wrap gap-1">
                {group.rows
                  .filter((r) => r.status === 'UNKNOWN')
                  .map((r) => (
                    <button
                      key={r.id}
                      type="button"
                      onClick={() => onWithdraw(r.id)}
                      title={`${lessonLabel(r, timeslots)} zurückziehen`}
                      className="flex items-center gap-1 rounded-sm border px-1.5 py-0.5 text-2xs text-foreground-subtle hover:border-destructive/50 hover:text-destructive"
                    >
                      {formatDay(r.date)}, {positionLabel(timeslots, r.date, r.position)} Std. <X className="size-3" aria-hidden />
                    </button>
                  ))}
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}