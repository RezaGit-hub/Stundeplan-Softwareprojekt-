package de.wsp3_1.solver;

import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;

/**
 * Eine aufgeloeste Regel des Regelwerks als Tatsache fuer den Solver — eine Zeile je Regel <b>und</b>
 * Objekt (docs/SOLVER-PLAN.md Abschnitt 1 und 9). Aufgeloest hat sie {@code rules.domain.RuleResolver};
 * das Modul {@code planning} bildet daraus diese Records.
 *
 * <ul>
 *   <li>{@code scope = CLASS}: {@code ownerId} ist die Klasse (die Jahrgangszeile ist schon aufgeloest)</li>
 *   <li>{@code scope = TEACHER}: {@code ownerId} ist die Lehrkraft</li>
 *   <li>{@code NO_LESSONS_IN_BREAKS}: {@code scope} und {@code ownerId} sind {@code null}</li>
 * </ul>
 *
 * <p>{@code severity} entscheidet, welcher Zwilling des Constraints greift (…Hard oder …Soft);
 * {@code weight} (Standard 1) vervielfacht die Strafe.</p>
 */
public record RuleFact(RuleType type, RuleScope scope, Long ownerId, int value, Severity severity, int weight) {
}
