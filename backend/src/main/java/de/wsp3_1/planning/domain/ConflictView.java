package de.wsp3_1.planning.domain;

import de.wsp3_1.shared.Severity;

import java.util.List;

/**
 * Ein gefundener Konflikt. Der Text {@code message} ist Schulsprache und nennt Regel, Wert, Objekt und
 * Tag (T-W2.1); {@code objects} sind die beteiligten Objekte zum Anspringen in der Oberflaeche.
 *
 * @param ruleId nur bei {@link ConflictType#RULE_VIOLATION} gesetzt
 */
public record ConflictView(ConflictType type, Severity severity, String message, List<Long> lessonIds,
                           Long ruleId, List<ConflictObjectView> objects) {

    /** Klasse, Lehrkraft oder Raum, an dem der Konflikt haengt. */
    public record ConflictObjectView(ObjectKind kind, Long id, String label) {
    }
}
