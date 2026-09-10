// Ab wann ein angenommener Plan gilt — und was die Plansicht daraus macht.
//
// Der Regelplan gilt ab dem Tag der Annahme (Server: der späteste Beginn, der nicht nach dem Tag liegt).
// Wer am Samstag annimmt, sieht in der Woche „heute" (Mo–Fr davor) noch den vorherigen Stand — oft eine
// leere Woche. Das sah aus, als wäre nichts gespeichert worden (Befund 05.09.). Beide Funktionen hier sind
// rein und ohne React, damit die Regel prüfbar bleibt.
import { addDaysIso, firstWeekMonday, startOfIsoWeek } from './dates'

/**
 * Standardwoche ohne `woche`-Parameter: die Woche von heute, frühestens aber die erste Woche,
 * in der der Plan gilt.
 */
export function defaultPlanMonday(todayIso: string, validFromIso: string | null): string {
  const current = startOfIsoWeek(todayIso)
  if (!validFromIso) return current
  const first = firstWeekMonday(validFromIso)
  return first > current ? first : current
}

/**
 * Zeigt die angezeigte Woche den vorherigen Stand? Nur dann, wenn die Woche vollständig vor dem
 * Geltungsbeginn liegt (Freitag < gilt ab), das Raster leer ist und der aktive Plan überhaupt Stunden hat.
 * Die Bedingung „Raster leer" hält den Hinweis aus Sichten heraus, in denen der Plan sehr wohl steht.
 */
export function showsPreviousPlan(a: { monday: string; validFrom: string | null; weekEntryCount: number; planLessonCount: number }): boolean {
  if (!a.validFrom || a.planLessonCount === 0 || a.weekEntryCount > 0) return false
  return addDaysIso(a.monday, 4) < a.validFrom
}
