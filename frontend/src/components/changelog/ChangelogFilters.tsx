// Filterleiste des Änderungsverlaufs: Zeitraum, Klasse, Lehrkraft, Stunden-ID (Deep-Link), Zurücksetzen.
import { RotateCcw } from 'lucide-react'
import { useClasses, useTeachers } from '@/api/hooks'
import { DateField } from '@/components/forms/DateField'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

export interface Filters {
  from: string | null
  to: string | null
  classId: number | null
  teacherId: number | null
  lessonId: number | null
}
const EMPTY: Filters = { from: null, to: null, classId: null, teacherId: null, lessonId: null }
const ALL = 'all'

export function ChangelogFilters({ value, onChange }: { value: Filters; onChange(next: Filters): void }) {
  const classes = useClasses()
  const teachers = useTeachers()
  const set = (patch: Partial<Filters>) => onChange({ ...value, ...patch })
  const active = Object.values(value).some((v) => v != null)

  return (
    <div className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-3 shadow-xs">
      <div>
        <Label htmlFor="cl-from" className="text-xs">Von</Label>
        <DateField id="cl-from" value={value.from} onChange={(d) => set({ from: d })} className="mt-1 w-36" />
      </div>
      <div>
        <Label htmlFor="cl-to" className="text-xs">Bis</Label>
        <DateField id="cl-to" value={value.to} onChange={(d) => set({ to: d })} className="mt-1 w-36" />
      </div>
      <div>
        <Label htmlFor="cl-class" className="text-xs">Klasse</Label>
        <Select value={value.classId == null ? ALL : String(value.classId)} onValueChange={(v) => set({ classId: v === ALL ? null : Number(v) })}>
          <SelectTrigger id="cl-class" className="mt-1 w-32"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Alle Klassen</SelectItem>
            {(classes.data ?? []).map((c) => <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
      <div>
        <Label htmlFor="cl-teacher" className="text-xs">Lehrkraft</Label>
        <Select value={value.teacherId == null ? ALL : String(value.teacherId)} onValueChange={(v) => set({ teacherId: v === ALL ? null : Number(v) })}>
          <SelectTrigger id="cl-teacher" className="mt-1 w-44"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Alle Lehrkräfte</SelectItem>
            {(teachers.data ?? []).map((t) => <SelectItem key={t.id} value={String(t.id)}>{t.name} ({t.shortName})</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
      <div>
        <Label htmlFor="cl-lesson" className="text-xs">Stunde (Nr.)</Label>
        <Input
          id="cl-lesson"
          type="number"
          min={1}
          inputMode="numeric"
          value={value.lessonId ?? ''}
          onChange={(e) => set({ lessonId: e.target.value ? Number(e.target.value) : null })}
          placeholder="ID"
          className="mt-1 w-24 tabular-nums"
        />
      </div>
      <Button variant="ghost" size="sm" onClick={() => onChange(EMPTY)} disabled={!active} className="ml-auto">
        <RotateCcw /> Zurücksetzen
      </Button>
    </div>
  )
}
