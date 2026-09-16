package de.wsp3_1.planning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Der Handaufbau ueber die Schnittstelle — Schritt fuer Schritt wie in Abschnitt 1.5 der
 * Abnahme-Checkliste (P-1 bis P-6) und damit die Testfaelle T-W3.1, T-W3.2 und T-W1.2 gegen den
 * laufenden Server statt gegen die Attrappe.
 */
class DraftControllerIT extends PlanningITBase {

    @Test
    @DisplayName("P-1: sieben Stunden von Hand — alle festgelegt, ohne Konflikte, 9c-Mathe offen")
    void p1_sevenLessonsByHand_allPinned_noConflicts_ninthClassStillOpen() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        JsonNode draft = getJson("/api/draft");
        assertThat(draft.get("lessons")).hasSize(7);
        assertThat(draft.get("conflicts")).isEmpty();
        assertThat(draft.get("status").asText()).isEqualTo("EDITING");
        for (JsonNode lesson : draft.get("lessons")) {
            assertThat(lesson.get("pinned").asBoolean()).isTrue();
        }
        // Die Lehrkraft folgt aus der Fachzuordnung, sie steht in keinem Rumpf (E-18).
        assertThat(draft.get("lessons").get(0).get("teacherShort").asText()).isEqualTo("MEY");
        assertThat(draft.get("lessons").get(1).get("teacherShort").asText()).isEqualTo("WEB");

        assertThat(draft.get("openLessons")).hasSize(1);
        JsonNode open = draft.get("openLessons").get(0);
        assertThat(open.get("classShort").asText()).isEqualTo("9c");
        assertThat(open.get("subjectShort").asText()).isEqualTo("Ma");
        assertThat(open.get("missing").asInt()).isEqualTo(1);
        assertThat(draft.get("warnings")).isEmpty();
    }

    @Test
    @DisplayName("T-W3.1 / T-W3.2: Raumkonflikt entsteht und verschwindet mit dem Verschieben")
    void tW3_1_and_tW3_2_roomConflictAppearsAndDisappears() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        // P-2: 9c Mathematik Montag 1. Stunde in R101 — R101 ist dort schon von der 7a belegt.
        JsonNode afterAdd = addLesson(daten.klasse9c, daten.mathe, daten.slot("MO", 1), daten.r101);
        long neunC = lessonIdAt(afterAdd, daten.klasse9c, daten.slot("MO", 1));
        long siebenA = lessonIdAt(afterAdd, daten.klasse7a, daten.slot("MO", 1));

        assertThat(afterAdd.get("conflicts")).hasSize(1);
        JsonNode conflict = afterAdd.get("conflicts").get(0);
        assertThat(conflict.get("type").asText()).isEqualTo("ROOM_DOUBLE_BOOKED");
        assertThat(conflict.get("objects").get(0).get("label").asText()).isEqualTo("R101");
        assertThat(conflict.get("message").asText())
                .isEqualTo("Raum R101 ist am Montag in der 1. Stunde doppelt belegt (7a, 9c).");
        assertThat(conflict.get("lessonIds").toString()).contains(String.valueOf(siebenA), String.valueOf(neunC));
        for (JsonNode lesson : afterAdd.get("lessons")) {
            if (lesson.get("id").asLong() == neunC) {
                assertThat(lesson.get("teacherShort").asText()).isEqualTo("SCZ");
            }
        }

        // P-3: dieselbe Stunde auf Montag 5. Stunde (Position 6) verschieben.
        JsonNode afterMove = call(patch("/api/draft/lessons/" + neunC).content("""
                {"timeslotId":%d}""".formatted(daten.slot("MO", 6))), 200);

        assertThat(afterMove.get("conflicts")).isEmpty();
        for (JsonNode lesson : afterMove.get("lessons")) {
            if (lesson.get("id").asLong() == neunC) {
                assertThat(lesson.get("timeslotId").asLong()).isEqualTo(daten.slot("MO", 6));
                assertThat(lesson.get("position").asInt()).isEqualTo(6);
                assertThat(lesson.get("roomShort").asText()).isEqualTo("R101");
                assertThat(lesson.get("subjectShort").asText()).isEqualTo("Ma");
                assertThat(lesson.get("teacherShort").asText()).isEqualTo("SCZ");
                assertThat(lesson.get("pinned").asBoolean()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("T-W1.2 / P-4, P-5: Stunde in der Sperrzeit von Schulz — ein Konflikt, Loeschen raeumt ihn weg")
    void tW1_2_blockedTimeConflict_andDeleteRemovesIt() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        JsonNode afterAdd = addLesson(daten.klasse9c, daten.mathe, daten.slot("FR", 6), daten.r101);
        long freitag = lessonIdAt(afterAdd, daten.klasse9c, daten.slot("FR", 6));

        assertThat(afterAdd.get("conflicts")).hasSize(1);
        JsonNode conflict = afterAdd.get("conflicts").get(0);
        assertThat(conflict.get("type").asText()).isEqualTo("TEACHER_BLOCKED_TIME");
        assertThat(conflict.get("message").asText())
                .isEqualTo("Gesperrte Zeit der Lehrkraft: Schulz, Freitag 5. Stunde");
        assertThat(conflict.get("objects").get(0).get("label").asText()).isEqualTo("SCZ");

        JsonNode afterDelete = call(delete("/api/draft/lessons/" + freitag), 200);
        assertThat(afterDelete.get("conflicts")).isEmpty();
        assertThat(afterDelete.get("lessons")).hasSize(7);
    }

    @Test
    @DisplayName("#133: Stunde in der Sperrzeit von R101 — ein Konflikt ROOM_BLOCKED_TIME im Entwurf")
    void roomBlockedTimeConflictShowsInDraft() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        call(put("/api/rooms/" + daten.r101).content("""
                {"name":"R101","roomType":"Klassenraum","active":true,"blockedTimeslotIds":[%d]}"""
                .formatted(daten.slot("FR", 6))), 200);
        planMoDiInEntwurf(daten);

        // 7a Deutsch (Weber, keine Sperrzeit) Freitag 5. Stunde in R101 — nur der Raum ist gesperrt.
        JsonNode afterAdd = addLesson(daten.klasse7a, daten.deutsch, daten.slot("FR", 6), daten.r101);

        assertThat(afterAdd.get("conflicts")).hasSize(1);
        JsonNode conflict = afterAdd.get("conflicts").get(0);
        assertThat(conflict.get("type").asText()).isEqualTo("ROOM_BLOCKED_TIME");
        assertThat(conflict.get("severity").asText()).isEqualTo("HARD");
        assertThat(conflict.get("message").asText())
                .isEqualTo("Raum R101 ist am Freitag in der 5. Stunde gesperrt.");
        assertThat(conflict.get("objects").get(0).get("kind").asText()).isEqualTo("ROOM");
        assertThat(conflict.get("objects").get(0).get("label").asText()).isEqualTo("R101");
    }

    @Test
    @DisplayName("P-6: annehmen — Entwurf weg, aktiver Plan hat dieselben sieben Stunden")
    void p6_acceptMakesDraftTheActivePlan() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        JsonNode accepted = call(post("/api/draft/accept").content("{}"), 200);
        assertThat(accepted.get("id").isNull()).isFalse();
        assertThat(accepted.get("lessons")).hasSize(7);
        assertThat(accepted.get("conflicts")).isEmpty();
        assertThat(accepted.get("warnings")).isEmpty();

        call(get("/api/draft"), 404);
        JsonNode active = getJson("/api/timetable");
        assertThat(active.get("lessons")).hasSize(7);
        assertThat(active.get("acceptedAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("Ein zweiter POST /api/draft legt einen zweiten Entwurf an; der Singular-Pfad meint den juengsten (#103, E-52)")
    void secondDraftIsAllowedAndSingularPathMeansTheNewest() throws Exception {
        pflichtenheftGrunddaten();
        JsonNode first = call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        JsonNode second = call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);

        assertThat(second.get("id").asLong()).isGreaterThan(first.get("id").asLong());
        assertThat(first.get("name").asText()).isEqualTo("Entwurf 1");
        assertThat(second.get("name").asText()).isEqualTo("Entwurf 2");
        assertThat(getJson("/api/draft").get("id").asLong()).isEqualTo(second.get("id").asLong());
        assertThat(getJson("/api/drafts")).hasSize(2);
    }

    @Test
    @DisplayName("Ein Fach, das die Klasse nicht hat, ist ein Eingabefehler mit Feldangabe")
    void addingASubjectTheClassDoesNotHaveIsAValidationError() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);

        JsonNode problem = call(post("/api/draft/lessons").content("""
                {"classId":%d,"subjectId":%d,"timeslotId":%d,"roomId":%d}"""
                .formatted(daten.klasse9c, daten.physik, daten.slot("MO", 1), daten.p1)), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("subjectId");
        assertThat(problem.get("errors").get(0).get("message").asText())
                .isEqualTo("Die Klasse hat dieses Fach nicht");
    }

    @Test
    @DisplayName("Eine offene Stunde braucht Zeitfenster UND Raum — der Server raet keinen Raum (D-9)")
    void placingAnOpenLessonNeedsBothTimeslotAndRoom() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        long lessonId = openLessonOfNinthClass(daten);

        JsonNode problem = call(patch("/api/draft/lessons/" + lessonId).content("""
                {"timeslotId":%d}""".formatted(daten.slot("MO", 1))), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("roomId");

        JsonNode ok = call(patch("/api/draft/lessons/" + lessonId).content("""
                {"timeslotId":%d,"roomId":%d}""".formatted(daten.slot("MO", 1), daten.r101)), 200);
        assertThat(lessonIdAt(ok, daten.klasse9c, daten.slot("MO", 1))).isEqualTo(lessonId);
    }

    @Test
    @DisplayName("Eine unbekannte Stunde und ein fehlender Entwurf sind 404")
    void unknownLessonAndMissingDraftAre404() throws Exception {
        call(get("/api/draft"), 404);
        pflichtenheftGrunddaten();
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        call(delete("/api/draft/lessons/999999"), 404);
    }

    @Test
    @DisplayName("D-5: verwerfen loescht den Entwurf und laesst den aktiven Plan stehen")
    void discardKeepsTheActivePlan() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("{}"), 200);

        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        call(delete("/api/draft"), 204);

        call(get("/api/draft"), 404);
        assertThat(getJson("/api/timetable").get("lessons")).hasSize(7);
    }

    /**
     * Legt die fehlende 9c-Mathematikstunde als offene Zeile an — dafuer reicht ein Lauf der
     * Vorbereitung; hier genuegt der direkte Weg ueber die Kann-Option „Berechnung", weshalb die Stunde
     * stattdessen von Hand ohne Zeitfenster entsteht.
     */
    private long openLessonOfNinthClass(Testdaten daten) throws Exception {
        long classSubjectId = jdbc.queryForObject(
                "SELECT id FROM class_subject WHERE class_id = ? AND subject_id = ?",
                Long.class, daten.klasse9c, daten.mathe);
        Long draftId = jdbc.queryForObject("SELECT id FROM timetable WHERE status = 'DRAFT'", Long.class);
        jdbc.update("""
                INSERT INTO lesson (timetable_id, class_subject_id, pinned, timeslot_id, room_id)
                VALUES (?, ?, false, NULL, NULL)""", draftId, classSubjectId);
        return jdbc.queryForObject("SELECT max(id) FROM lesson WHERE timetable_id = ?", Long.class, draftId);
    }
}
