// Datumsfeld: Knopf mit "21.09.2026", Popover mit shadcn Calendar (deutsch, Montag zuerst). Wert immer yyyy-MM-dd.
import { useState } from 'react'
import { CalendarIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { de, formatDate, fromIso, toIso } from '@/lib/dates'
import { cn } from '@/lib/utils'

export function DateField({ id, value, onChange, placeholder = 'Datum wählen', className, size = 'default' }: {
  id?: string
  value: string | null
  onChange(dateIso: string): void
  placeholder?: string
  className?: string
  size?: 'default' | 'sm'
}) {
  const [open, setOpen] = useState(false)
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button id={id} type="button" variant="outline" size={size} className={cn('justify-start font-normal tabular-nums', !value && 'text-foreground-subtle', className)}>
          <CalendarIcon className="text-foreground-subtle" aria-hidden />
          {value ? formatDate(value) : placeholder}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          locale={de}
          selected={value ? fromIso(value) : undefined}
          defaultMonth={value ? fromIso(value) : undefined}
          onSelect={(d) => {
            if (!d) return
            onChange(toIso(d))
            setOpen(false)
          }}
        />
      </PopoverContent>
    </Popover>
  )
}
