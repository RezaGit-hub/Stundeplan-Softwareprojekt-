// Nicht-Komponenten der Grunddaten-Seiten (Beschriftungen, Filter, Signaturen, Lösch-Hook) — getrennt von den
// Komponenten in shared.tsx, damit Fast Refresh sauber bleibt (react-refresh/only-export-components).
import { useState } from 'react'
import type { UseMutationResult } from '@tanstack/react-query'
import type { MutationOpts } from '@/api/hooks'
import { ApiError, WEEKDAYS, type ClassDto, type ProblemDetail, type RuleType, type TimeslotDto, type Weekday } from '@/api/types'

// ------------------------------------------------------------------ Beschriftungen
/**
 * Bundesland-Kürzel → Name (nur Anzeige; der Wert bleibt Daten in der Systemkonfiguration).
 * Dieselben sechzehn Einträge wie STATE_NAMES in src/mock/rule-presets.ts — hier noch einmal, damit
 * die Oberfläche nichts aus der Attrappe importiert; w2-flows.test.ts vergleicht beide Listen.
 */
export const STATE_LABEL: Record<string, string> = {
  BW: 'Baden-Württemberg', BY: 'Bayern', BE: 'Berlin', BB: 'Brandenburg', HB: 'Bremen', HH: 'Hamburg', HE: 'Hessen',
  MV: 'Mecklenburg-Vorpommern', NI: 'Niedersachsen', NW: 'Nordrhein-Westfalen', RP: 'Rheinland-Pfalz', SL: 'Saarland',
  SN: 'Sachsen', ST: 'Sachsen-Anhalt', SH: 'Schleswig-Holstein', TH: 'Thüringen',
}

/** Schulform-Kürzel → Name (Vorbelegung GYMNASIUM; freie Werte bleiben erlaubt) */
export const SCHOOL_TYPE_LABEL: Record<string, string> = {
  GYMNASIUM: 'Gymnasium', GEMEINSCHAFTSSCHULE: 'Gemeinschaftsschule', GESAMTSCHULE: 'Gesamtschule', REALSCHULE: 'Realschule',
  HAUPTSCHULE: 'Hauptschule', GRUNDSCHULE: 'Grundschule', BERUFSSCHULE: 'Berufsschule', FOERDERSCHULE: 'Förderschule',
}

export const ROLE_LABEL = { PLANNING: 'Planung', TEACHER: 'Lehrkraft', STUDENT: 'Klasse' } as const

// ------------------------------------------------------------------ Ferien- und Feiertagsvorlage (E-51)

/** "2026/27" → "Schuljahr 2026/27" */
export const schoolYearLabel = (schoolYear: string) => `Schuljahr ${schoolYear}`

/**
 * Meldung nach dem Übernehmen der Vorlage — dieselbe Zählung wie die Antwort des Servers.
 * `skipped` steht für Zeiträume, die mit gleicher Bezeichnung und gleichem Anfang und Ende schon standen.
 */
export function presetToast(added: number, skipped: number): string {
  if (added === 0 && skipped === 0) return 'Die Vorlage ist leer'
  if (added === 0) return `Nichts übernommen — ${skipped} ${skipped === 1 ? 'Zeitraum stand' : 'Zeiträume standen'} schon`
  return `${added} übernommen${skipped > 0 ? `, ${skipped} schon vorhanden` : ''}`
}

export const SCOPE_LABEL: Record<string, string> = { CLASS: 'je Klasse', TEACHER: 'je Lehrkraft' }
export const RULE_TYPE_LABEL: Record<RuleType, string> = {
  MAX_LESSONS_PER_DAY: 'Maximale Stunden pro Tag',
  MAX_LESSONS_PER_WEEK: 'Maximale Stunden pro Woche',
  NO_LESSONS_IN_BREAKS: 'Keine Stunden in Pausen',
}

// ------------------------------------------------------------------ Klassen
/** Summe der Wochenstunden einer Klasse über alle Fächer */
export const weeklyHoursOf = (c: ClassDto) => c.subjects.reduce((sum, r) => sum + r.weeklyHours, 0)

/** Kennung des Serverstands der Fächerzuordnung — als `key` der ClassSubjectsTable, damit sie nach dem Speichern neu startet */
export const classSignature = (c: ClassDto) => `${c.id}:${JSON.stringify(c.subjects.map((r) => [r.subjectId, r.weeklyHours, r.teacherId]))}`

// ------------------------------------------------------------------ Suche
/** Zeilen nach einem Suchtext filtern (Groß/klein egal, alle genannten Felder) */
export function matches(query: string, ...fields: (string | number | null | undefined)[]): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return fields.some((f) => f != null && String(f).toLowerCase().includes(q))
}

// ------------------------------------------------------------------ Stundenraster
let tempId = -1
const newTempId = () => tempId--

/** Vorlage des Pflichtenhefts: Mo–Fr, 6 Stunden, große Pause nach der 2. */
const TEMPLATE_DAY: [string, string, boolean][] = [
  ['08:00', '08:45', false], ['08:50', '09:35', false], ['09:35', '09:55', true],
  ['09:55', '10:40', false], ['10:45', '11:30', false], ['11:50', '12:35', false], ['12:40', '13:25', false],
]

export function templateSlots(days: Weekday[] = ['MO', 'TU', 'WE', 'TH', 'FR']): TimeslotDto[] {
  return normalize(days.flatMap((day) => TEMPLATE_DAY.map(([startTime, endTime, isBreak]) => ({ id: newTempId(), day, position: 0, startTime, endTime, isBreak }))))
}

/** Sortieren nach Tag/Reihenfolge und Positionen je Tag neu vergeben; neue Zeilen bekommen negative Kennungen */
export function normalize(slots: TimeslotDto[]): TimeslotDto[] {
  const out: TimeslotDto[] = []
  for (const day of WEEKDAYS) {
    const ofDay = slots.filter((s) => s.day === day).sort((a, b) => a.position - b.position || a.startTime.localeCompare(b.startTime))
    ofDay.forEach((s, i) => out.push({ ...s, position: i + 1 }))
  }
  return out
}

export function newSlot(day: Weekday, position: number, startTime: string, endTime: string, isBreak = false): TimeslotDto {
  return { id: newTempId(), day, position, startTime, endTime, isBreak }
}

// ------------------------------------------------------------------ Löschen mit 409 „in-use"
export function isInUse(err: unknown): err is ApiError {
  return err instanceof ApiError && err.status === 409 && err.problem.type.endsWith('in-use')
}

/**
 * Löschen ohne Doppelmeldung: 409 „in-use" bekommt den InUseDialog statt des Toasts, alle anderen Fehler den Toast
 * wie überall. `useDelete` ist der Hook aus api/hooks.ts (useDeleteTeacher, …) — Invalidierung bleibt dort.
 */
export function useDeleteWithInUse(useDelete: (opts: MutationOpts) => UseMutationResult<void, Error, number>) {
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const mutation = useDelete({ quiet: isInUse })
  const mutateAsync = async (id: number) => {
    try {
      return await mutation.mutateAsync(id)
    } catch (err) {
      if (isInUse(err)) setProblem(err.problem)
      throw err
    }
  }
  return { ...mutation, mutateAsync, inUse: problem, clearInUse: () => setProblem(null) }
}
