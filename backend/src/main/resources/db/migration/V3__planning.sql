-- V3 — Plan und Unterrichtsstunden.
--
-- Modul planning : timetable, lesson
--
-- Es gibt hoechstens einen Entwurf und hoechstens einen aktiven Plan; alles Aeltere ist ARCHIVED.
-- Konflikte werden NICHT gespeichert, sondern bei jedem Lesen berechnet (E-31) — deshalb gibt es
-- hier keine Konflikttabelle.

CREATE TABLE timetable (
    id                   bigserial PRIMARY KEY,
    status               varchar(10) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    created_at           timestamptz NOT NULL,
    accepted_at          timestamptz,
    based_on_id          bigint REFERENCES timetable (id),
    solve_started_at     timestamptz,
    solve_limit_seconds  int CHECK (solve_limit_seconds >= 1),
    -- Ein Entwurf ist noch nicht angenommen und hat deshalb keinen Annahmezeitpunkt.
    CONSTRAINT timetable_draft_has_no_accepted_at
        CHECK (status <> 'DRAFT' OR accepted_at IS NULL)
);

-- Genau ein Entwurf und genau ein aktiver Plan: Teilindizes statt Anwendungslogik.
-- Ein zweiter Entwurf scheitert damit schon in der Datenbank (409 draft-exists in der API).
CREATE UNIQUE INDEX timetable_one_draft ON timetable (status) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX timetable_one_active ON timetable (status) WHERE status = 'ACTIVE';

-- Eine Unterrichtsstunde. Klasse, Fach UND Lehrkraft folgen aus class_subject; die Stunde traegt
-- keine eigene Lehrkraft. pinned = festgelegt (W-9): Handaufbau und Verschieben setzen es auf true.
--
-- Invariante „belegt oder offen": entweder sind Zeitfenster und Raum beide gesetzt oder beide leer.
-- Einen dritten Zustand gibt es nicht (T-W8.1).
CREATE TABLE lesson (
    id                bigserial PRIMARY KEY,
    timetable_id      bigint  NOT NULL REFERENCES timetable (id) ON DELETE CASCADE,
    class_subject_id  bigint  NOT NULL REFERENCES class_subject (id),
    pinned            boolean NOT NULL DEFAULT false,
    timeslot_id       bigint REFERENCES timeslot (id),
    room_id           bigint REFERENCES room (id),
    CONSTRAINT lesson_assigned_or_open
        CHECK ((timeslot_id IS NULL) = (room_id IS NULL))
);

CREATE INDEX lesson_timetable_idx ON lesson (timetable_id);
CREATE INDEX lesson_class_subject_idx ON lesson (class_subject_id);
CREATE INDEX lesson_timeslot_idx ON lesson (timeslot_id);
