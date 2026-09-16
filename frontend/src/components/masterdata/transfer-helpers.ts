// Gemeinsame Bausteine für TransferCard (Einstellungen) und TransferButtons (Kopfzeile der
// Grunddatenseiten): Vorschautext, Download, Datei lesen, 409-Check.
import { ApiError } from '@/api/types'
import type { MasterDataSection, MasterDataExport, MasterDataImportResult, SectionImportResult, TransferScope } from '@/api/types'
import { today } from '@/lib/today'

// ------------------------------------------------------------------ Beschriftung je Bereich

/** Dateinamen und Beschriftungen je Bereich: „lehrkraefte-2026-09-15.json". */
export const SCOPE_LABEL: Record<TransferScope, { singular: string; plural: string; file: string }> = {
  subjects:  { singular: 'Fach',        plural: 'Fächer',      file: 'faecher' },
  teachers:  { singular: 'Lehrkraft',   plural: 'Lehrkräfte',  file: 'lehrkraefte' },
  rooms:     { singular: 'Raum',        plural: 'Räume',       file: 'raeume' },
  classes:   { singular: 'Klasse',      plural: 'Klassen',     file: 'klassen' },
  timeslots: { singular: 'Zeitfenster', plural: 'Zeitfenster', file: 'stundenraster' },
  rules:     { singular: 'Regel',       plural: 'Regeln',      file: 'regelwerk' },
  holidays:  { singular: 'Zeitraum',    plural: 'Zeiträume',   file: 'ferien' },
}

// ------------------------------------------------------------------ kleine Helfer

const plural = (n: number, one: string, many: string): string => `${n} ${n === 1 ? one : many}`

/** Zahl oder Liste — beides trägt dieselbe Angabe. */
const count = (v: number | readonly unknown[]): number => (typeof v === 'number' ? v : v.length)

// ------------------------------------------------------------------ Vorschautexte

/**
 * „12 Fächer, 24 Lehrkräfte, 18 Räume, 12 Klassen, 35 Zeitfenster, 5 Regeln, 1 Zeitraum"
 * Nimmt Export (Listen) **und** Import-Ergebnis (Zahlen) — beide tragen dieselben sieben Felder.
 */
export function preview(doc: MasterDataExport | MasterDataImportResult): string {
  return [
    plural(count(doc.subjects), 'Fach', 'Fächer'),
    plural(count(doc.teachers), 'Lehrkraft', 'Lehrkräfte'),
    plural(count(doc.rooms), 'Raum', 'Räume'),
    plural(count(doc.classes), 'Klasse', 'Klassen'),
    `${count(doc.timeslots)} Zeitfenster`,
    plural(count(doc.rules), 'Regel', 'Regeln'),
    plural(count(doc.holidays), 'Zeitraum', 'Zeiträume'),
  ].join(', ')
}

/** Bereichsvorschau: „3 Lehrkräfte" — oder bei timeslots/rules die reine Zahl. */
export function previewSection(doc: MasterDataSection): string {
  const scope = doc.scope
  const n = (doc[scope] ?? []).length
  const { singular, plural: many } = SCOPE_LABEL[scope]
  return plural(n, singular, many)
}

/** „3 neu, 2 aktualisiert" bzw. „3 neu". */
export function previewSectionResult(res: MasterDataImportResult | SectionImportResult): string {
  if ('scope' in res) {
    const teile = [`${res.created} neu`]
    if (res.updated > 0) teile.push(`${res.updated} aktualisiert`)
    return teile.join(', ')
  }
  return preview(res)
}

// ------------------------------------------------------------------ Downloads

/** Lädt das vollständige Dokument als `grunddaten-JJJJ-MM-TT.json` herunter. */
export function downloadMasterData(doc: MasterDataExport): void {
  const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `grunddaten-${today()}.json`
  a.click()
  URL.revokeObjectURL(url)
}

/** Lädt eine Bereichsdatei als `<bereich>-JJJJ-MM-TT.json` herunter. */
export function downloadSection(doc: MasterDataSection): void {
  const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${SCOPE_LABEL[doc.scope].file}-${today()}.json`
  a.click()
  URL.revokeObjectURL(url)
}

// ------------------------------------------------------------------ Datei lesen

/**
 * Liest ein vollständiges Dokument (Version 1) und prüft die Format-Version. Wirft `Error` mit
 * deutscher Meldung — der Aufrufer zeigt sie als Toast.
 */
export async function readMasterDataFile(file: File): Promise<MasterDataExport> {
  let doc: MasterDataExport
  try {
    doc = JSON.parse(await file.text()) as MasterDataExport
  } catch {
    throw new Error('Die gewählte Datei ist kein gültiges JSON.')
  }
  if (doc?.version !== '1') {
    throw new Error(`Format-Version ${doc?.version ?? '?'} wird nicht unterstützt (erwartet 1).`)
  }
  return doc
}

/** Liest eine Bereichsdatei (Version 2); prüft Version und erwarteten Bereich. */
export async function readSectionFile(file: File, erwartet: TransferScope): Promise<MasterDataSection> {
  let doc: MasterDataSection
  try {
    doc = JSON.parse(await file.text()) as MasterDataSection
  } catch {
    throw new Error('Die gewählte Datei ist kein gültiges JSON.')
  }
  if (doc?.version !== '2') {
    throw new Error(`Format-Version ${doc?.version ?? '?'} wird hier nicht unterstützt (erwartet 2). Für die vollständige Sicherung die Einstellungen verwenden.`)
  }
  if (doc.scope !== erwartet) {
    throw new Error(`Die Datei enthält „${SCOPE_LABEL[doc.scope]?.plural ?? doc.scope}", nicht „${SCOPE_LABEL[erwartet].plural}".`)
  }
  return doc
}

// ------------------------------------------------------------------ 409-Check

/** 409 data-exists (ohne replace) — dieselbe Prüfung wie in TransferCard. */
export const isDataExists = (err: unknown): boolean =>
  err instanceof ApiError && err.status === 409 && err.problem.type.endsWith('data-exists')