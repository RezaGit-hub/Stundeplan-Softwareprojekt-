// Hinweis „Plan gilt erst ab …" (Befund 05.09.): Ein Plan gilt ab dem Tag der Annahme. Wer am Samstag
// annimmt, landet in der Woche von heute (Mo–Fr davor) auf dem vorherigen — meist leeren — Stand und hält
// das für Datenverlust. Deshalb hier vollbreit und in Worten, im Aufbau wie das Entwurfsbanner, aber in
// Primärfarbe: der Plan ist gültig, nur diese Woche zeigt ihn nicht.
import { CalendarClock } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatDayDate } from '@/lib/dates'
import { cn } from '@/lib/utils'

export function ValidFromBanner({ validFrom, onJump, className }: { validFrom: string; onJump(): void; className?: string }) {
  return (
    <div
      className={cn('flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border border-primary/40 bg-primary-soft px-4 py-3 text-sm text-primary-soft-foreground', className)}
      role="status"
      aria-live="polite"
    >
      <CalendarClock className="size-4 shrink-0" aria-hidden />
      <span>
        <b>Dieser Plan gilt ab {formatDayDate(validFrom)}.</b> Diese Woche zeigt den vorherigen Stand.
      </span>
      <Button size="sm" variant="outline" className="ml-auto" onClick={onJump}>Zur ersten gültigen Woche</Button>
    </div>
  )
}
