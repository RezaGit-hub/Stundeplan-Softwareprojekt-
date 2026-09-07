-- V7 — Schuelersicht: Klassenkonto (E-50, NACHTRAG 05.09.2026)
--
-- Ein Konto je Klasse. Rolle STUDENT, gebunden ueber user_account.class_id; die Sicht dazu ist
-- GET /api/me/class-week (Klasse aus dem Token, Antwort ohne Abwesenheitsgruende). Begruendung und
-- Quellen: 07-Konzept/recherche/sammelzugang-schueler.md, Abschnitt "Bewertung".
--
-- Warum eine eigene Migration und nicht V1?
-- Die Schablone (08-Build/starter) traegt dieselbe Aenderung direkt in V1 — dort ist noch keine
-- Datenbank ausgeliefert. Der Vollausbau hat dagegen einen laufenden Referenz-Stack, dessen
-- flyway_schema_history V1 bereits mit ihrer Pruefsumme kennt: eine Aenderung an V1 wuerde jeden
-- Start mit "Migration checksum mismatch for migration version 1" abbrechen. Deshalb steht hier,
-- was dort in V1 steht — Schema gleich, Weg dorthin anders.

-- Die CHECK-Liste der Rollen ist in V1 ohne Namen angelegt; PostgreSQL nennt sie user_account_role_check.
ALTER TABLE user_account DROP CONSTRAINT user_account_role_check;
ALTER TABLE user_account ADD CONSTRAINT user_account_role_check
    CHECK (role IN ('PLANNING', 'TEACHER', 'STUDENT'));

-- Klasse eines Klassenkontos. UNIQUE wie teacher_id: jede Klasse hat hoechstens ein Konto.
ALTER TABLE user_account ADD COLUMN class_id bigint UNIQUE REFERENCES school_class (id);

-- Gegenstueck zu user_account_teacher_required aus V1: ein Klassenkonto ohne Klasse gibt es nicht.
ALTER TABLE user_account ADD CONSTRAINT user_account_class_required
    CHECK (role <> 'STUDENT' OR class_id IS NOT NULL);
