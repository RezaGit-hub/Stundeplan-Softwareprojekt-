-- V14 — Vertretungsanfragen an Lehrkraefte
--
-- Bevor die Planung eine Vertretung zuweist, fragt sie die Lehrkraft per App. Eine Zeile je
-- Anfrage; status laeuft UNKNOWN -> ACCEPTED/DECLINED. Wie change_event.kind (V4) als varchar mit
-- CHECK-Liste: ein Postgres-Enum waere nur mit ALTER TYPE erweiterbar und bindet JPA an einen
-- herstellerabhaengigen Typ. V1-V13 bleiben unveraendert (Flyway-Pruefsumme).

CREATE TABLE substitution_request (
    id         bigserial PRIMARY KEY,
    teacher_id bigint NOT NULL REFERENCES teacher (id),
    event_id   bigint NOT NULL REFERENCES change_event (id),
    status     varchar(20) NOT NULL DEFAULT 'UNKNOWN'
      CONSTRAINT substitution_request_status_check
          CHECK (status IN ('UNKNOWN', 'ACCEPTED', 'DECLINED')),

    -- Eine Lehrkraft wird zu einem Ereignis nur einmal gefragt.
    CONSTRAINT substitution_request_unique UNIQUE (teacher_id, event_id)
);

CREATE INDEX substitution_request_teacher_idx ON substitution_request (teacher_id);
CREATE INDEX substitution_request_event_idx   ON substitution_request (event_id);
CREATE INDEX substitution_request_open_idx    ON substitution_request (teacher_id)
    WHERE status = 'UNKNOWN';