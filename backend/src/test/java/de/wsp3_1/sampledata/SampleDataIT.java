package de.wsp3_1.sampledata;

import de.wsp3_1.sampledata.domain.SampleDataLoader;
import de.wsp3_1.shared.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/sample-data} gegen echtes PostgreSQL (Checkliste D-1): 12 Klassen, 24 Lehrkraefte,
 * 18 Raeume, zweiter Aufruf 409, mit {@code replace} dieselbe Zaehlung, Konto {@code admin} bleibt.
 *
 * <p>Der Test raeumt vor und nach jedem Fall auf: die uebrigen Integrationstests teilen sich
 * denselben Kontext und dieselbe Datenbank und sollen eine leere Grunddatentabelle vorfinden.</p>
 */
class SampleDataIT extends IntegrationTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SampleDataLoader loader;

    @BeforeEach
    void vorherLeeren() {
        loader.clear();
    }

    @AfterEach
    void nachherLeeren() {
        loader.clear();
    }

    @Test
    @DisplayName("Der Beispielbestand hat 12 Klassen, 24 Lehrkraefte und 18 Raeume")
    void loadsTwelveClassesTwentyFourTeachersEighteenRooms() throws Exception {
        mvc.perform(post("/api/sample-data").header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classes").value(12))
                .andExpect(jsonPath("$.teachers").value(24))
                .andExpect(jsonPath("$.rooms").value(18));

        assertThat(zaehle("school_class")).isEqualTo(12);
        assertThat(zaehle("teacher")).isEqualTo(24);
        assertThat(zaehle("room")).isEqualTo(18);
        assertThat(zaehle("timeslot")).isEqualTo(35);
        // Zwoelf Klassen mal zwoelf Faecher; je Lehrkraft zwei Faecher.
        assertThat(zaehle("class_subject")).isEqualTo(144);
        assertThat(zaehle("teacher_subject")).isEqualTo(48);
        // Fuenf Tage mit je einer Pause an Position 3.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timeslot WHERE is_break", Integer.class)).isEqualTo(5);
    }

    @Test
    @DisplayName("Der Bestand ist widerspruchsfrei: 30 Wochenstunden je Klasse, kein Deputat gesprengt, jede Raumart vorhanden")
    void loadedDataIsConsistent() throws Exception {
        mvc.perform(post("/api/sample-data").header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk());

        // Jede Klasse hat genau 30 Wochenstunden — genau der Wert der Regel MAX_LESSONS_PER_WEEK.
        assertThat(jdbc.queryForList("""
                SELECT sum(weekly_hours) FROM class_subject GROUP BY class_id
                """, Integer.class)).hasSize(12).containsOnly(30);

        // Keine Lehrkraft ueber ihrem Deputat: sonst zeigte der Bestand ueberall Warnungen (T-W1.5).
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT cs.teacher_id, sum(cs.weekly_hours) AS stunden
                    FROM class_subject cs GROUP BY cs.teacher_id) last
                JOIN teacher t ON t.id = last.teacher_id
                WHERE last.stunden > t.weekly_hours_quota
                """, Integer.class)).isZero();

        // Jedes Fach mit Raumanforderung findet mindestens einen passenden Raum (Constraint H4).
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM subject s
                WHERE s.required_room_type IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM room r WHERE r.room_type = s.required_room_type)
                """, Integer.class)).isZero();

        // Jede Lehrkraft unterrichtet in jeder Klasse nur Faecher, die sie laut Fachliste unterrichtet.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM class_subject cs
                WHERE NOT EXISTS (SELECT 1 FROM teacher_subject ts
                                  WHERE ts.teacher_id = cs.teacher_id AND ts.subject_id = cs.subject_id)
                """, Integer.class)).isZero();
    }

    /**
     * Checkliste D-1: nach dem Laden hat {@code GET /api/timetable} Stunden. Der Bestand kommt mit
     * fertigem, angenommenem Plan — genau der Punkt, an dem sich „Plan per Klick" (W-8) vorfuehren
     * laesst, ohne vorher eine Schule einzutippen.
     *
     * <p>Die Rechenzeit steht in der Pipeline auf wenige Sekunden ({@code application-ci.properties}):
     * geprueft wird, <b>dass</b> ein Plan entsteht und dass er keine harten Konflikte hat — wie voll er
     * ist, misst {@code SolveServiceIT.tW8_1_fullSample_…} mit der Rechenzeit des Betriebs.</p>
     */
    @Test
    @DisplayName("D-1: nach dem Laden steht ein angenommener Plan ohne harte Konflikte")
    void loadedSampleHasAnAcceptedPlan() throws Exception {
        String token = token();
        mvc.perform(post("/api/sample-data").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        String plan = mvc.perform(get("/api/timetable").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode timetable = objectMapper.readTree(plan);

        assertThat(timetable.get("id").isNull()).as("es gibt einen aktiven Plan").isFalse();
        assertThat(timetable.get("lessons")).as("der Plan ist nicht leer").isNotEmpty();
        for (JsonNode conflict : timetable.get("conflicts")) {
            assertThat(conflict.get("severity").asText())
                    .as("der Beispielplan hat keinen harten Konflikt: " + conflict.get("message").asText())
                    .isNotEqualTo("HARD");
        }

        // Der Weg lief ueber POST /api/draft/accept: dort entsteht der Verlaufseintrag (W-7).
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM changelog_entry WHERE action = 'TIMETABLE_ACCEPTED'", Integer.class))
                .isEqualTo(1);
        // Und der Entwurf ist verbraucht: es gibt genau einen Plan, und der ist aktiv.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM timetable WHERE status = 'DRAFT'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Ein zweiter Aufruf ohne replace endet mit 409 data-exists")
    void secondCallWithoutReplaceIsConflict() throws Exception {
        String token = token();
        mvc.perform(post("/api/sample-data").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(post("/api/sample-data").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replace\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:data-exists"));

        assertThat(zaehle("teacher")).isEqualTo(24);
    }

    @Test
    @DisplayName("Mit replace kommt dieselbe Zaehlung heraus und das Konto der Planung bleibt")
    void replaceKeepsCountsAndAdminAccount() throws Exception {
        String token = token();
        mvc.perform(post("/api/sample-data").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(post("/api/sample-data").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"replace\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classes").value(12))
                .andExpect(jsonPath("$.teachers").value(24))
                .andExpect(jsonPath("$.rooms").value(18));

        assertThat(zaehle("teacher")).isEqualTo(24);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM user_account WHERE username = 'admin'", Integer.class)).isEqualTo(1);
        // Nach dem Ersetzen ist die Anmeldung weiter moeglich.
        assertThat(token()).isNotBlank();
        // Regelwerk und Systemkonfiguration bleiben unberuehrt.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rule", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_config", Integer.class)).isEqualTo(1);
    }

    /**
     * Der abgelehnte Aufruf darf nichts anfassen. Frueher stand die Pruefung im Controller und das
     * Laden dahinter rief immer {@code loadFullReplacing()} — wer zwischen beidem eine Klasse anlegte,
     * verlor sie, obwohl {@code replace} nicht gesetzt war. Die Klasse hier steht stellvertretend fuer
     * diese Zeile: nach dem 409 muss sie noch da sein.
     */
    @Test
    @DisplayName("Ein Aufruf ohne replace loescht nichts, auch wenn er mit 409 endet")
    void refusedCallLeavesExistingDataAlone() throws Exception {
        jdbc.update("INSERT INTO school_class (name, grade, active) VALUES ('7a', 7, true)");

        mvc.perform(post("/api/sample-data").header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:pushnpull:problem:data-exists"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM school_class WHERE name = '7a'", Integer.class)).isEqualTo(1);
        assertThat(zaehle("school_class")).isEqualTo(1);
    }

    @Test
    @DisplayName("Ohne Token bleibt der Beispielbestand verschlossen")
    void withoutTokenUnauthenticated() throws Exception {
        mvc.perform(post("/api/sample-data"))
                .andExpect(status().isUnauthorized());

        assertThat(zaehle("teacher")).isZero();
    }

    private int zaehle(String tabelle) {
        Integer zeilen = jdbc.queryForObject("SELECT count(*) FROM " + tabelle, Integer.class);
        return zeilen == null ? 0 : zeilen;
    }

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
