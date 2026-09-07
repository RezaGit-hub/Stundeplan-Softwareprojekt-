package de.wsp3_1.masterdata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Das Stundenraster (G-3): Aufbau, Upsert nach (Tag, Position), 409 bei belegten Zeitfenstern und
 * die Sperrzeiten, die mit einem entfernten Zeitfenster verschwinden.
 */
class TimegridIT extends MasterdataITBase {

    @Test
    @DisplayName("G-3: Mo–Fr mit sieben Positionen, je Tag genau eine Pause an Position 3")
    void gridHas35SlotsWithOneBreakPerDay() throws Exception {
        pflichtenheftGrunddaten();

        JsonNode slots = getJson("/api/timegrid").get("slots");

        assertThat(slots.size()).isEqualTo(35);
        assertThat(slots.get(0).get("day").asText()).isEqualTo("MO");
        assertThat(slots.get(0).get("position").asInt()).isEqualTo(1);
        assertThat(slots.get(0).get("startTime").asText()).isEqualTo("08:00");
        assertThat(slots.get(0).get("endTime").asText()).isEqualTo("08:45");
        assertThat(slots.get(0).get("isBreak").asBoolean()).isFalse();
        // Montag zuerst, Freitag zuletzt — alphabetisch waere FR vorn.
        assertThat(slots.get(34).get("day").asText()).isEqualTo("FR");
        assertThat(slots.get(34).get("position").asInt()).isEqualTo(7);

        int pausen = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).get("isBreak").asBoolean()) {
                pausen++;
                assertThat(slots.get(i).get("position").asInt()).isEqualTo(3);
            }
        }
        assertThat(pausen).isEqualTo(5);
    }

    @Test
    @DisplayName("Der Upsert geht nach (Tag, Position): gleiche IDs, geaenderte Zeiten, fehlende Zeilen weg")
    void putIsAnUpsertByDayAndPosition() throws Exception {
        pflichtenheftGrunddaten();
        JsonNode vorher = getJson("/api/timegrid").get("slots");
        long ersteId = vorher.get(0).get("id").asLong();

        JsonNode nachher = call(put("/api/timegrid").content("""
                {"slots":[
                  {"day":"MO","position":1,"startTime":"07:55","endTime":"08:40","isBreak":false},
                  {"day":"MO","position":2,"startTime":"08:50","endTime":"09:35","isBreak":false}
                ]}"""), 200).get("slots");

        assertThat(nachher.size()).isEqualTo(2);
        assertThat(nachher.get(0).get("id").asLong()).isEqualTo(ersteId);
        assertThat(nachher.get(0).get("startTime").asText()).isEqualTo("07:55");
        assertThat(getJson("/api/timegrid").get("slots").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Ein belegtes Zeitfenster laesst sich nicht entfernen (409 in-use)")
    void removingAUsedSlotIsConflict() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        aktivenPlanMoDiAnlegen(daten);

        JsonNode problem = call(put("/api/timegrid").content("""
                {"slots":[{"day":"MO","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false}]}"""),
                409);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:in-use");
        assertThat(problem.get("detail").asText())
                .startsWith("4 Zeitfenster sind im aktiven Plan, im Entwurf oder in einer Verlegung belegt")
                .contains("Mo 08:50", "Di 08:00");
        assertThat(getJson("/api/timegrid").get("slots").size()).isEqualTo(35);
    }

    /**
     * Ein archivierter Plan haelt kein Zeitfenster fest, seine Stunden zeigen aber per Fremdschluessel
     * noch darauf. Gemessen 07.09.: Vorlage laden nach "Plan leeren" endete mit 23503 statt 200.
     */
    @Test
    @DisplayName("Stunden archivierter Plaene blockieren das Raster nicht und werden mit dem Zeitfenster entfernt")
    void archivedLessonsDoNotBlockRemoval() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        aktivenPlanMoDiAnlegen(daten);
        jdbc.update("UPDATE timetable SET status = 'ARCHIVED' WHERE status = 'ACTIVE'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson", Long.class)).isEqualTo(7L);

        JsonNode raster = call(put("/api/timegrid").content("""
                {"slots":[{"day":"MO","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false}]}"""),
                200);

        assertThat(raster.get("slots").size()).isEqualTo(1);
        // Nur die Stunde im verbliebenen Zeitfenster Mo 1. ueberlebt; die sechs anderen sind weg.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Mit einem entfernten Zeitfenster verschwindet die Sperrzeit der Lehrkraft")
    void removingASlotAlsoRemovesTheBlockedTime() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        assertThat(getJson("/api/teachers/" + daten.schulz).get("blockedTimeslotIds").size()).isEqualTo(2);

        // Neues Raster ohne den Freitag: Schulz hatte dort seine beiden Sperrzeiten.
        call(put("/api/timegrid").content(rasterOhneFreitag()), 200);

        assertThat(getJson("/api/timegrid").get("slots").size()).isEqualTo(28);
        assertThat(getJson("/api/teachers/" + daten.schulz).get("blockedTimeslotIds").size()).isZero();
    }

    @Test
    @DisplayName("Eine Endzeit vor dem Beginn ist ein Eingabefehler mit dem Pfad der Zeile")
    void endBeforeStartIsValidationError() throws Exception {
        JsonNode problem = call(put("/api/timegrid").content("""
                {"slots":[
                  {"day":"MO","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false},
                  {"day":"MO","position":2,"startTime":"09:00","endTime":"08:30","isBreak":false}
                ]}"""), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("slots[1].endTime");
    }

    @Test
    @DisplayName("Dieselbe Position zweimal am selben Tag ist ein Eingabefehler")
    void duplicatePositionIsValidationError() throws Exception {
        JsonNode problem = call(put("/api/timegrid").content("""
                {"slots":[
                  {"day":"MO","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false},
                  {"day":"MO","position":1,"startTime":"08:50","endTime":"09:35","isBreak":false}
                ]}"""), 400);

        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("slots[1].position");
    }

    /**
     * Verschiedene Positionen, aber dieselbe Uhrzeit: die Pruefung auf doppelte Positionen greift
     * hier nicht, im Browser stuenden die beiden Stunden trotzdem uebereinander.
     */
    @Test
    @DisplayName("Zwei Zeitfenster am selben Tag duerfen sich zeitlich nicht ueberschneiden")
    void overlappingTimesAreValidationError() throws Exception {
        JsonNode problem = call(put("/api/timegrid").content("""
                {"slots":[
                  {"day":"MO","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false},
                  {"day":"MO","position":2,"startTime":"08:30","endTime":"09:15","isBreak":false}
                ]}"""), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("slots[1].startTime");
    }

    /** Dieselben Uhrzeiten an verschiedenen Tagen sind der Normalfall und kein Fehler. */
    @Test
    @DisplayName("Gleiche Uhrzeiten an verschiedenen Tagen sind in Ordnung")
    void sameTimesOnDifferentDaysAreFine() throws Exception {
        JsonNode raster = call(put("/api/timegrid").content("""
                {"slots":[
                  {"day":"MO","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false},
                  {"day":"TU","position":1,"startTime":"08:00","endTime":"08:45","isBreak":false}
                ]}"""), 200);

        assertThat(raster.get("slots").size()).isEqualTo(2);
    }

    /**
     * {@code isBreak} steht im Vertrag unter {@code required}. Fehlt es, war es bisher stillschweigend
     * „keine Pause" — aus einer vergessenen Pausenzeile wuerde so eine Unterrichtsstunde.
     */
    @Test
    @DisplayName("Ein fehlendes isBreak ist ein Eingabefehler, keine stille Annahme")
    void missingIsBreakIsValidationError() throws Exception {
        JsonNode problem = call(put("/api/timegrid").content("""
                {"slots":[
                  {"day":"MO","position":1,"startTime":"08:00","endTime":"08:45"}
                ]}"""), 400);

        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("slots[0].isBreak");
    }

    /** Eine leere Zeile im Array gab bisher 500, weil der erste Zugriff darauf fehlschlug. */
    @Test
    @DisplayName("Eine leere Zeile im Raster ergibt 400 mit Feldliste, nicht 500")
    void nullSlotIsValidationError() throws Exception {
        JsonNode problem = call(put("/api/timegrid").content("""
                {"slots":[null]}"""), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("slots[0]");
    }

    private static String rasterOhneFreitag() {
        String[] days = {"MO", "TU", "WE", "TH"};
        String[][] times = {
                {"08:00", "08:45"}, {"08:50", "09:35"}, {"09:35", "09:55"}, {"09:55", "10:40"},
                {"10:45", "11:30"}, {"11:50", "12:35"}, {"12:40", "13:25"}};
        StringBuilder rows = new StringBuilder();
        for (String day : days) {
            for (int position = 1; position <= times.length; position++) {
                if (!rows.isEmpty()) {
                    rows.append(',');
                }
                rows.append("""
                        {"day":"%s","position":%d,"startTime":"%s","endTime":"%s","isBreak":%b}"""
                        .formatted(day, position, times[position - 1][0], times[position - 1][1], position == 3));
            }
        }
        return "{\"slots\":[" + rows + "]}";
    }
}
