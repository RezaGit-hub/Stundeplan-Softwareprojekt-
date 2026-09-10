package de.wsp3_1.substitution.domain;

/**
 * Art einer datierten Ausnahme (W-6, W-7). Wortgleich mit dem Enum {@code ExceptionKind} im Vertrag
 * und mit der CHECK-Liste der Spalte {@code lesson_exception.kind} (Migration V4).
 *
 * <ul>
 *   <li>{@code SUBSTITUTION} — andere Lehrkraft und/oder anderer Raum, in EINER Zeile (T-W6.1)</li>
 *   <li>{@code MOVE} — die Stunde findet an einem anderen Tag oder in einem anderen Zeitfenster statt</li>
 *   <li>{@code CANCEL} — die Stunde entfaellt ersatzlos</li>
 *   <li>{@code ADDED} — eine Zusatzstunde ohne Regelplanstunde; entsteht nur ueber ein Ereignis
 *       {@code EXTRA_LESSON}, nie ueber {@code POST /api/exceptions}</li>
 * </ul>
 */
public enum ExceptionKind {
    SUBSTITUTION, MOVE, CANCEL, ADDED
}
