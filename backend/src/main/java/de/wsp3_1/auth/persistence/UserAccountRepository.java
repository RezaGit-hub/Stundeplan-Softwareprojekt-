package de.wsp3_1.auth.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    /**
     * Konto zum Anmelden, mit Sperre auf der Kontozeile ({@code SELECT … FOR UPDATE}).
     *
     * <p><b>Warum eine Sperre?</b> „Passwort setzen" verspricht, alle Sitzungen des Kontos zu beenden
     * ({@link de.wsp3_1.auth.domain.UserService#setPassword}). Ohne Sperre kann eine gleichzeitige
     * Anmeldung das <em>alte</em> Passwort pruefen, das Setzen abwarten und danach ein zwoelf Stunden
     * gueltiges Token schreiben — genau das Token, das die Ruecksetzung entfernen sollte. Beide
     * Vorgaenge greifen jetzt ueber dieselbe gesperrte Zeile und laufen deshalb nacheinander.</p>
     *
     * <p>Gesperrt wird nur diese eine Zeile: Anmeldungen verschiedener Konten stoeren einander nicht.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserAccount> findWithLockByUsername(String username);

    /** Konto zum Passwortsetzen, mit derselben Sperre wie {@link #findWithLockByUsername}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserAccount> findWithLockById(Long id);

    boolean existsByUsername(String username);

    /** Je Lehrkraft hoechstens ein Konto — die Spalte {@code teacher_id} ist in V1 UNIQUE. */
    boolean existsByTeacherId(Long teacherId);

    /** Je Klasse hoechstens ein Konto — die Spalte {@code class_id} ist in V1 UNIQUE (E-50). */
    boolean existsByClassId(Long classId);

    /** Die Klassen, die schon ein Konto haben; Grundlage von {@code POST /api/users/class-accounts}. */
    @org.springframework.data.jpa.repository.Query(
            "select u.classId from UserAccount u where u.classId is not null")
    java.util.List<Long> findAllClassIds();

    /** Konten in fester Reihenfolge, damit die Liste in der Oberflaeche nicht springt. */
    java.util.List<UserAccount> findAllByOrderByUsernameAsc();
}
