-- V11 — Mehrere Entwuerfe nebeneinander (#103, T2-3.4, E-52).
--
-- Modul planning : timetable.name, Teilindex timetable_one_draft entfaellt
--
-- Bis V10 sicherte der Teilindex timetable_one_draft, dass es hoechstens einen Entwurf gibt. Jetzt darf
-- die Planung mehrere Entwuerfe zwischenspeichern („Variante A", „Variante B") und einen davon annehmen.
-- Hoechstens ein aktiver Plan bleibt Gesetz: timetable_one_active bleibt stehen.
-- Der Name unterscheidet die Entwuerfe in der Oberflaeche; aktive und archivierte Plaene haben keinen.
-- V1–V10 bleiben unveraendert (Flyway-Pruefsumme).
DROP INDEX timetable_one_draft;
ALTER TABLE timetable ADD COLUMN name varchar(80);
UPDATE timetable SET name = 'Entwurf' WHERE status = 'DRAFT';
