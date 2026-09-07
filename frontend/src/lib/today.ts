// Ein "Heute" für die ganze App. Seiten rufen nie direkt new Date() auf —
// so lassen sich Screenshots und der Demo-Rundgang auf ein festes Datum stellen (VITE_MOCK_TODAY).
import { format } from 'date-fns'

const FIXED = import.meta.env.VITE_MOCK_TODAY

function fixedDate(): string | null {
  return FIXED && /^\d{4}-\d{2}-\d{2}$/.test(FIXED) ? FIXED : null
}

/** Heutiges Datum als yyyy-MM-dd */
export function today(): string {
  return fixedDate() ?? format(new Date(), 'yyyy-MM-dd')
}

/** Jetzt als Date (Datum ggf. fest, Uhrzeit immer echt) */
export function now(): Date {
  const fixed = fixedDate()
  if (!fixed) return new Date()
  return new Date(`${fixed}T${format(new Date(), 'HH:mm:ss')}`)
}

/** Jetzt als ISO-Zeitstempel (für createdAt, timestamp) */
export function nowIso(): string {
  return now().toISOString()
}
