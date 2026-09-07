package de.wsp3_1.planning.persistence;

/**
 * Lebenslauf eines Plans. Es gibt hoechstens einen {@code DRAFT} und hoechstens einen {@code ACTIVE} —
 * das sichern zwei Teilindizes in der Datenbank (Migration V3), nicht die Anwendung.
 *
 * <ul>
 *   <li>{@code DRAFT} — der eine Entwurf: hier wird gebaut, berechnet und geaendert.</li>
 *   <li>{@code ACTIVE} — der angenommene Plan. Nur er wird gelesen (Wochensichten, App).</li>
 *   <li>{@code ARCHIVED} — ein frueher aktiver Plan. Er bleibt stehen, damit datierte Ausnahmen und
 *       Verlaufseintraege weiter auf ihre Stunden zeigen koennen.</li>
 * </ul>
 */
public enum TimetableStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
