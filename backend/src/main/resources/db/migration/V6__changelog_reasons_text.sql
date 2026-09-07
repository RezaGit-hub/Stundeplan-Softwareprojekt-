-- V6 — Die Anlaesse eines Verlaufseintrags in voller Laenge.
--
-- Modul changelog : changelog_entry.reasons
--
-- V4 gab der Spalte varchar(1000). Ein Anlass ist Freitext bis 255 Zeichen (change_event.reason), und
-- eine Stunde darf mehrere Anlaesse tragen (Leitszenario: Ausfall UND Raumsperrung). Ab fuenf Anlaessen
-- reichten 1000 Zeichen nicht mehr, und der Dienst kuerzte — dabei fielen ganze spaetere Anlaesse weg,
-- obwohl Vertrag und Rueckgrat ALLE Anlaesse verlangen (Feld `reasons`, Trennung mit "; ").
--
-- `text` hat in PostgreSQL dieselbe Ablage wie varchar, nur ohne Laengenpruefung. Nach oben begrenzt
-- bleibt der Wert durch die Daten selbst: je Ereignis hoechstens 255 Zeichen, und mehr Ereignisse als
-- angelegt kann eine Ausnahme nicht nennen.
--
-- V1-V5 bleiben unveraendert (Flyway-Pruefsumme).
ALTER TABLE changelog_entry
    ALTER COLUMN reasons TYPE text;
