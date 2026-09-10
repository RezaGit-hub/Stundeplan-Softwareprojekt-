-- V4 — Aenderungsereignisse, datierte Ausnahmen, Aenderungsverlauf.
--
-- Modul substitution : change_event, lesson_exception, exception_event
-- Modul changelog    : changelog_entry
--
-- Der Regelplan bleibt unveraendert: alles Datierte haengt am Kalendertag, nie am Plan.
-- Vertretungsbedarf wird nicht gespeichert, sondern vom WeekResolver berechnet.

-- Ausfall einer Lehrkraft, Sperrung eines Raums, Zusatzstunde — immer mit Zeitraum und Anlass (W-5).
CREATE TABLE change_event (
    id             bigserial PRIMARY KEY,
    kind           varchar(20)  NOT NULL
                   CHECK (kind IN ('TEACHER_ABSENCE', 'ROOM_BLOCKED', 'EXTRA_LESSON')),
    teacher_id     bigint REFERENCES teacher (id),
    room_id        bigint REFERENCES room (id),
    date_from      date         NOT NULL,
    date_to        date         NOT NULL,
    reason         varchar(255) NOT NULL,
    created_at     timestamptz  NOT NULL,
    created_by_id  bigint       NOT NULL REFERENCES user_account (id),
    CONSTRAINT change_event_period CHECK (date_to >= date_from),
    -- Ein Ausfall nennt die Lehrkraft, eine Sperrung den Raum. Die Zusatzstunde braucht beides nicht.
    CONSTRAINT change_event_subject_matches_kind CHECK (
        (kind = 'TEACHER_ABSENCE' AND teacher_id IS NOT NULL AND room_id IS NULL)
     OR (kind = 'ROOM_BLOCKED'    AND room_id    IS NOT NULL AND teacher_id IS NULL)
     OR (kind = 'EXTRA_LESSON'))
);

CREATE INDEX change_event_period_idx ON change_event (date_from, date_to);
CREATE INDEX change_event_teacher_idx ON change_event (teacher_id);
CREATE INDEX change_event_room_idx ON change_event (room_id);

-- Datierte Ausnahme zu einer Stunde: Vertretung (Lehrkraft und/oder Raum in EINER Zeile, T-W6.1),
-- Verlegung, Ausfall oder eine zusaetzlich angesetzte Stunde. Bei ADDED gibt es keine Regelplanstunde,
-- deshalb ist lesson_id dort leer und Klasse, Fach, Datum und Zeitfenster stehen in der Zeile selbst.
CREATE TABLE lesson_exception (
    id                     bigserial PRIMARY KEY,
    lesson_id              bigint REFERENCES lesson (id) ON DELETE CASCADE,
    date                   date        NOT NULL,
    kind                   varchar(20) NOT NULL
                           CHECK (kind IN ('SUBSTITUTION', 'MOVE', 'CANCEL', 'ADDED')),
    substitute_teacher_id  bigint REFERENCES teacher (id),
    substitute_room_id     bigint REFERENCES room (id),
    new_date               date,
    new_timeslot_id        bigint REFERENCES timeslot (id),
    added_class_id         bigint REFERENCES school_class (id),
    added_subject_id       bigint REFERENCES subject (id),
    created_at             timestamptz NOT NULL,
    created_by_id          bigint      NOT NULL REFERENCES user_account (id),
    -- Je Stunde und Kalendertag hoechstens eine Ausnahme.
    CONSTRAINT lesson_exception_unique UNIQUE (lesson_id, date),
    CONSTRAINT lesson_exception_lesson_required
        CHECK (kind = 'ADDED' OR lesson_id IS NOT NULL),
    CONSTRAINT lesson_exception_added_needs_class_and_subject
        CHECK (kind <> 'ADDED' OR (added_class_id IS NOT NULL AND added_subject_id IS NOT NULL))
);

CREATE INDEX lesson_exception_date_idx ON lesson_exception (date);
CREATE INDEX lesson_exception_lesson_idx ON lesson_exception (lesson_id);

-- Eine Ausnahme kann mehrere Anlaesse haben (Leitszenario: Ausfall der Lehrkraft UND Raumsperrung).
CREATE TABLE exception_event (
    exception_id  bigint NOT NULL REFERENCES lesson_exception (id) ON DELETE CASCADE,
    event_id      bigint NOT NULL REFERENCES change_event (id) ON DELETE CASCADE,
    PRIMARY KEY (exception_id, event_id)
);

-- Aenderungsverlauf (W-7). Nur Einfuegen und Lesen: es gibt bewusst keinen Endpunkt zum Aendern
-- oder Loeschen. Belegungen stehen als Textschnappschuss, damit der Eintrag auch dann lesbar bleibt,
-- wenn eine Lehrkraft spaeter deaktiviert oder ein Raum umbenannt wird.
CREATE TABLE changelog_entry (
    id              bigserial PRIMARY KEY,
    timestamp       timestamptz  NOT NULL,
    action          varchar(30)  NOT NULL
                    CHECK (action IN ('EXCEPTION_CREATED', 'EXCEPTION_DELETED', 'TIMETABLE_ACCEPTED')),
    date            date,
    position        int,
    old_assignment  varchar(255),
    new_assignment  varchar(255),
    reasons         varchar(1000),
    user_id         bigint       NOT NULL REFERENCES user_account (id),
    -- Filterspalten fuer GET /api/changelog; alle drei duerfen leer sein.
    lesson_id       bigint REFERENCES lesson (id) ON DELETE SET NULL,
    class_id        bigint REFERENCES school_class (id),
    teacher_id      bigint REFERENCES teacher (id)
);

CREATE INDEX changelog_entry_timestamp_idx ON changelog_entry (timestamp DESC);
CREATE INDEX changelog_entry_date_idx ON changelog_entry (date);
CREATE INDEX changelog_entry_class_idx ON changelog_entry (class_id);
CREATE INDEX changelog_entry_teacher_idx ON changelog_entry (teacher_id);
