package de.wsp3_1.substitution.domain;

/**
 * Art eines Aenderungsereignisses (W-5). Wortgleich mit dem Enum {@code EventKind} im Vertrag und mit
 * der CHECK-Liste der Spalte {@code change_event.kind} (Migration V4).
 *
 * <ul>
 *   <li>{@code TEACHER_ABSENCE} — eine Lehrkraft faellt aus; braucht {@code teacherId}</li>
 *   <li>{@code ROOM_BLOCKED} — ein Raum ist gesperrt; braucht {@code roomId}</li>
 *   <li>{@code EXTRA_LESSON} — eine Zusatzstunde wird angesetzt; braucht {@code extra}</li>
 * </ul>
 */
public enum EventKind {
    TEACHER_ABSENCE, ROOM_BLOCKED, EXTRA_LESSON
}
