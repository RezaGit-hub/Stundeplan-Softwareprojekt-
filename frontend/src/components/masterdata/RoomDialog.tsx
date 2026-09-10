// Raum anlegen oder ändern: Name, Raumart (Freitext mit Vorschlägen aus Räumen und Raumanforderungen der Fächer), aktiv.
import { useState } from 'react'
import { toast } from 'sonner'
import { useCreateRoom, useRooms, useSubjects, useUpdateRoom } from '@/api/hooks'
import type { RoomDto, RoomInput } from '@/api/types'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { FormDialog } from '@/components/dialogs/FormDialog'
import { useFieldErrors } from '@/lib/form-errors'
import { Field } from './shared'

const EMPTY: RoomInput = { name: '', roomType: 'Klassenraum', active: true }

export function RoomDialog({ open, room, onOpenChange }: { open: boolean; room?: RoomDto; onOpenChange(open: boolean): void }) {
  const rooms = useRooms({ enabled: open })
  const subjects = useSubjects({ enabled: open })
  const create = useCreateRoom()
  const update = useUpdateRoom()
  const fields = useFieldErrors()
  // Die Seite gibt beim Öffnen einen neuen `key` → frisches Formular ohne Effekt
  const [form, setForm] = useState<RoomInput>(() => (room ? { name: room.name, roomType: room.roomType, active: room.active } : EMPTY))

  const set = (patch: Partial<RoomInput>) => setForm((f) => ({ ...f, ...patch }))
  const suggestions = [...new Set([
    ...(rooms.data ?? []).map((r) => r.roomType),
    ...(subjects.data ?? []).map((s) => s.requiredRoomType).filter((t): t is string => !!t),
  ])].sort()

  const save = async () => {
    fields.clear()
    try {
      if (room) await update.mutateAsync({ id: room.id, body: form })
      else await create.mutateAsync(form)
      toast.success('Raum gespeichert')
      onOpenChange(false)
    } catch (err) {
      fields.handle(err)
    }
  }

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title={room ? `Raum ${room.name} bearbeiten` : 'Neuer Raum'} onSubmit={save} pending={create.isPending || update.isPending}>
      <Field label="Name" htmlFor="room-name" error={fields.errors.name}>
        <Input id="room-name" value={form.name} onChange={(e) => set({ name: e.target.value })} autoFocus placeholder="z. B. R101" aria-invalid={!!fields.errors.name} />
      </Field>
      <Field label="Raumart" htmlFor="room-type" error={fields.errors.roomType} hint="Fächer mit Raumanforderung finden nur Räume derselben Raumart.">
        <Input id="room-type" list="room-type-list" value={form.roomType} onChange={(e) => set({ roomType: e.target.value })} placeholder="Klassenraum" aria-invalid={!!fields.errors.roomType} />
        <datalist id="room-type-list">
          {suggestions.map((t) => <option key={t} value={t} />)}
        </datalist>
      </Field>
      <label className="flex items-center justify-between rounded-md border bg-surface px-3 py-2 text-sm">
        <span>
          <span className="font-medium">Aktiv</span>
          <span className="block text-xs text-foreground-subtle">Deaktivierte Räume werden nicht mehr vorgeschlagen.</span>
        </span>
        <Switch checked={form.active} onCheckedChange={(v) => set({ active: v })} aria-label="Aktiv" />
      </label>
    </FormDialog>
  )
}
