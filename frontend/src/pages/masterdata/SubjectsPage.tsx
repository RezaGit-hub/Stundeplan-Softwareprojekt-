// /grunddaten/faecher — Farbpunkt · Name · Kürzel · Raumanforderung; Löschen mit 409-Klassenliste (W-1 Schritt 9).
import { useState, type CSSProperties } from 'react'
import { toast } from 'sonner'
import { BookOpen, Pencil, Plus, Trash2 } from 'lucide-react'
import { useClasses, useDeleteSubject, useSubjects, useTeachers } from '@/api/hooks'
import type { SubjectDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { DataTable } from '@/components/table/DataTable'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { SubjectDialog } from '@/components/masterdata/SubjectDialog'
import { matches, useDeleteWithInUse } from '@/components/masterdata/helpers'
import { InUseDialog, NoMatches, RowMenu, SearchField } from '@/components/masterdata/shared'
import { TransferButtons } from '@/components/masterdata/TransferButtons'

export function SubjectsPage() {
  const subjects = useSubjects()
  const teachers = useTeachers()
  const classes = useClasses()
  const remove = useDeleteWithInUse(useDeleteSubject)
  const [query, setQuery] = useState('')
  const [dialog, setDialog] = useState<{ open: boolean; subject?: SubjectDto; key: number }>({ open: false, key: 0 })
  const openDialog = (subject?: SubjectDto) => setDialog((d) => ({ open: true, subject, key: d.key + 1 }))
  const [toDelete, setToDelete] = useState<SubjectDto | null>(null)

  if (subjects.isPending) return <PageSkeleton />
  if (subjects.isError) return <ErrorState problem={subjects.error} retry={() => void subjects.refetch()} />

  const rows = subjects.data.filter((s) => matches(query, s.name, s.shortName, s.requiredRoomType))
  const teacherCount = (id: number) => (teachers.data ?? []).filter((t) => t.subjectIds.includes(id)).length
  const classCount = (id: number) => (classes.data ?? []).filter((c) => c.subjects.some((r) => r.subjectId === id)).length

  const confirmDelete = async () => {
    if (!toDelete) return
    const name = toDelete.name
    try {
      await remove.mutateAsync(toDelete.id)
      toast.success(`Fach ${name} gelöscht`)
      setToDelete(null)
    } catch {
      // 409 in-use → InUseDialog; andere Fehler → Toast
    }
  }

  return (
    <>
      <PageHeader
        title="Fächer"
        description={`${subjects.data.length} Fächer · Farbe und Raumanforderung gelten in allen Plansichten`}
        actions={
          <>
            <TransferButtons />
            <SearchField value={query} onChange={setQuery} placeholder="Name, Kürzel, Raumart…" />
            <Button onClick={() => openDialog()}><Plus /> Neues Fach</Button>
          </>
        }
      />

      <DataTable<SubjectDto>
        rows={rows}
        rowKey={(s) => s.id}
        onRowClick={(s) => openDialog(s)}
        columns={[
          {
            key: 'name', header: 'Fach',
            cell: (s) => (
              <div className="flex items-center gap-2.5">
                <span className="lesson-tile inline-flex h-7 min-w-9 items-center justify-center rounded-sm px-1.5 text-xs font-semibold" style={{ '--subject': s.color } as CSSProperties}>{s.shortName}</span>
                <span className="font-medium">{s.name}</span>
              </div>
            ),
          },
          { key: 'short', header: 'Kürzel', className: 'w-20', cell: (s) => <span className="font-mono text-xs text-foreground-muted">{s.shortName}</span> },
          {
            key: 'room', header: 'Raumanforderung', className: 'w-48',
            cell: (s) => s.requiredRoomType ? <span>{s.requiredRoomType}</span> : <span className="text-foreground-subtle">jeder Raum</span>,
          },
          { key: 'teachers', header: 'Lehrkräfte', className: 'w-28', cell: (s) => <span className="tabular-nums text-foreground-muted">{teacherCount(s.id)}</span> },
          { key: 'classes', header: 'Klassen', className: 'w-24', cell: (s) => <span className="tabular-nums text-foreground-muted">{classCount(s.id)}</span> },
          {
            key: 'actions', header: <span className="sr-only">Aktionen</span>, className: 'w-10 text-right',
            cell: (s) => (
              <RowMenu items={[
                { label: 'Bearbeiten', icon: Pencil, onClick: () => openDialog(s) },
                { label: 'Löschen', icon: Trash2, destructive: true, separatorBefore: true, onClick: () => setToDelete(s) },
              ]} />
            ),
          },
        ]}
        empty={
          query
            ? <NoMatches query={query} />
            : <EmptyState icon={BookOpen} title="Noch keine Fächer angelegt" description="Fächer bekommen eine Farbe für das Raster und optional eine Raumanforderung." action={<Button onClick={() => openDialog()}><Plus /> Erstes Fach anlegen</Button>} />
        }
      />

      <SubjectDialog key={dialog.key} open={dialog.open} subject={dialog.subject} onOpenChange={(open) => setDialog((d) => ({ ...d, open }))} />

      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={(open) => { if (!open) setToDelete(null) }}
        title={`Fach ${toDelete?.name ?? ''} löschen?`}
        description="Löschen ist nur möglich, wenn das Fach in keiner Klasse und keinem Plan verwendet wird."
        confirmLabel="Löschen"
        destructive
        onConfirm={confirmDelete}
      />
      <InUseDialog problem={remove.inUse} onOpenChange={(open) => { if (!open) { remove.clearInUse(); setToDelete(null) } }} />
    </>
  )
}
