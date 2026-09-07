package de.wsp3_1.rules.api;

import de.wsp3_1.rules.domain.RuleView;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Die DTOs des Regelwerks. Feldnamen und Typen 1:1 wie in {@code frontend/src/api/types.ts} und im
 * Vertrag {@code api-contract/openapi.yaml} (Schemas {@code RuleDto} und {@code RuleInput}).
 */
public final class RuleDtos {

    private RuleDtos() {
    }

    /** Eine Regelzeile, wie die Oberflaeche sie anzeigt. */
    public record RuleDto(Long id, RuleType type, RuleScope scope, Integer grade, int value, Severity severity,
                          int weight, boolean active, String label) {

        public static RuleDto of(RuleView view) {
            return new RuleDto(view.id(), view.type(), view.scope(), view.grade(), view.value(),
                    view.severity(), view.weight(), view.active(), view.label());
        }
    }

    /**
     * {@code PUT /api/rules/{id}}: Wert, Haerte, Gewicht und Ein/Aus. Typ, Zaehleinheit und Jahrgang
     * stehen im Vertrag zwar im selben Schema, werden hier aber nicht gelesen — sie zu aendern hiesse,
     * eine andere Regel zu meinen.
     */
    public record UpdateRuleRequest(
            @NotNull(message = "muss mindestens 1 sein")
            @Min(value = 1, message = "muss mindestens 1 sein") Integer value,
            @NotNull(message = "Bitte hart oder weich wählen.") Severity severity,
            @NotNull(message = "muss mindestens 1 sein")
            @Min(value = 1, message = "muss mindestens 1 sein") Integer weight,
            @NotNull(message = "Bitte angeben, ob die Regel gilt.") Boolean active) {
    }

    /**
     * {@code POST /api/rules}: eine Jahrgangszeile. Alles ist Pflicht — die schulweiten Zeilen kommen aus
     * dem Beispielsatz, hier entsteht ausschliesslich ein Wert fuer genau einen Jahrgang (Soll, E-47).
     */
    public record CreateRuleRequest(
            @NotNull(message = "Bitte eine Regel wählen.") RuleType type,
            @NotNull(message = "Jahrgangsregeln zählen je Klasse.") RuleScope scope,
            @NotNull(message = "Bitte einen Jahrgang angeben.")
            @Min(value = 1, message = "muss mindestens 1 sein") Integer grade,
            @NotNull(message = "muss mindestens 1 sein")
            @Min(value = 1, message = "muss mindestens 1 sein") Integer value,
            @NotNull(message = "Bitte hart oder weich wählen.") Severity severity,
            @NotNull(message = "muss mindestens 1 sein")
            @Min(value = 1, message = "muss mindestens 1 sein") Integer weight,
            @NotNull(message = "Bitte angeben, ob die Regel gilt.") Boolean active) {
    }
}
