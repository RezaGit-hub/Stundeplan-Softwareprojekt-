#!/usr/bin/env node
// Erzeugt die Voreinstellungen des Regelwerks (E-48) aus der Recherche in EIN Format je Zielsystem:
//
//   1. backend/src/main/resources/db/migration/V2__rule_presets.sql   (Flyway-Migration)
//   2. frontend/src/mock/rule-presets.ts                              (Attrappe im Repo)
//   3. ../web/src/mock/rule-presets.ts                                (Arbeitskopie, nur wenn vorhanden)
//
// Damit stehen im Backend und in der Attrappe garantiert dieselben Zahlen. Nach Rueckfrage R2 im
// Interview 2 (08.09.) laesst sich das Skript erneut ausfuehren — ABER: sobald V2 committet ist,
// darf die Migration nicht mehr geaendert werden. Dann statt dessen die neu erzeugte Datei als
// V<n>__rule_presets_korrektur.sql ablegen (Diff gegen V2 bilden) und nur die TS-Dateien ersetzen.
//
//   node scripts/gen-rule-presets.mjs [--input <pfad-zur-json>] [--check]
//
// --check schreibt nichts, sondern meldet nur, ob die vorhandenen Dateien dem Stand der JSON entsprechen.
//
// Quelle: scripts/bundesland-regelwerte.json (Kopie der Recherche, damit das Skript ohne Dateien
// ausserhalb des Repos laeuft). Bericht dazu in 07-Konzept/recherche/bundesland-regelwerte.md,
// Abschnitt „Abbildung auf das Regelwerk" — er erklaert, wie aus den Recherchewerten Regelzeilen werden.

import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const HIER = dirname(fileURLToPath(import.meta.url))          // 08-Build/starter/scripts
const STARTER = resolve(HIER, '..')                           // 08-Build/starter
const BUILD = resolve(STARTER, '..')                          // 08-Build
const BASE = resolve(BUILD, '..')                             // Projektwurzel

const STAND = '04.09.2026'
const QUELLE_KURZ = '07-Konzept/recherche/bundesland-regelwerte.json'
const BERICHT_KURZ = '07-Konzept/recherche/bundesland-regelwerte.md'

// Das Bundesland, mit dem system_config und damit das aktive Regelwerk vorbelegt wird (E-37).
const SEED_STATE = 'SH'
const SEED_SCHULE = 'Gymnasium Musterstadt'
const SEED_SCHULFORM = 'GYMNASIUM'
const SEED_RECHENZEIT = 60

const args = process.argv.slice(2)
const nurPruefen = args.includes('--check')
const inputArg = args.indexOf('--input')
// Quelle: die Kopie IM Repo. Die Recherche selbst liegt im Konzeptordner ausserhalb des Repos —
// wer nur das GitLab-Repo geklont hat, koennte das Skript sonst gar nicht ausfuehren (und `--check`
// in der Pipeline auch nicht). Wird die Recherche fortgeschrieben, die Datei hier ersetzen:
//   cp <konzeptordner>/07-Konzept/recherche/bundesland-regelwerte.json scripts/bundesland-regelwerte.json
const INPUT = inputArg >= 0
  ? resolve(process.cwd(), args[inputArg + 1])
  : resolve(HIER, 'bundesland-regelwerte.json')

const ZIEL_SQL = resolve(STARTER, 'backend/src/main/resources/db/migration/V2__rule_presets.sql')
// Die Attrappe im Repo ist Pflichtziel. Die Arbeitskopie daneben (08-Build/web) gibt es nur auf
// einem Rechner; sie wird beschrieben, wenn ihr Ordner existiert, und sonst uebersprungen — sonst
// scheiterte das Skript bei allen anderen im Team.
const ZIELE_TS = [
  resolve(STARTER, 'frontend/src/mock/rule-presets.ts'),
  resolve(BUILD, 'web/src/mock/rule-presets.ts'),
].filter((ziel, i) => i === 0 || existsSync(dirname(ziel)))

// ------------------------------------------------------------------ Lesen und pruefen

if (!existsSync(INPUT)) {
  console.error(`Recherchedatei nicht gefunden: ${INPUT}`)
  console.error('Erwartet wird scripts/bundesland-regelwerte.json im Repo; anderer Pfad mit --input <datei>.')
  process.exit(1)
}

/** @type {{state: string, name: string, source_note: string, rules: object[]}[]} */
const laender = JSON.parse(readFileSync(INPUT, 'utf8'))

const TYPEN = ['MAX_LESSONS_PER_DAY', 'MAX_LESSONS_PER_WEEK', 'NO_LESSONS_IN_BREAKS']
const SCOPES = ['CLASS', 'TEACHER', null]
const SEVERITIES = ['HARD', 'SOFT']

function pruefe() {
  const fehler = []
  if (laender.length !== 16) fehler.push(`16 Bundeslaender erwartet, ${laender.length} gefunden`)
  for (const l of laender) {
    if (!/^[A-Z]{2}$/.test(l.state)) fehler.push(`Kuerzel unplausibel: ${l.state}`)
    const gesehen = new Set()
    for (const r of l.rules) {
      const schluessel = `${r.type}|${r.scope}|${r.grade}`
      if (gesehen.has(schluessel)) fehler.push(`${l.state}: doppelte Zeile ${schluessel}`)
      gesehen.add(schluessel)
      if (!TYPEN.includes(r.type)) fehler.push(`${l.state}: unbekannter Typ ${r.type}`)
      if (!SCOPES.includes(r.scope)) fehler.push(`${l.state}: unbekannter scope ${r.scope}`)
      if (!SEVERITIES.includes(r.severity)) fehler.push(`${l.state}: unbekannte severity ${r.severity}`)
      if (!Number.isInteger(r.value) || r.value < 1) fehler.push(`${l.state}: value muss ganzzahlig >= 1 sein (${r.value})`)
      if (!Number.isInteger(r.weight) || r.weight < 1) fehler.push(`${l.state}: weight muss ganzzahlig >= 1 sein (${r.weight})`)
      if (r.grade !== null && !(Number.isInteger(r.grade) && r.grade >= 5 && r.grade <= 10)) fehler.push(`${l.state}: grade ausserhalb 5..10 (${r.grade})`)
      // Dieselbe Bedingung wie CHECK rule_preset_scope_required_for_max in V1.
      if (r.type !== 'NO_LESSONS_IN_BREAKS' && r.scope === null) fehler.push(`${l.state}: ${r.type} ohne scope`)
      if (r.type === 'NO_LESSONS_IN_BREAKS' && (r.scope !== null || r.grade !== null)) fehler.push(`${l.state}: NO_LESSONS_IN_BREAKS mit scope/grade`)
      // Texte landen in SQL- und TS-Kommentaren bzw. in einfach gequoteten TS-Zeichenketten.
      for (const [feld, text] of [['basis', r.basis], ['source_note', l.source_note], ['name', l.name]]) {
        if (typeof text !== 'string' || !text.trim()) fehler.push(`${l.state}: ${feld} fehlt`)
        else if (/['\\\n\r]|--/.test(text)) fehler.push(`${l.state}: ${feld} enthaelt ' \\ Zeilenumbruch oder --`)
      }
    }
  }
  if (fehler.length) {
    console.error('Die Recherchedatei passt nicht zum Regelkatalog:')
    for (const f of fehler) console.error(`  - ${f}`)
    process.exit(1)
  }
}
pruefe()

// ------------------------------------------------------------------ Sortierung

// Reihenfolge wie bisher in der Attrappe: erst die fuenf schulweiten Zeilen
// (Klasse Tag, Klasse Woche, Lehrkraft Tag, Lehrkraft Woche, Pausen), dann die Jahrgangszeilen.
const SCOPE_RANG = { CLASS: 0, TEACHER: 1, null: 2 }
const TYP_RANG = { MAX_LESSONS_PER_DAY: 0, MAX_LESSONS_PER_WEEK: 1, NO_LESSONS_IN_BREAKS: 2 }

function sortiert(rules) {
  return [...rules].sort((a, b) => {
    const ga = a.grade ?? 0, gb = b.grade ?? 0
    if (ga !== gb) return ga - gb
    const sa = SCOPE_RANG[String(a.scope)], sb = SCOPE_RANG[String(b.scope)]
    if (sa !== sb) return sa - sb
    return TYP_RANG[a.type] - TYP_RANG[b.type]
  })
}

const daten = [...laender].sort((a, b) => a.state.localeCompare(b.state)).map((l) => ({ ...l, rules: sortiert(l.rules) }))
const zeilenGesamt = daten.reduce((n, l) => n + l.rules.length, 0)
const mitJahrgang = daten.filter((l) => l.rules.some((r) => r.grade !== null)).length

// ------------------------------------------------------------------ SQL

/** Kommentartext auf mehrere `-- `-Zeilen umbrechen */
function kommentar(text, breite = 108) {
  const zeilen = []
  let zeile = ''
  for (const wort of text.split(/\s+/)) {
    if (zeile && (zeile + ' ' + wort).length > breite) { zeilen.push(zeile); zeile = wort } else zeile = zeile ? `${zeile} ${wort}` : wort
  }
  if (zeile) zeilen.push(zeile)
  return zeilen.map((z) => `-- ${z}`).join('\n')
}

const sqlScope = (s) => (s === null ? 'NULL' : `'${s}'`)
const sqlGrade = (g) => (g === null ? 'NULL' : String(g))

function sqlDatei() {
  const out = []
  out.push(`-- V2 — Voreinstellungen des Regelwerks, Systemkonfiguration, aktives Regelwerk der Schule.`)
  out.push(`--`)
  out.push(`-- Modul rules  : rule_preset (alle 16 Bundeslaender), rule (die Zeilen der eingestellten Schule)`)
  out.push(`-- Modul config : system_config`)
  out.push(`--`)
  out.push(`-- ERZEUGT, NICHT VON HAND GESCHRIEBEN. Stand ${STAND}.`)
  out.push(`--   Generator: scripts/gen-rule-presets.mjs`)
  out.push(`--   Herkunft : ${QUELLE_KURZ}`)
  out.push(`--   Bericht  : ${BERICHT_KURZ} — Quellen je Land, Abschnitt`)
  out.push(`--              "Abbildung auf das Regelwerk" erklaert die Ableitung der Zahlen.`)
  out.push(`--`)
  out.push(`-- Werte nach Recherche 04.09., Rueckfrage R2 im Interview 2 am 08.09. kann einzelne Werte`)
  out.push(`-- aendern; dann Migration V<n> ergaenzen, V2 nicht aendern, sobald committet.`)
  out.push(`--`)
  out.push(`-- "Standard" am Zeilenende heisst: kein Landesrecht gefunden, fachliche Setzung des Projekts.`)
  out.push(`-- Das betrifft durchgehend beide Tagesobergrenzen (ausser Brandenburg) und die Pausenregel.`)
  out.push(`-- Jeder Wert bleibt in der Oberflaeche einzeln aenderbar; kein Bundesland ist im Code verdrahtet.`)
  out.push(`--`)
  out.push(`-- ${zeilenGesamt} Zeilen ueber ${daten.length} Bundeslaender: je Land fuenf schulweite Zeilen (grade IS NULL),`)
  out.push(`-- bei ${mitJahrgang} Laendern zusaetzlich Jahrgangszeilen mit grade 5..10 (Kann-Kriterium E-47).`)

  for (const l of daten) {
    const breiteTyp = Math.max(...l.rules.map((r) => r.type.length)) + 2
    const breiteScope = Math.max(...l.rules.map((r) => sqlScope(r.scope).length))
    const breiteGrade = Math.max(...l.rules.map((r) => sqlGrade(r.grade).length))
    const breiteWert = Math.max(...l.rules.map((r) => String(r.value).length))
    const breiteSev = Math.max(...l.rules.map((r) => r.severity.length)) + 2

    out.push(``)
    out.push(`-- ---------------------------------------------------------------- ${l.state} — ${l.name}`)
    out.push(kommentar(l.source_note))
    out.push(`INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES`)
    l.rules.forEach((r, i) => {
      const felder = [
        `'${l.state}',`,
        `'${r.type}',`.padEnd(breiteTyp + 1),
        `${sqlScope(r.scope)},`.padEnd(breiteScope + 1),
        `${sqlGrade(r.grade)},`.padEnd(breiteGrade + 1),
        `${String(r.value).padStart(breiteWert)},`,
        `'${r.severity}',`.padEnd(breiteSev + 1),
        String(r.weight),
      ]
      const ende = i === l.rules.length - 1 ? ');' : '),'
      out.push(`    (${felder.join(' ')}${ende}  -- ${r.basis}`)
    })
  }

  const seed = daten.find((l) => l.state === SEED_STATE)
  if (!seed) { console.error(`Kein Beispielsatz fuer das vorbelegte Bundesland ${SEED_STATE}`); process.exit(1) }
  const schulweit = seed.rules.filter((r) => r.grade === null)

  out.push(``)
  out.push(`-- ---------------------------------------------------------------- Systemkonfiguration`)
  out.push(``)
  const schulformName = SEED_SCHULFORM.charAt(0) + SEED_SCHULFORM.slice(1).toLowerCase()
  out.push(kommentar(`Die eine Zeile der Systemkonfiguration. Referenzszenario laut E-37: ${schulformName} in ${seed.name}. Das ist eine Vorbelegung, keine Grenze — Schulname, Bundesland, Schulform und Rechenzeit sind aenderbar.`))
  out.push(`INSERT INTO system_config (id, school_name, state, school_type, max_solve_seconds)`)
  out.push(`VALUES (1, '${SEED_SCHULE}', '${SEED_STATE}', '${SEED_SCHULFORM}', ${SEED_RECHENZEIT});`)
  out.push(``)
  out.push(`-- ---------------------------------------------------------------- Aktives Regelwerk der Schule`)
  out.push(``)
  out.push(kommentar(`Die Voreinstellung des eingestellten Bundeslands (${SEED_STATE}), einmal kopiert. POST /api/rules/reset tut zur Laufzeit dasselbe, kopiert dabei aber alle Zeilen des Landes — schulweite und Jahrgangszeilen; fuer ${SEED_STATE} sind das genau diese ${schulweit.length} schulweiten Zeilen, fuer ein Land mit Jahrgangszeilen (z. B. BE) entsprechend mehr. Die beiden Klassenzeilen stehen so im Pflichtenheft und sind aktiv; die Lehrkraftzeilen sind vorhanden, aber ausgeschaltet, damit das Testszenario nicht ungewollt Verstoesse meldet.`))
  out.push(`INSERT INTO rule (type, scope, grade, value, severity, weight, active) VALUES`)
  const breiteTyp = Math.max(...schulweit.map((r) => r.type.length)) + 2
  const breiteScope = Math.max(...schulweit.map((r) => sqlScope(r.scope).length))
  const breiteWert = Math.max(...schulweit.map((r) => String(r.value).length))
  const breiteSev = Math.max(...schulweit.map((r) => r.severity.length)) + 2
  schulweit.forEach((r, i) => {
    const aktiv = r.scope === 'TEACHER' ? 'false' : 'true'
    const felder = [
      `'${r.type}',`.padEnd(breiteTyp + 1),
      `${sqlScope(r.scope)},`.padEnd(breiteScope + 1),
      `NULL,`,
      `${String(r.value).padStart(breiteWert)},`,
      `'${r.severity}',`.padEnd(breiteSev + 1),
      `${r.weight},`,
      aktiv,
    ]
    out.push(`    (${felder.join(' ')}${i === schulweit.length - 1 ? ');' : '),'}`)
  })
  out.push(``)
  return out.join('\n')
}

// ------------------------------------------------------------------ TypeScript

const tsScope = (s) => (s === null ? 'null' : `'${s}'`)

function tsDatei() {
  const out = []
  out.push(`// Voreinstellungen des Regelwerks je Bundesland (E-48) — ERZEUGT, NICHT VON HAND SCHREIBEN.`)
  out.push(`// Stand ${STAND} · Generator: starter/scripts/gen-rule-presets.mjs`)
  out.push(`// Herkunft: ${QUELLE_KURZ}; Quellen je Land und die Ableitung stehen im Bericht ${BERICHT_KURZ}.`)
  out.push(`// Dieselbe Quelle erzeugt die Migration V2__rule_presets.sql — Attrappe und Backend zeigen dieselben Zahlen.`)
  out.push(`// Rueckfrage R2 im Interview 2 (08.09.) kann einzelne Werte aendern; dann das Skript erneut laufen lassen.`)
  out.push(`import type { RuleScope, RuleType, Severity } from '@/api/types'`)
  out.push(``)
  out.push(`/** Eine Zeile eines Beispielsatzes. Entspricht der Tabelle rule_preset ohne id und state. */`)
  out.push(`export interface MockRulePreset {`)
  out.push(`  type: RuleType`)
  out.push(`  scope: RuleScope | null`)
  out.push(`  /** null = schulweit; gesetzt = nur dieser Jahrgang (E-47) */`)
  out.push(`  grade: number | null`)
  out.push(`  value: number`)
  out.push(`  severity: Severity`)
  out.push(`  weight: number`)
  out.push(`  /** Ein Satz zur Herkunft: amtliche Quelle oder ausdruecklich "Standard" (Setzung des Projekts) */`)
  out.push(`  basis: string`)
  out.push(`}`)
  out.push(``)
  out.push(`/** Bundesland-Kürzel → Name, aus derselben Quelle wie die Werte */`)
  out.push(`export const STATE_NAMES: Record<string, string> = {`)
  for (const l of daten) out.push(`  ${l.state}: '${l.name}',`)
  out.push(`}`)
  out.push(``)
  out.push(`/**`)
  out.push(` * Bundesland-Kürzel → Zeilen des Beispielsatzes: erst die fünf schulweiten Zeilen, dann die`)
  out.push(` * Jahrgangszeilen (nur bei den ${mitJahrgang} Ländern mit amtlichen Jahrgangswerten). ${zeilenGesamt} Zeilen gesamt.`)
  out.push(` */`)
  out.push(`export const RULE_PRESETS: Record<string, MockRulePreset[]> = {`)
  daten.forEach((l, li) => {
    out.push(`  // ${l.state} — ${l.name}: ${l.source_note}`)
    out.push(`  ${l.state}: [`)
    for (const r of l.rules) {
      out.push(`    { type: '${r.type}', scope: ${tsScope(r.scope)}, grade: ${r.grade === null ? 'null' : r.grade}, value: ${r.value}, severity: '${r.severity}', weight: ${r.weight}, basis: '${r.basis}' },`)
    }
    out.push(`  ],${li === daten.length - 1 ? '' : ''}`)
  })
  out.push(`}`)
  out.push(``)
  return out.join('\n')
}

// ------------------------------------------------------------------ Schreiben

const ausgaben = [[ZIEL_SQL, sqlDatei()], ...ZIELE_TS.map((p) => [p, tsDatei()])]
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

console.log(`${zeilenGesamt} Zeilen, ${daten.length} Bundeslaender, davon ${mitJahrgang} mit Jahrgangszeilen.`)
if (nurPruefen && abweichung) process.exit(2)
