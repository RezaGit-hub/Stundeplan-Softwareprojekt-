package de.wsp3_1.shared;

/**
 * Haerte einer Regel. Neu am 05.09. durch das Modul {@code rules} ergaenzt (additiv, siehe {@link RuleType}).
 *
 * <ul>
 *   <li>{@code HARD} — ein Verstoss macht den Plan ungueltig; der Solver bestraft ihn im harten Punktestand,
 *       das Gewicht bleibt ohne Wirkung</li>
 *   <li>{@code SOFT} — ein Verstoss wird gezaehlt und mit dem Gewicht bewertet; der Plan bleibt gueltig</li>
 * </ul>
 */
public enum Severity {
    HARD,
    SOFT
}
