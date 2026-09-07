package de.wsp3_1.rules.domain;

import de.wsp3_1.shared.RuleType;

/**
 * Deutsche Beschriftung je Regeltyp. Ein Ort fuer die Texte, damit die Regeltabelle in der Oberflaeche,
 * die Konfliktmeldung („Maximale Stunden pro Tag (3) ueberschritten: …") und der Beispielsatz dieselben
 * Worte benutzen. Wortlaut wie in {@code api-contract/openapi.yaml} und
 * {@code frontend/src/mock/store.ts} ({@code RULE_LABEL}).
 */
public final class RuleLabels {

    private RuleLabels() {
    }

    public static String of(RuleType type) {
        return switch (type) {
            case MAX_LESSONS_PER_DAY -> "Maximale Stunden pro Tag";
            case MAX_LESSONS_PER_WEEK -> "Maximale Stunden pro Woche";
            case NO_LESSONS_IN_BREAKS -> "Keine Stunden in Pausen";
        };
    }
}
