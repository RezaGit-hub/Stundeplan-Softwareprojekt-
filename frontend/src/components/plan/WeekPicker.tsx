// Wochenwahl: Pfeile, Kalender-Popover (jede Wahl springt auf den Montag), „Heute", Text „KW 39 · 21.–25.09.2026".
import { useState } from 'react'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Separator } from '@/components/ui/separator'
import { addDaysIso, addWeeks, de, fromIso, startOfIsoWeek, toIso, weekLabel } from '@/lib/dates'
import { today } from '@/lib/today'
import { cn } from '@/lib/utils'

export function WeekPicker({ monday, onChange, className }: { monday: string; onChange(monday: string): void; className?: string }) {
  const [open, setOpen] = useState(false)
  const thisWeek = startOfIsoWeek(today())
  const isCurrent = monday === thisWeek
  return (
    <div className={cn('inline-flex h-8 items-center gap-0.5 rounded-lg border bg-surface p-0.5 shadow-xs', className)} role="group" aria-label="Woche">
      <Button variant="ghost" size="icon-xs" onClick={() => onChange(addWeeks(monday, -1))} aria-label="Vorige Woche"><ChevronLeft /></Button>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button variant="ghost" size="xs" className="gap-1.5 px-2 font-medium tabular-nums" aria-label="Woche im Kalender wählen">
            <CalendarDays className="size-3.5 text-foreground-subtle" aria-hidden />
            {weekLabel(monday)}
          </Button>
        </PopoverTrigger>
        <PopoverContent align="center" className="w-auto p-1">
          <Calendar
            mode="single"
            locale={de}
            weekStartsOn={1}
            showWeekNumber
            selected={fromIso(monday)}
            defaultMonth={fromIso(monday)}
            modifiers={{ week: { from: fromIso(monday), to: fromIso(addDaysIso(monday, 6)) } }}
            modifiersClassNames={{ week: 'bg-primary-soft/60' }}
            onSelect={(d) => {
              if (!d) return
              onChange(startOfIsoWeek(toIso(d)))
              setOpen(false)
            }}
          />
        </PopoverContent>
      </Popover>
      <Button variant="ghost" size="icon-xs" onClick={() => onChange(addWeeks(monday, 1))} aria-label="Nächste Woche"><ChevronRight /></Button>
      <Separator orientation="vertical" className="mx-0.5 !h-4" />
      <Button variant="ghost" size="xs" disabled={isCurrent} onClick={() => onChange(thisWeek)}>Heute</Button>
    </div>
  )
}
