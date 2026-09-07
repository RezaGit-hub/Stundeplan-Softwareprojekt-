package de.wsp3_1.config;

import de.wsp3_1.shared.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET/PUT /api/config} gegen echtes PostgreSQL: Vorbelegung aus V2, Speichern landet wirklich
 * in der Tabelle, ungueltige Eingaben aendern nichts, und der Pfad gehoert der Planung.
 *
 * <p>Jeder Test stellt die Vorbelegung wieder her, damit die uebrigen Tests im selben Kontext
 * denselben Ausgangsstand sehen.</p>
 */
class ConfigIT extends IntegrationTestBase {

    private static final String VORBELEGUNG = """
            {"schoolName":"Gymnasium Musterstadt","state":"SH","schoolType":"GYMNASIUM","maxSolveSeconds":60}""";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void vorbelegungWiederherstellen() {
        jdbc.update("UPDATE system_config SET school_name = 'Gymnasium Musterstadt', state = 'SH', "
                + "school_type = 'GYMNASIUM', max_solve_seconds = 60 WHERE id = 1");
    }

    @Test
    @DisplayName("Die Planung liest die Vorbelegung Schleswig-Holstein / Gymnasium")
    void planningReadsSeededConfig() throws Exception {
        mvc.perform(get("/api/config").header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolName").value("Gymnasium Musterstadt"))
                .andExpect(jsonPath("$.state").value("SH"))
                .andExpect(jsonPath("$.schoolType").value("GYMNASIUM"))
                .andExpect(jsonPath("$.maxSolveSeconds").value(60));
    }

    @Test
    @DisplayName("Eine geaenderte Rechenzeit steht danach in der Datenbank")
    void changedSolveSecondsArePersisted() throws Exception {
        mvc.perform(put("/api/config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolName":"Gymnasium Kiel","state":"HH","schoolType":"GESAMTSCHULE","maxSolveSeconds":30}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolName").value("Gymnasium Kiel"))
                .andExpect(jsonPath("$.state").value("HH"))
                .andExpect(jsonPath("$.maxSolveSeconds").value(30));

        // Nicht nur die Antwort, sondern die Zeile selbst: so faellt ein fehlendes save() auf.
        assertThat(jdbc.queryForObject("SELECT max_solve_seconds FROM system_config WHERE id = 1", Integer.class))
                .isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT state FROM system_config WHERE id = 1", String.class))
                .isEqualTo("HH");

        // Der Schulname der Erreichbarkeitspruefung kommt aus derselben Zeile.
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schoolName").value("Gymnasium Kiel"));
    }

    @Test
    @DisplayName("Ein unbekanntes Bundesland-Kuerzel aendert nichts")
    void unknownStateLeavesConfigUnchanged() throws Exception {
        mvc.perform(put("/api/config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolName":"Gymnasium Musterstadt","state":"XX","schoolType":"GYMNASIUM","maxSolveSeconds":60}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("state"));

        assertThat(jdbc.queryForObject("SELECT state FROM system_config WHERE id = 1", String.class))
                .isEqualTo("SH");
    }

    @Test
    @DisplayName("Ohne Token 401, als Lehrkraft 403")
    void configBelongsToPlanning() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));

        mvc.perform(get("/api/config").with(user("meyer").roles("TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:forbidden"));

        mvc.perform(put("/api/config").with(user("meyer").roles("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VORBELEGUNG))
                .andExpect(status().isForbidden());
    }

    /** Anmeldung der Planung; {@code AdminBootstrap} legt das Konto beim ersten Start an. */
    private String token() throws Exception {
        String antwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(antwort).get("token").asText();
    }
}
