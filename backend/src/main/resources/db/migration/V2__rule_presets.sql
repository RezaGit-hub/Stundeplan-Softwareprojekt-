-- V2 — Voreinstellungen des Regelwerks, Systemkonfiguration, aktives Regelwerk der Schule.
--
-- Modul rules  : rule_preset (alle 16 Bundeslaender), rule (die Zeilen der eingestellten Schule)
-- Modul config : system_config
--
-- ERZEUGT, NICHT VON HAND GESCHRIEBEN. Stand 04.09.2026.
--   Generator: scripts/gen-rule-presets.mjs
--   Herkunft : 07-Konzept/recherche/bundesland-regelwerte.json
--   Bericht  : 07-Konzept/recherche/bundesland-regelwerte.md — Quellen je Land, Abschnitt
--              "Abbildung auf das Regelwerk" erklaert die Ableitung der Zahlen.
--
-- Werte nach Recherche 04.09., Rueckfrage R2 im Interview 2 am 08.09. kann einzelne Werte
-- aendern; dann Migration V<n> ergaenzen, V2 nicht aendern, sobald committet.
--
-- "Standard" am Zeilenende heisst: kein Landesrecht gefunden, fachliche Setzung des Projekts.
-- Das betrifft durchgehend beide Tagesobergrenzen (ausser Brandenburg) und die Pausenregel.
-- Jeder Wert bleibt in der Oberflaeche einzeln aenderbar; kein Bundesland ist im Code verdrahtet.
--
-- 138 Zeilen ueber 16 Bundeslaender: je Land fuenf schulweite Zeilen (grade IS NULL),
-- bei 10 Laendern zusaetzlich Jahrgangszeilen mit grade 5..10 (Kann-Kriterium E-47).

-- ---------------------------------------------------------------- BB — Brandenburg
-- G8 mit Gymnasium ab Jahrgangsstufe 7 (Sek I = 7–10). Kontingentstundentafel nur für Blöcke 7/8 und 9/10 (§
-- 11 Abs. 3 Sek I-V), keine Einzeljahrgangswerte; stattdessen greift die ausdrückliche Wochenobergrenze aus §
-- 12 Abs. 6 Sek I-V. Tagesobergrenze für Schüler landesrechtlich geregelt.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('BB', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  8, 'SOFT', 1),  -- VV-Schulbetrieb Brandenburg, Nr. 2 Abs. 4 Nr. 3: in den Sekundarstufen I und II acht Unterrichtsstunden pro Tag als Regel-Obergrenze.
    ('BB', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 36, 'SOFT', 1),  -- § 12 Abs. 6 Sekundarstufe I-Verordnung (Sek I-V) Brandenburg: Pflicht- und Wahlpflichtunterricht in der Regel höchstens 36 Wochenstunden je Schülerin/Schüler.
    ('BB', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('BB', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 26, 'SOFT', 1),  -- Anlage zu § 16 Abs. 2 AZV Brandenburg i.V.m. KMK-Übersicht 2025/2026: 25 Wochenstunden, Erhöhung um eine Stunde zum 01.02.2026, also 26 zum Stichtag 04.09.2026.
    ('BB', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1);  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.

-- ---------------------------------------------------------------- BE — Berlin
-- Reguläres Gymnasium ab Jahrgangsstufe 7 (6-jährige Grundschule), Abitur nach 12 Schuljahren. Feste
-- Wochenstundentafel je Fach und Jahrgang (Sek I-VO Anlage 2, PDF-Stand 22.09.2025). Für Jahrgänge 5/6
-- existiert am regulären Gymnasium kein Wert; grundständige Gymnasien (Anlage 3) nicht ausgewertet.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('BE', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('BE', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 34, 'SOFT', 1),  -- Höchster Jahrgangswert der Stundentafel 5–10 (34 Wochenstunden in Jahrgang 9/10); Reguläres Gymnasium ab Jahrgangsstufe 7 (6-jährige Grundschule), Abitur nach 12 Schuljahren.
    ('BE', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('BE', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 26, 'SOFT', 1),  -- KMK, Pflichtstunden der Lehrkräfte 2025/2026 (Stand Januar 2026): Berlin Gymnasium 26 Wochenstunden; Rechtsgrundlage AZVOLehr Berlin, Volltext nicht eingesehen.
    ('BE', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1),  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.
    ('BE', 'MAX_LESSONS_PER_WEEK', 'CLASS',   7,    33, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 7: 33 Wochenstunden.
    ('BE', 'MAX_LESSONS_PER_WEEK', 'CLASS',   8,    33, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 8: 33 Wochenstunden.
    ('BE', 'MAX_LESSONS_PER_WEEK', 'CLASS',   9,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 9: 34 Wochenstunden.
    ('BE', 'MAX_LESSONS_PER_WEEK', 'CLASS',   10,   34, 'SOFT', 1);  -- Stundentafel des Landes, Jahrgang 10: 34 Wochenstunden.

-- ---------------------------------------------------------------- BW — Baden-Württemberg
-- G8/G9-Mischbetrieb 2026/27 (G9 Normalform seit 2025/26, aufwachsend). Kontingentstundentafel nach
-- Stundentafelverordnung Gymnasien (Gym5bis11StTafelV BW, Anlage 1) – Zahlenwerk nicht auslesbar, keine
-- landeseinheitliche Jahrgangsverteilung. Lehrkraft-Wert über KMK-Sekundärquelle.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('BW', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('BW', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 34, 'SOFT', 1),  -- Kein belegter Landeswert: Kontingentstundentafel (Gym5bis11StTafelV BW, Anlage 1, gültig ab 01.08.2020) weist nur Fach-Gesamtstunden 5–10 aus, die Jahrgangsverteilung legt jede Schule selbst fest; Presseangaben zum neuen G9 (28–33) sind unbelegt. Ersatzwert 34 aus dem Median der belegten Länder, in der Oberfläche anzupassen.
    ('BW', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('BW', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 25, 'SOFT', 1),  -- KMK, Übersicht über die Pflichtstunden der Lehrkräfte an allgemeinbildenden Schulen 2024/2025: Baden-Württemberg Gymnasium 25 Wochenstunden (VwV Arbeitszeit der Lehrer, Regelstundenmaß).
    ('BW', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1);  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.

-- ---------------------------------------------------------------- BY — Bayern
-- G9, Stundentafel gültig ab 01.08.2026. Gesamtwochenstunden je Jahrgangsstufe aus GSO Anlage 1 (zu § 15 Abs.
-- 1); Verteilung der 3 Intensivierungsstunden schulintern. Lehrkraft-Wert direkt aus BayUPZV.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('BY', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('BY', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 34, 'SOFT', 1),  -- Höchster Jahrgangswert der Stundentafel 5–10 (34 Wochenstunden in Jahrgang 8/9/10); G9, Stundentafel gültig ab 01.
    ('BY', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('BY', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 23, 'SOFT', 1),  -- BayUPZV vom 11.09.2018, Anlage zu § 1 Abs. 2 Satz 1, Abschnitt 3 (Gymnasien): 23 Wochenstunden für Lehrkräfte mit wissenschaftlichen Fächern.
    ('BY', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1),  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.
    ('BY', 'MAX_LESSONS_PER_WEEK', 'CLASS',   5,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 5: 30 Wochenstunden.
    ('BY', 'MAX_LESSONS_PER_WEEK', 'CLASS',   6,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 6: 30 Wochenstunden.
    ('BY', 'MAX_LESSONS_PER_WEEK', 'CLASS',   7,    32, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 7: Originalwert 31,5 Wochenstunden, auf 32 aufgerundet.
    ('BY', 'MAX_LESSONS_PER_WEEK', 'CLASS',   8,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 8: 34 Wochenstunden.
    ('BY', 'MAX_LESSONS_PER_WEEK', 'CLASS',   9,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 9: 34 Wochenstunden.
    ('BY', 'MAX_LESSONS_PER_WEEK', 'CLASS',   10,   34, 'SOFT', 1);  -- Stundentafel des Landes, Jahrgang 10: 34 Wochenstunden.

-- ---------------------------------------------------------------- HB — Bremen
-- G8 (§ 20 Abs. 3 BremSchulG), Sek I = Jahrgänge 5–10, Jahrgang 10 zugleich Einführungsphase.
-- Regelstundentafel aus Anlage 1 zur Deputationsvorlage L 56 (Stand ca. 2016); bei Spannen in Jahrgang 8/9
-- jeweils unterer Wert. Keine aktuellere Gymnasium-Novelle auffindbar.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('HB', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('HB', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 34, 'SOFT', 1),  -- Höchster Jahrgangswert der Stundentafel 5–10 (34 Wochenstunden in Jahrgang 10); G8 (§ 20 Abs.
    ('HB', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('HB', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 27, 'SOFT', 1),  -- § 3 Abs. 1 Lehrerarbeitszeitaufteilungsgesetz (BremLAAufG) vom 17.06.1997: 27 Unterrichtsstunden je Woche in der Sekundarstufe I einschließlich Gymnasium Jahrgänge 7–10.
    ('HB', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1),  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.
    ('HB', 'MAX_LESSONS_PER_WEEK', 'CLASS',   5,    29, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 5: 29 Wochenstunden.
    ('HB', 'MAX_LESSONS_PER_WEEK', 'CLASS',   6,    31, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 6: 31 Wochenstunden.
    ('HB', 'MAX_LESSONS_PER_WEEK', 'CLASS',   7,    33, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 7: 33 Wochenstunden.
    ('HB', 'MAX_LESSONS_PER_WEEK', 'CLASS',   8,    33, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 8: 33 Wochenstunden.
    ('HB', 'MAX_LESSONS_PER_WEEK', 'CLASS',   9,    33, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 9: 33 Wochenstunden.
    ('HB', 'MAX_LESSONS_PER_WEEK', 'CLASS',   10,   34, 'SOFT', 1);  -- Stundentafel des Landes, Jahrgang 10: 34 Wochenstunden.

-- ---------------------------------------------------------------- HE — Hessen
-- Keine Regelform: Schulen wählen G8, G9 oder Parallelangebot; faktisch überwiegend G9. Kontingentstundentafel
-- mit Blocksummen (5–6 und 7–10), keine amtliche Jahrgangsverteilung. Werte aus KMK-Länderabfrage, da der
-- Gymnasium-Paragraf der Stundentafelverordnung nicht abrufbar war.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('HE', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('HE', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 31, 'SOFT', 1),  -- Kontingent G9 laut KMK-Dokumentation Wochenpflichtstunden 2024/2025: Sek I gesamt 179–181 Wochenstunden; oberer Wert 181 geteilt durch 6 Jahrgänge = 30,2, aufgerundet 31.
    ('HE', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('HE', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 26, 'SOFT', 1),  -- Pflichtstundenverordnung Hessen vom 19.05.2017, zuletzt geändert 17.11.2022, § 1: 25,5 Wochenstunden an Gymnasien; Originalwert 25,5 kaufmännisch auf 26 gerundet.
    ('HE', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1);  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.

-- ---------------------------------------------------------------- HH — Hamburg
-- G8, Sek I = Jahrgänge 5–10. § 36 Abs. 3 Nr. 3a APO-GrundStGy (Fassung 09.09.2024) setzt 30/31 fest und für
-- 7–10 höchstens 34; die Grundstundensumme aus Anlage 6 (197 Wochenstunden) entspricht genau der vollen
-- Ausschöpfung. Lehrkräfte: kein festes Regelstundenmaß, sondern Lehrerarbeitszeitmodell (LehrArbzVO).
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('HH', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('HH', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 34, 'SOFT', 1),  -- Höchster Jahrgangswert der Stundentafel 5–10 (34 Wochenstunden in Jahrgang 7/8/9/10); G8, Sek I = Jahrgänge 5–10.
    ('HH', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('HH', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 25, 'SOFT', 1),  -- KMK, Pflichtstunden 2024/2025: Hamburg Gymnasium Sek I 25,1 Wochenstunden als rechnerischer Durchschnitt im Faktorisierungsmodell der LehrArbzVO vom 01.07.2003; Originalwert 25,1 kaufmännisch auf 25 gerundet.
    ('HH', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1),  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.
    ('HH', 'MAX_LESSONS_PER_WEEK', 'CLASS',   5,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 5: 30 Wochenstunden.
    ('HH', 'MAX_LESSONS_PER_WEEK', 'CLASS',   6,    31, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 6: 31 Wochenstunden.
    ('HH', 'MAX_LESSONS_PER_WEEK', 'CLASS',   7,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 7: 34 Wochenstunden.
    ('HH', 'MAX_LESSONS_PER_WEEK', 'CLASS',   8,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 8: 34 Wochenstunden.
    ('HH', 'MAX_LESSONS_PER_WEEK', 'CLASS',   9,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 9: 34 Wochenstunden.
    ('HH', 'MAX_LESSONS_PER_WEEK', 'CLASS',   10,   34, 'SOFT', 1);  -- Stundentafel des Landes, Jahrgang 10: 34 Wochenstunden.

-- ---------------------------------------------------------------- MV — Mecklenburg-Vorpommern
-- G8, Sek I = Jahrgänge 5–10 (Orientierungsstufe 5/6 + Gymnasium 7–10). Fachbezogene Stundentafel je Jahrgang
-- aus StdTafVO M-V vom 26.07.2024 (§ 8 und § 10), verpflichtend ab 2025/26; Gesamtsumme 199 Wochenstunden
-- durch KMK bestätigt.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('MV', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('MV', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 37, 'SOFT', 1),  -- Höchster Jahrgangswert der Stundentafel 5–10 (37 Wochenstunden in Jahrgang 10); G8, Sek I = Jahrgänge 5–10 (Orientierungsstufe 5/6 + Gymnasium 7–10).
    ('MV', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('MV', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 27, 'SOFT', 1),  -- LehrArbzLVO M-V vom 16.03.2016 i.V.m. KMK-Übersicht 2024/2025: Gymnasium 23–27 Wochenstunden je nach Oberstufeneinsatz; Regelfall ohne nennenswerten Oberstufeneinsatz 27.
    ('MV', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1),  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.
    ('MV', 'MAX_LESSONS_PER_WEEK', 'CLASS',   5,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 5: 30 Wochenstunden.
    ('MV', 'MAX_LESSONS_PER_WEEK', 'CLASS',   6,    31, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 6: 31 Wochenstunden.
    ('MV', 'MAX_LESSONS_PER_WEEK', 'CLASS',   7,    32, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 7: 32 Wochenstunden.
    ('MV', 'MAX_LESSONS_PER_WEEK', 'CLASS',   8,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 8: 34 Wochenstunden.
    ('MV', 'MAX_LESSONS_PER_WEEK', 'CLASS',   9,    35, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 9: 35 Wochenstunden.
    ('MV', 'MAX_LESSONS_PER_WEEK', 'CLASS',   10,   37, 'SOFT', 1);  -- Stundentafel des Landes, Jahrgang 10: 37 Wochenstunden.

-- ---------------------------------------------------------------- NI — Niedersachsen
-- G9, Sek I = Jahrgänge 5–10. Kontingentstundentafel mit zwei Varianten (RdErl. MK vom 01.08.2025, SVBl.
-- 9/2025 S. 492): Stundentafel 1 ohne Profilunterricht (181 Stunden) und Stundentafel 2 mit Profilunterricht
-- (187). Übernommen ist Stundentafel 1 als Basisvariante.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('NI', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('NI', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 31, 'SOFT', 1),  -- Höchster Jahrgangswert der Stundentafel 5–10 (31 Wochenstunden in Jahrgang 9/10); G9, Sek I = Jahrgänge 5–10.
    ('NI', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('NI', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 24, 'SOFT', 1),  -- § 3 Abs. 2 Nr. 6 Nds. ArbZVO-Schule: 23,5 Unterrichtsstunden je Woche an Gymnasien; Originalwert 23,5 kaufmännisch auf 24 gerundet.
    ('NI', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1),  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.
    ('NI', 'MAX_LESSONS_PER_WEEK', 'CLASS',   5,    29, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 5: 29 Wochenstunden.
    ('NI', 'MAX_LESSONS_PER_WEEK', 'CLASS',   6,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 6: 30 Wochenstunden.
    ('NI', 'MAX_LESSONS_PER_WEEK', 'CLASS',   7,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 7: 30 Wochenstunden.
    ('NI', 'MAX_LESSONS_PER_WEEK', 'CLASS',   8,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 8: 30 Wochenstunden.
    ('NI', 'MAX_LESSONS_PER_WEEK', 'CLASS',   9,    31, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 9: 31 Wochenstunden.
    ('NI', 'MAX_LESSONS_PER_WEEK', 'CLASS',   10,   31, 'SOFT', 1);  -- Stundentafel des Landes, Jahrgang 10: 31 Wochenstunden.

-- ---------------------------------------------------------------- NW — Nordrhein-Westfalen
-- G9 als Regelform seit 2019/20, G8 in der Sek I nicht mehr relevant. § 1 Abs. 1 VO zu § 93 Abs. 2 SchulG
-- (Fassung ab 01.08.2026) gibt Spannen vor: Klassen 5/6 28–30, Klassen 7–10 30–33; übernommen sind die
-- Mittelwerte. Fachbezogene Anlage 3a wurde nicht ausgewertet.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('NW', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('NW', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 32, 'SOFT', 1),  -- Höchster Jahrgangswert der Stundentafel 5–10 (31.5 Wochenstunden in Jahrgang 7/8/9/10); G9 als Regelform seit 2019/20, G8 in der Sek I nicht mehr relevant.
    ('NW', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('NW', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 26, 'SOFT', 1),  -- § 2 Abs. 1 Nr. 5 VO zur Ausführung des § 93 Abs. 2 SchulG NRW, Fassung ab 01.08.2026: Gymnasium 25,5 Wochenstunden; Originalwert 25,5 kaufmännisch auf 26 gerundet.
    ('NW', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1),  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.
    ('NW', 'MAX_LESSONS_PER_WEEK', 'CLASS',   5,    29, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 5: 29 Wochenstunden.
    ('NW', 'MAX_LESSONS_PER_WEEK', 'CLASS',   6,    29, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 6: 29 Wochenstunden.
    ('NW', 'MAX_LESSONS_PER_WEEK', 'CLASS',   7,    32, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 7: Originalwert 31,5 Wochenstunden, auf 32 aufgerundet.
    ('NW', 'MAX_LESSONS_PER_WEEK', 'CLASS',   8,    32, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 8: Originalwert 31,5 Wochenstunden, auf 32 aufgerundet.
    ('NW', 'MAX_LESSONS_PER_WEEK', 'CLASS',   9,    32, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 9: Originalwert 31,5 Wochenstunden, auf 32 aufgerundet.
    ('NW', 'MAX_LESSONS_PER_WEEK', 'CLASS',   10,   32, 'SOFT', 1);  -- Stundentafel des Landes, Jahrgang 10: Originalwert 31,5 Wochenstunden, auf 32 aufgerundet.

-- ---------------------------------------------------------------- RP — Rheinland-Pfalz
-- G9 als Regelform, G8GTS als Ganztagsangebot einzelner Schulen. Kontingentstundentafel ohne jahrgangsscharfe
-- Vorgabe für 7–9/10; Verwaltungsvorschrift war nicht als Volltext abrufbar, Werte daher aus der
-- KMK-Zusammenstellung.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('RP', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('RP', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 32, 'SOFT', 1),  -- KMK, Wochenpflichtstunden der Schülerinnen und Schüler 2025/2026: Gymnasium RP Sek I gesamt 188 Wochenstunden (Orientierungsstufe 60, Mittelstufe 120); 188 geteilt durch 6 Jahrgänge = 31,3, aufgerundet 32.
    ('RP', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('RP', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 24, 'SOFT', 1),  -- KMK, Pflichtstunden der Lehrkräfte 2023/2024: Rheinland-Pfalz Gymnasium 24 Deputatstunden (Rechtsgrundlage § 3 i.V.m. Anlage 1 LehrArbZVO vom 30.06.1999, Volltext nicht verifizierbar).
    ('RP', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1);  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.

-- ---------------------------------------------------------------- SH — Schleswig-Holstein
-- G8 und G9 parallel vorgehalten, je eigene Kontingentstundentafel (Runderlass vom 31.01.2025, geändert
-- 22.07.2025, in Kraft seit 01.08.2025). Amtlich fixiert sind nur Blocksummen (5–6 und 7–10), die
-- Feinverteilung liegt bei der Schule. Übernommen ist G9 als Regelbezug.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('SH', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('SH', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 30, 'SOFT', 1),  -- Kontingentstundentafel Gymnasium G9 (Runderlass vom 31.01.2025): Jahrgänge 5–6 zusammen 55, Jahrgänge 7–10 zusammen 121–123, Sek I gesamt 176–178 Wochenstunden; oberer Wert 178 geteilt durch 6 Jahrgänge = 29,7, aufgerundet 30.
    ('SH', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('SH', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 26, 'SOFT', 1),  -- PflichtStVO Schleswig-Holstein i.V.m. KMK-Übersicht 2023/2024 und Ministeriums-Tabelle: Gymnasium 25,5/27, Regelfall 25,5 bei Oberstufeneinsatz; Originalwert 25,5 kaufmännisch auf 26 gerundet.
    ('SH', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1);  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.

-- ---------------------------------------------------------------- SL — Saarland
-- G9 ab 2026/27 in allen Klassenstufen 5–10. Nach Fach und Klassenstufe ausdifferenzierte Stundentafel, aber
-- sechs Zweigvarianten (177–179 Wochenstunden); übernommen ist Anlage 1 (Sprachenzweig, Gesamtsumme 178).
-- Abweichungen je Klassenstufe durch Schulkonferenzbeschluss zulässig.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('SL', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('SL', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 32, 'SOFT', 1),  -- Höchster Jahrgangswert der Stundentafel 5–10 (32 Wochenstunden in Jahrgang 10); G9 ab 2026/27 in allen Klassenstufen 5–10.
    ('SL', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('SL', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 27, 'SOFT', 1),  -- KMK, Pflichtstunden der Lehrkräfte 2025/2026: Saarland Gymnasium 25/27, Grundregel 27 Pflichtstunden ohne Oberstufeneinsatz (PflichtstundenVO Saarland, Novelle in Kraft seit 01.08.2025).
    ('SL', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1),  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.
    ('SL', 'MAX_LESSONS_PER_WEEK', 'CLASS',   5,    28, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 5: 28 Wochenstunden.
    ('SL', 'MAX_LESSONS_PER_WEEK', 'CLASS',   6,    28, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 6: 28 Wochenstunden.
    ('SL', 'MAX_LESSONS_PER_WEEK', 'CLASS',   7,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 7: 30 Wochenstunden.
    ('SL', 'MAX_LESSONS_PER_WEEK', 'CLASS',   8,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 8: 30 Wochenstunden.
    ('SL', 'MAX_LESSONS_PER_WEEK', 'CLASS',   9,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 9: 30 Wochenstunden.
    ('SL', 'MAX_LESSONS_PER_WEEK', 'CLASS',   10,   32, 'SOFT', 1);  -- Stundentafel des Landes, Jahrgang 10: 32 Wochenstunden.

-- ---------------------------------------------------------------- SN — Sachsen
-- G8, Gymnasium Klassenstufen 5–12. Fachbezogene Stundentafel aus Anlage 4a VwV Stundentafeln (gültig
-- 01.08.2026–31.07.2027); die amtliche PDF war nicht maschinell auslesbar, die Jahrgangssummen stammen aus
-- einer Sekundärquelle. Zusatzstunden im Wahlbereich sind nicht eingerechnet.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('SN', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('SN', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 35, 'SOFT', 1),  -- Höchster Jahrgangswert der Stundentafel 5–10 (35 Wochenstunden in Jahrgang 10); G8, Gymnasium Klassenstufen 5–12.
    ('SN', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('SN', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 26, 'SOFT', 1),  -- § 2 Abs. 2 Nr. 3 Sächsische Lehrkräfte-Arbeitszeitverordnung (SächsLKAZVO) vom 07.07.2017, zuletzt geändert 02.04.2026: Gymnasium 26 Unterrichtsstunden je Woche.
    ('SN', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1),  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.
    ('SN', 'MAX_LESSONS_PER_WEEK', 'CLASS',   5,    29, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 5: 29 Wochenstunden.
    ('SN', 'MAX_LESSONS_PER_WEEK', 'CLASS',   6,    31, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 6: 31 Wochenstunden.
    ('SN', 'MAX_LESSONS_PER_WEEK', 'CLASS',   7,    32, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 7: 32 Wochenstunden.
    ('SN', 'MAX_LESSONS_PER_WEEK', 'CLASS',   8,    32, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 8: 32 Wochenstunden.
    ('SN', 'MAX_LESSONS_PER_WEEK', 'CLASS',   9,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 9: 34 Wochenstunden.
    ('SN', 'MAX_LESSONS_PER_WEEK', 'CLASS',   10,   35, 'SOFT', 1);  -- Stundentafel des Landes, Jahrgang 10: 35 Wochenstunden.

-- ---------------------------------------------------------------- ST — Sachsen-Anhalt
-- G8, kein G9-Modell am Gymnasium. Feste Jahrgangswerte aus der KMK-Dokumentation 2025/2026 (vom Land
-- gemeldete Werte), da die Stundentafelverordnung nicht als Volltext abrufbar war. Jahrgang 9 liegt bei 35
-- Stunden ohne zweite Fremdsprache ab Jahrgang 9; übernommen ist der Basiswert 34.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('ST', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('ST', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 34, 'SOFT', 1),  -- Höchster Jahrgangswert der Stundentafel 5–10 (34 Wochenstunden in Jahrgang 7/8/9/10); G8, kein G9-Modell am Gymnasium.
    ('ST', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('ST', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 25, 'SOFT', 1),  -- § 3 Abs. 2 Nr. 3 ArbZVO-Lehr Sachsen-Anhalt: 25 Unterrichtsstunden an Gymnasien; befristete Vorgriffsstunde nach § 4b (01.04.2023–31.07.2028) erhöht die Belastung faktisch auf 26, ändert die Regelstundenzahl aber nicht.
    ('ST', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1),  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.
    ('ST', 'MAX_LESSONS_PER_WEEK', 'CLASS',   5,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 5: 30 Wochenstunden.
    ('ST', 'MAX_LESSONS_PER_WEEK', 'CLASS',   6,    30, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 6: 30 Wochenstunden.
    ('ST', 'MAX_LESSONS_PER_WEEK', 'CLASS',   7,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 7: 34 Wochenstunden.
    ('ST', 'MAX_LESSONS_PER_WEEK', 'CLASS',   8,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 8: 34 Wochenstunden.
    ('ST', 'MAX_LESSONS_PER_WEEK', 'CLASS',   9,    34, 'SOFT', 1),  -- Stundentafel des Landes, Jahrgang 9: 34 Wochenstunden.
    ('ST', 'MAX_LESSONS_PER_WEEK', 'CLASS',   10,   34, 'SOFT', 1);  -- Stundentafel des Landes, Jahrgang 10: 34 Wochenstunden.

-- ---------------------------------------------------------------- TH — Thüringen
-- G8, Gymnasium Klassenstufen 5–12. Rahmenstundentafel mit Doppeljahrgangsstufen (ThürSchulO,
-- Änderungsverordnung vom 21.06.2024, gestaffelt bis 2029/30): 5+6 = 62, 7+8 = 67, 9+10 = 68 plus eine
-- flexible Stunde. Einzeljahrgangswerte sind amtlich nicht ausgewiesen.
INSERT INTO rule_preset (state, type, scope, grade, value, severity, weight) VALUES
    ('TH', 'MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('TH', 'MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 33, 'SOFT', 1),  -- Rahmenstundentafel Gymnasium (ThürSchulO, Stand 21.06.2024): Doppeljahrgänge 62 + 67 + 68 + 1 flexible Stunde = 198 Wochenstunden über sechs Jahrgänge; 198 geteilt durch 6 = 33.
    ('TH', 'MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1),  -- keine Landesvorgabe, Standard
    ('TH', 'MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 26, 'SOFT', 1),  -- KMK, Pflichtstunden der Lehrkräfte 2025/2026 (Stand Januar 2026) i.V.m. § 4 ThürLehrAzVO: Thüringen Gymnasium 26 Pflichtstunden je Woche.
    ('TH', 'NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1);  -- Standard: In Pausenzeiten wird kein Unterricht geplant; gilt unabhängig von der Zähleinheit in allen Bundesländern.

-- ---------------------------------------------------------------- Systemkonfiguration

-- Die eine Zeile der Systemkonfiguration. Referenzszenario laut E-37: Gymnasium in Schleswig-Holstein. Das ist
-- eine Vorbelegung, keine Grenze — Schulname, Bundesland, Schulform und Rechenzeit sind aenderbar.
INSERT INTO system_config (id, school_name, state, school_type, max_solve_seconds)
VALUES (1, 'Gymnasium Musterstadt', 'SH', 'GYMNASIUM', 60);

-- ---------------------------------------------------------------- Aktives Regelwerk der Schule

-- Die Voreinstellung des eingestellten Bundeslands (SH), einmal kopiert. POST /api/rules/reset tut zur
-- Laufzeit dasselbe, kopiert dabei aber alle Zeilen des Landes — schulweite und Jahrgangszeilen; fuer SH sind
-- das genau diese 5 schulweiten Zeilen, fuer ein Land mit Jahrgangszeilen (z. B. BE) entsprechend mehr. Die
-- beiden Klassenzeilen stehen so im Pflichtenheft und sind aktiv; die Lehrkraftzeilen sind vorhanden, aber
-- ausgeschaltet, damit das Testszenario nicht ungewollt Verstoesse meldet.
INSERT INTO rule (type, scope, grade, value, severity, weight, active) VALUES
    ('MAX_LESSONS_PER_DAY',  'CLASS',   NULL,  6, 'SOFT', 1, true),
    ('MAX_LESSONS_PER_WEEK', 'CLASS',   NULL, 30, 'SOFT', 1, true),
    ('MAX_LESSONS_PER_DAY',  'TEACHER', NULL,  6, 'SOFT', 1, false),
    ('MAX_LESSONS_PER_WEEK', 'TEACHER', NULL, 26, 'SOFT', 1, false),
    ('NO_LESSONS_IN_BREAKS', NULL,      NULL,  1, 'HARD', 1, true);
