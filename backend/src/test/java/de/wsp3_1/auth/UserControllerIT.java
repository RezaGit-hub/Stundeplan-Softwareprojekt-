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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kontenverwaltung gegen echtes PostgreSQL: anlegen, Fehlerfaelle, Passwort setzen, danach anmelden.
 * Abnahme A-1 („Konto fuer Meyer anlegen") und die Faelle aus VERTRAG Abschnitt 4 zum Modul auth.
 *
 * <p>Die Klasse laeuft in einer Transaktion, die am Ende zurueckgerollt wird: die Konten und die
 * Lehrkraft-Zeile aus diesem Test sind danach wieder weg und stoeren die anderen Tests nicht.
 * Die Zeile in {@code teacher} wird per SQL angelegt und nicht ueber den Endpunkt des Moduls
 * {@code masterdata}: dieser Test soll nur das Modul {@code auth} pruefen und nicht scheitern,
 * wenn sich am Formular fuer Lehrkraefte etwas aendert.</p>
 */
@Transactional
class UserControllerIT extends IntegrationTestBase {

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

    private long lehrkraftMeyer() {
        return jdbc.queryForObject("""
                INSERT INTO teacher (name, short_name, weekly_hours_quota, active)
                VALUES ('Meyer', 'MEY-IT', 25, true) RETURNING id""", Long.class);
    }

    private long adminId() {
        return jdbc.queryForObject(
                "SELECT id FROM user_account WHERE username = 'admin'", Long.class);
    }

    @Test
    @DisplayName("A-1: die Planung legt ein Konto fuer eine Lehrkraft an und meldet sich damit an")
    void createTeacherAccount_thenLogin() throws Exception {
        String token = planungsToken();
        long meyer = lehrkraftMeyer();

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"meyer","password":"start-passwort","role":"TEACHER","teacherId":%d}"""
                                .formatted(meyer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("meyer"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.teacherId").value((int) meyer));

        // Der Anzeigename kommt aus der Lehrkraft, nicht aus dem Benutzernamen.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"meyer","password":"start-passwort"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.displayName").value("Meyer"))
                .andExpect(jsonPath("$.teacherId").value((int) meyer));
    }

    @Test
    @DisplayName("Rolle TEACHER ohne Lehrkraft ergibt 400 am Feld teacherId")
    void teacherWithoutTeacherId_isValidationError() throws Exception {
        String token = planungsToken();

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ohne","password":"start-passwort","role":"TEACHER"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("teacherId"));
    }

    @Test
    @DisplayName("Eine unbekannte Lehrkraft ergibt 400 statt eines Datenbankfehlers")
    void unknownTeacher_isValidationError() throws Exception {
        String token = planungsToken();

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"geist","password":"start-passwort","role":"TEACHER","teacherId":999999}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("teacherId"))
                .andExpect(jsonPath("$.errors[0].message").value("Diese Lehrkraft gibt es nicht."));
    }

    @Test
    @DisplayName("Ein Passwort mit weniger als acht Zeichen wird abgelehnt")
    void shortPassword_isValidationError() throws Exception {
        String token = planungsToken();

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"kurz","password":"1234","role":"PLANNING"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:validation"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    @DisplayName("Ein zweites Konto mit demselben Benutzernamen ergibt 409 in-use")
    void duplicateUsername_isConflict() throws Exception {
        String token = planungsToken();

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"start-passwort","role":"PLANNING"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:in-use"));
    }

    @Test
    @DisplayName("Die Planung setzt ein neues Passwort; danach gilt nur noch das neue")
    void setPassword_replacesTheOldOne() throws Exception {
        String token = planungsToken();
        long meyer = lehrkraftMeyer();

        String angelegt = mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"meyer2","password":"start-passwort","role":"TEACHER","teacherId":%d}"""
                                .formatted(meyer)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(angelegt).get("id").asLong();

        mvc.perform(put("/api/users/" + id + "/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"neues-passwort"}"""))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"meyer2","password":"start-passwort"}"""))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"meyer2","password":"neues-passwort"}"""))
                .andExpect(status().isOk());
    }

    /**
     * Der Sinn des Passwortsetzens ist, eine verlorene oder verratene Sitzung zu beenden. Der Test
     * darueber prueft nur die beiden Passwoerter — eine Sitzung, die vor dem Setzen offen war, kam
     * darin nicht vor. Genau die muss danach wertlos sein.
     */
    @Test
    @DisplayName("Ein Passwort zu setzen entwertet die bereits offene Sitzung des Kontos")
    void setPassword_endsExistingSession() throws Exception {
        String token = planungsToken();
        long meyer = lehrkraftMeyer();

        String angelegt = mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"meyer3","password":"start-passwort","role":"TEACHER","teacherId":%d}"""
                                .formatted(meyer)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(angelegt).get("id").asLong();

        String meyerAntwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"meyer3","password":"start-passwort"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String meyerToken = objectMapper.readTree(meyerAntwort).get("token").asText();

        // Die Sitzung ist offen und traegt.
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + meyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("meyer3"));

        mvc.perform(put("/api/users/" + id + "/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"neues-passwort"}"""))
                .andExpect(status().isNoContent());

        // Und ist danach wertlos — ohne dass sich jemand abgemeldet haette.
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + meyerToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));
    }

    @Test
    @DisplayName("Das eigene Passwort zu aendern entwertet die anderen Sitzungen desselben Kontos")
    void changeOwnPassword_endsOtherSessions() throws Exception {
        String token = planungsToken();
        long meyer = lehrkraftMeyer();

        mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"meyer4","password":"start-passwort","role":"TEACHER","teacherId":%d}"""
                                .formatted(meyer)))
                .andExpect(status().isCreated());

        // Zwei Sitzungen desselben Kontos: die eine aendert das Passwort, die andere ist der „Dieb".
        String ersteSitzung = meyerToken("meyer4", "start-passwort");
        String zweiteSitzung = meyerToken("meyer4", "start-passwort");

        mvc.perform(put("/api/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ersteSitzung)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"start-passwort","newPassword":"neues-passwort"}"""))
                .andExpect(status().isNoContent());

        // Beide Token sind danach wertlos — genau das ist der Sinn der Aenderung (Befund SICHERHEIT-3).
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + zweiteSitzung))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:unauthenticated"));
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + ersteSitzung))
                .andExpect(status().isUnauthorized());

        // Mit dem neuen Passwort geht es wieder.
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + meyerToken("meyer4", "neues-passwort")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("meyer4"));
    }

    /** Anmeldung eines beliebigen Kontos; liefert das Klartext-Token. */
    private String meyerToken(String username, String password) throws Exception {
        String antwort = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}""".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(antwort).get("token").asText();
    }

    @Test
    @DisplayName("Ein Passwort fuer ein unbekanntes Konto ergibt 404")
    void setPasswordForUnknownUser_isNotFound() throws Exception {
        String token = planungsToken();

        mvc.perform(put("/api/users/999999/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"neues-passwort"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:not-found"));
    }

    @Test
    @DisplayName("Die Kontenliste zeigt das Konto der Planung")
    void listUsers_containsAdmin() throws Exception {
        String token = planungsToken();

        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].role").value("PLANNING"))
                .andExpect(jsonPath("$[0].teacherId").doesNotExist());
    }

    @Test
    @DisplayName("Das eigene Passwort aendert nur, wer das bisherige kennt")
    void changeOwnPassword_needsTheOldOne() throws Exception {
        String token = planungsToken();

        mvc.perform(put("/api/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"falsch","newPassword":"neues-passwort"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("oldPassword"));

        mvc.perform(put("/api/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"admin","newPassword":"neues-passwort"}"""))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"neues-passwort"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Das Konto admin kann nicht geloescht werden")
    void deleteAdmin_isConflict() throws Exception {
        String token = planungsToken();
        long admin = adminId();

        mvc.perform(delete("/api/users/" + admin)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:protected-account"));
    }

    @Test
    @DisplayName("Ein unbekanntes Konto zu loeschen ergibt 404")
    void deleteUnknownUser_isNotFound() throws Exception {
        String token = planungsToken();

        mvc.perform(delete("/api/users/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:not-found"));
    }

    @Test
    @DisplayName("Ein normales Konto laesst sich loeschen; danach ist sein Token wertlos")
    void deleteAccount_removesItAndInvalidatesItsSessions() throws Exception {
        String token = planungsToken();
        long meyer = lehrkraftMeyer();

        String angelegt = mvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"meyer5","password":"start-passwort","role":"TEACHER","teacherId":%d}"""
                                .formatted(meyer)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(angelegt).get("id").asLong();
        String meyerToken = meyerToken("meyer5", "start-passwort");

        mvc.perform(delete("/api/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // Konto ist weg
        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$[?(@.username == 'meyer5')]").doesNotExist());

        // seine Sitzung ist es auch
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + meyerToken))
                .andExpect(status().isUnauthorized());
    }
}
