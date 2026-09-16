// Was der Annehmen-Dialog (#116) aus seinen Feldern macht: der Körper von POST /api/drafts/{id}/accept
// und die Prüfung des Zeitraums. Beides rein und ohne React — wie lib/plan-validity.ts —, damit die Regel
// für sich prüfbar ist und nicht nur über den gerenderten Dialog.
import type { AcceptDraftInput } from '@/api/types'

/** Die drei Wege des Dialogs. `park` nimmt nichts an — er legt den Entwurf nur beiseite. */
export type AcceptWay = 'now' | 'from' | 'park'

/** Der Stand der Felder des Dialogs. Die Datumsangaben liest nur der Weg `from`. */
export interface AcceptInput {
  way: AcceptWay
  /** „Gültig ab", ISO-Kalendertag */
  validFrom: string
  /** Häkchen „Auch ein Enddatum setzen" */
  useValidUntil: boolean
  /** „Gültig bis", ISO-Kalendertag; zählt nur mit `useValidUntil` */
  validUntil: string
}

/**
 * Körper des Annehmen-Aufrufs. „Jetzt aktivieren" schickt kein Datum mit — dann rechnet der Server ab
 * heute. `park` nimmt gar nicht erst an und kommt deshalb hier nicht vor; der leere Körper ist trotzdem
 * die richtige Antwort, damit die Funktion für jeden Weg eine hat.
 */
export function acceptBody(input: AcceptInput): AcceptDraftInput {
  if (input.way !== 'from') return {}
  return input.useValidUntil
    ? { validFrom: input.validFrom, validUntil: input.validUntil }
    : { validFrom: input.validFrom }
}

/**
 * Zeitraum verdreht: das prüft die Oberfläche selbst, damit niemand für ein 400 auf den Server wartet.
 * ISO-Kalendertage (YYYY-MM-DD) vergleichen sich als Zeichenketten. `undefined` heißt: in Ordnung.
 */
export function acceptRangeError(input: AcceptInput): string | undefined {
  if (input.way !== 'from' || !input.useValidUntil) return undefined
  return input.validUntil < input.validFrom ? 'Das Ende darf nicht vor dem Beginn liegen.' : undefined
}
