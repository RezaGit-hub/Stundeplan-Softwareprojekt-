package de.wsp3_1.auth.api;

import de.wsp3_1.auth.domain.Account;
import de.wsp3_1.auth.domain.ClassAccount;
import de.wsp3_1.auth.domain.TeacherAccount;
import de.wsp3_1.shared.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Die DTOs rund um Anmeldung und Konten. Feldnamen und Typen 1:1 wie in
 * {@code frontend/src/api/types.ts} und im Vertrag {@code api-contract/openapi.yaml}.
 * Die Meldungstexte sind deutsch: das Frontend zeigt sie unveraendert am Feld an.
 */
public final class AuthDtos {

    /** Mindestlaenge eines Passworts, wie im Vertrag ({@code minLength: 8}). */
    private static final String PASSWORD_TOO_SHORT = "Das Passwort braucht mindestens 8 Zeichen.";

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Bitte den Benutzernamen eingeben.") String username,
            @NotBlank(message = "Bitte das Passwort eingeben.") String password) {
    }

    public record LoginResponse(String token, Role role, String displayName, Long teacherId) {
    }

    public record MeDto(Long id, String username, Role role, String displayName, Long teacherId) {
    }

    /** Body von {@code PUT /api/me/password}. */
    public record PasswordChange(
            @NotBlank(message = "Bitte das bisherige Passwort eingeben.") String oldPassword,
            @NotBlank(message = "Bitte ein neues Passwort eingeben.")
            @Size(min = 8, message = PASSWORD_TOO_SHORT) String newPassword) {
    }

    /** Body von {@code PUT /api/users/{id}/password}. */
    public record PasswordSet(
            @NotBlank(message = "Bitte ein Passwort eingeben.")
            @Size(min = 8, message = PASSWORD_TOO_SHORT) String password) {
    }

    /**
     * Body von {@code POST /api/users}. {@code teacherId} ist bei der Rolle TEACHER Pflicht,
     * {@code classId} bei der Rolle STUDENT (Klassenkonto, E-50).
     *
     * <p>Die Laengengrenze von {@code username} ist die Spaltenbreite aus V1
     * ({@code user_account.username varchar(60)}). Ohne sie scheitert ein zu langer Name erst an der
     * Datenbank, und der Client bekaeme 500 statt 400 mit Feldliste.</p>
     */
    public record CreateUser(
            @NotBlank(message = "Bitte einen Benutzernamen eingeben.")
            @Size(max = 60, message = "hoechstens 60 Zeichen") String username,
            @NotBlank(message = "Bitte ein Passwort eingeben.")
            @Size(min = 8, message = PASSWORD_TOO_SHORT) String password,
            @NotNull(message = "Bitte die Rolle waehlen.") Role role,
            Long teacherId,
            Long classId) {
    }

    public record UserDto(Long id, String username, Role role, Long teacherId, Long classId, String classShort) {

        static UserDto of(Account account) {
            return new UserDto(account.id(), account.username(), account.role(), account.teacherId(),
                    account.classId(), account.classShort());
        }
    }

    /**
     * Eine Zeile der Antwort von {@code POST /api/users/class-accounts}: das Passwort steht hier
     * <b>einmal</b> im Klartext. Danach kennt es nur noch die Klasse — der Server speichert den Hash.
     */
    public record ClassAccountDto(String username, String classShort, String password) {

        static ClassAccountDto of(ClassAccount account) {
            return new ClassAccountDto(account.username(), account.classShort(), account.password());
        }
    }

    public record TeacherAccountDto(String username, String teacherName, String teacherShort, String password) {

        static TeacherAccountDto of(TeacherAccount account) {
            return new TeacherAccountDto(account.username(), account.teacherName(), account.teacherShort(), account.password());
        }
    }
}
