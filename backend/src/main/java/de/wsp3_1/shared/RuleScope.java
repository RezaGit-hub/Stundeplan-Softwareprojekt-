package de.wsp3_1.shared;

/**
 * Zaehleinheit einer Regel: Wird je Klasse oder je Lehrkraft gezaehlt? Neu am 05.09. durch das Modul
 * {@code rules} ergaenzt (additiv, siehe {@link RuleType}).
 *
 * <p>Der Wert darf fehlen: die Pausenregel {@code NO_LESSONS_IN_BREAKS} betrifft weder Klasse noch
 * Lehrkraft und traegt {@code null} (Spalte {@code scope} in {@code rule} ist deshalb nullbar).</p>
 */
public enum RuleScope {
    /** je Klasse */
    CLASS,
    /** je Lehrkraft */
    TEACHER
}
