-- V9 — Woechentliche Sperrzeiten eines Raums (#133, T2-3.3, E-54).
--
-- Modul masterdata : room_blocked_timeslot
--
-- Bisher gab es Sperrzeiten nur bei Lehrkraeften (teacher_blocked_timeslot, V1). Ein Raum konnte nur
-- datiert gesperrt werden (change_event ROOM_BLOCKED im Modul substitution). Diese Tabelle traegt die
-- wiederkehrende Sperre je Zeitfenster — dieselbe Form wie bei der Lehrkraft: immer hart, unabhaengig
-- vom Regelwerk (Solver H8 roomBlockedTime, ConflictChecker ROOM_BLOCKED_TIME).
-- V1–V8 bleiben unveraendert (Flyway-Pruefsumme).
CREATE TABLE room_blocked_timeslot (
    room_id      bigint NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    timeslot_id  bigint NOT NULL REFERENCES timeslot (id),
    PRIMARY KEY (room_id, timeslot_id)
);
