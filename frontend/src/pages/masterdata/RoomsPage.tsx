// /grunddaten/raeume — Name · Raumart · aktiv; Hinweis auf Sperrungen unter Änderungen (W-1).
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowRight, Ban, CircleCheck, DoorOpen, Info, Pencil, Plus, Trash2 } from 'lucide-react'
import { useDeleteRoom, useRooms, useUpdateRoom } from '@/api/hooks'
import type { RoomDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/layout/PageHeader'
import { EmptyState } from '@/components/states/EmptyState'
import { ErrorState } from '@/components/states/ErrorState'
import { PageSkeleton } from '@/components/states/PageSkeleton'
import { DataTable } from '@/components/table/DataTable'
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog'
import { RoomDialog } from '@/components/masterdata/RoomDialog'
import { isInUse, matches, useDeleteWithInUse } from '@/components/masterdata/helpers'
import { ActiveBadge, InUseDialog, NoMatches, RowMenu, SearchField } from '@/components/masterdata/shared'
import { TransferButtons } from '@/components/masterdata/TransferButtons'

export function RoomsPage() {
  const rooms = useRooms()
  const update = useUpdateRoom()
  const remove = useDeleteWithInUse(useDeleteRoom)
  const [query, setQuery] = useState('')
  const [dialog, setDialog] = useState<{ open: boolean; room?: RoomDto; key: number }>({ open: false, key: 0 })
  const openDialog = (room?: RoomDto) => setDialog((d) => ({ open: true, room, key: d.key + 1 }))
  const [toDelete, setToDelete] = useState<RoomDto | null>(null)
  // Ziel des 409-Dialogs bleibt gesetzt, auch wenn der Bestätigungsdialog sich schon geschlossen hat
  const [inUseTarget, setInUseTarget] = useState<RoomDto | null>(null)

  if (rooms.isPending) return <PageSkeleton />
  if (rooms.isError) return <ErrorState problem={rooms.error} retry={() => void rooms.refetch()} />

  const rows = rooms.data.filter((r) => matches(query, r.name, r.roomType))
  const roomTypes = [...new Set(rooms.data.map((r) => r.roomType))]

  const setActive = async (r: RoomDto, active: boolean) => {
    await update.mutateAsync({ id: r.id, body: { name: r.name, roomType: r.roomType, active } })
    toast.success(active ? `${r.name} aktiviert` : `${r.name} deaktiviert`)
  }
  const confirmDelete = async () => {
    if (!toDelete) return
    const name = toDelete.name
    try {
      await remove.mutateAsync(toDelete.id)
      toast.success(`Raum ${name} gelöscht`)
      setToDelete(null)
    } catch (err) {
      if (isInUse(err)) setInUseTarget(toDelete) // → InUseDialog; andere Fehler → Toast aus dem Hook
    }
  }

  return (
    <>
      <PageHeader
        title="Räume"
        description={`${rooms.data.length} Räume · ${roomTypes.length} Raumarten`}
        actions={
          <>
            <TransferButtons />
            <SearchField value={query} onChange={setQuery} placeholder="Name, Raumart…" />
            <Button onClick={() => openDialog()}><Plus /> Neuer Raum</Button>
          </>
        }
      />

      <DataTable<RoomDto>
        rows={rows}
        rowKey={(r) => r.id}
        onRowClick={(r) => openDialog(r)}
        columns={[
          {
            key: 'name', header: 'Name',
            cell: (r) => (
              <div className="flex items-center gap-2.5">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-surface-sunken text-foreground-subtle"><DoorOpen className="size-3.5" aria-hidden /></span>
                <span className={r.active ? 'font-medium' : 'font-medium text-foreground-subtle'}>{r.name}</span>
              </div>
            ),
          },
          { key: 'type', header: 'Raumart', cell: (r) => r.roomType },
          { key: 'status', header: 'Status', className: 'w-32', cell: (r) => <ActiveBadge active={r.active} /> },
          {
            key: 'actions', header: <span className="sr-only">Aktionen</span>, className: 'w-10 text-right',
            cell: (r) => (
              <RowMenu items={[
                { label: 'Bearbeiten', icon: Pencil, onClick: () => openDialog(r) },
                r.active
                  ? { label: 'Deaktivieren', icon: Ban, onClick: () => void setActive(r, false) }
                  : { label: 'Aktivieren', icon: CircleCheck, onClick: () => void setActive(r, true) },
                { label: 'Löschen', icon: Trash2, destructive: true, separatorBefore: true, onClick: () => setToDelete(r) },
              ]} />
            ),
          },
        ]}
        empty={
          query
            ? <NoMatches query={query} />
            : <EmptyState icon={DoorOpen} title="Noch keine Räume angelegt" description="Räume mit Raumart — Fächer mit Raumanforderung finden nur passende Räume." action={<Button onClick={() => openDialog()}><Plus /> Ersten Raum anlegen</Button>} />
        }
      />

      <p className="mt-4 flex items-center gap-2 text-xs text-foreground-subtle">
        <Info className="size-3.5 shrink-0" aria-hidden />
        Zeitweise Sperrungen (z. B. Wasserschaden) werden nicht hier, sondern unter Änderungen erfasst.
        <Link to="/vertretung" className="inline-flex items-center gap-1 font-medium text-primary underline-offset-2 hover:underline">Zur Vertretung <ArrowRight className="size-3" aria-hidden /></Link>
      </p>

      <RoomDialog key={dialog.key} open={dialog.open} room={dialog.room} onOpenChange={(open) => setDialog((d) => ({ ...d, open }))} />
      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={(open) => { if (!open) setToDelete(null) }}
        title={`Raum ${toDelete?.name ?? ''} löschen?`}
        description="Löschen ist nur möglich, wenn der Raum in keinem Plan belegt ist. Sonst wird Deaktivieren angeboten."
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
