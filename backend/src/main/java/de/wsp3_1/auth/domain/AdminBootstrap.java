package de.wsp3_1.auth.domain;

import de.wsp3_1.shared.Role;
import de.wsp3_1.auth.persistence.UserAccount;
import de.wsp3_1.auth.persistence.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legt beim ersten Start das Konto {@code admin} mit der Rolle {@code PLANNING} an — sonst kaeme niemand hinein.
 * Ist bereits ein Konto vorhanden, geschieht nichts. Das Passwort kommt aus {@code app.admin-initial-password}
 * (gesetzt aus {@code ADMIN_INITIAL_PASSWORD}; im Profil {@code dev} faellt es ohne diese Variable auf
 * {@code admin} zurueck) und wird nie geloggt.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ADMIN_USERNAME = "admin";

    /**
     * Der Platzhalter aus {@code deployment/.env.example}. Wer die Vorlage nur kopiert und den Stack
     * startet, bekommt sonst klaglos ein Konto mit einem im Repo nachlesbaren Passwort (Befund 05.09.).
     * Seit dem Befund SICHERHEIT-6 (05.09.) bricht der Start damit ab, sobald der Platzhalter im
     * Betrieb steht; nur die Profile dev und ci lassen ihn mit einer Warnung durch
     * ({@code app.admin-allow-placeholder-password}).
     */
    private static final String PLACEHOLDER_PASSWORD = "bitte-aendern";

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String initialPassword;
    /** Nur die Profile dev und ci lassen den Platzhalter durch; im Betrieb bricht der Start ab. */
    private final boolean placeholderAllowed;

    public AdminBootstrap(UserAccountRepository users, PasswordEncoder passwordEncoder,
                          @Value("${app.admin-initial-password:}") String initialPassword,
                          @Value("${app.admin-allow-placeholder-password:false}") boolean placeholderAllowed) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.initialPassword = initialPassword;
        this.placeholderAllowed = placeholderAllowed;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException("""
                    Es ist noch kein Benutzerkonto vorhanden und ADMIN_INITIAL_PASSWORD ist nicht gesetzt. \
                    Bitte in deployment/.env ein Passwort fuer das Konto "admin" eintragen und neu starten.""");
        }
        if (PLACEHOLDER_PASSWORD.equalsIgnoreCase(initialPassword.strip()) && !placeholderAllowed) {
            throw new IllegalStateException("""
                    ADMIN_INITIAL_PASSWORD steht noch auf dem Beispielwert "bitte-aendern" aus \
                    deployment/.env.example. Der Wert steht im Repository und ist damit oeffentlich \
                    bekannt; im Betrieb (Profil prod) wird deshalb kein Konto damit angelegt. \
                    Bitte in deployment/.env ein eigenes Passwort eintragen und neu starten \
                    (Befund SICHERHEIT-6, 05.09.).""");
        }
        users.save(new UserAccount(ADMIN_USERNAME, passwordEncoder.encode(initialPassword), Role.PLANNING, null, null));
        log.info("Konto \"{}\" mit der Rolle PLANNING angelegt (Passwort aus app.admin-initial-password).", ADMIN_USERNAME);
        if (PLACEHOLDER_PASSWORD.equalsIgnoreCase(initialPassword.strip())) {
            log.warn("""
                    ACHTUNG: Das Konto "{}" wurde mit dem unveraenderten Beispielwert aus \
                    deployment/.env.example angelegt. Der steht im Repository und ist damit oeffentlich \
                    bekannt. Bitte das Passwort sofort in der Anwendung aendern (Einstellungen > Passwort) \
                    oder mit einem eigenen ADMIN_INITIAL_PASSWORD in deployment/.env neu aufsetzen \
                    (docker compose down -v loescht dabei alle Daten).""", ADMIN_USERNAME);
        }
    }
}
