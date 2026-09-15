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
 * Lehrkraftkonten, analog zu {@link UsersIT} fuer Klassenkonten: {@code POST /api/users/teacher-accounts}.
 * Gegen echtes PostgreSQL, damit CHECK und UNIQUE aus V1 mitgeprueft werden — insbesondere, dass ein
 * Konto der Rolle TEACHER eine {@code teacher_id} und keine {@code class_id} traegt.
 */
@Transactional
class TeacherAccountsIT extends IntegrationTestBase {

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

    private long lehrkraft(String name, String shortName, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO teacher (name, short_name, weekly_hours_quota, active)
                VALUES (?, ?, 25, ?) RETURNING id""",
                Long.class, name, shortName, active);
    }

    /** Die Zeile zu einer Lehrkraft; {@code null}, wenn die Antwort sie nicht enthaelt. */
    private static JsonNode zeileFuer(JsonNode antwort, String shortName) {
        for (JsonNode zeile : antwort) {
            if (shortName.equals(zeile.get("teacherShort").asText())) {
                return zeile;
            }
        }
        return null;
    }

    private JsonNode teacherAccounts(String token) throws Exception {
        String antwort = mvc.perform(post("/api/users/teacher-accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(antwort);
    }

    @Test
    @DisplayName("Fuer jede aktive Lehrkraft entsteht ein Konto; der zweite Aufruf legt nichts mehr an")
    void teacherAccountsAreCreatedOnceAndAreIdempotent() throws Exception {
        String token = planungsToken();
        lehrkraft("Schmidt", "IT-SCH", true);
        lehrkraft("Weber", "IT-WEB", true);
        lehrkraft("Fischer", "IT-FIS", false);

        JsonNode ersterLauf = teacherAccounts(token);

        assertThat(zeileFuer(ersterLauf, "IT-SCH")).isNotNull();
        assertThat(zeileFuer(ersterLauf, "IT-SCH").get("username").asText()).isEqualTo("it-sch");
        assertThat(zeileFuer(ersterLauf, "IT-SCH").get("password").asText()).hasSize(10);
        assertThat(zeileFuer(ersterLauf, "IT-WEB")).isNotNull();
        // Die deaktivierte Lehrkraft bekommt kein Konto.
        assertThat(zeileFuer(ersterLauf, "IT-FIS")).isNull();

        // Zweiter Aufruf: alle Lehrkraefte haben jetzt ein Konto, also kommt nichts Neues.
        assertThat(teacherAccounts(token)).isEmpty();
    }

    @Test
    @DisplayName("Mit dem ausgegebenen Passwort meldet sich das Lehrkraftkonto an — als Rolle TEACHER mit teacherId, ohne classId")
    void generatedPasswordWorksForLogin() throws Exception {
        String token = planungsToken();
        long lehrkraft = lehrkraft("Meyer", "IT-MEY", true);

        JsonNode zeile = zeileFuer(teacherAccounts(token), "IT-MEY");
        assertThat(zeile).isNotNull();
        String username = zeile.get("username").asText();
        String password = zeile.get("password").asText();

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.displayName").value("Meyer"))
                .andExpect(jsonPath("$.teacherId").value((int) lehrkraft));
    }

    @Test
    @DisplayName("Die Kontenliste nennt teacherId, nicht classId, bei einem Lehrkraftkonto")
    void userListShowsTheTeacher_notAClass() throws Exception {
        String token = planungsToken();
        long lehrkraft = lehrkraft("Bauer", "IT-BAU", true);
        teacherAccounts(token);

        String antwort = mvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode konten = objectMapper.readTree(antwort);
        JsonNode konto = null;
        for (JsonNode k : konten) {
            if ("it-bau".equals(k.get("username").asText())) {
                konto = k;
            }
        }
        assertThat(konto).isNotNull();
        assertThat(konto.get("role").asText()).isEqualTo("TEACHER");
        assertThat(konto.get("teacherId").asLong()).isEqualTo(lehrkraft);
        // Das ist genau der Fehler, den der Copy-Paste-Bug erzeugt haette: classId waere gesetzt.
        assertThat(konto.get("classId").isNull()).isTrue();
    }
}