// Auswahlliste je Sicht (nur aktive Objekte): „7a", „Meyer (MEY)", „R101". Wird von ObjectSelect und den Seiten
// (Vorbelegung mit dem ersten Eintrag) gemeinsam benutzt.
//
// Fehler werden durchgereicht: schlägt GET /classes|/teachers|/rooms fehl, ist `options` leer wie bei „keine
// Grunddaten" — die Seite muss die beiden Fälle auseinanderhalten und ErrorState statt Leerzustand zeigen.
import { useClasses, useRooms, useTeachers } from '@/api/hooks'
import type { View } from '@/api/types'

export interface ObjectOption { id: number; label: string }

export interface ObjectOptions {
  options: ObjectOption[] | undefined
  isPending: boolean
  isError: boolean
  error: unknown
  refetch(): void
}

export const VIEW_LABEL: Record<View, string> = { SCHOOL: 'Schule', CLASS: 'Klasse', TEACHER: 'Lehrkraft', ROOM: 'Raum' }

export function useObjectOptions(view: View): ObjectOptions {
  const classes = useClasses({ enabled: view === 'CLASS' })
  const teachers = useTeachers({ enabled: view === 'TEACHER' })
  const rooms = useRooms({ enabled: view === 'ROOM' })
  switch (view) {
    case 'CLASS':
      return {
        options: classes.data?.filter((c) => c.active).map((c) => ({ id: c.id, label: c.name })),
        isPending: classes.isPending, isError: classes.isError, error: classes.error, refetch: () => void classes.refetch(),
      }
    case 'TEACHER':
      return {
        options: teachers.data?.filter((t) => t.active).map((t) => ({ id: t.id, label: `${t.name} (${t.shortName})` })),
        isPending: teachers.isPending, isError: teachers.isError, error: teachers.error, refetch: () => void teachers.refetch(),
      }
    case 'ROOM':
      return {
        options: rooms.data?.filter((r) => r.active).map((r) => ({ id: r.id, label: r.name })),
        isPending: rooms.isPending, isError: rooms.isError, error: rooms.error, refetch: () => void rooms.refetch(),
      }
    default:
      return { options: [], isPending: false, isError: false, error: null, refetch: () => {} }
  }
}
