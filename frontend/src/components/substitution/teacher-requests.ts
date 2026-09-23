// Vertretungsanfragen (V14/V15): Admin fragt Lehrkräfte für einzelne Stunden an, Lehrkraft antwortet
// über die App (PATCH /me/{id}) — das Web zeigt hier nur an und kann zurückziehen (DELETE), simuliert
// aber keine Antwort mehr. Jede Zeile aus dem Backend ist genau eine Stunde; "3 Stunden angeboten, 1
// zugesagt" bei einer Lehrkraft ergibt sich aus mehreren Zeilen mit demselben teacherId, nicht aus
// einem einzigen Datensatz mit einer Liste.
import { useMemo } from 'react'
import { useSubstitutionRequests } from '@/api/hooks'
import type { SubstitutionRequestDto } from '@/api/types'

export interface TeacherRequestGroup {
  teacherId: number
  rows: SubstitutionRequestDto[]
}

/** Anfragen einer Woche, gruppiert nach Lehrkraft. Ungefiltert nach Datum: die Anzeige rechnet selbst,
 *  welche Zeilen zu den aktuell offenen Stunden dieses Ausfalls gehören (relatedLessonIds). */
export function useTeacherRequestGroups(relatedLessonIds: number[]) {
  const query = useSubstitutionRequests()
  const groups = useMemo<TeacherRequestGroup[]>(() => {
    const rows = (query.data ?? []).filter((r) => relatedLessonIds.includes(r.lessonId))
    const byTeacher = new Map<number, SubstitutionRequestDto[]>()
    for (const row of rows) {
      const list = byTeacher.get(row.teacherId) ?? []
      list.push(row)
      byTeacher.set(row.teacherId, list)
    }
    return [...byTeacher.entries()].map(([teacherId, rows]) => ({ teacherId, rows }))
  }, [query.data, relatedLessonIds])
  return { groups, isPending: query.isPending }
}