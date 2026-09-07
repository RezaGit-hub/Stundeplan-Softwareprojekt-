// Eine Definition von „offen" (Befund FRONTEND-9, 05.09.).
//
// Offen heisst: fehlende Wochenstunden je Klasse und Fach (`openLessons` aus GET /api/draft) — NICHT die
// Stunden ohne Zeitfenster. Beide Zahlen fallen auseinander, sobald eine Klasse mehr oder weniger Stunden
// im Entwurf hat, als ihr Fachplan vorsieht; vorher standen in der Entwurfsleiste und im Banner deshalb
// zwei verschiedene Zahlen unter demselben Wort.
import type { OpenLessonDto } from '@/api/types'

/** Summe der fehlenden Wochenstunden eines Entwurfs. */
export const openCount = (rows: OpenLessonDto[]): number => rows.reduce((sum, row) => sum + row.missing, 0)
