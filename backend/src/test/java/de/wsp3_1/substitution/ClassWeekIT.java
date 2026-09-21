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
 * {@code GET /api/me/class-week} — die Schuelersicht (E-50, ST-1 bis ST-3).
 *
 * <p>Geprueft wird genau das, was die Recherche verlangt
 * ({@code 07-Konzept/recherche/sammelzugang-schueler.md}, Abschnitt „Bewertung"):</p>
 * <ol>
 *   <li>Das Klassenkonto der 7a sieht <b>nur</b> Stunden der 7a — keine fremde Klasse.</li>
 *   <li>{@code reasons} ist leer, auch wenn ein Ausfall mit Anlass eingetragen ist. Die <em>Art</em>
 *       {@code NEEDS_SUBSTITUTION} bleibt sichtbar, der Grund verschwindet.</li>
 *   <li>Eine gesetzte Vertretung zeigt den Kuerzelwechsel ({@code original.teacherShort} →
 *       {@code teacherShort}), aber keine Kennung der alten Lehrkraft.</li>
 *   <li>Dieselbe Anmeldung bekommt auf den Planungspfaden 403.</li>
 *   <li>Ein Konto ohne Klasse — die Planung selbst — bekommt 404.</li>
 * </ol>
 */
class ClassWeekIT extends PlanningITBase {

    private static final LocalDate MONTAG = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1);

    /** Der Montag der <b>laufenden</b> Woche — den meint der Aufruf ohne {@code date}. */
    private static final LocalDate DIESE_WOCHE = LocalDate.now().with(DayOfWeek.MONDAY);

    /** Der Plan des Pflichtenhefts, angenommen mit Gueltigkeit ab dem Montag dieser Woche. */
    private Testdaten planDerWoche() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG)), 200);
        return daten;
    }

    /** Legt das Klassenkonto der Klasse an und meldet es an; zurueck kommt dessen Token. */
    private String klassenkonto(String benutzername, long classId) throws Exception {
        call(post("/api/users").content("""
                {"username":"%s","password":"geheim-2026","role":"STUDENT","classId":%d}"""
                .formatted(benutzername, classId)), 201);
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"geheim-2026"}""".formatted(benutzername)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private JsonNode alsKlasse(String token, String path) throws Exception {
        String body = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    @DisplayName("ST-1: das Konto der 7a sieht nur Stunden der 7a und keinen einzigen Anlass")
    void classAccountSeesOnlyItsOwnClass() throws Exception {
        Testdaten daten = planDerWoche();
        String token = klassenkonto("7a", daten.klasse7a);

        JsonNode week = alsKlasse(token, "/api/me/class-week?date=" + MONTAG);

        assertThat(week.get("monday").asText()).isEqualTo(MONTAG.toString());
        assertThat(week.get("days")).hasSize(5);

        int eintraege = 0;
        for (JsonNode day : week.get("days")) {
            for (JsonNode entry : day.get("entries")) {
                eintraege++;
                assertThat(entry.get("classId").asLong())
                        .as("keine fremde Klasse in der Klassensicht")
                        .isEqualTo(daten.klasse7a);
                assertThat(entry.get("classShort").asText()).isEqualTo("7a");
                assertThat(entry.get("reasons")).as("kein Anlass in der Klassensicht").isEmpty();
                // Das Kuerzel bleibt — es ist die einzige Angabe zur Lehrkraft im Vertrag.
                assertThat(entry.get("teacherShort").asText()).isNotBlank();
            }
        }
        // Der Regelplan der 7a: Mo 1./2./4. und Di 1.
        assertThat(eintraege).as("vier Stunden der 7a in dieser Woche").isEqualTo(4);
    }

    @Test
    @DisplayName("ST-1: ohne „date“ ist es die laufende Woche")
    void withoutADateItIsTheCurrentWeek() throws Exception {
        Testdaten daten = planDerWoche();
        String token = klassenkonto("7a", daten.klasse7a);

        assertThat(alsKlasse(token, "/api/me/class-week").get("monday").asText())
                .isEqualTo(DIESE_WOCHE.toString());
    }

    @Test
    @DisplayName("ST-3: ein Ausfall zeigt „Vertretung offen“ — ohne den Anlass „Krankmeldung“")
    void openSubstitutionHasNoReason() throws Exception {
        Testdaten daten = planDerWoche();
        String token = klassenkonto("7a", daten.klasse7a);
        call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Krankmeldung"}""".formatted(daten.meyer, MONTAG, MONTAG)), 201);

        JsonNode montag = alsKlasse(token, "/api/me/class-week?date=" + MONTAG).get("days").get(0);

        int offen = 0;
        for (JsonNode entry : montag.get("entries")) {
            assertThat(entry.get("reasons")).as("nie ein Anlass, auch nicht bei Vertretungsbedarf").isEmpty();
            if ("NEEDS_SUBSTITUTION".equals(entry.get("kind").asText())) {
                offen++;
            }
        }
        // Meyer haelt in der 7a am Montag die 1. (Ma) und die 4. Stunde (Ph).
        assertThat(offen).as("zwei offene Stunden der 7a am Montag").isEqualTo(2);

        // Zur Gegenprobe: die Planung sieht denselben Tag MIT Anlass.
        JsonNode planung = getJson("/api/timetable/week?date=" + MONTAG + "&view=CLASS&id=" + daten.klasse7a);
        boolean anlassBeiDerPlanung = false;
        for (JsonNode entry : planung.get("days").get(0).get("entries")) {
            if (!entry.get("reasons").isEmpty()) {
                anlassBeiDerPlanung = true;
            }
        }
        assertThat(anlassBeiDerPlanung).as("die Planung sieht den Anlass weiterhin").isTrue();
    }

    @Test
    @DisplayName("ST-3: eine gesetzte Vertretung zeigt den Kuerzelwechsel, aber keine Kennung der alten Lehrkraft")
    void substitutionShowsShortNamesOnly() throws Exception {
        Testdaten daten = planDerWoche();
        String token = klassenkonto("7a", daten.klasse7a);
        long ausfall = call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Krankmeldung"}""".formatted(daten.meyer, MONTAG, MONTAG)), 201).get("id").asLong();
        long mathe7aMontag = lessonIdAt(getJson("/api/timetable"), daten.klasse7a, daten.slot("MO", 1));
        call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d,"eventIds":[%d]}"""
                .formatted(MONTAG, mathe7aMontag, daten.schulz, ausfall)), 201);

        JsonNode montag = alsKlasse(token, "/api/me/class-week?date=" + MONTAG).get("days").get(0);

        JsonNode vertreten = null;
        for (JsonNode entry : montag.get("entries")) {
            if ("SUBSTITUTED".equals(entry.get("kind").asText())) {
                vertreten = entry;
            }
        }
        assertThat(vertreten).as("die vertretene Stunde steht in der Klassensicht").isNotNull();
        assertThat(vertreten.get("teacherShort").asText()).isEqualTo("SCZ");
        assertThat(vertreten.get("original").get("teacherShort").asText()).isEqualTo("MEY");
        assertThat(vertreten.get("original").get("teacherId").isNull())
                .as("keine Kennung der ausgefallenen Lehrkraft").isTrue();
        assertThat(vertreten.get("original").get("roomId").isNull()).isTrue();
        assertThat(vertreten.get("reasons")).isEmpty();
    }

    @Test
    @DisplayName("ST-2: dieselbe Anmeldung bekommt auf Planungs- und Lehrkraftpfaden 403, ohne Token 401")
    void classAccountMayNotReadAnythingElse() throws Exception {
        Testdaten daten = planDerWoche();
        String token = klassenkonto("7a", daten.klasse7a);

        for (String pfad : new String[] {
                "/api/timetable",
                "/api/timetable/week?date=" + MONTAG + "&view=CLASS&id=" + daten.klasse8b,
                "/api/teachers",
                "/api/classes",
                "/api/users",
                "/api/me/timetable/week",
                "/api/changelog"}) {
            mvc.perform(get(pfad).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/me/class-week")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ST-3: ein Konto ohne Klasse — die Planung selbst — bekommt 404")
    void accountWithoutAClassGets404() throws Exception {
        pflichtenheftGrunddaten();

        JsonNode problem = call(get("/api/me/class-week"), 404);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:not-found");
        assertThat(problem.get("detail").asText()).isEqualTo("Dieses Konto gehört zu keiner Klasse.");
    }
}
