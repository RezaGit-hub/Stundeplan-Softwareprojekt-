// /grunddaten/klassen — Name · Jahrgang · Fächer · Wochenstunden Σ · Warnungen · aktiv; Zeile → Detail (W-1).
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Ban, CircleCheck, GraduationCap, ListChecks, Pencil, Plus, Trash2 } from 'lucide-react'
import { useClasses, useDeleteClass, useUpdateClass } from '@/api/hooks'
import type { ClassDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { DataTable } from '@/components/table/DataTable'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { ClassDialog } from '@/components/masterdata/ClassDialog'
import { isInUse, matches, useDeleteWithInUse, weeklyHoursOf } from '@/components/masterdata/helpers'
import { ActiveBadge, InUseDialog, NoMatches, RowMenu, SearchField, WarningChip } from '@/components/masterdata/shared'
import { TransferButtons } from '@/components/masterdata/TransferButtons'


export function ClassesPage() {
  const classes = useClasses()
  const update = useUpdateClass()
  const remove = useDeleteWithInUse(useDeleteClass)
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [dialog, setDialog] = useState<{ open: boolean; schoolClass?: ClassDto; key: number }>({ open: false, key: 0 })
  const openDialog = (schoolClass?: ClassDto) => setDialog((d) => ({ open: true, schoolClass, key: d.key + 1 }))
  const [toDelete, setToDelete] = useState<ClassDto | null>(null)
  // Ziel des 409-Dialogs bleibt gesetzt, auch wenn der Bestätigungsdialog sich schon geschlossen hat
  const [inUseTarget, setInUseTarget] = useState<ClassDto | null>(null)

  if (classes.isPending) return <PageSkeleton />
  if (classes.isError) return <ErrorState problem={classes.error} retry={() => void classes.refetch()} />

  const rows = [...classes.data]
    .sort((a, b) => a.grade - b.grade || a.name.localeCompare(b.name, 'de'))
    .filter((c) => matches(query, c.name, c.grade, `Jahrgang ${c.grade}`))
  const warningCount = classes.data.reduce((n, c) => n + c.warnings.length, 0)

  const setActive = async (c: ClassDto, active: boolean) => {
    await update.mutateAsync({ id: c.id, body: { name: c.name, grade: c.grade, active } })
    toast.success(active ? `Klasse ${c.name} aktiviert` : `Klasse ${c.name} deaktiviert`)
  }
  const confirmDelete = async () => {
    if (!toDelete) return
    const name = toDelete.name
    try {
      await remove.mutateAsync(toDelete.id)
      toast.success(`Klasse ${name} gelöscht`)
      setToDelete(null)
    } catch (err) {
      if (isInUse(err)) setInUseTarget(toDelete) // → InUseDialog; andere Fehler → Toast aus dem Hook
    }
  }

  return (
    <>
      <PageHeader
        title="Klassen"
        description={`${classes.data.length} Klassen${warningCount > 0 ? ` · ${warningCount} Warnungen in der Fächerzuordnung` : ''}`}
        actions={
          <>
            <TransferButtons />
            <SearchField value={query} onChange={setQuery} placeholder="Klasse, Jahrgang…" />
            <Button onClick={() => openDialog()}><Plus /> Neue Klasse</Button>
          </>
        }
      />

      <DataTable<ClassDto>
        rows={rows}
        rowKey={(c) => c.id}
        onRowClick={(c) => navigate(`/grunddaten/klassen/${c.id}`)}
        columns={[
          {
            key: 'name', header: 'Klasse',
            cell: (c) => (
              <div className="flex items-center gap-2.5">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-primary-soft text-xs font-semibold text-primary-soft-foreground">{c.name}</span>
                <span className={c.active ? 'font-medium' : 'font-medium text-foreground-subtle'}>Klasse {c.name}</span>
              </div>
            ),
          },
          { key: 'grade', header: 'Jahrgang', className: 'w-24', cell: (c) => <span className="tabular-nums">{c.grade}</span> },
          { key: 'subjects', header: 'Fächer', className: 'w-24', cell: (c) => <span className="tabular-nums">{c.subjects.length}</span> },
          { key: 'hours', header: 'Wochenstunden', className: 'w-32', cell: (c) => <span className="tabular-nums">{weeklyHoursOf(c)}</span> },
          {
            key: 'status', header: 'Status',
            cell: (c) => (
              <div className="flex flex-wrap items-center gap-1.5">
                <ActiveBadge active={c.active} />
                {c.warnings.length === 1 && <WarningChip message={c.warnings[0].message} short className="max-w-72" />}
                {c.warnings.length > 1 && <WarningChip message={`${c.warnings.length} Warnungen`} />}
              </div>
            ),
          },
          {
            key: 'actions', header: <span className="sr-only">Aktionen</span>, className: 'w-10 text-right',
            cell: (c) => (
              <RowMenu items={[
                { label: 'Fächer und Lehrkräfte', icon: ListChecks, onClick: () => navigate(`/grunddaten/klassen/${c.id}`) },
                { label: 'Bearbeiten', icon: Pencil, onClick: () => openDialog(c) },
                c.active
                  ? { label: 'Deaktivieren', icon: Ban, onClick: () => void setActive(c, false) }
                  : { label: 'Aktivieren', icon: CircleCheck, onClick: () => void setActive(c, true) },
                { label: 'Löschen', icon: Trash2, destructive: true, separatorBefore: true, onClick: () => setToDelete(c) },
              ]} />
            ),
          },
        ]}
        empty={
          query
            ? <NoMatches query={query} />
            : <EmptyState icon={GraduationCap} title="Noch keine Klassen angelegt" description="Jede Klasse bekommt danach ihre Fächer mit Wochenstunden und Lehrkraft." action={<Button onClick={() => openDialog()}><Plus /> Erste Klasse anlegen</Button>} />
        }
      />

      <ClassDialog key={dialog.key}
        open={dialog.open}
        schoolClass={dialog.schoolClass}
        onOpenChange={(open) => setDialog((d) => ({ ...d, open }))}
        onCreated={(c) => navigate(`/grunddaten/klassen/${c.id}`)}
      />
      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={(open) => { if (!open) setToDelete(null) }}
        title={`Klasse ${toDelete?.name ?? ''} löschen?`}
        description="Die Fächerzuordnung der Klasse geht mit verloren. Löschen ist nur möglich, wenn die Klasse in keinem Plan Stunden hat."
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
