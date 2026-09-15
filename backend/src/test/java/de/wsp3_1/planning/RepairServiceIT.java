package de.wsp3_1.planning;

import de.wsp3_1.solver.SolverRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Konflikt per Knopf loesen (#110, E-53) gegen den echten Solver und die echte Datenbank: zwei
 * festgelegte Stunden derselben Lehrkraft im selben Zeitfenster, drei Stufen je fuenf Sekunden. Der
 * Vorschlag ist eine Vorschau — der Entwurf bleibt, bis die Oberflaeche die Zuege einzeln uebernimmt.
 */
class RepairServiceIT extends PlanningITBase {

    @Autowired
    private SolverRunner runner;

    @Test
    @DisplayName("Zwei festgelegte Stunden derselben Lehrkraft kollidieren: mindestens ein Vorschlag ohne harten Konflikt, nichts gespeichert, Uebernehmen per PATCH loest ihn")
    void teacherDoubleBooking_hasProposalWithoutHardConflicts_savesNothing_patchResolves() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        // Meyer unterrichtet Mathe in der 7a und in der 8b — beide Montag 1. Stunde, beide festgelegt.
        addLesson(daten.klasse7a, daten.mathe, daten.slot("MO", 1), daten.r101);
        JsonNode before = addLesson(daten.klasse8b, daten.mathe, daten.slot("MO", 1), daten.r204);
        long achtB = lessonIdAt(before, daten.klasse8b, daten.slot("MO", 1));
        assertThat(typesOf(before)).containsExactly("TEACHER_DOUBLE_BOOKED");

        JsonNode response = call(post("/api/draft/repair").content("""
                {"lessonId":%d}""".formatted(achtB)), 200);

        JsonNode proposals = response.get("proposals");
        assertThat(proposals.size()).as("mindestens ein Vorschlag").isGreaterThanOrEqualTo(1);
        JsonNode best = null;
        for (JsonNode proposal : proposals) {
            assertThat(proposal.get("level").asInt()).isBetween(1, 3);
            assertThat(proposal.get("label").asText()).isNotBlank();
            assertThat(proposal.get("moves").size()).as("nur Vorschlaege mit mindestens einem Zug").isGreaterThan(0);
            assertThat(proposal.get("score").isNull()).isFalse();
            if (proposal.get("hardConflictsAfter").asInt() == 0 && best == null) {
                best = proposal;
            }
        }
        assertThat(best).as("ein Vorschlag ohne verbleibenden harten Konflikt").isNotNull();

        // Gespeichert wurde nichts: der Entwurf zeigt denselben Konflikt und dieselben Lagen.
        JsonNode unchanged = getJson("/api/draft");
        assertThat(unchanged.get("status").asText()).isEqualTo("EDITING");
        assertThat(typesOf(unchanged)).containsExactly("TEACHER_DOUBLE_BOOKED");
        assertThat(unchanged.get("lessons").toString()).isEqualTo(before.get("lessons").toString());

        // Uebernehmen wie die Oberflaeche: je Zug ein PATCH mit Zeitfenster, Raum und pinned:false.
        JsonNode after = unchanged;
        for (JsonNode move : best.get("moves")) {
            after = call(patch("/api/draft/lessons/" + move.get("lessonId").asLong()).content("""
                    {"timeslotId":%d,"roomId":%d,"pinned":false}"""
                    .formatted(move.get("timeslotId").asLong(), move.get("roomId").asLong())), 200);
        }
        assertThat(hardTypesOf(after)).as("kein harter Konflikt mehr").isEmpty();
    }

    @Test
    @DisplayName("Waehrend ein Lauf vorgemerkt ist: 409 solving, und die Vormerkung bleibt beim Lauf")
    void whileReserved_is409Solving() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        JsonNode draft = call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        addLesson(daten.klasse7a, daten.mathe, daten.slot("MO", 1), daten.r101);
        JsonNode added = addLesson(daten.klasse8b, daten.mathe, daten.slot("MO", 1), daten.r204);
        long achtB = lessonIdAt(added, daten.klasse8b, daten.slot("MO", 1));

        long draftId = draft.get("id").asLong();
        assertThat(runner.reserve(draftId)).isTrue();
        try {
            JsonNode problem = call(post("/api/draft/repair").content("""
                    {"lessonId":%d}""".formatted(achtB)), 409);
            assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:solving");
            // Die fremde Vormerkung wurde nicht freigegeben.
            assertThat(runner.isSolving(draftId)).isTrue();
        } finally {
            runner.release(draftId);
        }
        assertThat(runner.isSolving(draftId)).isFalse();
    }

    @Test
    @DisplayName("Unbekannte Stunde: 404; Stunde ohne Konflikt: 400 validation; fehlende lessonId: 400")
    void unknownLessonIs404_lessonWithoutConflictIs400_missingLessonIdIs400() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        JsonNode added = addLesson(daten.klasse7a, daten.mathe, daten.slot("MO", 1), daten.r101);
        long alone = lessonIdAt(added, daten.klasse7a, daten.slot("MO", 1));

        JsonNode notFound = call(post("/api/draft/repair").content("""
                {"lessonId":999999}"""), 404);
        assertThat(notFound.get("type").asText()).isEqualTo("urn:pushnpull:problem:not-found");

        JsonNode noConflict = call(post("/api/draft/repair").content("""
                {"lessonId":%d}""".formatted(alone)), 400);
        assertThat(noConflict.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(noConflict.get("errors").get(0).get("field").asText()).isEqualTo("lessonId");
        assertThat(noConflict.get("errors").get(0).get("message").asText())
                .isEqualTo("Die Stunde steht in keinem Konflikt");

        JsonNode missing = call(post("/api/draft/repair").content("{}"), 400);
        assertThat(missing.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(missing.get("errors").get(0).get("field").asText()).isEqualTo("lessonId");
    }

    @Test
    @DisplayName("Ohne Entwurf: 404")
    void withoutDraftIs404() throws Exception {
        pflichtenheftGrunddaten();
        call(post("/api/draft/repair").content("""
                {"lessonId":1}"""), 404);
    }

    private static List<String> hardTypesOf(JsonNode draft) {
        List<String> types = new ArrayList<>();
        for (JsonNode conflict : draft.get("conflicts")) {
            if ("HARD".equals(conflict.get("severity").asText())) {
                types.add(conflict.get("type").asText());
            }
        }
        return types;
    }

    private static List<String> typesOf(JsonNode draft) {
        List<String> types = new ArrayList<>();
        for (JsonNode conflict : draft.get("conflicts")) {
            types.add(conflict.get("type").asText());
        }
        return types;
    }
}
