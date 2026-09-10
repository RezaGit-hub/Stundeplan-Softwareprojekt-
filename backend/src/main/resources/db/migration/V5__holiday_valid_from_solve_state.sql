-- V5 — Gueltig ab, Solver-Zustand, Ferien und Feiertage.
--
-- Modul planning     : timetable.valid_from, timetable.solve_last_score, timetable.solve_error
-- Modul substitution : holiday
--
-- Wortlaut aus docs/VERTRAG.md Abschnitt 6 („Migration V5"), Lieferbestandteil der Phase backend1.
-- V1–V4 bleiben unveraendert (Flyway-Pruefsumme). Wer eine Datenbank aus der Schablone hat:
-- `docker compose down -v`.

-- valid_from       : Kann-Option „Gueltig ab" beim Annehmen (POST /api/draft/accept {validFrom?}),
--                    erst nach Rueckfrage R1 am 08.09. — die Spalte steht schon, damit backend2
--                    keine zweite Migration braucht. Leer = ab sofort.
-- solve_last_score : letzte Bewertung des Solverlaufs als Text (Timefold-Score, z. B. „0hard/-12soft").
-- solve_error      : Meldung eines abgebrochenen Laufs; leer, solange nichts schiefging.
ALTER TABLE timetable
    ADD COLUMN valid_from       date,
    ADD COLUMN solve_last_score varchar(60),
    ADD COLUMN solve_error      varchar(500);

-- Ferien und Feiertage. Sie haengen am Kalendertag, nie am Plan (wie alles Datierte in V4):
-- der WeekResolver blendet Stunden in diesem Zeitraum aus, der Regelplan bleibt unberuehrt.
CREATE TABLE holiday (
    id        bigserial PRIMARY KEY,
    name      varchar(120) NOT NULL,
    date_from date         NOT NULL,
    date_to   date         NOT NULL,
    kind      varchar(20)  NOT NULL CHECK (kind IN ('VACATION', 'PUBLIC_HOLIDAY')),
    -- Ein Zeitraum endet nie vor seinem Anfang; ein einzelner Tag hat date_from = date_to.
    CONSTRAINT holiday_period CHECK (date_to >= date_from)
);
