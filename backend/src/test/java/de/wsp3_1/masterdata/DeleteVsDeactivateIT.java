package de.wsp3_1.masterdata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * „Loeschen nur ohne Verweise, sonst deaktivieren" fuer Fach, Raum und Klasse (Ausnahme 2 aus W-1,
 * Schritt G-9; T-W1.3 fuer die Lehrkraft steht in {@code TeacherServiceIT}).
 */
class DeleteVsDeactivateIT extends MasterdataITBase {

    @Test
    @DisplayName("G-9: Ein Fach, das drei Klassen unterrichten, wird nicht geloescht")
    void subjectInUseByClassesIsConflict() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        JsonNode problem = call(delete("/api/subjects/" + daten.mathe), 409);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:in-use");
        assertThat(problem.get("detail").asText()).isEqualTo("Mathematik wird in 7a, 8b, 9c unterrichtet.");
        assertThat(getJson("/api/subjects").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("Ein Fach ohne Klassenzuordnung laesst sich loeschen; die Lehrbefaehigung geht mit")
    void unusedSubjectCanBeDeleted() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        long kunst = postId("/api/subjects", """
                {"name":"Kunst","shortName":"Ku","requiredRoomType":null,"color":"#8E24AA"}""");
        call(put("/api/teachers/" + daten.weber).content("""
                {"name":"Weber","shortName":"WEB","weeklyHoursQuota":25,"subjectIds":[%d,%d],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}"""
                .formatted(daten.deutsch, kunst)), 200);

        call(delete("/api/subjects/" + kunst), 204);

        assertThat(getJson("/api/subjects").size()).isEqualTo(3);
        assertThat(getJson("/api/teachers/" + daten.weber).get("subjectIds").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Ein Raum mit Stunden im aktiven Plan wird nicht geloescht, sondern deaktiviert")
    void roomInUseIsConflictButCanBeDeactivated() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        aktivenPlanMoDiAnlegen(daten);

        JsonNode problem = call(delete("/api/rooms/" + daten.r101), 409);
        assertThat(problem.get("detail").asText())
                .isEqualTo("R101 ist in 3 Stunden eingeplant. Deaktivieren statt löschen.");

        JsonNode deaktiviert = call(put("/api/rooms/" + daten.r101).content("""
                {"name":"R101","roomType":"Klassenraum","active":false}"""), 200);
        assertThat(deaktiviert.get("active").asBoolean()).isFalse();
        assertThat(getJson("/api/rooms").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("Eine Klasse mit Stunden im aktiven Plan wird nicht geloescht")
    void classInUseIsConflict() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        aktivenPlanMoDiAnlegen(daten);

        JsonNode problem = call(delete("/api/classes/" + daten.klasse7a), 409);

        assertThat(problem.get("detail").asText())
                .isEqualTo("7a hat 4 eingeplante Stunden. Deaktivieren statt löschen.");
        assertThat(getJson("/api/classes").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("Eine Klasse ohne Stunden laesst sich mit ihrer Faecherliste loeschen")
    void classWithoutLessonsCanBeDeleted() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        call(delete("/api/classes/" + daten.klasse9c), 204);

        assertThat(getJson("/api/classes").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM class_subject WHERE class_id = ?",
                Long.class, daten.klasse9c)).isZero();
        // Schulz haengt an keiner Klasse mehr und ist damit loeschbar.
        call(delete("/api/teachers/" + daten.schulz), 204);
        assertThat(getJson("/api/teachers").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Ein Raum ohne Stunden laesst sich loeschen")
    void unusedRoomCanBeDeleted() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        call(delete("/api/rooms/" + daten.p1), 204);

        assertThat(getJson("/api/rooms").size()).isEqualTo(2);
    }
}
