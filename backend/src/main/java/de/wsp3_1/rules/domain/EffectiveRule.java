package de.wsp3_1.rules.domain;

import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;

/**
 * Eine Regel, die fuer genau ein Objekt gilt: „hoechstens 6 Stunden pro Tag fuer Klasse 7a".
 * Ergebnis des {@link RuleResolver} und einzige Regelquelle fuer den Solver (RuleFact) und die
 * Konfliktpruefung im Modul {@code planning} — beide sehen damit dieselben Regeln
 * ({@code docs/SOLVER-PLAN.md} Abschnitt 9).
 *
 * @param ruleId   die Regelzeile, aus der das kommt; steht spaeter in der Konfliktmeldung
 * @param ownerId  Klasse oder Lehrkraft, fuer die die Regel gilt; {@code null} bei der Pausenregel
 * @param label    deutsche Beschriftung fuer die Konfliktmeldung
 */
public record EffectiveRule(Long ruleId, RuleType type, RuleScope scope, Long ownerId, int value,
                            Severity severity, int weight, String label) {
}
