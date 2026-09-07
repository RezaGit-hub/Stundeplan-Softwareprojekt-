package de.wsp3_1.solver;

import de.wsp3_1.shared.Weekday;

/**
 * Ein Zeitfenster als unveraenderliche Tatsache fuer den Solver.
 * Bewusst ein Record und keine Entity: das Paket {@code solver} kennt die Datenbank nicht (ArchUnit-Regel 1).
 * {@code position} ist die laufende Nummer am Tag, Pausen zaehlen mit.
 */
public record TimeslotFact(Long id, Weekday day, int position, boolean isBreak) {
}
