package de.wsp3_1.auth.domain;

import de.wsp3_1.auth.persistence.AuthToken;
import de.wsp3_1.auth.persistence.AuthTokenRepository;
import de.wsp3_1.auth.persistence.UserAccount;
import de.wsp3_1.auth.persistence.UserAccountRepository;
import de.wsp3_1.masterdata.persistence.SchoolClassRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.shared.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Anmeldung, Token-Pruefung, Abmeldung (api.md „Auth-Fluss").
 * Das Token entsteht aus 32 zufaelligen Bytes und verlaesst den Server genau einmal; gespeichert wird
 * nur sein SHA-256-Hex. Kein JWT, kein Refresh-Token, kein OAuth2 — bewusste Abgrenzung.
 */
@Service
public class AuthService {

    /**
     * Lebensdauer einer Sitzung. Ursprung: 12 Stunden (api.md Auth-Fluss 5). Seit dem NACHTRAG 05.09.
     * dreissig Tage, weil die Web-App die Sitzung im Browser behaelt und ein Neuladen nicht mehr
     * abmelden soll. Ueber {@code app.auth.token-ttl} einstellbar (ISO-8601-Dauer, z. B. {@code PT12H}).
     */
    public static final Duration DEFAULT_TOKEN_LIFETIME = Duration.ofDays(30);

    private static final int TOKEN_BYTES = 32;

    private final Duration tokenLifetime;

    private final UserAccountRepository users;
    private final AuthTokenRepository tokens;
    private final TeacherRepository teachers;
    private final SchoolClassRepository classes;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    /**
     * Hash eines Zufallswertes, der zu keinem Konto gehoert. Gegen ihn rechnet die Anmeldung, wenn es den
     * Benutzernamen gar nicht gibt — sonst antwortet ein unbekannter Name ohne BCrypt rund elfmal
     * schneller als ein bekannter mit falschem Passwort (Befund SICHERHEIT-8, 05.09.). Einmal beim Start
     * berechnet, mit demselben Encoder und derselben Staerke wie echte Passwoerter.
     */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, AuthTokenRepository tokens, TeacherRepository teachers,
                       SchoolClassRepository classes, PasswordEncoder passwordEncoder,
                       @Value("${app.auth.token-ttl:P30D}") Duration tokenLifetime) {
        this.tokenLifetime = tokenLifetime;
        this.users = users;
        this.tokens = tokens;
        this.teachers = teachers;
        this.classes = classes;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Prueft Benutzername und Passwort und legt eine Sitzung an.
     *
     * @throws BadCredentialsException wenn das Konto fehlt oder das Passwort nicht passt — der
     *         {@code ApiExceptionHandler} macht daraus 401 {@code unauthenticated}.
     */
    @Transactional
    public LoginResult login(String username, String rawPassword) {
        // Mit Sperre auf der Kontozeile: sonst koennte eine gleichzeitige Passwort-Ruecksetzung
        // zwischen der Passwortpruefung und dem Schreiben des Tokens durchlaufen, und das neue Token
        // ueberlebte sie — obwohl setPassword alle Sitzungen beenden soll (UserAccountRepository).
        Optional<UserAccount> gefunden = users.findWithLockByUsername(username);
        if (gefunden.isEmpty()) {
            // Ohne diesen Vergleich antwortet ein unbekannter Benutzername rund elfmal schneller als ein
            // bekannter mit falschem Passwort (gemessen 05.09.: 0,008 s gegen 0,087 s), weil BCrypt gar
            // nicht erst laeuft — damit liessen sich gueltige Kontonamen erraten. Der Vergleich gegen
            // dummyHash kostet dieselbe Zeit und wird nie wahr (Befund SICHERHEIT-8). Genau EIN BCrypt
            // je Zweig: liefe er zusaetzlich zum echten Vergleich, waere der bekannte Name wieder der
            // langsamere und die Enumeration bloss umgedreht.
            passwordEncoder.matches(rawPassword, dummyHash);
            throw new BadCredentialsException("Benutzername oder Passwort ist falsch.");
        }
        UserAccount account = gefunden.get();
        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw new BadCredentialsException("Benutzername oder Passwort ist falsch.");
        }

        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Instant now = Instant.now();
        tokens.save(new AuthToken(hash(token), account, now, now.plus(tokenLifetime)));
        CurrentUser user = toCurrentUser(account);
        return new LoginResult(token, user, displayName(user));
    }

    /** Token aus dem {@code Authorization}-Header pruefen. Leer, wenn unbekannt oder abgelaufen. */
    @Transactional(readOnly = true)
    public Optional<CurrentUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hash(rawToken))
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .map(token -> toCurrentUser(token.getUser()));
    }

    /** Abmelden = Sitzungszeile loeschen. Unbekannte Token sind kein Fehler (Abmelden bleibt idempotent). */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            tokens.deleteByTokenHash(hash(rawToken));
        }
    }

    /**
     * Anzeigename fuer die Kopfzeile: der Name der Lehrkraft, bei einem Klassenkonto „Klasse 7a",
     * sonst der Benutzername (Vertrag {@code LoginResponse.displayName}).
     */
    @Transactional(readOnly = true)
    public String displayName(CurrentUser user) {
        if (user.teacherId() != null) {
            return teachers.findById(user.teacherId()).map(Teacher::getName).orElse(user.username());
        }
        if (user.classId() != null) {
            return classes.findById(user.classId()).map(c -> "Klasse " + c.getName()).orElse(user.username());
        }
        return user.username();
    }

    private CurrentUser toCurrentUser(UserAccount account) {
        return new CurrentUser(account.getId(), account.getUsername(), account.getRole(),
                account.getTeacherId(), account.getClassId());
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt in dieser Java-Laufzeitumgebung.", e);
        }
    }
}
