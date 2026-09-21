package de.wsp3_1.substitution;

import de.wsp3_1.planning.PlanningITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/me/timetable/week} — „Mein Plan" (Checkliste A-2 und A-3, Testfall T-A1.1).
 *
 * <p>Der Kern ist der Zugriffsschutz: die Lehrkraft kommt aus dem Token, und dieselbe Anmeldung darf
 * {@code GET /api/timetable} <b>nicht</b> lesen. Die Arten der Eintraege ({@code GIVEN_AWAY},
 * {@code NEEDS_SUBSTITUTION}) entstehen erst mit Teil 2 des Moduls; hier ist alles {@code REGULAR}.</p>
 */
class MyWeekIT extends PlanningITBase {

    private static final LocalDate MONTAG = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1);

    /** Der Montag der <b>laufenden</b> Woche — den meint der Aufruf ohne {@code date}. */
    private static final LocalDate DIESE_WOCHE = LocalDate.now().with(DayOfWeek.MONDAY);

    @Test
    @DisplayName("T-A1.1 (A-2/A-3): Meyer sieht ihre eigene Woche — Montag vier Stunden, Dienstag eine, sonst nichts")
    void tA1_1_teacherSeesOnlyHerOwnWeek() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG)), 200);
        String meyerToken = lehrkraftkonto("meyer", daten.meyer);

        JsonNode week = alsLehrkraft(meyerToken, "/api/me/timetable/week?date=" + MONTAG);

        assertThat(week.get("monday").asText()).isEqualTo(MONTAG.toString());
        assertThat(week.get("days").get(0).get("entries")).hasSize(4);
        assertThat(week.get("days").get(1).get("entries")).hasSize(1);
        assertThat(week.get("days").get(2).get("entries")).isEmpty();

        int inDerWoche = 0;
        for (JsonNode day : week.get("days")) {
            for (JsonNode entry : day.get("entries")) {
                inDerWoche++;
                assertThat(entry.get("teacherId").asLong())
                        .as("keine fremde Lehrkraft in der eigenen Woche")
                        .isEqualTo(daten.meyer);
                assertThat(entry.get("kind").asText()).isEqualTo("REGULAR");
            }
        }
        assertThat(inDerWoche).as("A-3: fuenf Eintraege in der Woche").isEqualTo(5);
    }

    @Test
    @DisplayName("T-A1.1: dieselbe Anmeldung bekommt auf GET /api/timetable 403, ohne Token 401")
    void tA1_1_teacherMayNotReadThePlanningEndpoints() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        String meyerToken = lehrkraftkonto("meyer", daten.meyer);

        mvc.perform(get("/api/timetable").header(HttpHeaders.AUTHORIZATION, "Bearer " + meyerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + meyerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/me/timetable/week")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Ein Konto ohne Lehrkraft — die Planung selbst — bekommt 404")
    void planningAccountHasNoOwnWeek() throws Exception {
        pflichtenheftGrunddaten();

        JsonNode problem = call(get("/api/me/timetable/week"), 404);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:not-found");
        assertThat(problem.get("detail").asText()).isEqualTo("Dieses Konto gehört zu keiner Lehrkraft.");
    }

    @Test
    @DisplayName("Ohne „date“ ist es die laufende Woche")
    void withoutADateItIsTheCurrentWeek() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        String meyerToken = lehrkraftkonto("meyer", daten.meyer);

        assertThat(alsLehrkraft(meyerToken, "/api/me/timetable/week").get("monday").asText())
                .isEqualTo(DIESE_WOCHE.toString());
    }

    // ------------------------------------------------------------------ Helfer

    /** Legt ein Konto fuer eine Lehrkraft an und meldet es an; zurueck kommt dessen Token. */
    private String lehrkraftkonto(String benutzername, long teacherId) throws Exception {
        call(post("/api/users").content("""
                {"username":"%s","password":"geheim-2026","role":"TEACHER","teacherId":%d}"""
                .formatted(benutzername, teacherId)), 201);
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"geheim-2026"}""".formatted(benutzername)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private JsonNode alsLehrkraft(String token, String path) throws Exception {
        String body = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }
}
