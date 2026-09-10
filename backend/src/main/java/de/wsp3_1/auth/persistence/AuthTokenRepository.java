package de.wsp3_1.auth.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    /** Alle Sitzungen eines Kontos beenden — nach dem Setzen eines neuen Passworts. */
    void deleteByUser(UserAccount user);

    /** Aufraeumen abgelaufener Sitzungen; taeglich aufgerufen von {@code TokenCleanup}. */
    long deleteByExpiresAtBefore(Instant moment);
}
