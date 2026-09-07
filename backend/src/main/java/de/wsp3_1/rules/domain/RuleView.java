package de.wsp3_1.rules.domain;

import de.wsp3_1.rules.persistence.Rule;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;

/**
 * Eine Regelzeile, wie der {@code RuleService} sie nach aussen gibt: dieselben Felder wie in der Tabelle,
 * dazu die deutsche Beschriftung. Die Entity bleibt im Modul (ArchUnit-Regel 2: Schnittstellenklassen
 * geben keine Entities nach aussen).
 */
public record RuleView(Long id, RuleType type, RuleScope scope, Integer grade, int value, Severity severity,
                       int weight, boolean active, String label) {

    public static RuleView of(Rule rule) {
        return new RuleView(rule.getId(), rule.getType(), rule.getScope(), rule.getGrade(), rule.getValue(),
                rule.getSeverity(), rule.getWeight(), rule.isActive(), RuleLabels.of(rule.getType()));
    }
}
