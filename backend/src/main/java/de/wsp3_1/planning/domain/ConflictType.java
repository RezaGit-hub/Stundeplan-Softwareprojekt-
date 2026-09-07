package de.wsp3_1.planning.domain;

/**
 * Art eines Konflikts. Wortlaut und Reihenfolge wie im Vertrag
 * ({@code api-contract/openapi.yaml}, Schema {@code ConflictType}).
 *
 * <p>Ein Konflikt ist immer ein Datum, nie ein Fehler: er steht in einer 200-Antwort neben den
 * Stunden. Nur bei {@code pinned-contradiction} taucht er ausnahmsweise in einem Fehlerkoerper auf.</p>
 */
public enum ConflictType {
    /** Zwei Stunden im selben Zeitfenster im selben Raum. */
    ROOM_DOUBLE_BOOKED,
    /** Zwei Stunden im selben Zeitfenster bei derselben Lehrkraft. */
    TEACHER_DOUBLE_BOOKED,
    /** Zwei Stunden im selben Zeitfenster in derselben Klasse. */
    CLASS_DOUBLE_BOOKED,
    /** Die Lehrkraft hat dieses Zeitfenster gesperrt. */
    TEACHER_BLOCKED_TIME,
    /** Das Fach braucht eine andere Raumart, als der Raum hat. */
    ROOM_TYPE_MISMATCH,
    /** Eine Zeile des Regelwerks ist ueberschritten. */
    RULE_VIOLATION
}
