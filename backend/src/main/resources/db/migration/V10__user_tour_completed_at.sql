-- V10 — Rundgang durch die Webanwendung (#139, T2-3.1, E-55).
--
-- Modul auth : user_account.tour_completed_at
--
-- Zeitpunkt, zu dem ein Konto den Rundgang beendet oder uebersprungen hat; NULL = noch nicht gezeigt.
-- Am Konto statt im Browser: wer sich an einem anderen Rechner anmeldet, sieht den Rundgang nicht
-- noch einmal. „Rundgang erneut zeigen" in den Einstellungen setzt die Spalte wieder auf NULL
-- (PUT /api/me/tour {completed: false}).
-- V1–V9 bleiben unveraendert (Flyway-Pruefsumme).
ALTER TABLE user_account ADD COLUMN tour_completed_at timestamptz;
