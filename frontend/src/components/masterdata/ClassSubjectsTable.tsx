// Fächerzuordnung einer Klasse: Fach · Wochenstunden · Lehrkraft, Warnung-Chip je Zeile, ein „Speichern" für die ganze Liste.
// Warnungen (SUBJECT_NOT_TAUGHT, QUOTA_EXCEEDED) sind Daten vom Server — Speichern bleibt möglich (T-W1.4, T-W1.5).
import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Plus, RotateCcw, Save, X } from 'lucide-react'
import { useReplaceClassSubjects } from '@/api/hooks'
import type { ClassDto, ClassSubjectInput, SubjectDto, TeacherDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { FieldError } from '@/components/forms/FieldError'
import { useFieldErrors } from '@/lib/form-errors'
import { cn } from '@/lib/utils'
import { ColorDot, WarningChip } from './shared'

interface Row { key: number; subjectId: number | null; weeklyHours: number | null; teacherId: number | null }

let nextKey = 1
const fromDto = (c: ClassDto): Row[] => c.subjects.map((r) => ({ key: nextKey++, subjectId: r.subjectId, weeklyHours: r.weeklyHours, teacherId: r.teacherId }))
const toInput = (rows: Row[]): ClassSubjectInput[] => rows.map((r) => ({ subjectId: r.subjectId ?? 0, weeklyHours: r.weeklyHours, teacherId: r.teacherId }))
const signature = (rows: ClassSubjectInput[]) => JSON.stringify(rows.map((r) => [r.subjectId, r.weeklyHours, r.teacherId]))

export function ClassSubjectsTable({ schoolClass, subjects, teachers }: { schoolClass: ClassDto; subjects: SubjectDto[]; teachers: TeacherDto[] }) {
  const replace = useReplaceClassSubjects()
  const fields = useFieldErrors()
  // Die Seite gibt `key={classSignature(c)}` → nach jedem Speichern startet die Tabelle vom Serverstand
  const [rows, setRows] = useState<Row[]>(() => fromDto(schoolClass))
  const serverSig = signature(schoolClass.subjects.map((r) => ({ subjectId: r.subjectId, weeklyHours: r.weeklyHours, teacherId: r.teacherId })))

  const dirty = signature(toInput(rows)) !== serverSig
  const subjectById = useMemo(() => new Map(subjects.map((s) => [s.id, s])), [subjects])
  const teacherById = useMemo(() => new Map(teachers.map((t) => [t.id, t])), [teachers])
  const usedSubjectIds = new Set(rows.map((r) => r.subjectId))

  const patch = (key: number, p: Partial<Row>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...p } : r)))
  const removeRow = (key: number) => setRows((rs) => rs.filter((r) => r.key !== key))
  const addRow = () => {
    const free = subjects.find((s) => !usedSubjectIds.has(s.id))
    setRows((rs) => [...rs, { key: nextKey++, subjectId: free?.id ?? null, weeklyHours: null, teacherId: null }])
  }

  const save = async () => {
    fields.clear()
    try {
      const result = await replace.mutateAsync({ id: schoolClass.id, rows: toInput(rows) })
      toast.success(result.warnings.length > 0 ? `Fächer gespeichert — ${result.warnings.length} Hinweis${result.warnings.length > 1 ? 'e' : ''}` : 'Fächer gespeichert')
    } catch (err) {
      if (!fields.handle(err)) return
      // 400: Zeilenfehler stehen jetzt unter den Feldern
    }
  }

  /** Warnungen zur Zeile: gleiche Lehrkraft; bei „Fach nicht unterrichtet" zusätzlich das Fach im Text */
  const warningsFor = (row: Row) => {
    const subjectName = row.subjectId != null ? subjectById.get(row.subjectId)?.name : undefined
    return schoolClass.warnings.filter((w) => w.teacherId === row.teacherId && (w.code !== 'SUBJECT_NOT_TAUGHT' || !subjectName || w.message.includes(subjectName)))
  }

  /** Lehrkräfte für ein Fach: aktive, Fachlehrkräfte zuerst; die aktuell eingetragene bleibt sichtbar, auch wenn sie deaktiviert ist */
  const teacherGroups = (row: Row) => {
    const active = teachers.filter((t) => t.active || t.id === row.teacherId)
    const teaches = active.filter((t) => row.subjectId != null && t.subjectIds.includes(row.subjectId))
    const others = active.filter((t) => !teaches.includes(t))
    return { teaches, others }
  }

  const err = (i: number, field: string) => fields.errors[`subjects[${i}].${field}`]

  return (
    <div>
      <div className="overflow-x-auto rounded-lg border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-56 text-xs">Fach</TableHead>
              <TableHead className="w-32 text-xs">Wochenstunden</TableHead>
              <TableHead className="text-xs">Lehrkraft</TableHead>
              <TableHead className="w-28 text-xs">Deputat</TableHead>
              <TableHead className="text-xs">Hinweis</TableHead>
              <TableHead className="w-10"><span className="sr-only">Entfernen</span></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-sm text-foreground-subtle">
                  Noch kein Fach zugeordnet — „Fach hinzufügen" legt die erste Zeile an.
                </TableCell>
              </TableRow>
            )}
            {rows.map((row, i) => {
              const teacher = row.teacherId != null ? teacherById.get(row.teacherId) : undefined
              const groups = teacherGroups(row)
              const warnings = warningsFor(row)
              return (
                <TableRow key={row.key} className={cn('align-top', warnings.length > 0 && 'bg-warning-soft/40')}>
                  <TableCell>
                    <Select value={row.subjectId != null ? String(row.subjectId) : ''} onValueChange={(v) => patch(row.key, { subjectId: Number(v) })}>
                      <SelectTrigger className="w-full" aria-label="Fach" aria-invalid={!!err(i, 'subjectId')}>
                        <SelectValue placeholder="Fach wählen" />
                      </SelectTrigger>
                      <SelectContent>
                        {subjects.map((s) => (
                          <SelectItem key={s.id} value={String(s.id)} disabled={usedSubjectIds.has(s.id) && s.id !== row.subjectId}>
                            <ColorDot color={s.color} /> {s.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FieldError message={err(i, 'subjectId')} />
                  </TableCell>
                  <TableCell>
                    <Input
                      type="number"
                      min={1}
                      value={row.weeklyHours ?? ''}
                      placeholder="—"
                      onChange={(e) => patch(row.key, { weeklyHours: e.target.value === '' ? null : Number(e.target.value) })}
                      aria-label="Wochenstunden"
                      aria-invalid={!!err(i, 'weeklyHours')}
                      className="w-24 tabular-nums"
                    />
                    <FieldError message={err(i, 'weeklyHours')} />
                  </TableCell>
                  <TableCell>
                    <Select value={row.teacherId != null ? String(row.teacherId) : ''} onValueChange={(v) => patch(row.key, { teacherId: Number(v) })}>
                      <SelectTrigger className="w-full" aria-label="Lehrkraft" aria-invalid={!!err(i, 'teacherId')}>
                        <SelectValue placeholder="Lehrkraft wählen" />
                      </SelectTrigger>
                      <SelectContent>
                        {groups.teaches.length > 0 && (
                          <SelectGroup>
                            <SelectLabel>Unterrichtet das Fach</SelectLabel>
                            {groups.teaches.map((t) => <TeacherItem key={t.id} t={t} />)}
                          </SelectGroup>
                        )}
                        {groups.others.length > 0 && (
                          <SelectGroup>
                            <SelectLabel>{groups.teaches.length > 0 ? 'Weitere Lehrkräfte' : 'Lehrkräfte'}</SelectLabel>
                            {groups.others.map((t) => <TeacherItem key={t.id} t={t} />)}
                          </SelectGroup>
                        )}
                        {groups.teaches.length + groups.others.length === 0 && <div className="px-2 py-1.5 text-xs text-foreground-subtle">Keine aktive Lehrkraft vorhanden</div>}
                      </SelectContent>
                    </Select>
                    <FieldError message={err(i, 'teacherId')} />
                  </TableCell>
                  <TableCell className="pt-3.5">
                    {teacher ? (
                      <span className={cn('tabular-nums', teacher.plannedHours > teacher.weeklyHoursQuota ? 'font-semibold text-warning' : 'text-foreground-muted')}>
                        {teacher.plannedHours} / {teacher.weeklyHoursQuota}
                      </span>
                    ) : <span className="text-foreground-subtle">—</span>}
                  </TableCell>
                  <TableCell className="pt-3">
                    <div className="flex flex-col items-start gap-1">
                      {warnings.map((w, k) => <WarningChip key={k} message={w.message} />)}
                    </div>
                  </TableCell>
                  <TableCell className="text-right">
                    <Button variant="ghost" size="icon-sm" aria-label="Zeile entfernen" onClick={() => removeRow(row.key)}><X /></Button>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Button variant="outline" onClick={addRow} disabled={subjects.length === 0}><Plus /> Fach hinzufügen</Button>
        <span className="text-xs text-foreground-subtle">
          {subjects.length === 0 ? 'Zuerst Fächer anlegen (Grunddaten → Fächer).' : `${rows.length} Fächer · ${rows.reduce((n, r) => n + (r.weeklyHours ?? 0), 0)} Wochenstunden`}
        </span>
        <div className="ml-auto flex items-center gap-2">
          {dirty && <Button variant="ghost" onClick={() => { setRows(fromDto(schoolClass)); fields.clear() }} disabled={replace.isPending}><RotateCcw /> Verwerfen</Button>}
          <Button onClick={() => void save()} disabled={!dirty || replace.isPending}><Save /> Speichern</Button>
        </div>
      </div>
    </div>
  )
}

function TeacherItem({ t }: { t: TeacherDto }) {
  return (
    <SelectItem value={String(t.id)}>
      <span>{t.name}</span>
      <span className="text-xs text-foreground-subtle tabular-nums">({t.shortName}) · {t.plannedHours}/{t.weeklyHoursQuota}{!t.active && ' · deaktiviert'}</span>
    </SelectItem>
  )
}
