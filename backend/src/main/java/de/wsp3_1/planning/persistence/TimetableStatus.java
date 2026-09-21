package de.wsp3_1.planning.persistence;

/**
 * Lebenslauf eines Plans. Entwuerfe darf es seit V11 (#103) mehrere geben, angenommene Plaene seit
 * V12 (#115) — dort sichert die Ausschlussbedingung {@code timetable_active_no_overlap} in der
 * Datenbank, dass sich zwei aktive Zeitraeume keinen Tag teilen. Die frueheren Teilindizes
 * {@code timetable_one_draft} und {@code timetable_one_active} sind gefallen.
 *
 * <ul>
 *   <li>{@code DRAFT} — ein Entwurf: hier wird gebaut, berechnet und geaendert. Mehrere nebeneinander,
 *       jeder mit Namen; die Singular-Pfade {@code /api/draft} meinen den juengsten (E-52). Ein
 *       Entwurf in der Ablage ({@code finished}) ist eingefroren, aber weiter annehmbar (#116).</li>
 *   <li>{@code ACTIVE} — ein angenommener Plan mit seinem Gueltigkeitszeitraum. Mehrere nebeneinander,
 *       ueberschneidungsfrei; welcher an einem Tag gilt, beantwortet der {@code PlanReader}.</li>
 *   <li>{@code ARCHIVED} — ein ersetzter oder abgelaufener Plan. Er bleibt stehen, damit datierte
 *       Ausnahmen und Verlaufseintraege weiter auf ihre Stunden zeigen koennen.</li>
 * </ul>
 */
public enum TimetableStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
