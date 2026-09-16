package de.wsp3_1.planning.persistence;

/**
 * Lebenslauf eines Plans. Es gibt hoechstens einen {@code ACTIVE} — das sichert ein Teilindex in der
 * Datenbank (Migration V3), nicht die Anwendung. Entwuerfe darf es seit V11 (#103) mehrere geben; der
 * fruehere Teilindex {@code timetable_one_draft} ist gefallen.
 *
 * <ul>
 *   <li>{@code DRAFT} — ein Entwurf: hier wird gebaut, berechnet und geaendert. Mehrere nebeneinander,
 *       jeder mit Namen; die Singular-Pfade {@code /api/draft} meinen den juengsten (E-52).</li>
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
