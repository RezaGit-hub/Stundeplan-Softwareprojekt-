package de.wsp3_1.auth;

import de.wsp3_1.auth.domain.TokenCleanup;
import de.wsp3_1.auth.persistence.AuthTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Das taegliche Aufraeumen loescht genau die abgelaufenen Sitzungen — nie die gueltigen.
 * Ohne Spring und ohne Datenbank: geprueft wird der Zeitpunkt, mit dem die Abfrage aufgerufen wird.
 */
class TokenCleanupTest {

    private final AuthTokenRepository tokens = mock(AuthTokenRepository.class);
    private final TokenCleanup cleanup = new TokenCleanup(tokens);

    @Test
    @DisplayName("Geloescht wird alles, was vor dem Jetzt abgelaufen ist")
    void removesOnlyExpiredTokens() {
        given(tokens.deleteByExpiresAtBefore(org.mockito.ArgumentMatchers.any())).willReturn(2L);
        Instant vorher = Instant.now();

        cleanup.removeExpiredTokens();

        ArgumentCaptor<Instant> zeitpunkt = ArgumentCaptor.forClass(Instant.class);
        verify(tokens).deleteByExpiresAtBefore(zeitpunkt.capture());
        assertThat(zeitpunkt.getValue()).isBetween(vorher, Instant.now());
    }
}
