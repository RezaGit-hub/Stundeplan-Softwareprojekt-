package de.wsp3_1.masterdata;

import de.wsp3_1.shared.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code DELETE /api/clear-data/all}: leert Grunddaten, Plaene, Ereignisse, Ausnahmen, Verlauf und
 * die Konten der Lehrkraefte und Klassen. Gegen echtes PostgreSQL, damit die tatsaechliche Leerung
 * und der Fortbestand des Planungskontos geprueft werden, nicht nur der HTTP-Status. Getestet ueber
 * den Controller statt {@link de.wsp3_1.masterdata.domain.ClearService} direkt: der Dienst delegiert
 * nur an {@link de.wsp3_1.shared.domain.DatabaseCleaner}, die eigentliche Pruefung gilt der Route,
 * der Transaktion und der Wirkung auf die Datenbank.
 */
@Transactional
class ClearControllerIT extends IntegrationTestBase {

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

    private long lehrkraft() {
        return jdbc.queryForObject("""
                INSERT INTO teacher (name, short_name, weekly_hours_quota, active)
                VALUES ('Meyer', 'CLR-MEY', 25, true) RETURNING id""", Long.class);
    }

    @Test
    @DisplayName("Grunddaten sind nach dem Leeren weg")
    void clearRemovesMasterData() throws Exception {
        String token = planungsToken();
        long klasse = jdbc.queryForObject(
                "INSERT INTO school_class (name, grade, active) VALUES ('CLR-7a', 7, true) RETURNING id",
                Long.class);
        long lehrer = lehrkraft();

        mvc.perform(delete("/api/clear-data/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        Integer klassen = jdbc.queryForObject("SELECT count(*) FROM school_class WHERE id = ?", Integer.class, klasse);
        Integer lehrkraefte = jdbc.queryForObject("SELECT count(*) FROM teacher WHERE id = ?", Integer.class, lehrer);
        assertThat(klassen).isZero();
        assertThat(lehrkraefte).isZero();
    }

    @Test
    @DisplayName("Das Konto der Planung bleibt nach dem Leeren bestehen")
    void clearKeepsThePlanningAccount() throws Exception {
        String token = planungsToken();

        mvc.perform(delete("/api/clear-data/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Lehrkraftkonten sind nach dem Leeren weg; ihr Token ist wertlos")
    void clearRemovesTeacherAccounts() throws Exception {
        String token = planungsToken();
        long lehrer = lehrkraft();

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"clr-meyer","password":"start-passwort","role":"TEACHER","teacherId":%d}"""
                                .formatted(lehrer)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/clear-data/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"clr-meyer","password":"start-passwort"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Die Sequenzen fangen nach dem Leeren wieder bei 1 an")
    void clearResetsSequences() throws Exception {
        String token = planungsToken();
        jdbc.update("INSERT INTO school_class (name, grade, active) VALUES ('CLR-8b', 8, true)");

        mvc.perform(delete("/api/clear-data/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        long neueId = jdbc.queryForObject(
                "INSERT INTO school_class (name, grade, active) VALUES ('CLR-9c', 9, true) RETURNING id",
                Long.class);
        assertThat(neueId).isEqualTo(1L);
    }

    @Test
    @DisplayName("Zweimaliges Leeren hintereinander funktioniert (idempotent, keine Fehler)")
    void clearingTwiceInARowSucceeds() throws Exception {
        String token = planungsToken();

        mvc.perform(delete("/api/clear-data/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/clear-data/all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Ohne Anmeldung ist der Endpunkt gesperrt")
    void clearWithoutToken_isUnauthenticated() throws Exception {
        mvc.perform(delete("/api/clear-data/all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));
    }
}