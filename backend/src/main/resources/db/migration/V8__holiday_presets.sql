-- V8 — Vorlage "Ferien und Feiertage je Bundesland" (E-51).
--
-- Modul substitution : holiday_preset (16 Bundeslaender x zwei Schuljahre)
--
-- V1-V7 bleiben unveraendert (Flyway-Pruefsumme). Die Tabelle wird hier angelegt UND gefuellt:
-- im Vollausbau steht das Schema der ersten sieben Migrationen fest.
--
-- ERZEUGT, NICHT VON HAND GESCHRIEBEN. Stand 05.09.2026.
--   Generator: scripts/gen-holiday-presets.mjs
--   Herkunft : 07-Konzept/recherche/ferien-2026-2028.json (174 Objekte)
--              07-Konzept/recherche/feiertage-2026-2028.json (535 Objekte)
--   Berichte : 07-Konzept/recherche/ferien-2026-2028.md, 07-Konzept/recherche/feiertage-2026-2028.md —
--              Quellen, Vertrauen und offene Punkte je Bundesland.
--
-- Die Vorlage ist DATEN, kein Dienst: nichts wird zur Laufzeit aus dem Internet geholt. Sie greift,
-- sobald in der Systemkonfiguration ein Bundesland steht — POST /api/holidays/preset kopiert die
-- Zeilen des Schuljahres nach holiday. Jeder Eintrag bleibt danach einzeln aenderbar und loeschbar.
--
-- Regeln der Ableitung:
--   R1 Nur landesweit geltende Feiertage. Die 12 Zeilen mit scope "teilgebiet" (Fronleichnam in Teilen von
--   Sachsen und Thueringen, Mariae Himmelfahrt in katholischen Gemeinden Bayerns, Augsburger Friedensfest nur
--   Stadt Augsburg) stehen NICHT in der Vorlage — eine Schule im betroffenen Gebiet traegt den Tag von Hand
--   nach.
--   R2 Feiertage an einem Samstag oder Sonntag bleiben drin (143 der 523 Zeilen). Sie schaden nicht und halten
--   die Vorlage nachpruefbar gleich der Recherche.
--   R3 Schuljahr eines Feiertags: 01.08. bis 31.07.
--   R4 Schuljahr eines Ferienblocks: das Schuljahr, in dem er beginnt (Feld schoolYear der Recherche,
--   KMK-Ferienkalender). Ausnahme Bayern: die Sommerferien 2026/27 beginnen am 02.08.2027 und bleiben trotzdem
--   bei 2026/27 — sonst haette Bayern dort keine Sommerferien.
--   R5 Bewegliche Ferientage und einzelne unterrichtsfreie Tage nach Schulordnung sind keine amtlichen
--   Ferientage und stehen nicht in der Vorlage; sie werden von Hand nachgetragen.
--
-- 523 Zeilen: 174 Ferienbloecke und 349 Feiertage ueber 16 Bundeslaender
-- und die Schuljahre 2026/27 und 2027/28. Fuer ein weiteres Schuljahr die beiden JSON-Dateien
-- fortschreiben und das Skript erneut laufen lassen; diese Migration bleibt dann unveraendert.

CREATE TABLE holiday_preset (
    id          bigserial PRIMARY KEY,
    state       varchar(2)   NOT NULL,
    school_year varchar(7)   NOT NULL,
    kind        varchar(20)  NOT NULL CHECK (kind IN ('VACATION', 'PUBLIC_HOLIDAY')),
    label       varchar(120) NOT NULL,
    date_from   date         NOT NULL,
    date_to     date         NOT NULL,
    source      varchar(300) NOT NULL,
    -- Wie holiday_period in der Tabelle holiday: ein Zeitraum endet nie vor seinem Anfang.
    CONSTRAINT holiday_preset_period CHECK (date_to >= date_from),
    -- Genau der Schluessel, ueber den POST /api/holidays/preset Duplikate ueberspringt.
    CONSTRAINT holiday_preset_unique UNIQUE (state, school_year, date_from, date_to, label)
);

-- Eine Abfrage je Ladevorgang und eine je Auswahlliste der Schuljahre.
CREATE INDEX holiday_preset_state_year ON holiday_preset (state, school_year, date_from);

-- ---------------------------------------------------------------- BB 2026/27 — Brandenburg
-- 6 Ferienbloecke, 12 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BB', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-19', '2026-10-30', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2026-10-31', '2026-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BB', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-23', '2027-01-02', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BB', '2026/27', 'VACATION',       'Winterferien',              '2027-02-01', '2027-02-06', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf'),
    ('BB', '2026/27', 'VACATION',       'Osterferien',               '2027-03-22', '2027-04-03', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', 'Ostersonntag',              '2027-03-28', '2027-03-28', 'https://bravors.brandenburg.de/gesetze/ftg_2003/6'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstsonntag',            '2027-05-16', '2027-05-16', 'https://bravors.brandenburg.de/gesetze/ftg_2003/6'),
    ('BB', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BB', '2026/27', 'VACATION',       'Pfingsten',                 '2027-05-18', '2027-05-18', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf'),
    ('BB', '2026/27', 'VACATION',       'Sommerferien',              '2027-07-01', '2027-08-14', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf');

-- ---------------------------------------------------------------- BB 2027/28 — Brandenburg
-- 6 Ferienbloecke, 12 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BB', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-11', '2027-10-23', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2027-10-31', '2027-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BB', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-23', '2027-12-31', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BB', '2027/28', 'VACATION',       'Winterferien',              '2028-01-31', '2028-02-05', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf'),
    ('BB', '2027/28', 'VACATION',       'Osterferien',               '2028-04-10', '2028-04-22', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', 'Ostersonntag',              '2028-04-16', '2028-04-16', 'https://bravors.brandenburg.de/gesetze/ftg_2003/6'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstsonntag',            '2028-06-04', '2028-06-04', 'https://bravors.brandenburg.de/gesetze/ftg_2003/6'),
    ('BB', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BB', '2027/28', 'VACATION',       'Pfingsten',                 '2028-06-06', '2028-06-06', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf'),
    ('BB', '2027/28', 'VACATION',       'Sommerferien',              '2028-06-29', '2028-08-12', 'https://bravors.brandenburg.de/sixcms/media.php/66/VV-Schulbetrieb-Anlage-1-2025.pdf');

-- ---------------------------------------------------------------- BE 2026/27 — Berlin
-- 6 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('BE', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BE', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-19', '2026-10-31', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf'),
    ('BE', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-23', '2027-01-02', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf'),
    ('BE', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BE', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BE', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BE', '2026/27', 'VACATION',       'Winterferien',              '2027-02-01', '2027-02-06', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf'),
    ('BE', '2026/27', 'PUBLIC_HOLIDAY', 'Internationaler Frauentag', '2027-03-08', '2027-03-08', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BE', '2026/27', 'VACATION',       'Osterferien',               '2027-03-22', '2027-04-02', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf'),
    ('BE', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BE', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BE', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BE', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BE', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BE', '2026/27', 'VACATION',       'Pfingstferien',             '2027-05-18', '2027-05-19', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf'),
    ('BE', '2026/27', 'VACATION',       'Sommerferien',              '2027-07-01', '2027-08-14', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf');

-- ---------------------------------------------------------------- BE 2027/28 — Berlin
-- 6 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit',               '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BE', '2027/28', 'VACATION',       'Herbstferien',                            '2027-10-11', '2027-10-23', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf'),
    ('BE', '2027/28', 'VACATION',       'Weihnachtsferien',                        '2027-12-22', '2027-12-31', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf'),
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',                        '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',                        '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                                 '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BE', '2027/28', 'VACATION',       'Winterferien',                            '2028-01-31', '2028-02-05', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf'),
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', 'Internationaler Frauentag',               '2028-03-08', '2028-03-08', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BE', '2027/28', 'VACATION',       'Osterferien',                             '2028-04-10', '2028-04-22', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf'),
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                              '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',                             '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',                          '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',                     '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BE', '2027/28', 'VACATION',       'Pfingstferien',                           '2028-06-01', '2028-06-02', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf'),
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',                           '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BE', '2027/28', 'PUBLIC_HOLIDAY', 'Gedenktag 17. Juni 1953 (einmalig 2028)', '2028-06-17', '2028-06-17', 'https://www.feiertage-deutschland.de/volksaufstand-ddr/'),
    ('BE', '2027/28', 'VACATION',       'Sommerferien',                            '2028-07-01', '2028-08-12', 'https://www.berlin.de/sen/bjf/service/kalender/ferien/ferienordnung-des-landes-berlin-2024_2025-bis-2029_2030.pdf');

-- ---------------------------------------------------------------- BW 2026/27 — Baden-Wuerttemberg
-- 5 Ferienbloecke, 12 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BW', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-26', '2026-10-30', 'https://km.baden-wuerttemberg.de/de/service/ferien'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', 'Allerheiligen',             '2026-11-01', '2026-11-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BW', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-23', '2027-01-09', 'https://km.baden-wuerttemberg.de/de/service/ferien'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', 'Heilige Drei Könige',       '2027-01-06', '2027-01-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2026/27', 'VACATION',       'Osterferien',               '2027-03-30', '2027-04-03', 'https://km.baden-wuerttemberg.de/de/service/ferien'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2026/27', 'VACATION',       'Pfingstferien',             '2027-05-18', '2027-05-29', 'https://km.baden-wuerttemberg.de/de/service/ferien'),
    ('BW', '2026/27', 'PUBLIC_HOLIDAY', 'Fronleichnam',              '2027-05-27', '2027-05-27', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('BW', '2026/27', 'VACATION',       'Sommerferien',              '2027-07-29', '2027-09-11', 'https://km.baden-wuerttemberg.de/de/service/ferien');

-- ---------------------------------------------------------------- BW 2027/28 — Baden-Wuerttemberg
-- 5 Ferienbloecke, 12 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', 'Allerheiligen',             '2027-11-01', '2027-11-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2027/28', 'VACATION',       'Herbstferien',              '2027-11-02', '2027-11-06', 'https://km.baden-wuerttemberg.de/de/service/ferien'),
    ('BW', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-23', '2028-01-08', 'https://km.baden-wuerttemberg.de/de/service/ferien'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', 'Heilige Drei Könige',       '2028-01-06', '2028-01-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BW', '2027/28', 'VACATION',       'Osterferien',               '2028-04-18', '2028-04-22', 'https://km.baden-wuerttemberg.de/de/service/ferien'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BW', '2027/28', 'VACATION',       'Pfingstferien',             '2028-06-06', '2028-06-17', 'https://km.baden-wuerttemberg.de/de/service/ferien'),
    ('BW', '2027/28', 'PUBLIC_HOLIDAY', 'Fronleichnam',              '2028-06-15', '2028-06-15', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('BW', '2027/28', 'VACATION',       'Sommerferien',              '2028-07-27', '2028-09-09', 'https://km.baden-wuerttemberg.de/de/service/ferien');

-- ---------------------------------------------------------------- BY 2026/27 — Bayern
-- 6 Ferienbloecke, 12 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit',               '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', 'Allerheiligen',                           '2026-11-01', '2026-11-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BY', '2026/27', 'VACATION',       'Herbstferien (Allerheiligen)',            '2026-11-02', '2026-11-06', 'https://www.km.bayern.de/termine/ferien-und-feiertage'),
    ('BY', '2026/27', 'VACATION',       'Weihnachtsferien',                        '2026-12-24', '2027-01-08', 'https://www.km.bayern.de/termine/ferien-und-feiertage'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',                        '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',                        '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                                 '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', 'Heilige Drei Könige',                     '2027-01-06', '2027-01-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2026/27', 'VACATION',       'Winterferien (Ferien um Aschermittwoch)', '2027-02-08', '2027-02-12', 'https://www.km.bayern.de/termine/ferien-und-feiertage'),
    ('BY', '2026/27', 'VACATION',       'Osterferien',                             '2027-03-22', '2027-04-02', 'https://www.km.bayern.de/termine/ferien-und-feiertage'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                              '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',                             '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',                          '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',                     '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',                           '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2026/27', 'VACATION',       'Pfingstferien',                           '2027-05-18', '2027-05-28', 'https://www.km.bayern.de/termine/ferien-und-feiertage'),
    ('BY', '2026/27', 'PUBLIC_HOLIDAY', 'Fronleichnam',                            '2027-05-27', '2027-05-27', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('BY', '2026/27', 'VACATION',       'Sommerferien',                            '2027-08-02', '2027-09-13', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf');

-- ---------------------------------------------------------------- BY 2027/28 — Bayern
-- 6 Ferienbloecke, 12 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit',               '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', 'Allerheiligen',                           '2027-11-01', '2027-11-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2027/28', 'VACATION',       'Herbstferien (Allerheiligen)',            '2027-11-02', '2027-11-05', 'https://www.km.bayern.de/termine/ferien-und-feiertage'),
    ('BY', '2027/28', 'VACATION',       'Weihnachtsferien',                        '2027-12-24', '2028-01-07', 'https://www.km.bayern.de/termine/ferien-und-feiertage'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',                        '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',                        '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                                 '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', 'Heilige Drei Könige',                     '2028-01-06', '2028-01-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BY', '2027/28', 'VACATION',       'Winterferien (Ferien um Aschermittwoch)', '2028-02-28', '2028-03-03', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('BY', '2027/28', 'VACATION',       'Osterferien',                             '2028-04-10', '2028-04-21', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                              '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',                             '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',                          '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',                     '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',                           '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('BY', '2027/28', 'VACATION',       'Pfingstferien',                           '2028-06-06', '2028-06-16', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('BY', '2027/28', 'PUBLIC_HOLIDAY', 'Fronleichnam',                            '2028-06-15', '2028-06-15', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('BY', '2027/28', 'VACATION',       'Sommerferien',                            '2028-07-31', '2028-09-11', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf');

-- ---------------------------------------------------------------- HB 2026/27 — Bremen
-- 7 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('HB', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit',       '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HB', '2026/27', 'VACATION',       'Herbstferien',                    '2026-10-12', '2026-10-24', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HB', '2026/27', 'PUBLIC_HOLIDAY', 'Reformationstag',                 '2026-10-31', '2026-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HB', '2026/27', 'VACATION',       'Weihnachtsferien',                '2026-12-23', '2027-01-09', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HB', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',                '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HB', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',                '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HB', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                         '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HB', '2026/27', 'VACATION',       'Winterferien (Halbjahresferien)', '2027-02-01', '2027-02-02', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HB', '2026/27', 'VACATION',       'Osterferien',                     '2027-03-22', '2027-04-03', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HB', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                      '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HB', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',                     '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HB', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',                  '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HB', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',             '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HB', '2026/27', 'VACATION',       'Ferien um 1. Mai/Himmelfahrt',    '2027-05-07', '2027-05-07', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HB', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',                   '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HB', '2026/27', 'VACATION',       'Pfingstferien',                   '2027-05-18', '2027-05-18', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HB', '2026/27', 'VACATION',       'Sommerferien',                    '2027-07-08', '2027-08-18', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf');

-- ---------------------------------------------------------------- HB 2027/28 — Bremen
-- 7 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('HB', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit',       '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HB', '2027/28', 'VACATION',       'Herbstferien',                    '2027-10-18', '2027-10-30', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HB', '2027/28', 'PUBLIC_HOLIDAY', 'Reformationstag',                 '2027-10-31', '2027-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HB', '2027/28', 'VACATION',       'Weihnachtsferien',                '2027-12-23', '2028-01-08', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HB', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',                '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HB', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',                '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HB', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                         '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HB', '2027/28', 'VACATION',       'Winterferien (Halbjahresferien)', '2028-01-31', '2028-02-01', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HB', '2027/28', 'VACATION',       'Osterferien',                     '2028-04-10', '2028-04-22', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HB', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                      '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HB', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',                     '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HB', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',                  '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HB', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',             '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HB', '2027/28', 'VACATION',       'Ferien um 1. Mai/Himmelfahrt',    '2028-05-26', '2028-05-26', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HB', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',                   '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HB', '2027/28', 'VACATION',       'Pfingstferien',                   '2028-06-06', '2028-06-06', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HB', '2027/28', 'VACATION',       'Sommerferien',                    '2028-07-20', '2028-08-30', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf');

-- ---------------------------------------------------------------- HE 2026/27 — Hessen
-- 4 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('HE', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HE', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-05', '2026-10-17', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HE', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-23', '2027-01-12', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HE', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HE', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HE', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HE', '2026/27', 'VACATION',       'Osterferien',               '2027-03-22', '2027-04-02', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HE', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HE', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HE', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HE', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HE', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HE', '2026/27', 'PUBLIC_HOLIDAY', 'Fronleichnam',              '2027-05-27', '2027-05-27', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('HE', '2026/27', 'VACATION',       'Sommerferien',              '2027-06-28', '2027-08-06', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf');

-- ---------------------------------------------------------------- HE 2027/28 — Hessen
-- 4 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('HE', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HE', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-04', '2027-10-16', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HE', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-23', '2028-01-11', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HE', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HE', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HE', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HE', '2027/28', 'VACATION',       'Osterferien',               '2028-04-03', '2028-04-14', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HE', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HE', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HE', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HE', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HE', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HE', '2027/28', 'PUBLIC_HOLIDAY', 'Fronleichnam',              '2028-06-15', '2028-06-15', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('HE', '2027/28', 'VACATION',       'Sommerferien',              '2028-07-03', '2028-08-11', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf');

-- ---------------------------------------------------------------- HH 2026/27 — Hamburg
-- 6 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('HH', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HH', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-19', '2026-10-30', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HH', '2026/27', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2026-10-31', '2026-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HH', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-21', '2027-01-01', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HH', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HH', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('HH', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HH', '2026/27', 'VACATION',       'Winterferien',              '2027-01-29', '2027-01-29', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HH', '2026/27', 'VACATION',       'Frühjahrsferien',           '2027-03-01', '2027-03-12', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HH', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HH', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HH', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HH', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HH', '2026/27', 'VACATION',       'Pfingstferien',             '2027-05-07', '2027-05-14', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('HH', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HH', '2026/27', 'VACATION',       'Sommerferien',              '2027-07-01', '2027-08-11', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf');

-- ---------------------------------------------------------------- HH 2027/28 — Hamburg
-- 6 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('HH', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HH', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-11', '2027-10-22', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HH', '2027/28', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2027-10-31', '2027-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HH', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-20', '2027-12-31', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HH', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HH', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('HH', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HH', '2027/28', 'VACATION',       'Winterferien',              '2028-01-28', '2028-01-28', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HH', '2027/28', 'VACATION',       'Frühjahrsferien',           '2028-03-06', '2028-03-17', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HH', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HH', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HH', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HH', '2027/28', 'VACATION',       'Pfingstferien',             '2028-05-22', '2028-05-26', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('HH', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HH', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('HH', '2027/28', 'VACATION',       'Sommerferien',              '2028-07-03', '2028-08-11', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf');

-- ---------------------------------------------------------------- MV 2026/27 — Mecklenburg-Vorpommern
-- 6 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('MV', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-15', '2026-10-24', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2026-10-31', '2026-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('MV', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-21', '2027-01-02', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2026/27', 'VACATION',       'Winterferien',              '2027-02-08', '2027-02-19', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', 'Internationaler Frauentag', '2027-03-08', '2027-03-08', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2026/27', 'VACATION',       'Osterferien',               '2027-03-24', '2027-04-02', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2026/27', 'VACATION',       'Pfingstferien',             '2027-05-14', '2027-05-18', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('MV', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2026/27', 'VACATION',       'Sommerferien',              '2027-07-05', '2027-08-14', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf');

-- ---------------------------------------------------------------- MV 2027/28 — Mecklenburg-Vorpommern
-- 6 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-14', '2027-10-23', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2027-10-31', '2027-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-22', '2028-01-04', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('MV', '2027/28', 'VACATION',       'Winterferien',              '2028-02-05', '2028-02-18', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', 'Internationaler Frauentag', '2028-03-08', '2028-03-08', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('MV', '2027/28', 'VACATION',       'Osterferien',               '2028-04-12', '2028-04-21', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('MV', '2027/28', 'VACATION',       'Pfingstferien',             '2028-06-02', '2028-06-06', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('MV', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('MV', '2027/28', 'VACATION',       'Sommerferien',              '2028-06-26', '2028-08-05', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf');

-- ---------------------------------------------------------------- NI 2026/27 — Niedersachsen
-- 7 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('NI', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit',    '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('NI', '2026/27', 'VACATION',       'Herbstferien',                 '2026-10-12', '2026-10-24', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2026/27', 'PUBLIC_HOLIDAY', 'Reformationstag',              '2026-10-31', '2026-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('NI', '2026/27', 'VACATION',       'Weihnachtsferien',             '2026-12-23', '2027-01-09', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',             '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('NI', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',             '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('NI', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                      '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NI', '2026/27', 'VACATION',       'Halbjahresferien',             '2027-02-01', '2027-02-02', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2026/27', 'VACATION',       'Osterferien',                  '2027-03-22', '2027-04-03', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                   '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NI', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',                  '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NI', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',               '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NI', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',          '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NI', '2026/27', 'VACATION',       'Ferien um 1. Mai/Himmelfahrt', '2027-05-07', '2027-05-07', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',                '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NI', '2026/27', 'VACATION',       'Pfingstferien',                '2027-05-18', '2027-05-18', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2026/27', 'VACATION',       'Sommerferien',                 '2027-07-08', '2027-08-18', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf');

-- ---------------------------------------------------------------- NI 2027/28 — Niedersachsen
-- 7 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('NI', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit',    '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NI', '2027/28', 'VACATION',       'Herbstferien',                 '2027-10-16', '2027-10-30', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2027/28', 'PUBLIC_HOLIDAY', 'Reformationstag',              '2027-10-31', '2027-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NI', '2027/28', 'VACATION',       'Weihnachtsferien',             '2027-12-23', '2028-01-08', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',             '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NI', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',             '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NI', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                      '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NI', '2027/28', 'VACATION',       'Halbjahresferien',             '2028-01-31', '2028-02-01', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2027/28', 'VACATION',       'Osterferien',                  '2028-04-10', '2028-04-22', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                   '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NI', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',                  '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NI', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',               '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NI', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',          '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NI', '2027/28', 'VACATION',       'Ferien um 1. Mai/Himmelfahrt', '2028-05-26', '2028-05-26', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',                '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NI', '2027/28', 'VACATION',       'Pfingstferien',                '2028-06-06', '2028-06-06', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf'),
    ('NI', '2027/28', 'VACATION',       'Sommerferien',                 '2028-07-20', '2028-08-30', 'https://www.mk.niedersachsen.de/download/98089/Ferienuebersicht_für_Niedersachsen_Schuljahr_2024_25_-_2029_30.pdf');

-- ---------------------------------------------------------------- NW 2026/27 — Nordrhein-Westfalen
-- 5 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('NW', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-17', '2026-10-31', 'https://www.schulministerium.nrw/service/ferienordnung-für-nordrhein-westfalen-für-die-schuljahre-bis-202930'),
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', 'Allerheiligen',             '2026-11-01', '2026-11-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('NW', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-23', '2027-01-06', 'https://www.schulministerium.nrw/service/ferienordnung-für-nordrhein-westfalen-für-die-schuljahre-bis-202930'),
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NW', '2026/27', 'VACATION',       'Osterferien',               '2027-03-22', '2027-04-03', 'https://www.schulministerium.nrw/service/ferienordnung-für-nordrhein-westfalen-für-die-schuljahre-bis-202930'),
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NW', '2026/27', 'VACATION',       'Pfingstferien',             '2027-05-18', '2027-05-18', 'https://www.schulministerium.nrw/service/ferienordnung-für-nordrhein-westfalen-für-die-schuljahre-bis-202930'),
    ('NW', '2026/27', 'PUBLIC_HOLIDAY', 'Fronleichnam',              '2027-05-27', '2027-05-27', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('NW', '2026/27', 'VACATION',       'Sommerferien',              '2027-07-19', '2027-08-31', 'https://www.schulministerium.nrw/service/ferienordnung-für-nordrhein-westfalen-für-die-schuljahre-bis-202930');

-- ---------------------------------------------------------------- NW 2027/28 — Nordrhein-Westfalen
-- 4 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NW', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-23', '2027-11-06', 'https://www.schulministerium.nrw/service/ferienordnung-für-nordrhein-westfalen-für-die-schuljahre-bis-202930'),
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', 'Allerheiligen',             '2027-11-01', '2027-11-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NW', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-24', '2028-01-08', 'https://www.schulministerium.nrw/service/ferienordnung-für-nordrhein-westfalen-für-die-schuljahre-bis-202930'),
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NW', '2027/28', 'VACATION',       'Osterferien',               '2028-04-10', '2028-04-22', 'https://www.schulministerium.nrw/service/ferienordnung-für-nordrhein-westfalen-für-die-schuljahre-bis-202930'),
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('NW', '2027/28', 'PUBLIC_HOLIDAY', 'Fronleichnam',              '2028-06-15', '2028-06-15', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('NW', '2027/28', 'VACATION',       'Sommerferien',              '2028-07-10', '2028-08-22', 'https://www.schulministerium.nrw/service/ferienordnung-für-nordrhein-westfalen-für-die-schuljahre-bis-202930');

-- ---------------------------------------------------------------- RP 2026/27 — Rheinland-Pfalz
-- 4 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('RP', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-05', '2026-10-16', 'https://bm.rlp.de/service/ferientermine'),
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', 'Allerheiligen',             '2026-11-01', '2026-11-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('RP', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-23', '2027-01-08', 'https://bm.rlp.de/service/ferientermine'),
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('RP', '2026/27', 'VACATION',       'Osterferien',               '2027-03-22', '2027-04-02', 'https://bm.rlp.de/service/ferientermine'),
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('RP', '2026/27', 'PUBLIC_HOLIDAY', 'Fronleichnam',              '2027-05-27', '2027-05-27', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('RP', '2026/27', 'VACATION',       'Sommerferien',              '2027-06-28', '2027-08-06', 'https://bm.rlp.de/service/ferientermine');

-- ---------------------------------------------------------------- RP 2027/28 — Rheinland-Pfalz
-- 4 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('RP', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-04', '2027-10-15', 'https://bm.rlp.de/service/ferientermine'),
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', 'Allerheiligen',             '2027-11-01', '2027-11-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('RP', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-23', '2028-01-07', 'https://bm.rlp.de/service/ferientermine'),
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('RP', '2027/28', 'VACATION',       'Osterferien',               '2028-04-10', '2028-04-21', 'https://bm.rlp.de/service/ferientermine'),
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('RP', '2027/28', 'PUBLIC_HOLIDAY', 'Fronleichnam',              '2028-06-15', '2028-06-15', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('RP', '2027/28', 'VACATION',       'Sommerferien',              '2028-07-03', '2028-08-11', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf');

-- ---------------------------------------------------------------- SH 2026/27 — Schleswig-Holstein
-- 4 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('SH', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SH', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-12', '2026-10-24', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('SH', '2026/27', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2026-10-31', '2026-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SH', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-21', '2027-01-06', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('SH', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SH', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SH', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SH', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SH', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SH', '2026/27', 'VACATION',       'Osterferien',               '2027-03-30', '2027-04-10', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('SH', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SH', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SH', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SH', '2026/27', 'VACATION',       'Sommerferien',              '2027-07-03', '2027-08-14', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf');

-- ---------------------------------------------------------------- SH 2027/28 — Schleswig-Holstein
-- 4 Ferienbloecke, 10 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('SH', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SH', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-11', '2027-10-23', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('SH', '2027/28', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2027-10-31', '2027-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SH', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-23', '2028-01-08', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('SH', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SH', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SH', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SH', '2027/28', 'VACATION',       'Osterferien',               '2028-04-03', '2028-04-15', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('SH', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SH', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SH', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SH', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SH', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SH', '2027/28', 'VACATION',       'Sommerferien',              '2028-06-24', '2028-08-04', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf');

-- ---------------------------------------------------------------- SL 2026/27 — Saarland
-- 5 Ferienbloecke, 12 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', 'Mariä Himmelfahrt',         '2026-08-15', '2026-08-15', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SL', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-05', '2026-10-16', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', 'Allerheiligen',             '2026-11-01', '2026-11-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SL', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-21', '2026-12-31', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2026/27', 'VACATION',       'Winterferien',              '2027-02-08', '2027-02-12', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2026/27', 'VACATION',       'Osterferien',               '2027-03-30', '2027-04-09', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2026/27', 'PUBLIC_HOLIDAY', 'Fronleichnam',              '2027-05-27', '2027-05-27', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('SL', '2026/27', 'VACATION',       'Sommerferien',              '2027-06-28', '2027-08-06', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf');

-- ---------------------------------------------------------------- SL 2027/28 — Saarland
-- 5 Ferienbloecke, 12 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', 'Mariä Himmelfahrt',         '2027-08-15', '2027-08-15', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-04', '2027-10-15', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', 'Allerheiligen',             '2027-11-01', '2027-11-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-20', '2027-12-31', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SL', '2027/28', 'VACATION',       'Winterferien',              '2028-02-21', '2028-02-29', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('SL', '2027/28', 'VACATION',       'Osterferien',               '2028-04-12', '2028-04-21', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SL', '2027/28', 'PUBLIC_HOLIDAY', 'Fronleichnam',              '2028-06-15', '2028-06-15', 'https://www.serafinum.de/ratgeber/fronleichnam/'),
    ('SL', '2027/28', 'VACATION',       'Sommerferien',              '2028-07-03', '2028-08-11', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf');

-- ---------------------------------------------------------------- SN 2026/27 — Sachsen
-- 6 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SN', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-12', '2026-10-24', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html'),
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2026-10-31', '2026-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', 'Buß- und Bettag',           '2026-11-18', '2026-11-18', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SN', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-23', '2027-01-02', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html'),
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2026/27', 'VACATION',       'Winterferien',              '2027-02-08', '2027-02-19', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html'),
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2026/27', 'VACATION',       'Osterferien',               '2027-03-26', '2027-04-02', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html'),
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2026/27', 'VACATION',       'Pfingstferien',             '2027-05-15', '2027-05-18', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html'),
    ('SN', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2026/27', 'VACATION',       'Sommerferien',              '2027-07-10', '2027-08-20', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html');

-- ---------------------------------------------------------------- SN 2027/28 — Sachsen
-- 5 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-11', '2027-10-23', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html'),
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2027-10-31', '2027-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', 'Buß- und Bettag',           '2027-11-17', '2027-11-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-23', '2028-01-01', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html'),
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SN', '2027/28', 'VACATION',       'Winterferien',              '2028-02-14', '2028-02-26', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html'),
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SN', '2027/28', 'VACATION',       'Osterferien',               '2028-04-14', '2028-04-22', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html'),
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SN', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('SN', '2027/28', 'VACATION',       'Sommerferien',              '2028-07-22', '2028-09-01', 'https://www.schule.sachsen.de/schuljahrestermine-4793.html');

-- ---------------------------------------------------------------- ST 2026/27 — Sachsen-Anhalt
-- 6 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('ST', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-19', '2026-10-30', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2026-10-31', '2026-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('ST', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-21', '2027-01-02', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', 'Heilige Drei Könige',       '2027-01-06', '2027-01-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2026/27', 'VACATION',       'Winterferien',              '2027-02-01', '2027-02-06', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('ST', '2026/27', 'VACATION',       'Osterferien',               '2027-03-22', '2027-03-27', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2026/27', 'VACATION',       'Pfingstferien',             '2027-05-15', '2027-05-22', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('ST', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2026/27', 'VACATION',       'Sommerferien',              '2027-07-10', '2027-08-20', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf');

-- ---------------------------------------------------------------- ST 2027/28 — Sachsen-Anhalt
-- 6 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-18', '2027-10-23', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2027-10-31', '2027-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-20', '2027-12-31', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', 'Heilige Drei Könige',       '2028-01-06', '2028-01-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('ST', '2027/28', 'VACATION',       'Winterferien',              '2028-02-07', '2028-02-12', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('ST', '2027/28', 'VACATION',       'Osterferien',               '2028-04-10', '2028-04-22', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('ST', '2027/28', 'VACATION',       'Pfingstferien',             '2028-06-03', '2028-06-10', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('ST', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('ST', '2027/28', 'VACATION',       'Sommerferien',              '2028-07-22', '2028-09-01', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf');

-- ---------------------------------------------------------------- TH 2026/27 — Thueringen
-- 5 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', 'Weltkindertag',             '2026-09-20', '2026-09-20', 'https://www.kirchenrecht-ekm.de/document/9627'),
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2026-10-03', '2026-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('TH', '2026/27', 'VACATION',       'Herbstferien',              '2026-10-12', '2026-10-24', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2026-10-31', '2026-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('TH', '2026/27', 'VACATION',       'Weihnachtsferien',          '2026-12-23', '2027-01-02', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2026-12-25', '2026-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2026-12-26', '2026-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2026.html'),
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2027-01-01', '2027-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('TH', '2026/27', 'VACATION',       'Winterferien',              '2027-02-01', '2027-02-06', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('TH', '2026/27', 'VACATION',       'Osterferien',               '2027-03-22', '2027-04-03', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf'),
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2027-03-26', '2027-03-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2027-03-29', '2027-03-29', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2027-05-01', '2027-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2027-05-06', '2027-05-06', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('TH', '2026/27', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2027-05-17', '2027-05-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('TH', '2026/27', 'VACATION',       'Sommerferien',              '2027-07-10', '2027-08-20', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2026_27.pdf');

-- ---------------------------------------------------------------- TH 2027/28 — Thueringen
-- 5 Ferienbloecke, 11 Feiertage
INSERT INTO holiday_preset (state, school_year, kind, label, date_from, date_to, source) VALUES
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', 'Weltkindertag',             '2027-09-20', '2027-09-20', 'https://www.kirchenrecht-ekm.de/document/9627'),
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Deutschen Einheit', '2027-10-03', '2027-10-03', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('TH', '2027/28', 'VACATION',       'Herbstferien',              '2027-10-09', '2027-10-23', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', 'Reformationstag',           '2027-10-31', '2027-10-31', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('TH', '2027/28', 'VACATION',       'Weihnachtsferien',          '2027-12-23', '2027-12-31', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', '1. Weihnachtstag',          '2027-12-25', '2027-12-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', '2. Weihnachtstag',          '2027-12-26', '2027-12-26', 'https://www.kalenderpedia.de/feiertage/feiertage-2027.html'),
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', 'Neujahr',                   '2028-01-01', '2028-01-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('TH', '2027/28', 'VACATION',       'Winterferien',              '2028-02-07', '2028-02-12', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('TH', '2027/28', 'VACATION',       'Osterferien',               '2028-04-03', '2028-04-15', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf'),
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', 'Karfreitag',                '2028-04-14', '2028-04-14', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', 'Ostermontag',               '2028-04-17', '2028-04-17', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', 'Tag der Arbeit',            '2028-05-01', '2028-05-01', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', 'Christi Himmelfahrt',       '2028-05-25', '2028-05-25', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('TH', '2027/28', 'PUBLIC_HOLIDAY', 'Pfingstmontag',             '2028-06-05', '2028-06-05', 'https://www.kalenderpedia.de/feiertage/feiertage-2028.html'),
    ('TH', '2027/28', 'VACATION',       'Sommerferien',              '2028-07-22', '2028-09-01', 'https://www.kmk.org/fileadmin/Dateien/pdf/Ferienkalender/FER2027_28.pdf');
