package de.wsp3_1.auth;

import de.wsp3_1.shared.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rundgang durch die Webanwendung (#139, T2-3.1, E-55): {@code PUT /api/me/tour} setzt oder loescht
 * {@code user_account.tour_completed_at} (V10), {@code GET /api/me} zeigt das Feld. Gegen echtes
 * PostgreSQL, damit die neue Spalte aus der Migration mitgeprueft wird.
 *
 * <p>Die Klasse laeuft in einer Transaktion, die am Ende zurueckgerollt wird: das Konto {@code admin}
 * steht danach wieder ohne Rundgang da, und die Lehrkraft samt Konto ist wieder weg.</p>
 */
@Transactional
class MeTourIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String token(String username, String password) throws Exception {
        String antwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(antwort).get("token").asText();
    }

    private void setzeRundgang(String token, boolean completed) throws Exception {
        mvc.perform(put("/api/me/tour")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":" + completed + "}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Planung: Rundgang beenden setzt den Zeitpunkt, erneut zeigen loescht ihn wieder")
    void planningMarksAndResetsTour() throws Exception {
        String token = token("admin", "admin");

        // Vor dem ersten Rundgang ist das Feld leer — das Web zeigt ihn dann.
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tourCompletedAt").isEmpty());

        setzeRundgang(token, true);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                // ISO-Zeitstempel als Zeichenkette, wie createdAt im uebrigen Vertrag.
                .andExpect(jsonPath("$.tourCompletedAt").isString())
                .andExpect(jsonPath("$.tourCompletedAt").value(startsWith("20")));

        setzeRundgang(token, false);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tourCompletedAt").isEmpty());
    }

    @Test
    @DisplayName("Lehrkraft: auch ein Lehrkraft-Konto darf seinen Rundgang setzen (x-role ANY)")
    void teacherMarksTour() throws Exception {
        String planung = token("admin", "admin");
        long meyer = jdbc.queryForObject("""
                INSERT INTO teacher (name, short_name, weekly_hours_quota, active)
                VALUES ('Meyer', 'MEY-TR', 25, true) RETURNING id""", Long.class);
        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + planung)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"meyer-tour","password":"start-passwort","role":"TEACHER","teacherId":%d}"""
                                .formatted(meyer)))
                .andExpect(status().isCreated());

        String lehrkraft = token("meyer-tour", "start-passwort");
        setzeRundgang(lehrkraft, true);

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + lehrkraft))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.tourCompletedAt").isString());

        // Das Feld gehoert zum Konto: die Planung bleibt davon unberuehrt.
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + planung))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tourCompletedAt").isEmpty());
    }

    @Test
    @DisplayName("Ohne Token ist der Rundgang 401, nicht 403")
    void withoutTokenIsUnauthenticated() throws Exception {
        mvc.perform(put("/api/me/tour")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));
    }

    @Test
    @DisplayName("Fehlendes completed ergibt 400 mit Feldliste")
    void missingFlagIsValidationError() throws Exception {
        String token = token("admin", "admin");
        mvc.perform(put("/api/me/tour")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("completed"));
    }
}
