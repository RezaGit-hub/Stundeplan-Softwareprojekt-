#!/usr/bin/env node
// Erzeugt die mitgelieferte Vorlage „Ferien und Feiertage je Bundesland" (E-51) aus der Recherche in
// EIN Format je Zielsystem — genau wie scripts/gen-rule-presets.mjs es fuer das Regelwerk tut:
//
//   1. backend/src/main/resources/db/migration/V8__holiday_presets.sql   (Flyway-Migration, Vollausbau)
//   2. ../starter/backend/.../db/migration/V5__holiday_presets.sql       (Schablone, nur wenn vorhanden)
//   3. frontend/src/mock/holiday-presets.ts                              (Attrappe im Repo)
//   4. ../web/src/mock/holiday-presets.ts                                (Arbeitskopie, nur wenn vorhanden)
//
// Damit stehen im Backend und in der Attrappe garantiert dieselben Zeitraeume. Einmal im Jahr — wenn
// die KMK das naechste Schuljahr veroeffentlicht — die beiden JSON-Dateien fortschreiben und das
// Skript erneut ausfuehren. ABER: sobald V8 committet ist, darf die Migration nicht mehr geaendert
// werden (Flyway-Pruefsumme). Dann die neu erzeugte Datei als V<n>__holiday_presets_<schuljahr>.sql
// ablegen (Diff gegen V8 bilden) und nur die TS-Dateien ersetzen.
//
//   node scripts/gen-holiday-presets.mjs [--ferien <pfad>] [--feiertage <pfad>] [--check]
//
// --check schreibt nichts, sondern meldet nur, ob die vorhandenen Dateien dem Stand der JSON entsprechen.
//
// Quelle: scripts/ferien-2026-2028.json und scripts/feiertage-2026-2028.json (Kopien der Recherche,
// damit das Skript ohne Dateien ausserhalb des Repos laeuft). Berichte dazu in
// 07-Konzept/recherche/ferien-2026-2028.md und feiertage-2026-2028.md — sie nennen Quellen, Vertrauen
// und offene Punkte je Bundesland.
//
// ------------------------------------------------------------------ Regeln der Ableitung (E-51)
//
// R1  Nur `scope` „bundesweit" und „land" werden uebernommen. Die zwoelf `teilgebiet`-Zeilen
//     (Fronleichnam in Teilen von SN/TH, Mariae Himmelfahrt in katholischen Gemeinden Bayerns,
//     Augsburger Friedensfest nur Stadt Augsburg) gelten NICHT landesweit; eine Schule im
//     betroffenen Gebiet traegt den Tag von Hand nach.
// R2  Feiertage, die auf Samstag oder Sonntag fallen, bleiben DRIN. Sie schaden nicht (an diesen
//     Tagen steht ohnehin kein Unterricht im Raster), die Vorlage bleibt so eine nachpruefbare
//     Kopie der Recherche — und eine Schule mit Samstagsunterricht verliert keinen Feiertag.
//     143 der 523 Zeilen sind solche Wochenendtage.
// R3  Schuljahr eines FEIERTAGS: 01.08.–31.07. Ein Feiertag am 03.10.2026 gehoert zu „2026/27",
//     Neujahr 2027 ebenfalls. Feiertage vor dem 01.08.2026 und ab dem 01.08.2028 liegen ausserhalb
//     der beiden belegten Schuljahre und stehen nicht in der Vorlage.
// R4  Schuljahr eines FERIENBLOCKS: das Schuljahr, in dem er beginnt — das ist genau die Zuordnung,
//     die die Recherche im Feld `schoolYear` schon fuehrt (KMK-Ferienkalender). Sommerferien, die
//     ueber den Schuljahreswechsel laufen (z. B. BW 29.07.–11.09.2027), bleiben deshalb beim
//     beginnenden Schuljahr 2026/27.
//     EINE Ausnahme: Bayerns Sommerferien 2026/27 beginnen am 02.08.2027, also zwei Tage NACH dem
//     Schuljahreswechsel. Sie bleiben trotzdem bei „2026/27", wie die KMK sie fuehrt — sonst haette
//     Bayern in diesem Schuljahr gar keine Sommerferien und in 2027/28 zwei.
// R5  Bewegliche Ferientage und einzelne unterrichtsfreie Tage nach Schulordnung (Gruendonnerstag
//     in BW, „Variabler Ferientag" in BB, Tag nach Christi Himmelfahrt in BE …) sind KEINE
//     amtlichen Ferientage und stehen nicht in der Vorlage. Sie werden von Hand nachgetragen.

import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const HIER = dirname(fileURLToPath(import.meta.url))          // 08-Build/plattform/scripts
const REPO = resolve(HIER, '..')                              // 08-Build/plattform
const BUILD = resolve(REPO, '..')                             // 08-Build
const BASE = resolve(BUILD, '..')                             // Projektwurzel

const STAND = '05.09.2026'
const QUELLE_FERIEN = '07-Konzept/recherche/ferien-2026-2028.json'
const QUELLE_FEIERTAGE = '07-Konzept/recherche/feiertage-2026-2028.json'
const BERICHT_FERIEN = '07-Konzept/recherche/ferien-2026-2028.md'
const BERICHT_FEIERTAGE = '07-Konzept/recherche/feiertage-2026-2028.md'

/** Die Schuljahre, fuer die BEIDE Recherchen vollstaendig sind. */
const SCHULJAHRE = ['2026/27', '2027/28']

const args = process.argv.slice(2)
const nurPruefen = args.includes('--check')
const pfadArg = (name, fallback) => {
  const i = args.indexOf(name)
  return i >= 0 ? resolve(process.cwd(), args[i + 1]) : resolve(HIER, fallback)
}
const INPUT_FERIEN = pfadArg('--ferien', 'ferien-2026-2028.json')
const INPUT_FEIERTAGE = pfadArg('--feiertage', 'feiertage-2026-2028.json')

const ZIEL_SQL = resolve(REPO, 'backend/src/main/resources/db/migration/V8__holiday_presets.sql')
// Die Schablone bekommt dieselben Zeilen unter ihrer eigenen Nummer: dort ist V5 die naechste freie
// (V1–V4 existieren), und die Kann-Karte T-110 legt die Tabelle `holiday` seither als V6 an. Der
// Ordner liegt nur auf einem Rechner neben diesem Repo; fehlt er, wird das Ziel uebersprungen.
const ZIEL_SQL_STARTER = resolve(BUILD, 'starter/backend/src/main/resources/db/migration/V5__holiday_presets.sql')
// Die Attrappe im Repo ist Pflichtziel; die Arbeitskopie daneben (08-Build/web) nur, wenn vorhanden.
const ZIELE_TS = [
  resolve(REPO, 'frontend/src/mock/holiday-presets.ts'),
  resolve(BUILD, 'web/src/mock/holiday-presets.ts'),
].filter((ziel, i) => i === 0 || existsSync(dirname(ziel)))

// ------------------------------------------------------------------ Lesen

for (const [name, pfad] of [['Ferien', INPUT_FERIEN], ['Feiertage', INPUT_FEIERTAGE]]) {
  if (existsSync(pfad)) continue
  console.error(`Recherchedatei ${name} nicht gefunden: ${pfad}`)
  console.error('Erwartet werden scripts/ferien-2026-2028.json und scripts/feiertage-2026-2028.json im Repo;')
  console.error('andere Pfade mit --ferien <datei> bzw. --feiertage <datei>.')
  process.exit(1)
}

/** @type {{state: string, name: string, schoolYear: string, kind: string, label: string, from: string, to: string, source: string, note?: string}[]} */
const ferien = JSON.parse(readFileSync(INPUT_FERIEN, 'utf8'))
/** @type {{state: string, name: string, year: number, date: string, label: string, scope: string, source: string, note?: string}[]} */
const feiertage = JSON.parse(readFileSync(INPUT_FEIERTAGE, 'utf8'))

// ------------------------------------------------------------------ Bundeslaender

/** Umlaute wie in der Ferien-Recherche schreiben — dort heisst es „Baden-Wuerttemberg", in der
 *  Feiertags-Recherche „Baden-Württemberg". Beide Dateien treffen sich ueber diese Normalform. */
const ohneUmlaut = (text) => text
  .replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue')
  .replace(/Ä/g, 'Ae').replace(/Ö/g, 'Oe').replace(/Ü/g, 'Ue').replace(/ß/g, 'ss')

/** Landesname (ASCII) → Kuerzel; die Ferien-Recherche fuehrt beides. */
const KUERZEL = {}
/** Kuerzel → Landesname wie in der Recherche (ASCII, passt zu STATE_NAMES in rule-presets.ts). */
const NAME = {}
for (const zeile of ferien) {
  KUERZEL[ohneUmlaut(zeile.name)] = zeile.state
  NAME[zeile.state] = zeile.name
}
const LAENDER = Object.keys(NAME).sort()

// ------------------------------------------------------------------ Ableiten

const ISO = /^\d{4}-\d{2}-\d{2}$/

/** Schuljahr eines Kalendertags nach R3: 01.08.–31.07. */
function schuljahrVon(datum) {
  const [jahr, monat] = datum.split('-').map(Number)
  const start = monat >= 8 ? jahr : jahr - 1
  return `${start}/${String(start + 1).slice(2)}`
}

/**
 * Alle Zeilen der Vorlage: Kuerzel → Schuljahr → Zeitraeume, nach Beginn und Bezeichnung sortiert.
 * @type {Record<string, Record<string, {kind: string, label: string, from: string, to: string, source: string}[]>>}
 */
const vorlage = {}
for (const state of LAENDER) {
  vorlage[state] = {}
  for (const schuljahr of SCHULJAHRE) {
    const zeilen = []
    for (const f of ferien) {
      if (f.state !== state || f.schoolYear !== schuljahr) continue          // R4
      zeilen.push({ kind: 'VACATION', label: f.label, from: f.from, to: f.to, source: f.source })
    }
    for (const t of feiertage) {
      if (KUERZEL[ohneUmlaut(t.state)] !== state) continue
      if (t.scope === 'teilgebiet') continue                                 // R1
      if (schuljahrVon(t.date) !== schuljahr) continue                       // R3
      zeilen.push({ kind: 'PUBLIC_HOLIDAY', label: t.label, from: t.date, to: t.date, source: t.source })
    }
    zeilen.sort((a, b) => a.from.localeCompare(b.from) || a.to.localeCompare(b.to) || a.label.localeCompare(b.label))
    vorlage[state][schuljahr] = zeilen
  }
}

// ------------------------------------------------------------------ Pruefen

function pruefe() {
  const fehler = []
  if (LAENDER.length !== 16) fehler.push(`16 Bundeslaender erwartet, ${LAENDER.length} gefunden`)
  const teilgebiet = feiertage.filter((t) => t.scope === 'teilgebiet').length
  if (!['bundesweit', 'land', 'teilgebiet'].includes(feiertage[0]?.scope)) fehler.push('unbekannter scope in der Feiertags-Recherche')
  for (const t of feiertage) {
    if (!['bundesweit', 'land', 'teilgebiet'].includes(t.scope)) fehler.push(`unbekannter scope ${t.scope} (${t.state} ${t.label})`)
    if (KUERZEL[ohneUmlaut(t.state)] === undefined) fehler.push(`unbekanntes Bundesland in der Feiertags-Recherche: ${t.state}`)
  }
  for (const state of LAENDER) {
    for (const schuljahr of SCHULJAHRE) {
      const zeilen = vorlage[state][schuljahr]
      if (zeilen.length === 0) { fehler.push(`${state} ${schuljahr}: keine Zeile`); continue }
      const ferienZeilen = zeilen.filter((z) => z.kind === 'VACATION').length
      if (ferienZeilen < 3) fehler.push(`${state} ${schuljahr}: nur ${ferienZeilen} Ferienbloecke — Recherche unvollstaendig?`)
      const gesehen = new Set()
      for (const z of zeilen) {
        // Genau der Schluessel, ueber den POST /api/holidays/preset Duplikate ueberspringt.
        const schluessel = `${z.from}|${z.to}|${z.label}`
        if (gesehen.has(schluessel)) fehler.push(`${state} ${schuljahr}: doppelte Zeile ${schluessel}`)
        gesehen.add(schluessel)
        if (!ISO.test(z.from) || !ISO.test(z.to)) fehler.push(`${state} ${schuljahr}: Datum unplausibel ${z.from}..${z.to}`)
        if (z.to < z.from) fehler.push(`${state} ${schuljahr}: ${z.label} endet vor dem Beginn`)
        if (z.label.length > 120) fehler.push(`${state} ${schuljahr}: Bezeichnung laenger als 120 Zeichen (${z.label})`)
        if (z.source.length > 300) fehler.push(`${state} ${schuljahr}: Quelle laenger als 300 Zeichen (${z.label})`)
        // Texte landen in SQL- und TS-Kommentaren bzw. in einfach gequoteten Zeichenketten.
        for (const [feld, text] of [['label', z.label], ['source', z.source]]) {
          if (typeof text !== 'string' || !text.trim()) fehler.push(`${state} ${schuljahr}: ${feld} fehlt`)
          else if (/['\\\n\r]|--$/.test(text)) fehler.push(`${state} ${schuljahr}: ${feld} enthaelt ' \\ oder einen Zeilenumbruch`)
        }
      }
    }
  }
  if (fehler.length) {
    console.error('Die Recherchedateien passen nicht zur Vorlage:')
    for (const f of fehler) console.error(`  - ${f}`)
    process.exit(1)
  }
  return { teilgebiet }
}
const { teilgebiet } = pruefe()

const zeilenGesamt = LAENDER.reduce((n, s) => n + SCHULJAHRE.reduce((m, j) => m + vorlage[s][j].length, 0), 0)
const ferienGesamt = LAENDER.reduce((n, s) => n + SCHULJAHRE.reduce((m, j) => m + vorlage[s][j].filter((z) => z.kind === 'VACATION').length, 0), 0)
const feiertagGesamt = zeilenGesamt - ferienGesamt
const istWochenende = (datum) => [0, 6].includes(new Date(`${datum}T12:00:00Z`).getUTCDay())
const wochenendeGesamt = LAENDER.reduce((n, s) => n + SCHULJAHRE.reduce((m, j) =>
  m + vorlage[s][j].filter((z) => z.kind === 'PUBLIC_HOLIDAY' && istWochenende(z.from)).length, 0), 0)

// ------------------------------------------------------------------ gemeinsame Textbausteine

/** Kommentartext auf mehrere Zeilen umbrechen */
function umbrechen(text, praefix, breite = 108) {
  const zeilen = []
  let zeile = ''
  for (const wort of text.split(/\s+/)) {
    if (zeile && (zeile + ' ' + wort).length > breite) { zeilen.push(zeile); zeile = wort } else zeile = zeile ? `${zeile} ${wort}` : wort
  }
  if (zeile) zeilen.push(zeile)
  return zeilen.map((z) => `${praefix}${z}`).join('\n')
}

const REGELN = [
  `R1 Nur landesweit geltende Feiertage. Die ${teilgebiet} Zeilen mit scope "teilgebiet" (Fronleichnam in Teilen von Sachsen und Thueringen, Mariae Himmelfahrt in katholischen Gemeinden Bayerns, Augsburger Friedensfest nur Stadt Augsburg) stehen NICHT in der Vorlage — eine Schule im betroffenen Gebiet traegt den Tag von Hand nach.`,
  `R2 Feiertage an einem Samstag oder Sonntag bleiben drin (${wochenendeGesamt} der ${zeilenGesamt} Zeilen). Sie schaden nicht und halten die Vorlage nachpruefbar gleich der Recherche.`,
  `R3 Schuljahr eines Feiertags: 01.08. bis 31.07.`,
  `R4 Schuljahr eines Ferienblocks: das Schuljahr, in dem er beginnt (Feld schoolYear der Recherche, KMK-Ferienkalender). Ausnahme Bayern: die Sommerferien 2026/27 beginnen am 02.08.2027 und bleiben trotzdem bei 2026/27 — sonst haette Bayern dort keine Sommerferien.`,
  `R5 Bewegliche Ferientage und einzelne unterrichtsfreie Tage nach Schulordnung sind keine amtlichen Ferientage und stehen nicht in der Vorlage; sie werden von Hand nachgetragen.`,
]

/** Die eine Tabellendefinition, wortgleich in beiden Migrationen. */
function tabelle(einrueckung = '') {
  return [
    `CREATE TABLE holiday_preset (`,
    `    id          bigserial PRIMARY KEY,`,
    `    state       varchar(2)   NOT NULL,`,
    `    school_year varchar(7)   NOT NULL,`,
    `    kind        varchar(20)  NOT NULL CHECK (kind IN ('VACATION', 'PUBLIC_HOLIDAY')),`,
    `    label       varchar(120) NOT NULL,`,
    `    date_from   date         NOT NULL,`,
    `    date_to     date         NOT NULL,`,
    `    source      varchar(300) NOT NULL,`,
    `    -- Wie holiday_period in der Tabelle holiday: ein Zeitraum endet nie vor seinem Anfang.`,
    `    CONSTRAINT holiday_preset_period CHECK (date_to >= date_from),`,
    `    -- Genau der Schluessel, ueber den POST /api/holidays/preset Duplikate ueberspringt.`,
    `    CONSTRAINT holiday_preset_unique UNIQUE (state, school_year, date_from, date_to, label)`,
    `);`,
    ``,
    `-- Eine Abfrage je Ladevorgang und eine je Auswahlliste der Schuljahre.`,
    `CREATE INDEX holiday_preset_state_year ON holiday_preset (state, school_year, date_from);`,
  ].map((z) => (z ? einrueckung + z : z)).join('\n')
}

// ------------------------------------------------------------------ SQL

function sqlDatei({ version, dateiname, mitTabelle, kopf }) {
  const out = []
  out.push(`-- ${version} — Vorlage "Ferien und Feiertage je Bundesland" (E-51).`)
  out.push(`--`)
  for (const zeile of kopf) out.push(zeile ? `-- ${zeile}` : '--')
  out.push(`--`)
  out.push(`-- ERZEUGT, NICHT VON HAND GESCHRIEBEN. Stand ${STAND}.`)
  out.push(`--   Generator: scripts/gen-holiday-presets.mjs`)
  out.push(`--   Herkunft : ${QUELLE_FERIEN} (${ferien.length} Objekte)`)
  out.push(`--              ${QUELLE_FEIERTAGE} (${feiertage.length} Objekte)`)
  out.push(`--   Berichte : ${BERICHT_FERIEN}, ${BERICHT_FEIERTAGE} —`)
  out.push(`--              Quellen, Vertrauen und offene Punkte je Bundesland.`)
  out.push(`--`)
  out.push(`-- Die Vorlage ist DATEN, kein Dienst: nichts wird zur Laufzeit aus dem Internet geholt. Sie greift,`)
  out.push(`-- sobald in der Systemkonfiguration ein Bundesland steht — POST /api/holidays/preset kopiert die`)
  out.push(`-- Zeilen des Schuljahres nach holiday. Jeder Eintrag bleibt danach einzeln aenderbar und loeschbar.`)
  out.push(`--`)
  out.push(`-- Regeln der Ableitung:`)
  for (const regel of REGELN) out.push(umbrechen(regel, '--   '))
  out.push(`--`)
  out.push(`-- ${zeilenGesamt} Zeilen: ${ferienGesamt} Ferienbloecke und ${feiertagGesamt} Feiertage ueber ${LAENDER.length} Bundeslaender`)
  out.push(`-- und die Schuljahre ${SCHULJAHRE.join(' und ')}. Fuer ein weiteres Schuljahr die beiden JSON-Dateien`)
  out.push(`-- fortschreiben und das Skript erneut laufen lassen; diese Migration bleibt dann unveraendert.`)

  if (mitTabelle) {
    out.push(``)
    out.push(tabelle())
  }

  for (const state of LAENDER) {
    for (const schuljahr of SCHULJAHRE) {
      const zeilen = vorlage[state][schuljahr]
      const breiteKind = Math.max(...zeilen.map((z) => z.kind.length)) + 2
      const breiteLabel = Math.max(...zeilen.map((z) => z.label.length)) + 2
      out.push(``)
      out.push(`-- ---------------------------------------------------------------- ${state} ${schuljahr} — ${NAME[state]}`)
      out.push(`-- ${zeilen.filter((z) => z.kind === 'VACATION').length} Ferienbloecke, ${zeilen.filter((z) => z.kind === 'PUBLIC_HOLIDAY').length} Feiertage`)
      out.push(`INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES`)
      zeilen.forEach((z, i) => {
        const felder = [
          `'${state}',`,
          `'${schuljahr}',`,
          `'${z.kind}',`.padEnd(breiteKind + 1),
          `'${z.label}',`.padEnd(breiteLabel + 1),
          `'${z.from}',`,
          `'${z.to}',`,
          `'${z.source}'`,
        ]
        out.push(`    (${felder.join(' ')}${i === zeilen.length - 1 ? ');' : '),'}`)
      })
    }
  }
  out.push(``)
  return out.join('\n')
}

// ------------------------------------------------------------------ TypeScript

function tsDatei() {
  const out = []
  out.push(`// Vorlage "Ferien und Feiertage je Bundesland" (E-51) — ERZEUGT, NICHT VON HAND SCHREIBEN.`)
  out.push(`// Stand ${STAND} · Generator: plattform/scripts/gen-holiday-presets.mjs`)
  out.push(`// Herkunft: ${QUELLE_FERIEN} und ${QUELLE_FEIERTAGE};`)
  out.push(`// Quellen, Vertrauen und offene Punkte je Bundesland stehen in den Berichten daneben (.md).`)
  out.push(`// Dieselbe Quelle erzeugt die Migration V8__holiday_presets.sql — Attrappe und Backend zeigen dieselben Zeitraeume.`)
  out.push(`//`)
  out.push(`// Regeln der Ableitung:`)
  for (const regel of REGELN) out.push(umbrechen(regel, '//   '))
  out.push(`import type { HolidayKind } from '@/api/types'`)
  out.push(``)
  out.push(`/** Eine Zeile der Vorlage. Entspricht der Tabelle holiday_preset ohne id, state und school_year. */`)
  out.push(`export interface MockHolidayPreset {`)
  out.push(`  kind: HolidayKind`)
  out.push(`  /** Bezeichnung, die beim Uebernehmen zu holiday.name wird */`)
  out.push(`  label: string`)
  out.push(`  from: string`)
  out.push(`  to: string`)
  out.push(`  /** Amtliche Fundstelle — steht nur in der Vorlage, nicht im uebernommenen Zeitraum */`)
  out.push(`  source: string`)
  out.push(`}`)
  out.push(``)
  out.push(`/** Die Schuljahre, fuer die die Vorlage Zeilen kennt (aelteste zuerst). */`)
  out.push(`export const HOLIDAY_PRESET_YEARS = [${SCHULJAHRE.map((j) => `'${j}'`).join(', ')}] as const`)
  out.push(``)
  out.push(`/**`)
  out.push(` * Bundesland-Kürzel → Schuljahr → Zeitraeume, nach Beginn sortiert.`)
  out.push(` * ${zeilenGesamt} Zeilen: ${ferienGesamt} Ferienblöcke und ${feiertagGesamt} Feiertage über ${LAENDER.length} Bundesländer.`)
  out.push(` */`)
  out.push(`export const HOLIDAY_PRESETS: Record<string, Record<string, MockHolidayPreset[]>> = {`)
  for (const state of LAENDER) {
    out.push(`  // ${state} — ${NAME[state]}`)
    out.push(`  ${state}: {`)
    for (const schuljahr of SCHULJAHRE) {
      const zeilen = vorlage[state][schuljahr]
      out.push(`    '${schuljahr}': [`)
      for (const z of zeilen) {
        out.push(`      { kind: '${z.kind}', label: '${z.label}', from: '${z.from}', to: '${z.to}', source: '${z.source}' },`)
      }
      out.push(`    ],`)
    }
    out.push(`  },`)
  }
  out.push(`}`)
  out.push(``)
  return out.join('\n')
}

// ------------------------------------------------------------------ Schreiben

const ausgaben = [
  [ZIEL_SQL, sqlDatei({
    version: 'V8',
    dateiname: 'V8__holiday_presets.sql',
    mitTabelle: true,
    kopf: [
      'Modul substitution : holiday_preset (16 Bundeslaender x zwei Schuljahre)',
      '',
      'V1-V7 bleiben unveraendert (Flyway-Pruefsumme). Die Tabelle wird hier angelegt UND gefuellt:',
      'im Vollausbau steht das Schema der ersten sieben Migrationen fest.',
    ],
  })],
  ...(existsSync(dirname(ZIEL_SQL_STARTER)) ? [[ZIEL_SQL_STARTER, sqlDatei({
    version: 'V5',
    dateiname: 'V5__holiday_presets.sql',
    mitTabelle: false,
    kopf: [
      'Modul substitution : holiday_preset (16 Bundeslaender x zwei Schuljahre)',
      '',
      'Die TABELLE steht in V1 (Schema); hier stehen nur die Zeilen — wie bei V2__rule_presets.sql.',
      'V1-V4 bleiben unveraendert. Die Kann-Karte T-110 legt die Tabelle holiday als V6 an; die',
      'Vorlage braucht sie nicht, sie ist reine Datenhaltung.',
    ],
  })]] : []),
  ...ZIELE_TS.map((p) => [p, tsDatei()]),
]

let abweichung = false
for (const [pfad, inhalt] of ausgaben) {
  const kurz = pfad.startsWith(BASE) ? pfad.slice(BASE.length + 1) : pfad
  const alt = existsSync(pfad) ? readFileSync(pfad, 'utf8') : null
  if (alt === inhalt) { console.log(`unveraendert  ${kurz}`); continue }
  abweichung = true
  if (nurPruefen) { console.log(`WEICHT AB     ${kurz}`); continue }
  writeFileSync(pfad, inhalt, 'utf8')
  console.log(`${alt === null ? 'neu          ' : 'geschrieben  '} ${kurz}`)
}

console.log(`${zeilenGesamt} Zeilen (${ferienGesamt} Ferien, ${feiertagGesamt} Feiertage), ${LAENDER.length} Bundeslaender, ${SCHULJAHRE.length} Schuljahre.`)
for (const state of LAENDER) {
  console.log(`  ${state}  ${SCHULJAHRE.map((j) => `${j}: ${String(vorlage[state][j].length).padStart(2)}`).join('   ')}`)
}
if (nurPruefen && abweichung) process.exit(2)
