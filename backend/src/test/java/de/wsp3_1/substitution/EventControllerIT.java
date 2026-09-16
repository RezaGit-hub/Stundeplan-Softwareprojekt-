package de.wsp3_1.substitution;

import de.wsp3_1.planning.PlanningITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/events**} gegen die echte Datenbank (Checkliste V-1 und V-2, Testfall T-W5.1).
 *
 * <p>Geprueft wird vor allem das, was <b>berechnet</b> wird: {@code affected} je Art des Ereignisses
 * und {@code hint}, wenn nichts betroffen ist. Ausserdem der Sonderfall Zusatzstunde — sie legt eine
 * Ausnahme an und verschwindet mit ihrem Ereignis wieder.</p>
 */
class EventControllerIT extends PlanningITBase {

    private static final LocalDate MONTAG = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1);
    private static final LocalDate MITTWOCH = MONTAG.plusDays(2);
    private static final DateTimeFormatter DEUTSCH = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Test
    @DisplayName("V-1: der Ausfall einer Lehrkraft nennt genau ihre vier Montagsstunden")
    void teacherAbsenceListsHerLessons() throws Exception {
        Testdaten daten = planDerWoche();

        JsonNode event = call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Krankmeldung"}""".formatted(daten.meyer, MONTAG, MONTAG)), 201);

        assertThat(betroffen(event)).containsExactly("7a/1", "8b/2", "7a/4", "8b/5");
        assertThat(event.get("kind").asText()).isEqualTo("TEACHER_ABSENCE");
        assertThat(event.get("roomId").isNull()).isTrue();
        assertThat(event.get("createdBy").asText()).isEqualTo("admin");
        assertThat(event.get("hint").isNull()).isTrue();
        assertThat(event.get("extra").isNull()).isTrue();
    }

    @Test
    @DisplayName("V-2: die Sperrung eines Raums nennt genau die Stunden in diesem Raum")
    void roomBlockListsTheLessonsInThatRoom() throws Exception {
        Testdaten daten = planDerWoche();

        JsonNode event = call(post("/api/events").content("""
                {"kind":"ROOM_BLOCKED","roomId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Wasserschaden"}""".formatted(daten.r101, MONTAG, MONTAG)), 201);

        assertThat(betroffen(event)).containsExactly("7a/1", "7a/2");
        assertThat(event.get("teacherId").isNull()).isTrue();
    }

    @Test
    @DisplayName("Trifft ein Ereignis keine Stunde, sagt die Antwort das (Ausnahme aus W-5)")
    void anEventWithoutLessonsCarriesAHint() throws Exception {
        Testdaten daten = planDerWoche();

        // Schulz unterrichtet in diesem Plan keine Stunde.
        JsonNode event = call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Fortbildung"}""".formatted(daten.schulz, MITTWOCH, MITTWOCH)), 201);

        assertThat(event.get("affected")).isEmpty();
        assertThat(event.get("hint").asText()).isEqualTo("Das Ereignis betrifft keine eingeplante Stunde.");
    }

    @Test
    @DisplayName("Eine Zusatzstunde legt eine Ausnahme ADDED an und steht als ADDED in der Woche")
    void extraLessonCreatesAnAddedException() throws Exception {
        Testdaten daten = planDerWoche();

        JsonNode event = call(post("/api/events").content("""
                {"kind":"EXTRA_LESSON","dateFrom":"%s","dateTo":"%s","reason":"Nachholstunde",
                 "extra":{"date":"%s","timeslotId":%d,"classId":%d,"subjectId":%d,
                          "teacherId":%d,"roomId":%d}}"""
                .formatted(MITTWOCH, MITTWOCH, MITTWOCH, daten.slot("WE", 1), daten.klasse9c,
                        daten.mathe, daten.schulz, daten.r204)), 201);

        assertThat(event.get("hint").asText())
                .isEqualTo("Zusatzstunde für 9c am " + MITTWOCH.format(DEUTSCH) + " angelegt.");
        assertThat(event.get("extra").get("classId").asLong()).isEqualTo(daten.klasse9c);
        assertThat(event.get("affected")).isEmpty();

        JsonNode ausnahmen = getJson("/api/exceptions?from=" + MITTWOCH + "&to=" + MITTWOCH);
        assertThat(ausnahmen).hasSize(1);
        assertThat(ausnahmen.get(0).get("kind").asText()).isEqualTo("ADDED");
        assertThat(ausnahmen.get(0).get("lessonId").isNull()).isTrue();

        List<JsonNode> mittwoch = eintraege(getJson("/api/timetable/week?date=" + MONTAG
                + "&view=SCHOOL"), 2);
        assertThat(mittwoch).hasSize(1);
        assertThat(mittwoch.getFirst().get("kind").asText()).isEqualTo("ADDED");
        assertThat(mittwoch.getFirst().get("lessonId").isNull()).isTrue();
        assertThat(mittwoch.getFirst().get("classShort").asText()).isEqualTo("9c");
        assertThat(mittwoch.getFirst().get("teacherShort").asText()).isEqualTo("SCZ");
        assertThat(namen(mittwoch.getFirst().get("reasons"))).containsExactly("Nachholstunde");

        // Das Ereignis zuruecknehmen loescht die Zusatzstunde mit.
        mvc.perform(authorized(delete("/api/events/" + event.get("id").asLong())))
                .andExpect(status().isNoContent());
        assertThat(getJson("/api/exceptions?from=" + MITTWOCH + "&to=" + MITTWOCH)).isEmpty();
        assertThat(eintraege(getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL"), 2)).isEmpty();
    }

    @Test
    @DisplayName("Ein geloeschtes Ereignis nimmt die Vertretungsbedarfe mit, die Ausnahme bleibt")
    void deletingAnEventRemovesThePendingLessons() throws Exception {
        Testdaten daten = planDerWoche();
        long ausfall = call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Krankmeldung"}""".formatted(daten.meyer, MONTAG, MONTAG)), 201)
                .get("id").asLong();

        mvc.perform(authorized(delete("/api/events/" + ausfall))).andExpect(status().isNoContent());

        JsonNode woche = getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL");
        assertThat(eintraege(woche, 0)).allSatisfy(entry ->
                assertThat(entry.get("kind").asText()).isEqualTo("REGULAR"));
        assertThat(getJson("/api/events")).isEmpty();
    }

    @Test
    @DisplayName("Filter und Pflichtangaben: teacherId filtert, ein Ausfall ohne Lehrkraft ist 400")
    void filtersAndValidation() throws Exception {
        Testdaten daten = planDerWoche();
        call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Krankmeldung"}""".formatted(daten.meyer, MONTAG, MONTAG)), 201);
        call(post("/api/events").content("""
                {"kind":"ROOM_BLOCKED","roomId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Wasserschaden"}""".formatted(daten.r101, MONTAG, MONTAG)), 201);

        assertThat(getJson("/api/events?teacherId=" + daten.meyer)).hasSize(1);
        assertThat(getJson("/api/events?roomId=" + daten.r101)).hasSize(1);
        assertThat(getJson("/api/events?from=" + MITTWOCH + "&to=" + MITTWOCH)).isEmpty();
        assertThat(getJson("/api/events")).hasSize(2);

        JsonNode problem = call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","dateFrom":"%s","dateTo":"%s","reason":"ohne Lehrkraft"}"""
                .formatted(MONTAG, MONTAG)), 400);
        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("teacherId");

        JsonNode zeitraum = call(post("/api/events").content("""
                {"kind":"ROOM_BLOCKED","roomId":%d,"dateFrom":"%s","dateTo":"%s","reason":"rueckwaerts"}"""
                .formatted(daten.r101, MITTWOCH, MONTAG)), 400);
        assertThat(zeitraum.get("errors").get(0).get("field").asText()).isEqualTo("dateTo");

        // Ein Zeitraum, der laenger ist, als `affected` rechnet: 400 statt still gekuerzter Liste.
        JsonNode zuLang = call(post("/api/events").content("""
                {"kind":"ROOM_BLOCKED","roomId":%d,"dateFrom":"%s","dateTo":"%s","reason":"zu lang"}"""
                .formatted(daten.r101, MONTAG, MONTAG.plusDays(400))), 400);
        assertThat(zuLang.get("errors").get(0).get("message").asText()).contains("366 Tage");

        call(delete("/api/events/999999"), 404);
    }

    /**
     * Ein verdrehter Bereich ist ein Eingabefehler und keine leere Liste — sonst sieht niemand den
     * vertauschten Wert. Dieselbe Pruefung wie an {@code /api/substitutions/pending} (SU-16).
     */
    @Test
    @DisplayName("Ein verdrehter Bereich in from/to ist 400, keine leere Liste")
    void aReversedRangeIsAValidationError() throws Exception {
        planDerWoche();

        JsonNode problem = call(get("/api/events?from=" + MITTWOCH + "&to=" + MONTAG), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("to");
    }

    /** Fehlt ein Pflichtparameter, ist das 400 mit dem Feldnamen — frueher war es 500. */
    @Test
    @DisplayName("Ein fehlender Pflichtparameter ist 400 mit dem Namen des Feldes")
    void aMissingRequiredParameterIsAValidationError() throws Exception {
        Testdaten daten = planDerWoche();
        long stunde = lessonIdAt(getJson("/api/timetable"), daten.klasse7a, daten.slot("MO", 1));

        JsonNode problem = call(get("/api/substitutions/options?lessonId=" + stunde), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("date");
    }

    // ------------------------------------------------------------------ Helfer

    /** Grunddaten und Regelplan Montag/Dienstag, angenommen mit Gueltigkeit ab dem Montag dieser Woche. */
    private Testdaten planDerWoche() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG)), 200);
        return daten;
    }

    private static List<String> betroffen(JsonNode event) {
        List<String> rows = new ArrayList<>();
        event.get("affected").forEach(row ->
                rows.add(row.get("classShort").asText() + "/" + row.get("position").asInt()));
        return rows;
    }

    private static List<JsonNode> eintraege(JsonNode week, int dayIndex) {
        List<JsonNode> entries = new ArrayList<>();
        week.get("days").get(dayIndex).get("entries").forEach(entries::add);
        return entries;
    }

    private static List<String> namen(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }
}
