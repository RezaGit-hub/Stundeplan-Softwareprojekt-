package de.wsp3_1.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.wsp3_1.shared.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Der Anmeldeweg von Anfang bis Ende gegen echtes PostgreSQL: anmelden, Token benutzen, abmelden,
 * Token ist danach wertlos. Das Konto {@code admin} legt {@code AdminBootstrap} beim ersten Start an.
 */
class AuthFlowIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Anmelden liefert ein Token, das Token liefert die eigene Identitaet, Abmelden entwertet es")
    void loginUseLogout() throws Exception {
        String antwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLANNING"))
                .andExpect(jsonPath("$.displayName").value("admin"))
                .andExpect(jsonPath("$.teacherId").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(antwort);
        String token = json.get("token").asText();
        assertThat(token).isNotBlank();

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("PLANNING"));

        mvc.perform(delete("/api/auth/token").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));
    }

    @Test
    @DisplayName("Falsches Passwort ergibt 401 mit dem Text fuer den Anmeldedialog")
    void wrongPasswordIsUnauthenticated() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"falsch"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"))
                // Das Frontend zeigt `detail` im Anmeldedialog: dort darf nicht „Token abgelaufen" stehen.
                .andExpect(jsonPath("$.detail").value("Benutzername oder Passwort ist falsch."));
    }

    @Test
    @DisplayName("Ein unbekanntes Token und ein falsches Passwort sind im Text unterscheidbar")
    void unknownTokenKeepsItsOwnText() throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer kein-gueltiges-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"))
                .andExpect(jsonPath("$.detail").value("Bitte erneut anmelden: das Token fehlt oder ist abgelaufen."));
    }

    /**
     * Ein wirklich abgelaufenes Token, nicht nur ein unbekanntes: der Test darueber prueft die
     * Zeichenkette „kein-gueltiges-token", die in gar keiner Zeile steht — die Ablaufpruefung in
     * {@code AuthService.authenticate} kommt darin nie zum Zuge.
     *
     * <p>Die Ablauffrist (app.auth.token-ttl, Standard 30 Tage) laesst sich in einem Test nicht abwarten. Deshalb wird die soeben
     * angelegte Sitzungszeile per SQL in die Vergangenheit datiert — genau eine Zeile, damit die
     * Sitzungen anderer Tests unberuehrt bleiben.</p>
     */
    @Test
    @DisplayName("Ein abgelaufenes Token wird abgewiesen, obwohl seine Zeile noch in der Datenbank steht")
    void expiredTokenIsRejected() throws Exception {
        String antwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(antwort).get("token").asText();

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        int datiert = jdbc.update("""
                UPDATE auth_token SET expires_at = now() - interval '1 minute'
                WHERE id = (SELECT id FROM auth_token ORDER BY created_at DESC, id DESC LIMIT 1)""");
        assertThat(datiert).isOne();

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"))
                .andExpect(jsonPath("$.detail").value("Bitte erneut anmelden: das Token fehlt oder ist abgelaufen."));

        // Die Zeile steht noch: abgewiesen hat die Ablaufpruefung, nicht ein fehlender Eintrag.
        // Das taegliche Aufraeumen (TokenCleanup) entfernt sie spaeter.
        Integer zeilen = jdbc.queryForObject(
                "SELECT count(*) FROM auth_token WHERE expires_at < now()", Integer.class);
        assertThat(zeilen).isPositive();
    }

    @Test
    @DisplayName("Fehlender Benutzername ergibt 400 mit Feldliste")
    void missingUsernameIsValidationError() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"admin"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("username"));
    }

    @Test
    @DisplayName("Ohne Token bleibt jeder Planungspfad verschlossen")
    void planningPathWithoutTokenIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/teachers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));
    }

    /**
     * Bewusst ein Pfad, den es nie geben wird: {@code /api/teachers} beantwortet das Modul
     * {@code masterdata}, sobald es gebaut ist — dieser Test soll davon unabhaengig bleiben.
     */
    @Test
    @DisplayName("Mit Token ist ein unbekannter Pfad 404, nicht 500")
    void plannedButMissingPathIsNotFound() throws Exception {
        String antwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(antwort).get("token").asText();

        mvc.perform(get("/api/gibt-es-nicht").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:not-found"));
    }
}
