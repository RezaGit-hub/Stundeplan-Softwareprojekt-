// Unterrichtsfreie Zeiträume (Kann): Ferien und Feiertage — Liste, Anlegen, Löschen, Vorlage laden.
import { useState } from 'react'
import { toast } from 'sonner'
import { CalendarOff, Download, Plus, Trash2 } from 'lucide-react'
import { useCreateHoliday, useDeleteHoliday, useHolidayPresetYears, useHolidays, useLoadHolidayPreset } from '@/api/hooks'
import type { HolidayDto, HolidayInput, HolidayKind } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { DataTable } from '@/components/table/DataTable'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { DateField } from '@/components/forms/DateField'
import { formatDate, schoolYearOf } from '@/lib/dates'
import { useFieldErrors } from '@/lib/form-errors'
import { today } from '@/lib/today'
import { presetToast, schoolYearLabel, STATE_LABEL } from './helpers'
import { Field, RowMenu } from './shared'

const KIND_LABEL: Record<HolidayKind, string> = { VACATION: 'Ferien', PUBLIC_HOLIDAY: 'Feiertag' }

export function HolidaysTable({ presetSchoolYear }: { presetSchoolYear?: string } = {}) {
  const holidays = useHolidays()
  const remove = useDeleteHoliday()
  const [addOpen, setAddOpen] = useState(false)
  const [addKey, setAddKey] = useState(0)
  const [presetOpen, setPresetOpen] = useState(false)
  const [presetKey, setPresetKey] = useState(0)
  const [toDelete, setToDelete] = useState<HolidayDto | null>(null)

  if (holidays.isPending) return <PageSkeleton />
  if (holidays.isError) return <ErrorState problem={holidays.error} retry={() => void holidays.refetch()} />

  const rows = [...holidays.data].sort((a, b) => a.dateFrom.localeCompare(b.dateFrom))
  const now = today()

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs text-foreground-subtle">An diesen Tagen zeigen Wochensichten und App keine Stunden; Vertretungen werden dort nicht gesucht.</p>
        <div className="flex shrink-0 gap-2">
          <Button size="sm" variant="outline" onClick={() => { setPresetKey((k) => k + 1); setPresetOpen(true) }}><Download /> Vorlage laden</Button>
          <Button size="sm" onClick={() => { setAddKey((k) => k + 1); setAddOpen(true) }}><Plus /> Zeitraum hinzufügen</Button>
        </div>
      </div>
      <DataTable<HolidayDto>
        rows={rows}
        rowKey={(h) => h.id}
        columns={[
          { key: 'name', header: 'Name', cell: (h) => <span className={h.dateTo < now ? 'text-foreground-subtle' : 'font-medium'}>{h.name}</span> },
          { key: 'kind', header: 'Art', className: 'w-28', cell: (h) => <Badge variant="secondary">{KIND_LABEL[h.kind]}</Badge> },
          { key: 'from', header: 'Von', className: 'w-32', cell: (h) => <span className="tabular-nums">{formatDate(h.dateFrom)}</span> },
          { key: 'to', header: 'Bis', className: 'w-32', cell: (h) => <span className="tabular-nums">{formatDate(h.dateTo)}</span> },
          {
            key: 'actions', header: <span className="sr-only">Aktionen</span>, className: 'w-10 text-right',
            cell: (h) => <RowMenu items={[{ label: 'Löschen', icon: Trash2, destructive: true, onClick: () => setToDelete(h) }]} />,
          },
        ]}
        empty={<EmptyState icon={CalendarOff} title="Keine unterrichtsfreien Zeiträume eingetragen" description="Ferien und Feiertage nehmen die Tage aus Wochensichten und Vertretungssuche heraus. Die Vorlage bringt sie für das eingestellte Bundesland mit." action={<div className="flex gap-2"><Button variant="outline" onClick={() => { setPresetKey((k) => k + 1); setPresetOpen(true) }}><Download /> Vorlage laden</Button><Button onClick={() => { setAddKey((k) => k + 1); setAddOpen(true) }}><Plus /> Zeitraum hinzufügen</Button></div>} />}
      />
      <HolidayDialog key={addKey} open={addOpen} onOpenChange={setAddOpen} />
      <PresetDialog key={presetKey} open={presetOpen} onOpenChange={setPresetOpen} preselect={presetSchoolYear} />
      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={(o) => { if (!o) setToDelete(null) }}
        title={`„${toDelete?.name ?? ''}" löschen?`}
        confirmLabel="Löschen"
        destructive
        onConfirm={async () => {
          if (!toDelete) return
          try { await remove.mutateAsync(toDelete.id); toast.success('Zeitraum gelöscht') } catch { /* Toast aus dem Hook */ }
        }}
      />
    </div>
  )
}

function HolidayDialog({ open, onOpenChange }: { open: boolean; onOpenChange(open: boolean): void }) {
  const create = useCreateHoliday()
  const fields = useFieldErrors()
  const [form, setForm] = useState<HolidayInput>({ name: '', dateFrom: today(), dateTo: today(), kind: 'VACATION' })
  const set = (p: Partial<HolidayInput>) => setForm((f) => ({ ...f, ...p }))

  const save = async () => {
    fields.clear()
    if (form.dateTo < form.dateFrom) {
      fields.setFromProblem({ type: 'urn:pushnpull:problem:validation', title: 'Eingaben unvollständig', status: 400, errors: [{ field: 'dateTo', message: 'muss nach dem Beginn liegen' }] })
      return
    }
    try {
      await create.mutateAsync(form)
      toast.success('Zeitraum gespeichert')
      onOpenChange(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title="Unterrichtsfreier Zeitraum" onSubmit={save} pending={create.isPending}>
      <div className="grid grid-cols-[1fr_9rem] gap-3">
        <Field label="Name" htmlFor="holiday-name" error={fields.errors.name}>
          <Input id="holiday-name" value={form.name} onChange={(e) => set({ name: e.target.value })} placeholder="z. B. Herbstferien" autoFocus aria-invalid={!!fields.errors.name} />
        </Field>
        <Field label="Art" error={fields.errors.kind}>
          <Select value={form.kind} onValueChange={(v) => set({ kind: v as HolidayKind })}>
            <SelectTrigger className="w-full" aria-label="Art"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="VACATION">{KIND_LABEL.VACATION}</SelectItem>
              <SelectItem value="PUBLIC_HOLIDAY">{KIND_LABEL.PUBLIC_HOLIDAY}</SelectItem>
            </SelectContent>
          </Select>
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Von" htmlFor="holiday-from" error={fields.errors.dateFrom}>
          <DateField id="holiday-from" value={form.dateFrom} onChange={(d) => set({ dateFrom: d, dateTo: form.dateTo < d ? d : form.dateTo })} className="w-full" />
        </Field>
        <Field label="Bis" htmlFor="holiday-to" error={fields.errors.dateTo}>
          <DateField id="holiday-to" value={form.dateTo} onChange={(d) => set({ dateTo: d })} className="w-full" />
        </Field>
      </div>
    </FormDialog>
  )
}

/**
 * „Vorlage laden": übernimmt Ferien und Feiertage des in den Einstellungen gewählten Bundeslands für
 * ein Schuljahr (E-51). Die Zeiträume sind mitgeliefert — es wird nichts aus dem Internet geholt.
 * Vorbelegt ist das laufende Schuljahr, sofern die Vorlage es kennt.
 */
function PresetDialog({ open, onOpenChange, preselect }: { open: boolean; onOpenChange(open: boolean): void; preselect?: string }) {
  const years = useHolidayPresetYears()
  const load = useLoadHolidayPreset()
  const verfuegbar = years.data?.schoolYears ?? []
  const laufend = preselect ?? schoolYearOf(today())
  const [schoolYear, setSchoolYear] = useState('')
  const gewaehlt = schoolYear || (verfuegbar.includes(laufend) ? laufend : verfuegbar[0] ?? '')

  const uebernehmen = async () => {
    try {
      const res = await load.mutateAsync({ schoolYear: gewaehlt })
      toast.success(presetToast(res.added, res.skipped))
      onOpenChange(false)
    } catch { /* Toast aus dem Hook */ }
  }

  const land = years.data ? (STATE_LABEL[years.data.state] ?? years.data.state) : ''

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Ferien und Feiertage übernehmen"
      description={years.isPending
        ? 'Vorlage wird geladen …'
        : verfuegbar.length === 0
          ? `Für ${land || 'das eingestellte Bundesland'} liegt keine Vorlage bei. Zeiträume bitte einzeln eintragen.`
          : `Mitgelieferte Zeiträume für ${land}. Was schon eingetragen ist, bleibt unverändert; jeder übernommene Eintrag ist danach einzeln änderbar und löschbar.`}
      onSubmit={uebernehmen}
      submitLabel="Übernehmen"
      pending={load.isPending}
      submitDisabled={years.isPending || verfuegbar.length === 0 || !gewaehlt}
    >
      {verfuegbar.length > 0 && (
        <Field label="Schuljahr">
          <Select value={gewaehlt} onValueChange={setSchoolYear}>
            <SelectTrigger className="w-full" aria-label="Schuljahr"><SelectValue /></SelectTrigger>
            <SelectContent>
              {verfuegbar.map((y) => <SelectItem key={y} value={y}>{schoolYearLabel(y)}</SelectItem>)}
            </SelectContent>
          </Select>
        </Field>
      )}
      <p className="text-xs text-foreground-subtle">
        Enthalten sind die amtlichen Ferien und die landesweit geltenden Feiertage. Bewegliche Ferientage
        und Feiertage, die nur in einem Teilgebiet gelten, sind nicht dabei — sie werden von Hand ergänzt.
      </p>
    </FormDialog>
  )
}
