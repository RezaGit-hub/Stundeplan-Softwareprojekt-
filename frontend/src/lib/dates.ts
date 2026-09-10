// Datumshelfer. Alle Datumswerte in der App sind Strings yyyy-MM-dd (wie im API-Vertrag);
// nur hier wird in Date umgerechnet.
import { addDays, addWeeks as dfAddWeeks, format, getISODay, getISOWeek, parseISO, startOfISOWeek } from 'date-fns'
import { de } from 'date-fns/locale'

export { de }
export const ISO = 'yyyy-MM-dd'

export function toIso(d: Date): string {
  return format(d, ISO)
}

export function fromIso(dateIso: string): Date {
  return parseISO(dateIso)
}

/** Montag der ISO-Woche als yyyy-MM-dd */
export function startOfIsoWeek(dateIso: string): string {
  return toIso(startOfISOWeek(fromIso(dateIso)))
}

export function addWeeks(mondayIso: string, n: number): string {
  return toIso(dfAddWeeks(fromIso(mondayIso), n))
}

export function addDaysIso(dateIso: string, n: number): string {
  return toIso(addDays(fromIso(dateIso), n))
}

/** 1 = Montag … 7 = Sonntag */
export function isoDay(dateIso: string): number {
  return getISODay(fromIso(dateIso))
}

export function isoWeekOf(dateIso: string): number {
  return getISOWeek(fromIso(dateIso))
}

/** "Mo 21.09." */
export function formatDay(dateIso: string): string {
  return format(fromIso(dateIso), 'EEEEEE dd.MM.', { locale: de })
}

/** "21.09.2026" */
export function formatDate(dateIso: string): string {
  return format(fromIso(dateIso), 'dd.MM.yyyy')
}

/** "Mo 21.09.2026" — Wochentag plus volles Datum (für „gilt ab") */
export function formatDayDate(dateIso: string): string {
  return format(fromIso(dateIso), 'EEEEEE dd.MM.yyyy', { locale: de })
}

/** "21.09.2026 07:42" aus einem ISO-Zeitstempel */
export function formatDateTime(iso: string): string {
  return format(new Date(iso), 'dd.MM.yyyy HH:mm')
}

/**
 * Montag der Woche, in der dateIso liegt — fällt dateIso auf Samstag oder Sonntag, der nächste Montag.
 * Ein Plan, der am Wochenende gültig wird, hat seine erste Schulwoche erst danach.
 */
export function firstWeekMonday(dateIso: string): string {
  const day = isoDay(dateIso) // 1 = Mo … 7 = So
  return day >= 6 ? addDaysIso(dateIso, 8 - day) : startOfIsoWeek(dateIso)
}

// Der Kalendertag hängt an der Zeitzone: 2026-09-05T23:30:00Z ist in Berlin schon der 06.09.
const BERLIN_DAY = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Berlin', year: 'numeric', month: '2-digit', day: '2-digit' })

/**
 * Kalendertag (Europe/Berlin) aus einem ISO-Zeitstempel — der Tag, ab dem ein ohne Stichtag angenommener
 * Plan gilt. Rückfallweg: seit 05.09. trägt das TimetableDto ein eigenes `validFrom` (E-40,
 * Befund PFLICHTENHEFT-6); diese Umrechnung greift nur noch, wenn dort `null` steht.
 */
export function planValidFrom(acceptedAt: string): string {
  const p = Object.fromEntries(BERLIN_DAY.formatToParts(new Date(acceptedAt)).map((x) => [x.type, x.value]))
  return `${p.year}-${p.month}-${p.day}`
}

/** "KW 39 · 21.–25.09.2026" (Mo–Fr) */
export function weekLabel(mondayIso: string): string {
  const mon = fromIso(mondayIso)
  const fri = addDays(mon, 4)
  return `KW ${getISOWeek(mon)} · ${format(mon, 'dd.')}–${format(fri, 'dd.MM.yyyy')}`
}

/** Montag..Sonntag der Woche, in der dateIso liegt */
export function weekRange(dateIso: string): { from: string; to: string } {
  const from = startOfIsoWeek(dateIso)
  return { from, to: addDaysIso(from, 6) }
}

/** a <= x <= b, alles yyyy-MM-dd (lexikografisch vergleichbar) */
export function between(x: string, a: string, b: string): boolean {
  return x >= a && x <= b
}

/**
 * Schuljahr eines Kalendertags in der Schreibweise der KMK: "2026/27". Der Wechsel liegt am 1. August
 * — derselbe Schnitt wie in der mitgelieferten Ferien- und Feiertagsvorlage (E-51,
 * `V8__holiday_presets.sql`). Der 31.07.2027 gehört also noch zu "2026/27", der 01.08.2027 zu "2027/28".
 */
export function schoolYearOf(dateIso: string): string {
  const [year, month] = dateIso.split('-').map(Number)
  const start = month >= 8 ? year : year - 1
  return `${start}/${String(start + 1).slice(2)}`
}
