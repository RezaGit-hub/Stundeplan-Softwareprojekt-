// Fehler → ProblemDetail → Toast. Regeln aus SPEZIFIKATION 3.8:
// 409/404/500/Netz → toast.error(title, detail); 400 validation → kein Toast (Feldfehler); 401 → Toast kommt vom Client.
import { toast } from 'sonner'
import { ApiError, PROBLEM_PREFIX, type ProblemDetail, type ProblemSlug } from './types'

export function problemOf(err: unknown): ProblemDetail {
  if (err instanceof ApiError) return err.problem
  return { type: 'urn:pushnpull:problem:unknown', title: 'Unbekannter Fehler', status: 0, detail: err instanceof Error ? err.message : undefined }
}

/** Der Slug eines Fehlers (`urn:pushnpull:problem:plan-overlap` → `plan-overlap`); '' wenn keiner. */
export function slugOf(err: unknown): string {
  const type = problemOf(err).type
  return type.startsWith(PROBLEM_PREFIX) ? type.slice(PROBLEM_PREFIX.length) : ''
}

/** Trägt dieser Fehler genau diesen Slug? Für die Fälle, die eine Seite selbst darstellt. */
export function isProblem(err: unknown, slug: ProblemSlug): boolean {
  return slugOf(err) === slug
}

export function isValidation(err: unknown): boolean {
  return err instanceof ApiError && err.status === 400 && (err.problem.errors?.length ?? 0) > 0
}

export function isUnauthorized(err: unknown): boolean {
  return err instanceof ApiError && err.status === 401
}

/** Serverfehler als Toast (5 s). Für 400-Feldfehler und 401 nichts tun. */
export function toastProblem(err: unknown): void {
  if (isValidation(err) || isUnauthorized(err)) return
  const p = problemOf(err)
  toast.error(p.title, { description: p.detail, duration: 5000 })
}

/**
 * 400-Feldfehler dort zeigen, wo es kein Eingabefeld gibt (ein Knopf schickt fertige Daten, z. B. „Zuweisen").
 * Ohne diesen Aufruf bliebe die Meldung unsichtbar: `toastProblem` überspringt 400 absichtlich, weil Formulare
 * die Feldfehler selbst anzeigen. Alles außer 400 hat das globale `onError` schon gemeldet.
 */
export function toastValidation(err: unknown): void {
  if (!isValidation(err)) return
  const p = problemOf(err)
  const fields = p.errors?.map((e) => e.message).join(' · ')
  toast.error(p.title, { description: fields || p.detail, duration: 5000 })
}
