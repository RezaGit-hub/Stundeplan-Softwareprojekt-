// Stundenraster bearbeiten: Tage Mo–Sa als Schalter, je Tag Zeitfenster (Nr. · von · bis · Pause), Vorlage.
// `value` ist immer nach Tag und Position sortiert und je Tag durchnummeriert — der Index im Array ist der
// Index im PUT-Body (Feldfehler `slots[i].startTime` finden so ihre Zeile).
import { Fragment } from 'react'
import { Coffee, Plus, Wand2, X } from 'lucide-react'
import type { TimeslotDto, Weekday } from '@/api/types'
import { WEEKDAYS } from '@/api/types'
import { newSlot, normalize, templateSlots } from './helpers'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { FieldError } from '@/components/forms/FieldError'
import { DAY_LABEL, DAY_SHORT, lessonNumber } from '@/lib/timegrid'
import { cn } from '@/lib/utils'

function addMinutes(hhmm: string, n: number): string {
  const [h, m] = hhmm.split(':').map(Number)
  if (!Number.isFinite(h) || !Number.isFinite(m)) return hhmm
  const total = Math.max(0, Math.min(23 * 60 + 59, h * 60 + m + n))
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

export function TimegridEditor({ value, onChange, errors = {} }: {
  value: TimeslotDto[]
  onChange(next: TimeslotDto[]): void
  /** Feldfehler aus PUT /timegrid: `slots[i].startTime` … */
  errors?: Record<string, string>
}) {
  const activeDays = new Set(value.map((s) => s.day))
  const set = (next: TimeslotDto[]) => onChange(normalize(next))

  const toggleDay = (day: Weekday, on: boolean) => {
    if (!on) return set(value.filter((s) => s.day !== day))
    // Neuer Tag übernimmt die Zeiten des ersten vorhandenen Tages, sonst die Vorlage
    const source = value.filter((s) => s.day === (value[0]?.day ?? day))
    const rows = source.length > 0 ? source : templateSlots([day])
    set([...value, ...rows.map((s) => newSlot(day, s.position, s.startTime, s.endTime, s.isBreak))])
  }
  const patch = (id: number, p: Partial<TimeslotDto>) => set(value.map((s) => (s.id === id ? { ...s, ...p } : s)))
  const remove = (id: number) => set(value.filter((s) => s.id !== id))
  const addRow = (day: Weekday) => {
    const ofDay = value.filter((s) => s.day === day)
    const last = ofDay[ofDay.length - 1]
    const startTime = last ? addMinutes(last.endTime, 5) : '08:00'
    set([...value, newSlot(day, ofDay.length + 1, startTime, addMinutes(startTime, 45))])
  }

  const err = (i: number, field: string) => errors[`slots[${i}].${field}`]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 rounded-lg border bg-card px-4 py-3">
        <span className="text-xs font-medium text-foreground-muted">Unterrichtstage</span>
        <div className="flex flex-wrap gap-4">
          {WEEKDAYS.map((day) => (
            <label key={day} className="inline-flex items-center gap-2 text-sm">
              <Switch size="sm" checked={activeDays.has(day)} onCheckedChange={(on) => toggleDay(day, on)} aria-label={DAY_LABEL[day]} />
              {DAY_SHORT[day]}
            </label>
          ))}
        </div>
        <Button variant="outline" size="sm" className="ml-auto" onClick={() => onChange(templateSlots())}>
          <Wand2 /> Vorlage: Mo–Fr, 6 Stunden, große Pause nach der 2.
        </Button>
      </div>

      {value.length === 0 && (
        <p className="rounded-lg border border-dashed px-4 py-8 text-center text-sm text-foreground-subtle">
          Kein Unterrichtstag gewählt. Tag einschalten oder die Vorlage laden.
        </p>
      )}

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {WEEKDAYS.filter((d) => activeDays.has(d)).map((day) => {
          const ofDay = value.filter((s) => s.day === day)
          const lessons = ofDay.filter((s) => !s.isBreak).length
          return (
            <section key={day} className="rounded-lg border bg-card shadow-xs">
              <header className="flex items-center justify-between border-b px-4 py-2.5">
                <h3 className="text-sm font-semibold">{DAY_LABEL[day]}</h3>
                <span className="text-xs text-foreground-subtle tabular-nums">{lessons} Stunden · {ofDay.length - lessons} {ofDay.length - lessons === 1 ? 'Pause' : 'Pausen'}</span>
              </header>
              <div className="grid grid-cols-[2.5rem_1fr_1fr_3rem_2rem] items-center gap-x-2 gap-y-1.5 px-3 py-2 text-xs">
                <span className="text-2xs font-medium text-foreground-subtle">Nr.</span>
                <span className="text-2xs font-medium text-foreground-subtle">von</span>
                <span className="text-2xs font-medium text-foreground-subtle">bis</span>
                <span className="text-center text-2xs font-medium text-foreground-subtle">Pause</span>
                <span />
                {ofDay.map((s) => {
                  const i = value.indexOf(s)
                  const n = lessonNumber(s, value)
                  return (
                    <Fragment key={s.id}>
                      <span className={cn('flex h-8 items-center justify-center rounded-sm text-sm font-semibold tabular-nums', s.isBreak ? 'bg-break-row text-break-foreground' : 'bg-surface-sunken text-foreground-muted')}>
                        {s.isBreak ? <Coffee className="size-3.5" aria-label="Pause" /> : `${n}.`}
                      </span>
                      <div>
                        <Input type="time" value={s.startTime} onChange={(e) => patch(s.id, { startTime: e.target.value })} aria-label={`${DAY_SHORT[day]} Beginn ${s.position}`} aria-invalid={!!err(i, 'startTime')} className="h-8 tabular-nums" />
                        <FieldError message={err(i, 'startTime')} />
                      </div>
                      <div>
                        <Input type="time" value={s.endTime} onChange={(e) => patch(s.id, { endTime: e.target.value })} aria-label={`${DAY_SHORT[day]} Ende ${s.position}`} aria-invalid={!!err(i, 'endTime')} className="h-8 tabular-nums" />
                        <FieldError message={err(i, 'endTime')} />
                      </div>
                      <div className="flex justify-center">
                        <Switch size="sm" checked={s.isBreak} onCheckedChange={(v) => patch(s.id, { isBreak: v })} aria-label="Pause" />
                      </div>
                      <Button variant="ghost" size="icon-sm" aria-label="Zeitfenster entfernen" onClick={() => remove(s.id)}><X /></Button>
                      {err(i, 'position') && <p className="col-span-5 text-xs text-destructive">{err(i, 'position')}</p>}
                    </Fragment>
                  )
                })}
              </div>
              <footer className="border-t px-3 py-2">
                <Button variant="ghost" size="sm" onClick={() => addRow(day)}><Plus /> Zeile hinzufügen</Button>
              </footer>
            </section>
          )
        })}
      </div>
    </div>
  )
}
