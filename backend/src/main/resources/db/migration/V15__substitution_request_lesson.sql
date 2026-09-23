-- V15 — Vertretungsanfragen auf die konkrete Stunde umstellen
--
-- Bis V14 zeigte eine Anfrage auf ein change_event. Das war falsch: ein Ereignis betrifft
-- mehrere Stunden, und mehrere Ereignisse koennen dieselbe Stunde offen machen (Krankmeldung
-- UND Raumsperrung). Der stabile Griff ist die Stunde selbst — (lesson_id, date), derselbe
-- Schluessel wie in lesson_exception (V4).
--
-- created_at/answered_at bleiben weg: fuer den Ablauf (UNKNOWN -> ACCEPTED/DECLINED) reicht
-- der Status, und ein Zeitstempel laesst sich per V16 nachreichen, wenn er gebraucht wird.
--
-- V1-V14 bleiben unveraendert (Flyway-Pruefsumme).

ALTER TABLE substitution_request DROP CONSTRAINT substitution_request_unique;
ALTER TABLE substitution_request DROP COLUMN event_id;
ALTER TABLE substitution_request ADD COLUMN lesson_id bigint NOT NULL;
ALTER TABLE substitution_request ADD COLUMN date      date   NOT NULL;
ALTER TABLE substitution_request ADD CONSTRAINT substitution_request_unique
    UNIQUE (teacher_id, lesson_id, date);

CREATE INDEX substitution_request_lesson_idx ON substitution_request (lesson_id);