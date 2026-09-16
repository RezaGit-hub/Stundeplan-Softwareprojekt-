package de.wsp3_1.auth;

import de.wsp3_1.auth.api.AuthController;
import de.wsp3_1.auth.api.SecurityConfig;
import de.wsp3_1.auth.api.UserController;
import de.wsp3_1.auth.domain.AuthService;
import de.wsp3_1.auth.domain.UserService;
import de.wsp3_1.config.api.HealthController;
import de.wsp3_1.config.domain.SystemConfigService;
import de.wsp3_1.shared.CurrentUser;
import de.wsp3_1.shared.Role;
import de.wsp3_1.shared.api.ApiExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Die Pfadregeln des Zugriffsschutzes (E-45, E-38, VERTRAG Abschnitt 6). Ohne Datenbank: der
 * Anmeldedienst ist eine Attrappe, geprueft wird die Kette aus Filter, Pfadregeln und Fehlerformat.
 * Den Ende-zu-Ende-Weg gegen echtes PostgreSQL pruefen {@code AuthFlowIT} und {@code UserControllerIT}.
 *
 * <p>Geladen werden nur die drei Controller, die dieser Test braucht. Ohne die Aufzaehlung zoege
 * {@code @WebMvcTest} jeden Controller der Anwendung herein und der Test wuerde scheitern, sobald
 * ein anderes Modul einen Controller mit einer neuen Abhaengigkeit anlegt. Pfade ohne geladenen
 * Controller antworten 404 — das genuegt, denn geprueft wird die Regel davor, nicht die Fachlogik.</p>
 */
@WebMvcTest(controllers = {AuthController.class, UserController.class, HealthController.class})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class SecurityConfigTest {

    private static final String TEACHER_TOKEN = "tokenEinerLehrkraft";
    private static final String STUDENT_TOKEN = "tokenEinesKlassenkontos";
    private static final CurrentUser MEYER = new CurrentUser(7L, "meyer", Role.TEACHER, 3L, null);
    /** Klassenkonto der 7a (E-50): keine Lehrkraft, dafuer eine Klasse. */
    private static final CurrentUser KLASSE_7A = new CurrentUser(11L, "7a", Role.STUDENT, null, 1L);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SystemConfigService systemConfigService;

    // Seit 05.09. laedt dieser Test nur die drei Controller oben (siehe Klassenkommentar). Andere
    // Module brauchen hier keine Attrappe mehr einzutragen; ihre Controller sind gar nicht geladen
    // (die Attrappe fuer RuleService des Moduls rules ist deshalb am 05.09. wieder entfallen).

    private void angemeldetAls(CurrentUser user) {
        given(authService.authenticate(anyString())).willReturn(Optional.of(user));
        given(authService.displayName(any())).willReturn(user.username());
    }

    @Test
    @DisplayName("Die Erreichbarkeitspruefung antwortet ohne Anmeldung")
    void health_withoutToken_isOk() throws Exception {
        given(systemConfigService.schoolName()).willReturn("Gymnasium Musterstadt");

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.schoolName").value("Gymnasium Musterstadt"));
    }

    @Test
    @DisplayName("Negativ 401: ohne Token gibt es 401 im ProblemDetail-Format, nicht 403")
    void me_withoutToken_isUnauthenticated() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));
    }

    @Test
    @DisplayName("Negativ 401: auch die Kontenverwaltung verlangt eine Anmeldung")
    void users_withoutToken_isUnauthenticated() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));
    }

    @Test
    @DisplayName("Negativ 403: eine Lehrkraft bekommt auf Planungspfaden 403")
    void teachers_withTeacherToken_isForbidden() throws Exception {
        angemeldetAls(MEYER);

        mvc.perform(get("/api/teachers").header(HttpHeaders.AUTHORIZATION, "Bearer " + TEACHER_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:forbidden"));
    }

    /** Abnahme A-6: die App kann nichts planen — das entscheidet der Server, nicht die App. */
    @Test
    @DisplayName("Negativ 403 (A-6): eine Lehrkraft darf keine Stunde in den Entwurf legen")
    void draftLessons_withTeacherToken_isForbidden() throws Exception {
        angemeldetAls(MEYER);

        mvc.perform(post("/api/draft/lessons")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEACHER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"classId":1,"subjectId":1,"timeslotId":1,"roomId":1}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:forbidden"));
    }

    @Test
    @DisplayName("Negativ 403: die Kontenverwaltung ist der Planung vorbehalten")
    void users_withTeacherToken_isForbidden() throws Exception {
        angemeldetAls(MEYER);

        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + TEACHER_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:forbidden"));
    }

    @Test
    @DisplayName("Dieselbe Lehrkraft darf ihre eigene Identitaet lesen")
    void me_withTeacherToken_isOk() throws Exception {
        angemeldetAls(MEYER);

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + TEACHER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("meyer"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.teacherId").value(3));
    }

    /**
     * Das Stundenraster darf auch eine Lehrkraft lesen (App und „Mein Plan" brauchen die Zeiten).
     * Der Controller dazu gehoert dem Modul {@code masterdata} und existiert im Modul {@code auth}
     * noch nicht — geprueft wird deshalb nur, dass die Anfrage die Pfadregel passiert: die Antwort
     * ist 404 {@code not-found} und eben nicht 403.
     */
    @Test
    @DisplayName("Das Stundenraster ist fuer eine Lehrkraft nicht gesperrt")
    void timegrid_withTeacherToken_passesTheRule() throws Exception {
        angemeldetAls(MEYER);

        mvc.perform(get("/api/timegrid").header(HttpHeaders.AUTHORIZATION, "Bearer " + TEACHER_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:not-found"));
    }

    // ------------------------------------------------------------------ Klassenkonto (E-50, ST-2)

    /**
     * Der Kern der Schuelersicht: das Klassenkonto kommt an genau sechs Pfaden vorbei. Alles andere
     * ist 403 — auch der Gesamtplan und die Grunddaten, aus denen sich Klarnamen lesen liessen.
     */
    @Test
    @DisplayName("Negativ 403 (ST-2): ein Klassenkonto darf den Gesamtplan nicht lesen")
    void timetable_withStudentToken_isForbidden() throws Exception {
        angemeldetAls(KLASSE_7A);

        mvc.perform(get("/api/timetable").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:forbidden"));

        // Die Planliste (#115) faellt unter dieselbe Regel „alles Uebrige ist Planungssache" —
        // es gibt dafuer keine eigene Zeile in SecurityConfig, und genau das wird hier gemessen.
        mvc.perform(get("/api/timetables").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:forbidden"));
        mvc.perform(get("/api/timetables/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:forbidden"));
    }

    @Test
    @DisplayName("Negativ 403 (ST-2): ein Klassenkonto darf die Lehrkraefte nicht lesen")
    void teachers_withStudentToken_isForbidden() throws Exception {
        angemeldetAls(KLASSE_7A);

        mvc.perform(get("/api/teachers").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:forbidden"));
    }

    /**
     * Die Lehrkraft-Sicht ist fuer ein Klassenkonto gesperrt, obwohl sie unter {@code /api/me/**}
     * liegt: sonst saehe die Klasse den Tagesablauf einer einzelnen Person.
     */
    @Test
    @DisplayName("Negativ 403 (ST-2): ein Klassenkonto darf „Mein Plan“ einer Lehrkraft nicht lesen")
    void myWeek_withStudentToken_isForbidden() throws Exception {
        angemeldetAls(KLASSE_7A);

        mvc.perform(get("/api/me/timetable/week").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:forbidden"));
    }

    /**
     * Umgekehrt: die Klassensicht ist <b>nur</b> fuer das Klassenkonto da. Der Controller dazu gehoert
     * dem Modul {@code substitution} und ist hier nicht geladen — geprueft wird also nur, dass die
     * Anfrage die Pfadregel passiert (404 statt 403), genau wie beim Stundenraster.
     */
    @Test
    @DisplayName("Die Klassensicht ist fuer ein Klassenkonto nicht gesperrt")
    void classWeek_withStudentToken_passesTheRule() throws Exception {
        angemeldetAls(KLASSE_7A);

        mvc.perform(get("/api/me/class-week").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:not-found"));
    }

    @Test
    @DisplayName("Ein Klassenkonto darf seine eigene Identitaet und das Stundenraster lesen")
    void me_andTimegrid_withStudentToken_passTheRule() throws Exception {
        angemeldetAls(KLASSE_7A);

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("7a"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.teacherId").isEmpty());
        // Der Raster-Controller gehoert masterdata und ist hier nicht geladen: 404 heisst „Regel passiert".
        mvc.perform(get("/api/timegrid").header(HttpHeaders.AUTHORIZATION, "Bearer " + STUDENT_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:not-found"));
    }

    /** Seit dem 05.09. ist das Team-Beispiel nicht mehr ohne Anmeldung erreichbar (VERTRAG, backend1). */
    @Test
    @DisplayName("Das Team-Beispiel ist nicht mehr freigegeben")
    void clients_withoutToken_isUnauthenticated() throws Exception {
        mvc.perform(get("/api/clients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));
    }
}
