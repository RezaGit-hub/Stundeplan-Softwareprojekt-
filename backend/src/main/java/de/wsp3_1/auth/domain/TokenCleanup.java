package de.wsp3_1.auth.domain;

import de.wsp3_1.auth.persistence.AuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Raeumt abgelaufene Sitzungen weg (api.md: Token zwoelf Stunden gueltig). Ohne das waechst
 * {@code auth_token} unbegrenzt; angemeldet bleibt durch das Aufraeumen niemand laenger oder kuerzer,
 * denn {@code AuthService.authenticate} prueft den Ablauf ohnehin bei jeder Anfrage.
 *
 * <p>Laeuft taeglich um 03:15 Uhr (Serverzeit {@code Europe/Berlin}); {@code @EnableScheduling} steht
 * an {@code BackendApplication}. Der Zeitplan steht in {@code app.token-cleanup-cron} und ist ohne
 * Neuuebersetzen aenderbar — das braucht die Abnahme, um das Aufraeumen nicht zwoelf Stunden lang
 * beobachten zu muessen.</p>
 */
@Component
public class TokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanup.class);

    private final AuthTokenRepository tokens;

    public TokenCleanup(AuthTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Scheduled(cron = "${app.token-cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void removeExpiredTokens() {
        long geloescht = tokens.deleteByExpiresAtBefore(Instant.now());
        if (geloescht > 0) {
            log.info("{} abgelaufene Sitzungen geloescht.", geloescht);
        }
    }
}
