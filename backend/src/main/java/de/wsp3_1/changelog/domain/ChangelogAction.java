package de.wsp3_1.changelog.domain;

/**
 * Was der Verlaufseintrag festhaelt (W-7). Wortgleich mit dem Enum {@code ChangelogAction} im Vertrag
 * und mit der CHECK-Liste der Spalte {@code changelog_entry.action} (Migration V4).
 *
 * <ul>
 *   <li>{@code EXCEPTION_CREATED} — eine datierte Ausnahme entstand (Vertretung, Verlegung, Ausfall,
 *       Zusatzstunde); geschrieben vom Modul {@code substitution}</li>
 *   <li>{@code EXCEPTION_DELETED} — eine datierte Ausnahme wurde zurueckgenommen</li>
 *   <li>{@code TIMETABLE_ACCEPTED} — ein Entwurf wurde angenommen; geschrieben vom Modul {@code planning}</li>
 * </ul>
 */
public enum ChangelogAction {
    EXCEPTION_CREATED, EXCEPTION_DELETED, TIMETABLE_ACCEPTED
}
