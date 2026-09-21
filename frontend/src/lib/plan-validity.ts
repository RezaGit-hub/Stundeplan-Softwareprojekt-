// Ab wann ein angenommener Plan gilt — und was die Plansicht daraus macht.
//
// Der Regelplan gilt ab dem Tag der Annahme (Server: der späteste Beginn, der nicht nach dem Tag liegt).
// Wer am Samstag annimmt, sieht in der Woche „heute" (Mo–Fr davor) noch den vorherigen Stand — oft eine
// leere Woche. Das sah aus, als wäre nichts gespeichert worden (Befund 05.09.). Beide Funktionen hier sind
// rein und ohne React, damit die Regel prüfbar bleibt.
import { addDaysIso, firstWeekMonday, formatDate, formatDayDate, planValidFrom, startOfIsoWeek } from './dates'

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

// ------------------------------------------------------------------ Gültigkeitszeitraum (#115)
//
// Seit 1.4.0 trägt jeder angenommene Plan einen Zeitraum [Beginn, Ende|∞], und es dürfen mehrere
// nebeneinander gelten, solange die Zeiträume sich nicht überschneiden. Die drei Funktionen hier machen
// daraus Text — rein und ohne React, damit die Regel prüfbar bleibt.

/**
 * Ab welchem Kalendertag ein angenommener Plan gilt: sein Stichtag, sonst der Tag der Annahme
 * (Europe/Berlin). Kennt die Antwort weder das eine noch das andere, ist der Beginn unbekannt (null).
 */
export function planStartOf(plan: { validFrom: string | null; acceptedAt: string | null }): string | null {
  return plan.validFrom ?? (plan.acceptedAt ? planValidFrom(plan.acceptedAt) : null)
}

/** Volle Zeile für die Kopfzeile: „gilt ab Mo 01.09.2026", mit Ende „gilt Mo 01.09.2026 – So 31.01.2027". */
export function validityText(validFrom: string | null, validUntil: string | null): string {
  if (!validFrom) return validUntil ? `gilt bis ${formatDayDate(validUntil)}` : ''
  return validUntil ? `gilt ${formatDayDate(validFrom)} – ${formatDayDate(validUntil)}` : `gilt ab ${formatDayDate(validFrom)}`
}

/** Kurzfassung für die Planwahl: „01.09.2026 – 31.01.2027", „ab 01.02.2027", „bis 31.01.2027", „unbegrenzt". */
export function planPeriodLabel(validFrom: string | null, validUntil: string | null): string {
  if (validFrom && validUntil) return `${formatDate(validFrom)} – ${formatDate(validUntil)}`
  if (validFrom) return `ab ${formatDate(validFrom)}`
  if (validUntil) return `bis ${formatDate(validUntil)}`
  return 'unbegrenzt'
}
