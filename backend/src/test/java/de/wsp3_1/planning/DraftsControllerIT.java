package de.wsp3_1.planning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Mehrere Entwuerfe nebeneinander ueber {@code /api/drafts} (#103, T2-3.4, E-52): zwei Entwuerfe mit
 * getrennten Stunden, Liste und Singular-Alias, Umbenennen, Loeschen mit Stunden, Annehmen nur ohne
 * harte Konflikte — und der andere Entwurf bleibt dabei stehen.
 */
class DraftsControllerIT extends PlanningITBase {

    @Test
    @DisplayName("Zwei Entwuerfe nebeneinander: beide lesbar, Stunden getrennt, Liste juengster zuerst, Alias zeigt auf den juengsten")
    void twoDraftsSideBySide() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        long a = call(post("/api/drafts").content("""
                {"name":"Variante A","source":"EMPTY"}"""), 201).get("id").asLong();
        long b = call(post("/api/drafts").content("""
                {"source":"EMPTY"}"""), 201).get("id").asLong();

        JsonNode inA = call(post("/api/drafts/" + a + "/lessons").content("""
                {"classId":%d,"subjectId":%d,"timeslotId":%d,"roomId":%d}"""
                .formatted(daten.klasse7a, daten.mathe, daten.slot("MO", 1), daten.r101)), 201);
        assertThat(inA.get("id").asLong()).isEqualTo(a);
        assertThat(inA.get("name").asText()).isEqualTo("Variante A");
        assertThat(inA.get("lessons")).hasSize(1);

        assertThat(getJson("/api/drafts/" + a).get("lessons")).hasSize(1);
        assertThat(getJson("/api/drafts/" + b).get("lessons")).isEmpty();
        assertThat(getJson("/api/drafts/" + b).get("name").asText()).isEqualTo("Entwurf 2");

        JsonNode list = getJson("/api/drafts");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(b);
        assertThat(list.get(1).get("id").asLong()).isEqualTo(a);
        assertThat(list.get(0).get("status").asText()).isEqualTo("EDITING");
        assertThat(list.get(0).get("source").asText()).isEqualTo("EMPTY");
        assertThat(list.get(1).get("hardConflicts").asInt()).isZero();

        // Der Singular-Pfad meint den juengsten Entwurf — und schreibt auch nur in ihn.
        assertThat(getJson("/api/draft").get("id").asLong()).isEqualTo(b);
        JsonNode viaAlias = addLesson(daten.klasse8b, daten.mathe, daten.slot("MO", 2), daten.r204);
        assertThat(viaAlias.get("id").asLong()).isEqualTo(b);
        assertThat(getJson("/api/drafts/" + a).get("lessons")).hasSize(1);
        assertThat(getJson("/api/drafts/" + b).get("lessons")).hasSize(1);
    }

    @Test
    @DisplayName("Annehmen mit hartem Konflikt: 409 draft-has-conflicts; ohne Konflikt 200, und der andere Entwurf bleibt stehen")
    void acceptRejectsHardConflictsAndLeavesOtherDraftsAlone() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        long other = call(post("/api/drafts").content("""
                {"name":"Variante B","source":"EMPTY"}"""), 201).get("id").asLong();
        long id = call(post("/api/drafts").content("""
                {"name":"Variante A","source":"EMPTY"}"""), 201).get("id").asLong();
        call(post("/api/drafts/" + id + "/lessons").content("""
                {"classId":%d,"subjectId":%d,"timeslotId":%d,"roomId":%d}"""
                .formatted(daten.klasse7a, daten.mathe, daten.slot("MO", 1), daten.r101)), 201);
        // 9c Mathematik im selben Zeitfenster und Raum: Raumkonflikt (hart).
        JsonNode conflicted = call(post("/api/drafts/" + id + "/lessons").content("""
                {"classId":%d,"subjectId":%d,"timeslotId":%d,"roomId":%d}"""
                .formatted(daten.klasse9c, daten.mathe, daten.slot("MO", 1), daten.r101)), 201);
        long neunC = lessonIdAt(conflicted, daten.klasse9c, daten.slot("MO", 1));
        assertThat(getJson("/api/drafts").get(0).get("hardConflicts").asInt()).isEqualTo(1);

        JsonNode problem = call(post("/api/drafts/" + id + "/accept").content("{}"), 409);
        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:draft-has-conflicts");
        assertThat(problem.get("detail").asText())
                .isEqualTo("Der Entwurf hat einen harten Konflikt. Erst lösen, dann annehmen.");
        assertThat(problem.get("conflicts")).hasSize(1);
        assertThat(problem.get("conflicts").get(0).get("type").asText()).isEqualTo("ROOM_DOUBLE_BOOKED");
        // Nichts ist passiert: kein aktiver Plan, der Entwurf steht noch.
        assertThat(getJson("/api/timetable").get("id").isNull()).isTrue();
        assertThat(getJson("/api/drafts/" + id).get("status").asText()).isEqualTo("EDITING");

        // Konflikt wegraeumen, dann klappt es — und Variante B bleibt als Entwurf stehen.
        call(delete("/api/drafts/" + id + "/lessons/" + neunC), 200);
        JsonNode accepted = call(post("/api/drafts/" + id + "/accept").content("{}"), 200);
        assertThat(accepted.get("id").asLong()).isEqualTo(id);
        assertThat(accepted.get("lessons")).hasSize(1);

        call(get("/api/drafts/" + id), 404);
        JsonNode list = getJson("/api/drafts");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(other);
        assertThat(list.get(0).get("name").asText()).isEqualTo("Variante B");
        assertThat(getJson("/api/draft").get("id").asLong()).isEqualTo(other);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ACTIVE'", Long.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE /api/drafts/{id} loescht die Stunden mit; der andere Entwurf behaelt seine")
    void deleteRemovesLessonsOfThatDraftOnly() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        long keep = call(post("/api/drafts").content("""
                {"source":"EMPTY"}"""), 201).get("id").asLong();
        call(post("/api/drafts/" + keep + "/lessons").content("""
                {"classId":%d,"subjectId":%d,"timeslotId":%d,"roomId":%d}"""
                .formatted(daten.klasse7a, daten.mathe, daten.slot("MO", 1), daten.r101)), 201);
        long gone = call(post("/api/drafts").content("""
                {"source":"EMPTY"}"""), 201).get("id").asLong();
        call(post("/api/drafts/" + gone + "/lessons").content("""
                {"classId":%d,"subjectId":%d,"timeslotId":%d,"roomId":%d}"""
                .formatted(daten.klasse8b, daten.mathe, daten.slot("MO", 2), daten.r204)), 201);
        call(post("/api/drafts/" + gone + "/lessons").content("""
                {"classId":%d,"subjectId":%d,"timeslotId":%d,"roomId":%d}"""
                .formatted(daten.klasse8b, daten.deutsch, daten.slot("MO", 4), daten.r204)), 201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson WHERE timetable_id = ?", Long.class, gone))
                .isEqualTo(2);

        call(delete("/api/drafts/" + gone), 204);

        call(get("/api/drafts/" + gone), 404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson WHERE timetable_id = ?", Long.class, gone))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE id = ?", Long.class, gone)).isZero();
        assertThat(getJson("/api/drafts/" + keep).get("lessons")).hasSize(1);
        assertThat(getJson("/api/drafts")).hasSize(1);
        call(delete("/api/drafts/" + gone), 404);
    }

    @Test
    @DisplayName("Umbenennen: PATCH {name}; leer und zu lang sind 400 validation mit Feld name")
    void renameAndNameValidation() throws Exception {
        pflichtenheftGrunddaten();
        long id = call(post("/api/drafts").content("""
                {"source":"EMPTY"}"""), 201).get("id").asLong();

        JsonNode renamed = call(patch("/api/drafts/" + id).content("""
                {"name":"  Variante B  "}"""), 200);
        assertThat(renamed.get("name").asText()).isEqualTo("Variante B");
        assertThat(getJson("/api/drafts").get(0).get("name").asText()).isEqualTo("Variante B");

        JsonNode blank = call(patch("/api/drafts/" + id).content("""
                {"name":"   "}"""), 400);
        assertThat(blank.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(blank.get("errors").get(0).get("field").asText()).isEqualTo("name");

        JsonNode tooLong = call(post("/api/drafts").content("""
                {"name":"%s","source":"EMPTY"}""".formatted("x".repeat(81))), 400);
        assertThat(tooLong.get("errors").get(0).get("field").asText()).isEqualTo("name");
        assertThat(tooLong.get("errors").get(0).get("message").asText()).isEqualTo("hoechstens 80 Zeichen");

        // Die Vorgabe „Entwurf n" ueberspringt vergebene Namen.
        call(patch("/api/drafts/" + id).content("""
                {"name":"Entwurf 2"}"""), 200);
        assertThat(call(post("/api/drafts").content("""
                {"source":"EMPTY"}"""), 201).get("name").asText()).isEqualTo("Entwurf 3");
    }

    @Test
    @DisplayName("Unbekannte Kennung und leere Liste: 404 bzw. []")
    void unknownDraftIs404AndEmptyListIsAState() throws Exception {
        pflichtenheftGrunddaten();
        assertThat(getJson("/api/drafts")).isEmpty();
        call(get("/api/drafts/999999"), 404);
        call(post("/api/drafts/999999/solve").content("{}"), 404);
        call(post("/api/drafts/999999/accept").content("{}"), 404);
        call(get("/api/draft"), 404);
    }
}
