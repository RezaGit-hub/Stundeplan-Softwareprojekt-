package de.wsp3_1.shared;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Die angemeldete Person, wie sie durch die Anwendung wandert. Der Token-Filter in {@code auth.api}
 * legt genau diesen Record als Principal ab; jedes Modul, das den Verursacher einer Aenderung braucht
 * ({@code planning}, {@code substitution}, {@code changelog}), liest ihn mit {@link #require()},
 * ohne {@code auth} zu importieren (VERTRAG Abschnitt 4 und 6).
 *
 * <p>Bewusst ein Record statt der Entity {@code UserAccount}: so haengt weder Filter noch Controller
 * an der Datenbankklasse (ArchUnit-Regel 2). {@code teacherId} ist bei der Planung {@code null},
 * {@code classId} nur bei einem Klassenkonto (Rolle {@code STUDENT}, E-50) gesetzt.</p>
 *
 * <p>Additiv ergaenzt vom Modul auth am 05.09.</p>
 */
public record CurrentUser(Long id, String username, Role role, Long teacherId, Long classId) {

    /**
     * Die angemeldete Person aus dem Sicherheitskontext.
     *
     * @throws IllegalStateException wenn niemand angemeldet ist. Das ist kein Bedienfehler, sondern
     *         ein Programmfehler: die Pfadregeln in {@code SecurityConfig} lassen unangemeldete
     *         Anfragen gar nicht bis hierher durch.
     */
    public static CurrentUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw new IllegalStateException("Kein angemeldetes Konto im Sicherheitskontext.");
        }
        return user;
    }

    /** Wahr, wenn dieses Konto zu einer Lehrkraft gehoert. */
    public boolean isTeacher() {
        return role == Role.TEACHER;
    }

    /** Wahr, wenn dieses Konto ein Klassenkonto ist (Rolle {@code STUDENT}, E-50). */
    public boolean isStudent() {
        return role == Role.STUDENT;
    }
}
