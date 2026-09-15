// Gemeinsame Bausteine für TransferCard (Einstellungen) und TransferButtons (Kopfzeile der
// Grunddatenseiten): Vorschautext, Download, Datei lesen, 409-Check.
import { ApiError, type MasterDataExport, type MasterDataImportResult } from '@/api/types'
import { today } from '@/lib/today'

/** 409 data-exists (ohne replace) — dieselbe Prüfung wie in TransferCard. */
export const isDataExists = (err: unknown): boolean =>
  err instanceof ApiError && err.status === 409 && err.problem.type.endsWith('data-exists')

const count = (v: number | readonly unknown[]): number => (typeof v === 'number' ? v : v.length)

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

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

/** Lädt das Dokument als `grunddaten-JJJJ-MM-TT.json` herunter. */
export function downloadMasterData(doc: MasterDataExport): void {
  const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `grunddaten-${today()}.json`
  a.click()
  URL.revokeObjectURL(url)
}

/**
 * Liest eine gewählte Datei und prüft die Format-Version. Wirft `Error` mit deutscher Meldung —
 * der Aufrufer zeigt sie als Toast.
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