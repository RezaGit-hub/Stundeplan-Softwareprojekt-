// Dialog beim Klick auf das Fragezeichen einer Lehrkraft: der Admin legt hier fest, welche der offenen
// Stunden angeboten werden — alle, oder eine Auswahl (z. B. wenn eine andere Lehrkraft schon einen Teil
// übernommen hat). Erst mit "Anfrage senden" entsteht der Eintrag in der Liste; vorher passiert nichts.
import { useState } from 'react'
import { Mail } from 'lucide-react'
import type { PendingRow, TimeslotDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { formatDay } from '@/lib/dates'
import { positionLabel } from './position-label'

function lessonLabel(row: PendingRow, slots: TimeslotDto[] | undefined) {
  return `${formatDay(row.date)}, ${positionLabel(slots, row.date, row.position)} Std. · ${row.classShort} ${row.subjectShort}`
}

export function RequestTeacherDialog({ open, onOpenChange, teacherName, rows, timeslots, onConfirm }: {
  open: boolean
  onOpenChange(open: boolean): void
  teacherName: string
  rows: PendingRow[]
  timeslots: TimeslotDto[] | undefined
  onConfirm(lessonIds: number[]): void
}) {
  const [mode, setMode] = useState<'ALL' | 'SOME'>('ALL')
  const [checked, setChecked] = useState<Set<number>>(new Set(rows.map((r) => r.lessonId)))
  const toggle = (id: number) => setChecked((s) => { const next = new Set(s); if (next.has(id)) next.delete(id); else next.add(id); return next })

  const send = () => {
    onConfirm(mode === 'ALL' ? rows.map((r) => r.lessonId) : [...checked])
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{teacherName} anfragen</DialogTitle>
          <DialogDescription>Welche der {rows.length} offenen Stunden soll {teacherName} übernehmen?</DialogDescription>
        </DialogHeader>

        <RadioGroup value={mode} onValueChange={(v) => setMode(v as 'ALL' | 'SOME')} className="space-y-2">
          <label className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm hover:bg-accent">
            <RadioGroupItem value="ALL" />
            Alle {rows.length} Stunden
          </label>
          <label className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm hover:bg-accent">
            <RadioGroupItem value="SOME" />
            Bestimmte Stunden auswählen
          </label>
        </RadioGroup>

        {mode === 'SOME' && (
          <div className="max-h-64 space-y-1 overflow-y-auto rounded-md border p-2">
            {rows.map((row) => (
              <label key={row.lessonId} className="flex items-center gap-2 rounded-md px-1 py-1 text-sm hover:bg-accent">
                <Checkbox checked={checked.has(row.lessonId)} onCheckedChange={() => toggle(row.lessonId)} />
                {lessonLabel(row, timeslots)}
              </label>
            ))}
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Abbrechen</Button>
          <Button disabled={mode === 'SOME' && checked.size === 0} onClick={send}><Mail /> Anfrage senden</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}