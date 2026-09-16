package de.wsp3_1.auth.domain;

import de.wsp3_1.auth.persistence.AuthTokenRepository;
import de.wsp3_1.auth.persistence.UserAccount;
import de.wsp3_1.auth.persistence.UserAccountRepository;
import de.wsp3_1.masterdata.persistence.SchoolClass;
import de.wsp3_1.masterdata.persistence.SchoolClassRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.shared.CurrentUser;
import de.wsp3_1.shared.Role;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Konten anlegen und Passwoerter setzen (W-1 Vorbedingung, Abnahme A-1). Die Planung legt die Konten
 * an; einen Selbstregistrierung gibt es nicht, und Passwoerter stehen nirgends im Quelltext.
 *
 * <p>Ein Konto der Rolle {@code TEACHER} braucht eine Lehrkraft, eines der Rolle {@code STUDENT} eine
 * Klasse, eines der Rolle {@code PLANNING} keins von beiden — alles steht schon als CHECK und UNIQUE
 * in V1; hier wird es vorher geprueft, damit statt eines Datenbankfehlers (500) eine Feldmeldung (400)
 * herauskommt.</p>
 *
 * <p><b>Klassenkonten (E-50).</b> {@link #createClassAccounts()} legt fuer jede aktive Klasse ohne
 * Konto genau ein Konto an. Das Passwort entsteht zufaellig, verlaesst den Server genau einmal — in
 * der Antwort dieses einen Aufrufs — und liegt danach nur noch als BCrypt-Hash vor. Wer es verliert,
 * setzt ueber {@code PUT /api/users/&#123;id&#125;/password} ein neues; ausgelesen werden kann es nie.</p>
 */
@Service
public class UserService {

    /** Mindestlaenge eines Passworts (Vertrag {@code CreateUser.password}, {@code minLength: 8}). */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** Laenge der erzeugten Klassenpasswoerter — zehn Zeichen, vorlesbar und lang genug. */
    public static final int GENERATED_PASSWORD_LENGTH = 10;

    /**
     * Zeichenvorrat der erzeugten Passwoerter. Ohne {@code 0/O}, {@code 1/l/I} und ohne Sonderzeichen:
     * das Passwort wird in der Klasse vorgelesen und abgeschrieben, Verwechslungen kosten Zeit.
     */
    private static final String PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    private final UserAccountRepository users;
    private final AuthTokenRepository tokens;
    private final TeacherRepository teachers;
    private final SchoolClassRepository classes;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserAccountRepository users, AuthTokenRepository tokens, TeacherRepository teachers,
                       SchoolClassRepository classes, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokens = tokens;
        this.teachers = teachers;
        this.classes = classes;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() {
        List<UserAccount> alle = users.findAllByOrderByUsernameAsc();
        Map<Long, String> namen = classNames(alle.stream().map(UserAccount::getClassId).filter(java.util.Objects::nonNull).toList());
        return alle.stream().map(account -> toAccount(account, namen)).toList();
    }

    /**
     * Legt ein Konto an.
     *
     * @throws ValidationException wenn Rolle, Lehrkraft und Klasse nicht zusammenpassen
     * @throws ConflictException {@code in-use}, wenn Benutzername, Lehrkraft oder Klasse schon ein Konto haben
     */
    @Transactional
    public Account create(String username, String rawPassword, Role role, Long teacherId, Long classId) {
        if (role == Role.TEACHER && teacherId == null) {
            throw new ValidationException("teacherId", "Bitte die Lehrkraft waehlen, zu der dieses Konto gehoert.");
        }
        if (role != Role.TEACHER && teacherId != null) {
            throw new ValidationException("teacherId", "Nur ein Konto einer Lehrkraft gehoert zu einer Lehrkraft.");
        }
        if (role == Role.STUDENT && classId == null) {
            throw new ValidationException("classId", "Bitte die Klasse waehlen, zu der dieses Konto gehoert.");
        }
        if (role != Role.STUDENT && classId != null) {
            throw new ValidationException("classId", "Nur ein Klassenkonto gehoert zu einer Klasse.");
        }
        if (teacherId != null && !teachers.existsById(teacherId)) {
            throw new ValidationException("teacherId", "Diese Lehrkraft gibt es nicht.");
        }
        if (classId != null && !classes.existsById(classId)) {
            throw new ValidationException("classId", "Diese Klasse gibt es nicht.");
        }
        if (users.existsByUsername(username)) {
            throw new ConflictException("in-use", "Den Benutzernamen \"" + username + "\" gibt es schon.");
        }
        if (teacherId != null && users.existsByTeacherId(teacherId)) {
            throw new ConflictException("in-use", "Diese Lehrkraft hat bereits ein Konto.");
        }
        if (classId != null && users.existsByClassId(classId)) {
            throw new ConflictException("in-use", "Diese Klasse hat bereits ein Konto.");
        }
        UserAccount gespeichert = users.save(
                new UserAccount(username, encode(rawPassword), role, teacherId, classId));
        return toAccount(gespeichert, classNames(List.of()));
    }

    /**
     * Legt fuer jede aktive Klasse <b>ohne</b> Konto ein Klassenkonto an (E-50). Bestehende Konten
     * bleiben unberuehrt: ein zweiter Aufruf liefert deshalb eine leere Liste (idempotent).
     *
     * <p>Der Benutzername ist das Klassenkuerzel in Kleinbuchstaben („7a"). Ist er schon als anderes
     * Konto vergeben, wird die Klasse uebersprungen statt einen Fehler zu werfen — sonst blockierte
     * ein einziger Namenskonflikt die ganze Schule. Diese Klassen stehen dann weiter ohne Konto da
     * und lassen sich einzeln ueber {@code POST /api/users} mit eigenem Namen anlegen.</p>
     *
     * @return je neu angelegtem Konto Benutzername, Klasse und das Passwort im Klartext — das einzige
     *         Mal, dass der Server es herausgibt
     */
    @Transactional
    public List<ClassAccount> createClassAccounts() {
        Set<Long> mitKonto = new HashSet<>(users.findAllClassIds());
        List<ClassAccount> angelegt = new ArrayList<>();
        for (SchoolClass klasse : classes.findAllByOrderByIdAsc()) {
            if (!klasse.isActive() || mitKonto.contains(klasse.getId())) {
                continue;
            }
            String username = klasse.getName().trim().toLowerCase(java.util.Locale.GERMAN);
            if (username.isEmpty() || users.existsByUsername(username)) {
                continue;
            }
            String password = generatePassword();
            users.save(new UserAccount(username, encode(password), Role.STUDENT, null, klasse.getId()));
            angelegt.add(new ClassAccount(username, klasse.getName(), password));
        }
        return angelegt;
    }

    @Transactional
    public List<TeacherAccount> createTeacherAccounts() {
        Set<Long> mitKonto = new HashSet<>(users.findAllTeacherIds());
        List<TeacherAccount> angelegt = new ArrayList<>();
        for (Teacher lehrer : teachers.findAllByOrderByIdAsc()) {
            if (!lehrer.isActive() || mitKonto.contains(lehrer.getId())) {
                continue;
            }
            String username = lehrer.getShortName().trim().toLowerCase(java.util.Locale.GERMAN);
            if (username.isEmpty() || users.existsByUsername(username)) {
                continue;
            }
            String password = generatePassword();
            users.save(new UserAccount(username, encode(password), Role.TEACHER, lehrer.getId(), null));
            angelegt.add(new TeacherAccount(username, lehrer.getName(), lehrer.getShortName(), password));
        }
        return angelegt;
    }

    /**
     * Passwort eines fremden Kontos setzen (Planung, Ruecksetzung ohne Kenntnis des alten Passworts).
     * Alle Sitzungen dieses Kontos enden dabei — sonst bliebe ein altes Token weiter gueltig.
     */
    @Transactional
    public void setPassword(Long userId, String rawPassword) {
        // Dieselbe Sperre wie beim Anmelden (AuthService.login): eine Anmeldung, die gerade das alte
        // Passwort geprueft hat, wartet damit hier — sonst schriebe sie ihr Token erst nach dem
        // Loeschen, und die Sitzung ueberlebte die Ruecksetzung.
        UserAccount account = users.findWithLockById(userId)
                .orElseThrow(() -> new NotFoundException("Dieses Konto gibt es nicht."));
        account.setPasswordHash(encode(rawPassword));
        tokens.deleteByUser(account);
    }

    /**
     * Eigenes Passwort aendern. Das bisherige Passwort muss stimmen; sonst 400 am Feld
     * {@code oldPassword} (nicht 401 — angemeldet ist die Person ja).
     *
     * <p>Wie bei {@link #setPassword} enden dabei alle Sitzungen dieses Kontos (Befund SICHERHEIT-3,
     * 05.09.): wer sein Passwort aendert, weil ein Token in fremde Haende geraten ist, sperrt damit
     * auch den Dieb aus. Der Aufrufer bekommt danach 401 und meldet sich neu an — das Web schickt
     * ihn wie bei jedem 401 auf die Anmeldemaske.</p>
     */
    @Transactional
    public void changeOwnPassword(CurrentUser user, String oldPassword, String newPassword) {
        // Dieselbe Sperre wie in setPassword: sonst koennte eine gleichzeitige Anmeldung ihr Token
        // erst nach dem Loeschen schreiben und die Sitzung ueberlebte die Aenderung.
        UserAccount account = users.findWithLockById(user.id())
                .orElseThrow(() -> new NotFoundException("Dieses Konto gibt es nicht."));
        if (!passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
            throw new ValidationException("oldPassword", "Das bisherige Passwort ist falsch.");
        }
        account.setPasswordHash(encode(newPassword));
        tokens.deleteByUser(account);
    }

    /**
     * Entfernt ein Konto (Planung). Das Konto "admin" kann nicht entfernt werden, damit die Planung
     * sich nicht selbst aus dem System aussperrt (Systemregel, kein Berechtigungsproblem — daher 409,
     * nicht 403).
     *
     * <p>Bestehende Sitzungen dieses Kontos werden mitgeloescht — sonst bliebe ein altes Token trotz
     * geloeschtem Konto gueltig.</p>
     *
     * @throws NotFoundException wenn es kein Konto mit dieser ID gibt
     * @throws ConflictException {@code protected-account}, wenn versucht wird, das Konto "admin" zu loeschen
     */
    @Transactional
    public void deleteAccount(Long userId) {
        UserAccount account = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Dieses Konto gibt es nicht."));
        if ("admin".equals(account.getUsername())) {
            throw new ConflictException("protected-account", "Das Konto \"admin\" kann nicht entfernt werden.");
        }
        tokens.deleteByUser(account);
        users.delete(account);
    }

    /** Klassennamen zu den Kennungen; leer, wenn kein Konto zu einer Klasse gehoert. */
    private Map<Long, String> classNames(List<Long> classIds) {
        Map<Long, String> namen = new HashMap<>();
        if (classIds.isEmpty()) {
            return namen;
        }
        for (SchoolClass klasse : classes.findAllById(classIds)) {
            namen.put(klasse.getId(), klasse.getName());
        }
        return namen;
    }

    private Account toAccount(UserAccount account, Map<Long, String> classNames) {
        Long classId = account.getClassId();
        String classShort = classId == null ? null
                : classNames.computeIfAbsent(classId, id -> classes.findById(id).map(SchoolClass::getName).orElse(null));
        return new Account(account.getId(), account.getUsername(), account.getRole(),
                account.getTeacherId(), classId, classShort);
    }

    /** Zufaelliges Passwort aus {@link #PASSWORD_ALPHABET}; {@link SecureRandom}, nicht {@code Math.random}. */
    private String generatePassword() {
        StringBuilder sb = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("password", "Das Passwort braucht mindestens "
                    + MIN_PASSWORD_LENGTH + " Zeichen.");
        }
        return passwordEncoder.encode(rawPassword);
    }
}
