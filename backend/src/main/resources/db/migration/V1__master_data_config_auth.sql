-- V1 — Grunddaten, Regelwerk, Systemkonfiguration, Konten.
--
-- Modul masterdata : teacher, subject, room, school_class, class_subject, timeslot,
--                    teacher_subject, teacher_preferred_timeslot, teacher_blocked_timeslot
-- Modul rules      : rule, rule_preset
-- Modul config     : system_config
-- Modul auth       : user_account, auth_token
--
-- Konventionen: Tabellen und Spalten snake_case englisch, Schluessel bigserial/bigint,
-- Aufzaehlungen als varchar mit CHECK-Liste (der Katalog steht als Enum im Java-Code),
-- Uhrzeiten als time, Zeitstempel als timestamptz. Das Schema entsteht nur hier, nie durch Hibernate.

-- ---------------------------------------------------------------- Grunddaten

-- Lehrkraft. weekly_hours_quota ist das Deputat (Wochenstunden). Statt zu loeschen wird deaktiviert:
-- active = false laesst bestehende Stunden unveraendert, nimmt die Lehrkraft aber aus den Auswahllisten.
CREATE TABLE teacher (
    id                  bigserial PRIMARY KEY,
    name                varchar(120) NOT NULL,
    short_name          varchar(20)  NOT NULL UNIQUE,
    weekly_hours_quota  int          NOT NULL CHECK (weekly_hours_quota >= 0),
    active              boolean      NOT NULL DEFAULT true
);

-- Fach. required_room_type ist Freitext und wird exakt mit room.room_type verglichen
-- (bewusst keine eigene Tabelle fuer Raumarten). color ist ein Hex-Wert fuer die Kacheln.
CREATE TABLE subject (
    id                  bigserial PRIMARY KEY,
    name                varchar(120) NOT NULL,
    short_name          varchar(20)  NOT NULL UNIQUE,
    required_room_type  varchar(60),
    color               varchar(9)   NOT NULL
);

-- Raum. Keine Kapazitaet (Abgrenzung). Sperrzeiten sind datiert und stehen als change_event in V4.
CREATE TABLE room (
    id         bigserial PRIMARY KEY,
    name       varchar(60) NOT NULL UNIQUE,
    room_type  varchar(60) NOT NULL,
    active     boolean     NOT NULL DEFAULT true
);

-- Klasse im Klassenverband. Keine Klassengroesse (Abgrenzung). grade ist eine freie Zahl,
-- damit jede Schulform passt — es ist kein Jahrgangsbereich im Code verdrahtet.
CREATE TABLE school_class (
    id      bigserial PRIMARY KEY,
    name    varchar(20) NOT NULL UNIQUE,
    grade   int         NOT NULL,
    active  boolean     NOT NULL DEFAULT true
);

-- Zeitfenster des Stundenrasters. position ist die laufende Nummer am Tag, Pausen zaehlen mit;
-- die angezeigte Stundennummer wird ueber is_break = false gezaehlt und nicht gespeichert.
CREATE TABLE timeslot (
    id          bigserial PRIMARY KEY,
    day         varchar(2) NOT NULL CHECK (day IN ('MO', 'TU', 'WE', 'TH', 'FR', 'SA')),
    position    int        NOT NULL CHECK (position >= 1),
    start_time  time       NOT NULL,
    end_time    time       NOT NULL,
    is_break    boolean    NOT NULL DEFAULT false,
    CONSTRAINT timeslot_day_position_unique UNIQUE (day, position),
    CONSTRAINT timeslot_end_after_start CHECK (end_time > start_time)
);

-- Klasse x Fach: Wochenstunden und die feste Lehrkraft. Beides ist Pflicht — ohne sie kein Speichern.
CREATE TABLE class_subject (
    id            bigserial PRIMARY KEY,
    class_id      bigint NOT NULL REFERENCES school_class (id),
    subject_id    bigint NOT NULL REFERENCES subject (id),
    teacher_id    bigint NOT NULL REFERENCES teacher (id),
    weekly_hours  int    NOT NULL CHECK (weekly_hours >= 1),
    CONSTRAINT class_subject_unique UNIQUE (class_id, subject_id)
);

-- Welche Faecher eine Lehrkraft unterrichtet. Wird nur fuer Warnungen und die Vertretungssuche gebraucht.
CREATE TABLE teacher_subject (
    teacher_id  bigint NOT NULL REFERENCES teacher (id) ON DELETE CASCADE,
    subject_id  bigint NOT NULL REFERENCES subject (id),
    PRIMARY KEY (teacher_id, subject_id)
);

-- Bevorzugte Zeiten (weiche Regel S1).
CREATE TABLE teacher_preferred_timeslot (
    teacher_id   bigint NOT NULL REFERENCES teacher (id) ON DELETE CASCADE,
    timeslot_id  bigint NOT NULL REFERENCES timeslot (id),
    PRIMARY KEY (teacher_id, timeslot_id)
);

-- Gesperrte Zeiten. Immer hart, unabhaengig vom Regelwerk.
CREATE TABLE teacher_blocked_timeslot (
    teacher_id   bigint NOT NULL REFERENCES teacher (id) ON DELETE CASCADE,
    timeslot_id  bigint NOT NULL REFERENCES timeslot (id),
    PRIMARY KEY (teacher_id, timeslot_id)
);

-- ---------------------------------------------------------------- Regelwerk

-- Regelwerk der Schule. Der Katalog der Typen steht als Enum im Code; hier stehen nur Werte.
-- grade ist leer fuer die schulweite Zeile und gesetzt fuer eine jahrgangsspezifische (E-47).
-- NULLS NOT DISTINCT (PostgreSQL 15+) sorgt dafuer, dass auch zwei Zeilen mit leerem scope
-- oder leerem grade als doppelt erkannt werden — sonst greift die Eindeutigkeit dort nicht.
CREATE TABLE rule (
    id        bigserial PRIMARY KEY,
    type      varchar(40) NOT NULL
              CHECK (type IN ('MAX_LESSONS_PER_DAY', 'MAX_LESSONS_PER_WEEK', 'NO_LESSONS_IN_BREAKS')),
    scope     varchar(10) CHECK (scope IN ('CLASS', 'TEACHER')),
    grade     int,
    value     int         NOT NULL CHECK (value >= 1),
    severity  varchar(10) NOT NULL CHECK (severity IN ('HARD', 'SOFT')),
    weight    int         NOT NULL DEFAULT 1 CHECK (weight >= 1),
    active    boolean     NOT NULL DEFAULT true,
    CONSTRAINT rule_unique UNIQUE NULLS NOT DISTINCT (type, scope, grade),
    CONSTRAINT rule_scope_required_for_max
        CHECK (type = 'NO_LESSONS_IN_BREAKS' OR scope IS NOT NULL)
);

-- Voreinstellung je Bundesland (E-48). Nur lesend ueber die API; POST /api/rules/reset kopiert
-- die Zeilen des in system_config eingestellten Bundeslands nach rule.
-- Spalten und Bedingungen spiegeln rule: grade leer = schulweite Zeile, gesetzt = ein Jahrgang (E-47).
-- Anders als rule ist grade hier auf 5..10 begrenzt, weil die Voreinstellungen aus den amtlichen
-- Stundentafeln der Sekundarstufe I stammen; das eigene Regelwerk der Schule bleibt frei.
CREATE TABLE rule_preset (
    id        bigserial PRIMARY KEY,
    state     varchar(2)  NOT NULL,
    type      varchar(40) NOT NULL
              CHECK (type IN ('MAX_LESSONS_PER_DAY', 'MAX_LESSONS_PER_WEEK', 'NO_LESSONS_IN_BREAKS')),
    scope     varchar(10) CHECK (scope IN ('CLASS', 'TEACHER')),
    grade     int         CHECK (grade BETWEEN 5 AND 10),
    value     int         NOT NULL CHECK (value >= 1),
    severity  varchar(10) NOT NULL CHECK (severity IN ('HARD', 'SOFT')),
    weight    int         NOT NULL DEFAULT 1 CHECK (weight >= 1),
    CONSTRAINT rule_preset_unique UNIQUE NULLS NOT DISTINCT (state, type, scope, grade),
    CONSTRAINT rule_preset_scope_required_for_max
        CHECK (type = 'NO_LESSONS_IN_BREAKS' OR scope IS NOT NULL)
);

-- ---------------------------------------------------------------- Systemkonfiguration

-- Genau eine Zeile mit id = 1. Bundesland und Schulform sind Daten, kein Wert steht im Code (E-37).
CREATE TABLE system_config (
    id                 bigint       PRIMARY KEY CHECK (id = 1),
    school_name        varchar(120) NOT NULL,
    state              varchar(2)   NOT NULL,
    school_type        varchar(40)  NOT NULL,
    max_solve_seconds  int          NOT NULL CHECK (max_solve_seconds >= 1)
);

-- ---------------------------------------------------------------- Konten und Sitzungen

-- Benutzerkonto. Rolle PLANNING = Planung (in der Oberflaeche so genannt), TEACHER = Lehrkraft.
-- Ein Konto der Rolle TEACHER gehoert zu genau einer Lehrkraft und jede Lehrkraft zu hoechstens einem Konto.
CREATE TABLE user_account (
    id             bigserial PRIMARY KEY,
    username       varchar(60)  NOT NULL UNIQUE,
    password_hash  varchar(100) NOT NULL,
    role           varchar(10)  NOT NULL CHECK (role IN ('PLANNING', 'TEACHER')),
    teacher_id     bigint UNIQUE REFERENCES teacher (id),
    CONSTRAINT user_account_teacher_required
        CHECK (role <> 'TEACHER' OR teacher_id IS NOT NULL)
);

-- Angemeldete Sitzung. Gespeichert wird nur der SHA-256-Hex des Tokens, nie das Token selbst.
CREATE TABLE auth_token (
    id          bigserial PRIMARY KEY,
    token_hash  varchar(64) NOT NULL UNIQUE,
    user_id     bigint      NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL
);

CREATE INDEX auth_token_expires_at_idx ON auth_token (expires_at);
