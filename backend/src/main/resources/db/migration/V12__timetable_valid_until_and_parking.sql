-- V12 — Gueltigkeitszeitraum und Ablage (#115, #116, T2-3.5, E-40).
--
-- Modul planning : timetable.valid_until, timetable.finished, Ausschlussbedingung statt Teilindex
--
-- Bis V11 galt: hoechstens EIN aktiver Plan (Teilindex timetable_one_active aus V3). Das trug die
-- Kann-Option „Gueltig ab" nicht zu Ende: wer einen Plan fuer das naechste Halbjahr annimmt, verlor
-- damit sofort den laufenden. Ab hier darf es mehrere aktive Plaene geben — sie duerfen sich nur
-- nicht ueberschneiden. Der Zeitraum ist [valid_from, valid_until], beide Grenzen einschliesslich;
-- valid_from leer = „gilt seit jeher" (in der Anwendung: der Tag der Annahme), valid_until leer =
-- „unbegrenzt". ARCHIVED heisst seitdem „ersetzt oder abgelaufen"; solche Plaene bleiben stehen,
-- damit Wochensichten in der Vergangenheit und datierte Ausnahmen weiter aufloesen.
--
-- finished ist die Ablage (#116): ein Entwurf wird eingefroren und spaeter angenommen. Nur fuer
-- DRAFT bedeutsam, deshalb keine Bedingung darauf.
--
-- V1–V11 bleiben unveraendert (Flyway-Pruefsumme).

ALTER TABLE timetable ADD COLUMN valid_until date;
ALTER TABLE timetable ADD COLUMN finished boolean NOT NULL DEFAULT false;

-- Bestand: ein archivierter Plan endete bisher stillschweigend dort, wo der naechste angenommene
-- Plan begann. Genau das wird jetzt hingeschrieben, damit die Zeitleiste dieselbe bleibt wie vor
-- dieser Migration. Der aktive Plan behaelt valid_until NULL — er gilt unbegrenzt weiter.
WITH s AS (
    SELECT id,
           COALESCE(valid_from, (accepted_at AT TIME ZONE 'Europe/Berlin')::date) AS start
      FROM timetable
     WHERE status IN ('ACTIVE', 'ARCHIVED')
),
n AS (
    SELECT id, LEAD(start) OVER (ORDER BY start, id) AS next_start FROM s
)
UPDATE timetable t
   SET valid_until = n.next_start - 1
  FROM n
 WHERE t.id = n.id
   AND t.status = 'ARCHIVED'
   AND n.next_start IS NOT NULL;

DROP INDEX timetable_one_active;

-- Ein aktiver Plan endet nie vor seinem Beginn. Fuer ARCHIVED gilt das bewusst nicht: beim Annehmen
-- „ab sofort" bekommt ein noch nicht begonnener Vorgaenger valid_until = heute - 1 und damit einen
-- leeren Zeitraum — er soll an keinem Tag mehr aufloesen.
ALTER TABLE timetable ADD CONSTRAINT timetable_active_valid_range
    CHECK (status <> 'ACTIVE' OR valid_from IS NULL OR valid_until IS NULL OR valid_until >= valid_from);

-- Die eigentliche Regel: zwei aktive Plaene teilen sich keinen Tag. Teil-Ausschlussbedingung, also
-- werden Zeilen ausserhalb status = 'ACTIVE' gar nicht erst indiziert; ein leerer Zeitraum bei
-- ARCHIVED ist deshalb unschaedlich. btree_gist wird nicht gebraucht: verglichen wird ein einziger
-- Ausdruck vom Typ daterange mit &&, und dafuer reicht die eingebaute GiST-Operatorklasse (gemessen
-- gegen postgres:16-alpine am 16.09.). Das ist Absicht — CREATE EXTENSION verlangt Rechte, die der
-- Betriebsbenutzer nicht haben muss.
ALTER TABLE timetable ADD CONSTRAINT timetable_active_no_overlap
    EXCLUDE USING gist ((daterange(valid_from, valid_until, '[]')) WITH &&) WHERE (status = 'ACTIVE');
