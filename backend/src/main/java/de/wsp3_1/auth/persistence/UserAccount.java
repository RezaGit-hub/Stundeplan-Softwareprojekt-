package de.wsp3_1.auth.persistence;

import de.wsp3_1.shared.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Benutzerkonto. {@code teacherId} verweist auf die Lehrkraft, {@code classId} auf die Klasse — beides
 * ist bewusst nur eine Spalte: die Entities {@code Teacher} und {@code SchoolClass} gehoeren dem Modul
 * {@code masterdata}; die Fremdschluesselbedingungen stehen schon in V1.
 *
 * <p>{@code classId} traegt die Klassenbindung eines Klassenkontos (Rolle {@code STUDENT}, E-50). Sie
 * steht in der Datenbank und kommt nie aus einem Aufruf — deshalb kann ein Klassenkonto keine fremde
 * Klasse lesen.</p>
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /** BCrypt-Hash, nie das Klartextpasswort. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "teacher_id")
    private Long teacherId;

    /** Klasse eines Klassenkontos (Rolle {@code STUDENT}); bei den anderen Rollen {@code null}. */
    @Column(name = "class_id")
    private Long classId;

    protected UserAccount() {
    }

    public UserAccount(String username, String passwordHash, Role role, Long teacherId, Long classId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.teacherId = teacherId;
        this.classId = classId;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }
}
