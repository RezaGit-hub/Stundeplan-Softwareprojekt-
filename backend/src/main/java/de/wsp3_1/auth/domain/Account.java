package de.wsp3_1.auth.domain;

import de.wsp3_1.shared.Role;

/**
 * Ein Konto, wie es die Fachlogik nach aussen gibt — ohne Passwort-Hash und ohne die Entity selbst.
 * Der Umweg ueber diesen Record ist die Architekturregel 2 („Schnittstellenklassen geben keine
 * Entities nach aussen", {@code ArchitectureTest}): der Controller sieht nie {@code UserAccount}.
 *
 * <p>{@code classId} und {@code classShort} gehoeren zu einem Klassenkonto (Rolle {@code STUDENT},
 * E-50); {@code classShort} ist der Name der Klasse („7a") und wird nur zum Anzeigen mitgegeben,
 * damit die Kontenliste keine zweite Abfrage braucht.</p>
 */
public record Account(Long id, String username, Role role, Long teacherId, Long classId, String classShort) {
}
