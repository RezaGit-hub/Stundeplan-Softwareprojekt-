// Konflikte je Stunde, für die Abzeichen an den Kacheln. Dieselbe Karte bauten bis zum 05.09. drei
// Seiten (Entwurf, Plan, Übersicht) mit derselben Doppelschleife, obwohl die Attrappe die Funktion
// schon hatte (Befund FRONTEND-8). Jetzt steht sie hier, und die Attrappe nimmt sie von hier.
import type { ConflictDto } from '@/api/types'

export function conflictsByLesson(conflicts: ConflictDto[]): Map<number, ConflictDto[]> {
  const map = new Map<number, ConflictDto[]>()
  for (const conflict of conflicts) {
    for (const lessonId of conflict.lessonIds) {
      map.set(lessonId, [...(map.get(lessonId) ?? []), conflict])
    }
  }
  return map
}
