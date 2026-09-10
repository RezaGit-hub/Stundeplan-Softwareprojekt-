// Eine Tabelle: kind → Beschriftung, Badge, Rahmenart, Klassen (ui.md). Web-Raster (W3) und Telefon-Vorschau (W5)
// beschriften damit identisch. Farbe ist nie der einzige Träger: jede Art hat Badge und/oder Text.
import type { EntryDto, EntryKind } from '@/api/types'

export interface EntryKindUi {
  /** Kurztext unter/neben der Kachel; leer bei REGULAR */
  label: string
  /** Badge oben rechts (V / ! / ✕ / +) */
  badge: string | null
  frame: 'none' | 'solid' | 'dashed'
  /** Tailwind-Klassen für die Kachel (Rahmen, Fläche, Textfarbe) */
  className: string
}

export const ENTRY_KIND: Record<EntryKind, EntryKindUi> = {
  REGULAR: { label: '', badge: null, frame: 'none', className: '' },
  NEEDS_SUBSTITUTION: { label: 'noch nicht vertreten', badge: '!', frame: 'dashed', className: 'border-2 border-dashed border-substitution' },
  SUBSTITUTED: { label: 'Vertretung', badge: 'V', frame: 'solid', className: 'border-2 border-substitution' },
  GIVEN_AWAY: { label: 'abgegeben', badge: null, frame: 'none', className: 'opacity-40 italic text-given-away' },
  MOVED: { label: 'verlegt', badge: 'V', frame: 'solid', className: 'border-2 border-substitution' },
  CANCELLED: { label: 'entfällt', badge: '✕', frame: 'none', className: 'bg-surface-sunken text-cancelled' },
  ADDED: { label: 'Zusatzstunde', badge: '+', frame: 'solid', className: 'border-2 border-substitution' },
}

/** Textlabel mit Kontext: "abgegeben an SCZ", "Raumwechsel", "verlegt → Di 3." */
export function entryLabel(entry: EntryDto): string {
  switch (entry.kind) {
    case 'GIVEN_AWAY':
      return entry.teacherShort ? `abgegeben an ${entry.teacherShort}` : 'abgegeben'
    case 'SUBSTITUTED':
      return entry.original?.teacherShort ? 'Vertretung' : 'Raumwechsel'
    case 'CANCELLED': {
      const moved = entry.reasons.find((r) => r.startsWith('verlegt'))
      return moved ?? 'entfällt'
    }
    default:
      return ENTRY_KIND[entry.kind].label
  }
}

/** Legende aus ui.md, in dieser Reihenfolge */
export const LEGEND: { kind: EntryKind | 'CONFLICT' | 'WARNING' | 'PINNED' | 'DRAFT'; text: string }[] = [
  { kind: 'SUBSTITUTED', text: 'Orange durchgezogen + V = Vertretung' },
  { kind: 'NEEDS_SUBSTITUTION', text: 'Orange gestrichelt + ! = noch nicht vertreten' },
  { kind: 'CANCELLED', text: 'Grau durchgestrichen + ✕ = entfällt' },
  { kind: 'CONFLICT', text: 'Rot + ⚠ = Konflikt' },
  { kind: 'WARNING', text: 'Rot gestrichelt + ⚠ = Warnung' },
  { kind: 'PINNED', text: 'Pin = festgelegt' },
  { kind: 'DRAFT', text: 'Amber-Banner = Entwurf' },
]
