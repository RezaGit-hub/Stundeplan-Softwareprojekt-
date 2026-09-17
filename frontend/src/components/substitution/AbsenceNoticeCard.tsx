// "Auf einen Blick"-Hinweis zwischen Formular und Ereignisliste (Issue #96, Anthonys Wunsch):
// wer ist im angezeigten Zeitraum abwesend/gesperrt und muss vertreten werden. Heller Hintergrund,
// mehrere Einträge klar getrennt statt als Fließtext.
import { AlertCircle } from 'lucide-react'
import { useEvents, useRooms, useTeachers } from '@/api/hooks'
import type { EventDto, EventQuery } from '@/api/types'
import { formatDay } from '@/lib/dates'

function rangeLabel(ev: EventDto): string {
  return ev.dateFrom === ev.dateTo ? formatDay(ev.dateFrom) : `${formatDay(ev.dateFrom)} – ${formatDay(ev.dateTo)}`
}

export function AbsenceNoticeCard({ from, to }: EventQuery) {
  const events = useEvents({ from, to })
  const teachers = useTeachers()
  const rooms = useRooms()

  const relevant = (events.data ?? []).filter((e) => e.kind === 'TEACHER_ABSENCE' || e.kind === 'ROOM_BLOCKED')
  if (relevant.length === 0) return null

  const describe = (ev: EventDto) => {
    if (ev.kind === 'TEACHER_ABSENCE') {
      const t = teachers.data?.find((x) => x.id === ev.teacherId)
      return { who: t?.name ?? 'Lehrkraft', what: 'krankgemeldet' }
    }
    const r = rooms.data?.find((x) => x.id === ev.roomId)
    return { who: r?.name ?? 'Raum', what: 'gesperrt' }
  }

  return (
    <div className="flex flex-col gap-2 rounded-lg border border-warning/40 bg-warning-surface p-4 text-sm" role="status">
      {relevant.map((ev) => {
        const { who, what } = describe(ev)
        return (
          <div key={ev.id} className="flex items-start gap-2 border-b border-warning/20 pb-2 last:border-b-0 last:pb-0">
            <AlertCircle className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden />
            <span>
              <b>{who}</b> ist {rangeLabel(ev)} {what} — für diesen Zeitraum muss vertreten werden
              {ev.affected.length > 0 && ` (${ev.affected.length} ${ev.affected.length === 1 ? 'Stunde' : 'Stunden'})`}.
            </span>
          </div>
        )
      })}
    </div>
  )
}