package de.wsp3_1.substitution.domain;

/**
 * Plansicht der Wochenansicht (W-4). Wortgleich mit dem Enum {@code View} im Vertrag.
 *
 * <ul>
 *   <li>{@code SCHOOL} — alle Eintraege der Schule; {@code id} entfaellt</li>
 *   <li>{@code CLASS} — eine Klasse</li>
 *   <li>{@code TEACHER} — eine Lehrkraft (auch die Grundlage von „Mein Plan")</li>
 *   <li>{@code ROOM} — ein Raum (Musskriterium E-39, neu aus Interview 1)</li>
 * </ul>
 */
public enum View {
    SCHOOL, CLASS, TEACHER, ROOM
}
