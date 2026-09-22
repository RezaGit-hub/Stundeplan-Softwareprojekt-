-- V13 — Abwesenheitsmeldungen
--
-- Neues Modul absences: eine Lehrkraft meldet sich krank (oder aus anderem Grund abwesend),
-- die Planung sieht die Meldungen und kann sie in ein Ereignis umwandeln. Bis V12 gab es
-- keine Tabelle dafuer; V1-V12 bleiben unveraendert (Flyway-Pruefsumme).
--
-- read heisst die Spalte is_read: "read" ist in SQL ein reserviertes Wort.

CREATE TABLE absence (
     id         bigserial PRIMARY KEY,
     teacher_id bigint  NOT NULL REFERENCES teacher (id),
     date_from  date    NOT NULL,
     date_to    date    NOT NULL,
     reason     text    NOT NULL,
     active     boolean NOT NULL DEFAULT true,
     seen       boolean NOT NULL DEFAULT false,
     event_id   bigint REFERENCES change_event (id),
     CONSTRAINT absence_date_range CHECK (date_to >= date_from)
);

CREATE INDEX absence_teacher_idx ON absence (teacher_id);
CREATE INDEX absence_active_idx  ON absence (active) WHERE active;
CREATE INDEX absence_event_idx ON absence (event_id);