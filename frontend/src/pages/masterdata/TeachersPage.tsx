// /grunddaten/lehrkraefte — Tabelle, Dialog, Deaktivieren, Löschen mit 409-Dialog (W-1, T-W1.1, T-W1.3).
import { useState } from 'react'
import { toast } from 'sonner'
import { Ban, CircleCheck, Pencil, Plus, Trash2, Users } from 'lucide-react'
import { useDeleteTeacher, useSubjects, useTeachers, useUpdateTeacher } from '@/api/hooks'
import type { TeacherDto } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { DataTable } from '@/components/table/DataTable'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { TeacherDialog } from '@/components/masterdata/TeacherDialog'
import { isInUse, matches, useDeleteWithInUse } from '@/components/masterdata/helpers'
import { ActiveBadge, ColorDot, InUseDialog, NoMatches, QuotaMeter, RowMenu, SearchField, WarningChip } from '@/components/masterdata/shared'

export function TeachersPage() {
  const teachers = useTeachers()
  const subjects = useSubjects()
  const update = useUpdateTeacher()
  const remove = useDeleteWithInUse(useDeleteTeacher)
  const [query, setQuery] = useState('')
  const [dialog, setDialog] = useState<{ open: boolean; teacher?: TeacherDto; key: number }>({ open: false, key: 0 })
  const openDialog = (teacher?: TeacherDto) => setDialog((d) => ({ open: true, teacher, key: d.key + 1 }))
  const [toDelete, setToDelete] = useState<TeacherDto | null>(null)
  // Ziel des 409-Dialogs bleibt gesetzt, auch wenn der Bestätigungsdialog sich schon geschlossen hat
  const [inUseTarget, setInUseTarget] = useState<TeacherDto | null>(null)

  if (teachers.isPending) return <PageSkeleton />
  if (teachers.isError) return <ErrorState problem={teachers.error} retry={() => void teachers.refetch()} />

  const subjectById = new Map((subjects.data ?? []).map((s) => [s.id, s]))
  const rows = teachers.data.filter((t) => matches(query, t.name, t.shortName, ...t.subjectIds.map((id) => subjectById.get(id)?.name)))
  const activeCount = teachers.data.filter((t) => t.active).length

  const setActive = async (t: TeacherDto, active: boolean) => {
    await update.mutateAsync({
      id: t.id,
      body: {
        name: t.name, shortName: t.shortName, weeklyHoursQuota: t.weeklyHoursQuota, subjectIds: t.subjectIds,
        preferredTimeslotIds: t.preferredTimeslotIds, blockedTimeslotIds: t.blockedTimeslotIds, active,
      },
    })
    toast.success(active ? `${t.name} aktiviert` : `${t.name} deaktiviert`)
  }

  const confirmDelete = async () => {
    if (!toDelete) return
    const name = toDelete.name
    try {
      await remove.mutateAsync(toDelete.id)
      toast.success(`Lehrkraft ${name} gelöscht`)
    } catch (err) {
      if (isInUse(err)) setInUseTarget(toDelete) // → InUseDialog; andere Fehler → Toast aus dem Hook
    }
  }

  return (
    <>
      <PageHeader
        title="Lehrkräfte"
        description={`${teachers.data.length} Lehrkräfte · ${activeCount} aktiv`}
        actions={
          <>
            <SearchField value={query} onChange={setQuery} placeholder="Name, Kürzel, Fach…" />
            <Button onClick={() => openDialog()}><Plus /> Neue Lehrkraft</Button>
          </>
        }
      />

      <DataTable<TeacherDto>
        rows={rows}
        rowKey={(t) => t.id}
        onRowClick={(t) => openDialog(t)}
        columns={[
          {
            key: 'name', header: 'Name',
            cell: (t) => (
              <div className="flex items-center gap-2.5">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-primary-soft text-2xs font-semibold text-primary-soft-foreground">{t.shortName}</span>
                <span className={t.active ? 'font-medium' : 'font-medium text-foreground-subtle'}>{t.name}</span>
              </div>
            ),
          },
          { key: 'short', header: 'Kürzel', className: 'w-20', cell: (t) => <span className="font-mono text-xs text-foreground-muted">{t.shortName}</span> },
          { key: 'quota', header: 'Deputat', className: 'w-24', cell: (t) => <span className="tabular-nums">{t.weeklyHoursQuota} Std.</span> },
          { key: 'planned', header: 'Verplant / Deputat', className: 'w-44', cell: (t) => <QuotaMeter planned={t.plannedHours} quota={t.weeklyHoursQuota} /> },
          {
            key: 'subjects', header: 'Fächer',
            cell: (t) => (
              <div className="flex flex-wrap gap-1">
                {t.subjectIds.map((id) => {
                  const s = subjectById.get(id)
                  return s ? (
                    <Badge key={id} variant="outline" className="gap-1.5 font-normal"><ColorDot color={s.color} />{s.shortName}</Badge>
                  ) : null
                })}
                {t.subjectIds.length === 0 && <span className="text-xs text-foreground-subtle">—</span>}
              </div>
            ),
          },
          {
            key: 'times', header: 'Zeiten', className: 'w-32',
            cell: (t) => (
              <span className="text-xs text-foreground-subtle tabular-nums">
                {t.preferredTimeslotIds.length > 0 && <span className="text-primary-soft-foreground">{t.preferredTimeslotIds.length} bevorzugt</span>}
                {t.preferredTimeslotIds.length > 0 && t.blockedTimeslotIds.length > 0 && ' · '}
                {t.blockedTimeslotIds.length > 0 && <span className="text-destructive">{t.blockedTimeslotIds.length} gesperrt</span>}
                {t.preferredTimeslotIds.length + t.blockedTimeslotIds.length === 0 && '—'}
              </span>
            ),
          },
          {
            key: 'status', header: 'Status', className: 'w-36',
            cell: (t) => (
              <div className="flex flex-wrap items-center gap-1.5">
                <ActiveBadge active={t.active} />
                {t.warnings.map((w, i) => <WarningChip key={i} message={w.message} short className="max-w-40" />)}
              </div>
            ),
          },
          {
            key: 'actions', header: <span className="sr-only">Aktionen</span>, className: 'w-10 text-right',
            cell: (t) => (
              <RowMenu items={[
                { label: 'Bearbeiten', icon: Pencil, onClick: () => openDialog(t) },
                t.active
                  ? { label: 'Deaktivieren', icon: Ban, onClick: () => void setActive(t, false) }
                  : { label: 'Aktivieren', icon: CircleCheck, onClick: () => void setActive(t, true) },
                { label: 'Löschen', icon: Trash2, destructive: true, separatorBefore: true, onClick: () => setToDelete(t) },
              ]} />
            ),
          },
        ]}
        empty={
          query
            ? <NoMatches query={query} />
            : <EmptyState icon={Users} title="Noch keine Lehrkräfte angelegt" description="Lehrkräfte mit Kürzel, Deputat und Fächern sind die Grundlage jeder Planung." action={<Button onClick={() => openDialog()}><Plus /> Erste Lehrkraft anlegen</Button>} />
        }
      />

      <TeacherDialog key={dialog.key} open={dialog.open} teacher={dialog.teacher} onOpenChange={(open) => setDialog((d) => ({ ...d, open }))} />

      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={(open) => { if (!open) setToDelete(null) }}
        title={`Lehrkraft ${toDelete?.name ?? ''} löschen?`}
        description="Löschen ist nur möglich, wenn die Lehrkraft in keinem Plan und keiner Klasse eingetragen ist. Sonst wird Deaktivieren angeboten."
        confirmLabel="Löschen"
        destructive
        onConfirm={confirmDelete}
      />

      <InUseDialog
        problem={remove.inUse}
        onOpenChange={(open) => { if (!open) { remove.clearInUse(); setInUseTarget(null) } }}
        onDeactivate={inUseTarget?.active ? async () => { await setActive(inUseTarget, false); setInUseTarget(null) } : undefined}
      />
    </>
  )
}
