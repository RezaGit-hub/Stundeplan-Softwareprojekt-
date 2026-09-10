package de.wsp3_1.auth;

import de.wsp3_1.shared.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Klassenkonten (E-50, ST-4): {@code POST /api/users/class-accounts} und die Rolle {@code STUDENT}
 * in {@code POST /api/users}. Gegen echtes PostgreSQL, damit CHECK und UNIQUE aus V1 mitgeprueft
 * werden.
 *
 * <p>Die Klasse laeuft in einer Transaktion, die am Ende zurueckgerollt wird — die Klassen- und
 * Kontozeilen dieses Tests sind danach wieder weg. Die Zeilen in {@code school_class} entstehen per
 * SQL und nicht ueber den Endpunkt des Moduls {@code masterdata}: geprueft wird das Modul
 * {@code auth}, nicht das Formular fuer Klassen.</p>
 */
@Transactional
class UsersIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String planungsToken() throws Exception {
        String antwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(antwort).get("token").asText();
    }

    private long klasse(String name, int grade, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO school_class (name, grade, active) VALUES (?, ?, ?) RETURNING id""",
                Long.class, name, grade, active);
    }

    /** Die Zeile zu einer Klasse; {@code null}, wenn die Antwort sie nicht enthaelt. */
    private static JsonNode zeileFuer(JsonNode antwort, String classShort) {
        for (JsonNode zeile : antwort) {
            if (classShort.equals(zeile.get("classShort").asText())) {
                return zeile;
            }
        }
        return null;
    }

    private JsonNode classAccounts(String token) throws Exception {
        String antwort = mvc.perform(post("/api/users/class-accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(antwort);
    }

    @Test
    @DisplayName("ST-4: fuer jede aktive Klasse entsteht ein Konto; der zweite Aufruf legt nichts mehr an")
    void classAccountsAreCreatedOnceAndAreIdempotent() throws Exception {
        String token = planungsToken();
        klasse("IT-7a", 7, true);
        klasse("IT-8b", 8, true);
        klasse("IT-9c", 9, false);

        JsonNode ersterLauf = classAccounts(token);

        // Nicht auf die Gesamtzahl geprueft: alle Integrationstests teilen eine Datenbank, andere
        // Klassen koennen also noch stehen. Geprueft wird, was mit DIESEN drei Klassen geschieht.
        assertThat(zeileFuer(ersterLauf, "IT-7a")).isNotNull();
        assertThat(zeileFuer(ersterLauf, "IT-7a").get("username").asText()).isEqualTo("it-7a");
        assertThat(zeileFuer(ersterLauf, "IT-7a").get("password").asText()).hasSize(10);
        assertThat(zeileFuer(ersterLauf, "IT-8b")).isNotNull();
        // Die deaktivierte Klasse bekommt kein Konto.
        assertThat(zeileFuer(ersterLauf, "IT-9c")).isNull();

        // Zweiter Aufruf: alle Klassen haben jetzt ein Konto, also kommt nichts Neues.
        assertThat(classAccounts(token)).isEmpty();
    }

    @Test
    @DisplayName("ST-4: mit dem ausgegebenen Passwort meldet sich das Klassenkonto an — als Rolle STUDENT")
    void generatedPasswordWorksForLogin() throws Exception {
        String token = planungsToken();
        klasse("IT-10a", 10, true);

        JsonNode zeile = zeileFuer(classAccounts(token), "IT-10a");
        assertThat(zeile).isNotNull();
        String username = zeile.get("username").asText();
        String password = zeile.get("password").asText();

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.displayName").value("Klasse IT-10a"))
                .andExpect(jsonPath("$.teacherId").isEmpty());
    }

    @Test
    @DisplayName("Die Kontenliste nennt Klasse und Klassenkuerzel eines Klassenkontos")
    void userListShowsTheClass() throws Exception {
        String token = planungsToken();
        long klasse11a = klasse("IT-11a", 11, true);
        classAccounts(token);

        String antwort = mvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode konten = objectMapper.readTree(antwort);
        JsonNode klassenkonto = null;
        for (JsonNode konto : konten) {
            if ("it-11a".equals(konto.get("username").asText())) {
                klassenkonto = konto;
            }
        }
        assertThat(klassenkonto).isNotNull();
        assertThat(klassenkonto.get("role").asText()).isEqualTo("STUDENT");
        assertThat(klassenkonto.get("classId").asLong()).isEqualTo(klasse11a);
        assertThat(klassenkonto.get("classShort").asText()).isEqualTo("IT-11a");
        assertThat(klassenkonto.get("teacherId").isNull()).isTrue();
    }

    @Test
    @DisplayName("POST /api/users legt ein Klassenkonto mit eigenem Namen an")
    void createStudentAccountByHand() throws Exception {
        String token = planungsToken();
        long klasse = klasse("IT-12a", 12, true);

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"klasse-12a","password":"start-passwort","role":"STUDENT","classId":%d}"""
                                .formatted(klasse)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.classId").value((int) klasse))
                .andExpect(jsonPath("$.classShort").value("IT-12a"))
                .andExpect(jsonPath("$.teacherId").isEmpty());
    }

    @Test
    @DisplayName("Rolle STUDENT ohne Klasse ergibt 400 am Feld classId")
    void studentWithoutClass_isValidationError() throws Exception {
        String token = planungsToken();

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ohne-klasse","password":"start-passwort","role":"STUDENT"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("classId"));
    }

    @Test
    @DisplayName("Ein zweites Konto fuer dieselbe Klasse ergibt 409 in-use")
    void secondAccountForTheSameClass_isConflict() throws Exception {
        String token = planungsToken();
        long klasse = klasse("IT-13a", 13, true);

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"klasse-13a","password":"start-passwort","role":"STUDENT","classId":%d}"""
                                .formatted(klasse)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"klasse-13a-zweit","password":"start-passwort","role":"STUDENT","classId":%d}"""
                                .formatted(klasse)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:in-use"));
    }
}
