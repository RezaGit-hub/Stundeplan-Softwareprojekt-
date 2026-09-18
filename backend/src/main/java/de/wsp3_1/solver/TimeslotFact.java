package de.wsp3_1.solver;

import de.wsp3_1.shared.Weekday;

/**
 * Ein Zeitfenster als unveraenderliche Tatsache fuer den Solver.
 * Bewusst ein Record und keine Entity: das Paket {@code solver} kennt die Datenbank nicht (ArchUnit-Regel 1).
 * {@code position} ist die laufende Nummer am Tag, Pausen zaehlen mit.
 *
 * <p>{@code lessonNumber} ist die angezeigte Stundennummer ohne Pausen (1..n je Tag); {@code null} in
 * einer Pause. Sie wird aus {@code Timegrid.lessonNumbers} abgeleitet und hier mitgefuehrt, damit die
 * Regel {@code avoidFreePeriods} echte Freistunden von Pausen unterscheiden kann — eine Positionsluecke
 * ueber eine Pause hinweg ist keine Freistunde.</p>
 */
public record TimeslotFact(Long id, Weekday day, int position, boolean isBreak, Integer lessonNumber) {

    /**
     * Bequemlichkeits-Konstruktor ohne Stundennummer. Nur fuer Tests gedacht, die andere Regeln pruefen;
     * fuer die Regel {@code avoidFreePeriods} liefert er nichts — {@code lessonNumber} bleibt {@code null}
     * und die Zelle zaehlt in dieser Regel nicht mit.
     */
    public TimeslotFact(Long id, Weekday day, int position, boolean isBreak) {
        this(id, day, position, isBreak, isBreak ? null : null);
    }
}
