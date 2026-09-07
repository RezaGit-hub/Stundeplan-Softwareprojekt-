// Voreinstellungen des Regelwerks je Bundesland (E-48) — ERZEUGT, NICHT VON HAND SCHREIBEN.
// Stand 04.09.2026 · Generator: starter/scripts/gen-rule-presets.mjs
// Herkunft: 07-Konzept/recherche/bundesland-regelwerte.json; Quellen je Land und die Ableitung stehen im Bericht 07-Konzept/recherche/bundesland-regelwerte.md.
// Dieselbe Quelle erzeugt die Migration V2__rule_presets.sql — Attrappe und Backend zeigen dieselben Zahlen.
// Rueckfrage R2 im Interview 2 (08.09.) kann einzelne Werte aendern; dann das Skript erneut laufen lassen.
import type { RuleScope, RuleType, Severity } from '@/api/types'

/** Eine Zeile eines Beispielsatzes. Entspricht der Tabelle rule_preset ohne id und state. */
export interface MockRulePreset {
  type: RuleType
  scope: RuleScope | null
  /** null = schulweit; gesetzt = nur dieser Jahrgang (E-47) */
  grade: number | null
  value: number
  severity: Severity
  weight: number
  /** Ein Satz zur Herkunft: amtliche Quelle oder ausdruecklich "Standard" (Setzung des Projekts) */
  basis: string
}

/** Bundesland-Kürzel → Name, aus derselben Quelle wie die Werte */
export const STATE_NAMES: Record<string, string> = {
  BB: 'Brandenburg',
  BE: 'Berlin',
  BW: 'Baden-Württemberg',
  BY: 'Bayern',
  HB: 'Bremen',
  HE: 'Hessen',
  HH: 'Hamburg',
  MV: 'Mecklenburg-Vorpommern',
  NI: 'Niedersachsen',
  NW: 'Nordrhein-Westfalen',
  RP: 'Rheinland-Pfalz',
  SH: 'Schleswig-Holstein',
  SL: 'Saarland',
  SN: 'Sachsen',
  ST: 'Sachsen-Anhalt',
  TH: 'Thüringen',
}

/**
 * Bundesland-Kürzel → Zeilen des Beispielsatzes: erst die fünf schulweiten Zeilen, dann die
 * Jahrgangszeilen (nur bei den 10 Ländern mit amtlichen Jahrgangswerten). 138 Zeilen gesamt.
 */
export const RULE_PRESETS: Record<string, MockRulePreset[]> = {
  // BB — Brandenburg: G8 mit Gymnasium ab Jahrgangsstufe 7 (Sek I = 7–10). Kontingentstundentafel nur für Blöcke 7/8 und 9/10 (§ 11 Abs. 3 Sek I-V), keine Einzeljahrgangswerte; stattdessen greift die ausdrückliche Wochenobergrenze aus § 12 Abs. 6 Sek I-V. Tagesobergrenze für Schüler landesrechtlich geregelt.
  BB: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 8, severity: 'SOFT', weight: 1, basis: 'VV-Schulbetrieb Brandenburg, Nr. 2 Abs. 4 Nr. 3: in den Sekundarstufen I und II acht Unterrichtsstunden pro Tag als Regel-Obergrenze.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 36, severity: 'SOFT', weight: 1, basis: '§ 12 Abs. 6 Sekundarstufe I-Verordnung (Sek I-V) Brandenburg: Pflicht- und Wahlpflichtunterricht in der Regel höchstens 36 Wochenstunden je Schülerin/Schüler.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 26, severity: 'SOFT', weight: 1, basis: 'Anlage zu § 16 Abs. 2 AZV Brandenburg i.V.m. KMK-Übersicht 2025/2026: 25 Wochenstunden, Erhöhung um eine Stunde zum 01.02.2026, also 26 zum Stichtag 04.09.2026.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
  ],
  // BE — Berlin: Reguläres Gymnasium ab Jahrgangsstufe 7 (6-jährige Grundschule), Abitur nach 12 Schuljahren. Feste Wochenstundentafel je Fach und Jahrgang (Sek I-VO Anlage 2, PDF-Stand 22.09.2025). Für Jahrgänge 5/6 existiert am regulären Gymnasium kein Wert; grundständige Gymnasien (Anlage 3) nicht ausgewertet.
  BE: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 34, severity: 'SOFT', weight: 1, basis: 'Höchster Jahrgangswert der Stundentafel 5–10 (34 Wochenstunden in Jahrgang 9/10); Reguläres Gymnasium ab Jahrgangsstufe 7 (6-jährige Grundschule), Abitur nach 12 Schuljahren.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 26, severity: 'SOFT', weight: 1, basis: 'KMK, Pflichtstunden der Lehrkräfte 2025/2026 (Stand Januar 2026): Berlin Gymnasium 26 Wochenstunden; Rechtsgrundlage AZVOLehr Berlin, Volltext nicht eingesehen.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 7, value: 33, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 7: 33 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 8, value: 33, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 8: 33 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 9, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 9: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 10, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 10: 34 Wochenstunden.' },
  ],
  // BW — Baden-Württemberg: G8/G9-Mischbetrieb 2026/27 (G9 Normalform seit 2025/26, aufwachsend). Kontingentstundentafel nach Stundentafelverordnung Gymnasien (Gym5bis11StTafelV BW, Anlage 1) – Zahlenwerk nicht auslesbar, keine landeseinheitliche Jahrgangsverteilung. Lehrkraft-Wert über KMK-Sekundärquelle.
  BW: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 34, severity: 'SOFT', weight: 1, basis: 'Kein belegter Landeswert: Kontingentstundentafel (Gym5bis11StTafelV BW, Anlage 1, gültig ab 01.08.2020) weist nur Fach-Gesamtstunden 5–10 aus, die Jahrgangsverteilung legt jede Schule selbst fest; Presseangaben zum neuen G9 (28–33) sind unbelegt. Ersatzwert 34 aus dem Median der belegten Länder, in der Oberfläche anzupassen.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 25, severity: 'SOFT', weight: 1, basis: 'KMK, Übersicht über die Pflichtstunden der Lehrkräfte an allgemeinbildenden Schulen 2024/2025: Baden-Württemberg Gymnasium 25 Wochenstunden (VwV Arbeitszeit der Lehrer, Regelstundenmaß).' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
  ],
  // BY — Bayern: G9, Stundentafel gültig ab 01.08.2026. Gesamtwochenstunden je Jahrgangsstufe aus GSO Anlage 1 (zu § 15 Abs. 1); Verteilung der 3 Intensivierungsstunden schulintern. Lehrkraft-Wert direkt aus BayUPZV.
  BY: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 34, severity: 'SOFT', weight: 1, basis: 'Höchster Jahrgangswert der Stundentafel 5–10 (34 Wochenstunden in Jahrgang 8/9/10); G9, Stundentafel gültig ab 01.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 23, severity: 'SOFT', weight: 1, basis: 'BayUPZV vom 11.09.2018, Anlage zu § 1 Abs. 2 Satz 1, Abschnitt 3 (Gymnasien): 23 Wochenstunden für Lehrkräfte mit wissenschaftlichen Fächern.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 5, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 5: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 6, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 6: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 7, value: 32, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 7: Originalwert 31,5 Wochenstunden, auf 32 aufgerundet.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 8, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 8: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 9, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 9: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 10, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 10: 34 Wochenstunden.' },
  ],
  // HB — Bremen: G8 (§ 20 Abs. 3 BremSchulG), Sek I = Jahrgänge 5–10, Jahrgang 10 zugleich Einführungsphase. Regelstundentafel aus Anlage 1 zur Deputationsvorlage L 56 (Stand ca. 2016); bei Spannen in Jahrgang 8/9 jeweils unterer Wert. Keine aktuellere Gymnasium-Novelle auffindbar.
  HB: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 34, severity: 'SOFT', weight: 1, basis: 'Höchster Jahrgangswert der Stundentafel 5–10 (34 Wochenstunden in Jahrgang 10); G8 (§ 20 Abs.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 27, severity: 'SOFT', weight: 1, basis: '§ 3 Abs. 1 Lehrerarbeitszeitaufteilungsgesetz (BremLAAufG) vom 17.06.1997: 27 Unterrichtsstunden je Woche in der Sekundarstufe I einschließlich Gymnasium Jahrgänge 7–10.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 5, value: 29, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 5: 29 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 6, value: 31, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 6: 31 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 7, value: 33, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 7: 33 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 8, value: 33, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 8: 33 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 9, value: 33, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 9: 33 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 10, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 10: 34 Wochenstunden.' },
  ],
  // HE — Hessen: Keine Regelform: Schulen wählen G8, G9 oder Parallelangebot; faktisch überwiegend G9. Kontingentstundentafel mit Blocksummen (5–6 und 7–10), keine amtliche Jahrgangsverteilung. Werte aus KMK-Länderabfrage, da der Gymnasium-Paragraf der Stundentafelverordnung nicht abrufbar war.
  HE: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 31, severity: 'SOFT', weight: 1, basis: 'Kontingent G9 laut KMK-Dokumentation Wochenpflichtstunden 2024/2025: Sek I gesamt 179–181 Wochenstunden; oberer Wert 181 geteilt durch 6 Jahrgänge = 30,2, aufgerundet 31.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 26, severity: 'SOFT', weight: 1, basis: 'Pflichtstundenverordnung Hessen vom 19.05.2017, zuletzt geändert 17.11.2022, § 1: 25,5 Wochenstunden an Gymnasien; Originalwert 25,5 kaufmännisch auf 26 gerundet.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
  ],
  // HH — Hamburg: G8, Sek I = Jahrgänge 5–10. § 36 Abs. 3 Nr. 3a APO-GrundStGy (Fassung 09.09.2024) setzt 30/31 fest und für 7–10 höchstens 34; die Grundstundensumme aus Anlage 6 (197 Wochenstunden) entspricht genau der vollen Ausschöpfung. Lehrkräfte: kein festes Regelstundenmaß, sondern Lehrerarbeitszeitmodell (LehrArbzVO).
  HH: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 34, severity: 'SOFT', weight: 1, basis: 'Höchster Jahrgangswert der Stundentafel 5–10 (34 Wochenstunden in Jahrgang 7/8/9/10); G8, Sek I = Jahrgänge 5–10.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 25, severity: 'SOFT', weight: 1, basis: 'KMK, Pflichtstunden 2024/2025: Hamburg Gymnasium Sek I 25,1 Wochenstunden als rechnerischer Durchschnitt im Faktorisierungsmodell der LehrArbzVO vom 01.07.2003; Originalwert 25,1 kaufmännisch auf 25 gerundet.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 5, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 5: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 6, value: 31, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 6: 31 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 7, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 7: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 8, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 8: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 9, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 9: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 10, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 10: 34 Wochenstunden.' },
  ],
  // MV — Mecklenburg-Vorpommern: G8, Sek I = Jahrgänge 5–10 (Orientierungsstufe 5/6 + Gymnasium 7–10). Fachbezogene Stundentafel je Jahrgang aus StdTafVO M-V vom 26.07.2024 (§ 8 und § 10), verpflichtend ab 2025/26; Gesamtsumme 199 Wochenstunden durch KMK bestätigt.
  MV: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 37, severity: 'SOFT', weight: 1, basis: 'Höchster Jahrgangswert der Stundentafel 5–10 (37 Wochenstunden in Jahrgang 10); G8, Sek I = Jahrgänge 5–10 (Orientierungsstufe 5/6 + Gymnasium 7–10).' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 27, severity: 'SOFT', weight: 1, basis: 'LehrArbzLVO M-V vom 16.03.2016 i.V.m. KMK-Übersicht 2024/2025: Gymnasium 23–27 Wochenstunden je nach Oberstufeneinsatz; Regelfall ohne nennenswerten Oberstufeneinsatz 27.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 5, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 5: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 6, value: 31, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 6: 31 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 7, value: 32, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 7: 32 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 8, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 8: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 9, value: 35, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 9: 35 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 10, value: 37, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 10: 37 Wochenstunden.' },
  ],
  // NI — Niedersachsen: G9, Sek I = Jahrgänge 5–10. Kontingentstundentafel mit zwei Varianten (RdErl. MK vom 01.08.2025, SVBl. 9/2025 S. 492): Stundentafel 1 ohne Profilunterricht (181 Stunden) und Stundentafel 2 mit Profilunterricht (187). Übernommen ist Stundentafel 1 als Basisvariante.
  NI: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 31, severity: 'SOFT', weight: 1, basis: 'Höchster Jahrgangswert der Stundentafel 5–10 (31 Wochenstunden in Jahrgang 9/10); G9, Sek I = Jahrgänge 5–10.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 24, severity: 'SOFT', weight: 1, basis: '§ 3 Abs. 2 Nr. 6 Nds. ArbZVO-Schule: 23,5 Unterrichtsstunden je Woche an Gymnasien; Originalwert 23,5 kaufmännisch auf 24 gerundet.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 5, value: 29, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 5: 29 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 6, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 6: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 7, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 7: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 8, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 8: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 9, value: 31, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 9: 31 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 10, value: 31, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 10: 31 Wochenstunden.' },
  ],
  // NW — Nordrhein-Westfalen: G9 als Regelform seit 2019/20, G8 in der Sek I nicht mehr relevant. § 1 Abs. 1 VO zu § 93 Abs. 2 SchulG (Fassung ab 01.08.2026) gibt Spannen vor: Klassen 5/6 28–30, Klassen 7–10 30–33; übernommen sind die Mittelwerte. Fachbezogene Anlage 3a wurde nicht ausgewertet.
  NW: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 32, severity: 'SOFT', weight: 1, basis: 'Höchster Jahrgangswert der Stundentafel 5–10 (31.5 Wochenstunden in Jahrgang 7/8/9/10); G9 als Regelform seit 2019/20, G8 in der Sek I nicht mehr relevant.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 26, severity: 'SOFT', weight: 1, basis: '§ 2 Abs. 1 Nr. 5 VO zur Ausführung des § 93 Abs. 2 SchulG NRW, Fassung ab 01.08.2026: Gymnasium 25,5 Wochenstunden; Originalwert 25,5 kaufmännisch auf 26 gerundet.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 5, value: 29, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 5: 29 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 6, value: 29, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 6: 29 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 7, value: 32, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 7: Originalwert 31,5 Wochenstunden, auf 32 aufgerundet.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 8, value: 32, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 8: Originalwert 31,5 Wochenstunden, auf 32 aufgerundet.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 9, value: 32, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 9: Originalwert 31,5 Wochenstunden, auf 32 aufgerundet.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 10, value: 32, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 10: Originalwert 31,5 Wochenstunden, auf 32 aufgerundet.' },
  ],
  // RP — Rheinland-Pfalz: G9 als Regelform, G8GTS als Ganztagsangebot einzelner Schulen. Kontingentstundentafel ohne jahrgangsscharfe Vorgabe für 7–9/10; Verwaltungsvorschrift war nicht als Volltext abrufbar, Werte daher aus der KMK-Zusammenstellung.
  RP: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 32, severity: 'SOFT', weight: 1, basis: 'KMK, Wochenpflichtstunden der Schülerinnen und Schüler 2025/2026: Gymnasium RP Sek I gesamt 188 Wochenstunden (Orientierungsstufe 60, Mittelstufe 120); 188 geteilt durch 6 Jahrgänge = 31,3, aufgerundet 32.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 24, severity: 'SOFT', weight: 1, basis: 'KMK, Pflichtstunden der Lehrkräfte 2023/2024: Rheinland-Pfalz Gymnasium 24 Deputatstunden (Rechtsgrundlage § 3 i.V.m. Anlage 1 LehrArbZVO vom 30.06.1999, Volltext nicht verifizierbar).' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
  ],
  // SH — Schleswig-Holstein: G8 und G9 parallel vorgehalten, je eigene Kontingentstundentafel (Runderlass vom 31.01.2025, geändert 22.07.2025, in Kraft seit 01.08.2025). Amtlich fixiert sind nur Blocksummen (5–6 und 7–10), die Feinverteilung liegt bei der Schule. Übernommen ist G9 als Regelbezug.
  SH: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 30, severity: 'SOFT', weight: 1, basis: 'Kontingentstundentafel Gymnasium G9 (Runderlass vom 31.01.2025): Jahrgänge 5–6 zusammen 55, Jahrgänge 7–10 zusammen 121–123, Sek I gesamt 176–178 Wochenstunden; oberer Wert 178 geteilt durch 6 Jahrgänge = 29,7, aufgerundet 30.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 26, severity: 'SOFT', weight: 1, basis: 'PflichtStVO Schleswig-Holstein i.V.m. KMK-Übersicht 2023/2024 und Ministeriums-Tabelle: Gymnasium 25,5/27, Regelfall 25,5 bei Oberstufeneinsatz; Originalwert 25,5 kaufmännisch auf 26 gerundet.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
  ],
  // SL — Saarland: G9 ab 2026/27 in allen Klassenstufen 5–10. Nach Fach und Klassenstufe ausdifferenzierte Stundentafel, aber sechs Zweigvarianten (177–179 Wochenstunden); übernommen ist Anlage 1 (Sprachenzweig, Gesamtsumme 178). Abweichungen je Klassenstufe durch Schulkonferenzbeschluss zulässig.
  SL: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 32, severity: 'SOFT', weight: 1, basis: 'Höchster Jahrgangswert der Stundentafel 5–10 (32 Wochenstunden in Jahrgang 10); G9 ab 2026/27 in allen Klassenstufen 5–10.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 27, severity: 'SOFT', weight: 1, basis: 'KMK, Pflichtstunden der Lehrkräfte 2025/2026: Saarland Gymnasium 25/27, Grundregel 27 Pflichtstunden ohne Oberstufeneinsatz (PflichtstundenVO Saarland, Novelle in Kraft seit 01.08.2025).' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 5, value: 28, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 5: 28 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 6, value: 28, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 6: 28 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 7, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 7: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 8, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 8: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 9, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 9: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 10, value: 32, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 10: 32 Wochenstunden.' },
  ],
  // SN — Sachsen: G8, Gymnasium Klassenstufen 5–12. Fachbezogene Stundentafel aus Anlage 4a VwV Stundentafeln (gültig 01.08.2026–31.07.2027); die amtliche PDF war nicht maschinell auslesbar, die Jahrgangssummen stammen aus einer Sekundärquelle. Zusatzstunden im Wahlbereich sind nicht eingerechnet.
  SN: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 35, severity: 'SOFT', weight: 1, basis: 'Höchster Jahrgangswert der Stundentafel 5–10 (35 Wochenstunden in Jahrgang 10); G8, Gymnasium Klassenstufen 5–12.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 26, severity: 'SOFT', weight: 1, basis: '§ 2 Abs. 2 Nr. 3 Sächsische Lehrkräfte-Arbeitszeitverordnung (SächsLKAZVO) vom 07.07.2017, zuletzt geändert 02.04.2026: Gymnasium 26 Unterrichtsstunden je Woche.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 5, value: 29, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 5: 29 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 6, value: 31, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 6: 31 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 7, value: 32, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 7: 32 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 8, value: 32, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 8: 32 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 9, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 9: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 10, value: 35, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 10: 35 Wochenstunden.' },
  ],
  // ST — Sachsen-Anhalt: G8, kein G9-Modell am Gymnasium. Feste Jahrgangswerte aus der KMK-Dokumentation 2025/2026 (vom Land gemeldete Werte), da die Stundentafelverordnung nicht als Volltext abrufbar war. Jahrgang 9 liegt bei 35 Stunden ohne zweite Fremdsprache ab Jahrgang 9; übernommen ist der Basiswert 34.
  ST: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 34, severity: 'SOFT', weight: 1, basis: 'Höchster Jahrgangswert der Stundentafel 5–10 (34 Wochenstunden in Jahrgang 7/8/9/10); G8, kein G9-Modell am Gymnasium.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 25, severity: 'SOFT', weight: 1, basis: '§ 3 Abs. 2 Nr. 3 ArbZVO-Lehr Sachsen-Anhalt: 25 Unterrichtsstunden an Gymnasien; befristete Vorgriffsstunde nach § 4b (01.04.2023–31.07.2028) erhöht die Belastung faktisch auf 26, ändert die Regelstundenzahl aber nicht.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 5, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 5: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 6, value: 30, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 6: 30 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 7, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 7: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 8, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 8: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 9, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 9: 34 Wochenstunden.' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: 10, value: 34, severity: 'SOFT', weight: 1, basis: 'Stundentafel des Landes, Jahrgang 10: 34 Wochenstunden.' },
  ],
  // TH — Thüringen: G8, Gymnasium Klassenstufen 5–12. Rahmenstundentafel mit Doppeljahrgangsstufen (ThürSchulO, Änderungsverordnung vom 21.06.2024, gestaffelt bis 2029/30): 5+6 = 62, 7+8 = 67, 9+10 = 68 plus eine flexible Stunde. Einzeljahrgangswerte sind amtlich nicht ausgewiesen.
  TH: [
    { type: 'MAX_LESSONS_PER_DAY', scope: 'CLASS', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'CLASS', grade: null, value: 33, severity: 'SOFT', weight: 1, basis: 'Rahmenstundentafel Gymnasium (ThürSchulO, Stand 21.06.2024): Doppeljahrgänge 62 + 67 + 68 + 1 flexible Stunde = 198 Wochenstunden über sechs Jahrgänge; 198 geteilt durch 6 = 33.' },
    { type: 'MAX_LESSONS_PER_DAY', scope: 'TEACHER', grade: null, value: 6, severity: 'SOFT', weight: 1, basis: 'keine Landesvorgabe, Standard' },
    { type: 'MAX_LESSONS_PER_WEEK', scope: 'TEACHER', grade: null, value: 26, severity: 'SOFT', weight: 1, basis: 'KMK, Pflichtstunden der Lehrkräfte 2025/2026 (Stand Januar 2026) i.V.m. § 4 ThürLehrAzVO: Thüringen Gymnasium 26 Pflichtstunden je Woche.' },
    { type: 'NO_LESSONS_IN_BREAKS', scope: null, grade: null, value: 1, severity: 'HARD', weight: 1, basis: 'Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.' },
  ],
}
