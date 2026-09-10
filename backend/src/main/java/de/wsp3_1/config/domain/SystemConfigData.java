package de.wsp3_1.config.domain;

/**
 * Die Systemkonfiguration als einfacher Wert — ohne JPA, ohne Setter.
 *
 * <p>Warum nicht die Entity? Klassen im Paket {@code ..api..} duerfen laut ArchUnit-Regel 2 nichts
 * benutzen, was mit {@code @Entity} annotiert ist. Der Dienst gibt deshalb diesen Record heraus und
 * behaelt {@link de.wsp3_1.config.persistence.SystemConfig} fuer sich.</p>
 *
 * <p>{@code state} (Bundesland) und {@code schoolType} (Schulform) sind reine Daten (E-37): im Code
 * steht keine Fallunterscheidung darauf. Das Bundesland waehlt nur die Voreinstellung, die
 * {@code POST /api/rules/reset} kopiert; {@code maxSolveSeconds} ist die Rechenzeit je Berechnung.</p>
 */
public record SystemConfigData(String schoolName, String state, String schoolType, int maxSolveSeconds) {
}
