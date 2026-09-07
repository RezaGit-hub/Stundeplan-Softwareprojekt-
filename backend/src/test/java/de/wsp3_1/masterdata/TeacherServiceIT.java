package de.wsp3_1.masterdata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * T-W1.1 (Lehrkraft anlegen) und T-W1.3 (loeschen gegen deaktivieren) gegen echtes PostgreSQL.
 */
class TeacherServiceIT extends MasterdataITBase {

    /**
     * T-W1.1: Schulz wird mit allen fuenf Werten gespeichert, die Liste enthaelt genau einen Schulz,
     * Meyer und Weber bleiben unveraendert.
     */
    @Test
    @DisplayName("T-W1.1: Schulz behaelt alle fuenf Werte, Meyer und Weber bleiben unveraendert")
    void tW1_1_createSchulz_persistsAllFiveValues_othersUnchanged() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        JsonNode alle = getJson("/api/teachers");
        assertThat(alle.size()).isEqualTo(3);
        assertThat(namen(alle)).containsExactly("Meyer", "Weber", "Schulz");

        JsonNode schulz = getJson("/api/teachers/" + daten.schulz);
        assertThat(schulz.get("name").asText()).isEqualTo("Schulz");
        assertThat(schulz.get("shortName").asText()).isEqualTo("SCZ");
        assertThat(schulz.get("weeklyHoursQuota").asInt()).isEqualTo(2);
        assertThat(zahlen(schulz.get("subjectIds"))).containsExactly(daten.mathe);
        assertThat(zahlen(schulz.get("blockedTimeslotIds")))
                .containsExactlyInAnyOrder(daten.slot("FR", 6), daten.slot("FR", 7));
        assertThat(schulz.get("preferredTimeslotIds").size()).isZero();
        assertThat(schulz.get("active").asBoolean()).isTrue();
        // 9c Mathematik, eine Wochenstunde — genau das Deputat, also keine Warnung.
        assertThat(schulz.get("plannedHours").asInt()).isEqualTo(1);
        assertThat(schulz.get("warnings").size()).isZero();

        JsonNode meyer = getJson("/api/teachers/" + daten.meyer);
        assertThat(meyer.get("shortName").asText()).isEqualTo("MEY");
        assertThat(meyer.get("weeklyHoursQuota").asInt()).isEqualTo(25);
        assertThat(meyer.get("plannedHours").asInt()).isEqualTo(5);
        JsonNode weber = getJson("/api/teachers/" + daten.weber);
        assertThat(weber.get("shortName").asText()).isEqualTo("WEB");
        assertThat(weber.get("plannedHours").asInt()).isEqualTo(2);
    }

    /**
     * T-W1.3, Schritte 1 bis 3 und 5a: Krause ohne Verweise laesst sich loeschen, Meyer nicht —
     * Meyer wird deaktiviert und bleibt in den Stunden stehen.
     */
    @Test
    @DisplayName("T-W1.3: Krause geloescht, Meyer nur deaktiviert, Stunden behalten Meyer")
    void tW1_3_deleteKrause_deactivateMeyer_lessonsKeepMeyer_notSelectable() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        aktivenPlanMoDiAnlegen(daten);

        // 1. Krause hat kein Fach und keine Stunde -> 204, danach in keiner Liste.
        long krause = postId("/api/teachers", """
                {"name":"Krause","shortName":"KRA","weeklyHoursQuota":10,"subjectIds":[],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}""");
        call(delete("/api/teachers/" + krause), 204);
        assertThat(namen(getJson("/api/teachers"))).doesNotContain("Krause");

        // 2. Meyer ist in fuenf Stunden eingeplant -> 409 mit dem Hinweis auf das Deaktivieren.
        JsonNode problem = call(delete("/api/teachers/" + daten.meyer), 409);
        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:in-use");
        assertThat(problem.get("detail").asText())
                .isEqualTo("Meyer ist in 5 Stunden eingeplant. Deaktivieren statt löschen.");
        assertThat(getJson("/api/teachers/" + daten.meyer).get("id").asLong()).isEqualTo(daten.meyer);

        // 3. Deaktivieren geht: die uebrigen Werte bleiben, active wird false.
        JsonNode deaktiviert = call(put("/api/teachers/" + daten.meyer).content("""
                {"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[%d,%d],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":false}"""
                .formatted(daten.mathe, daten.physik)), 200);
        assertThat(deaktiviert.get("active").asBoolean()).isFalse();
        assertThat(deaktiviert.get("plannedHours").asInt()).isEqualTo(5);

        // 5a. Meyer steht weiter in der Liste — mit active:false. Die Oberflaeche bietet ihn dann
        // nicht mehr an; die Stunden im Plan zeigen aber unveraendert auf ihn.
        JsonNode alle = getJson("/api/teachers");
        assertThat(alle.size()).isEqualTo(3);
        assertThat(eintrag(alle, "Meyer").get("active").asBoolean()).isFalse();
        Long stundenMitMeyer = jdbc.queryForObject("""
                SELECT count(*) FROM lesson l
                  JOIN class_subject cs ON cs.id = l.class_subject_id
                WHERE cs.teacher_id = ?""", Long.class, daten.meyer);
        assertThat(stundenMitMeyer).isEqualTo(5);

        // Ruecknahme wie in der Checkliste: wieder aktiv.
        JsonNode wiederAktiv = call(put("/api/teachers/" + daten.meyer).content("""
                {"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[%d,%d],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}"""
                .formatted(daten.mathe, daten.physik)), 200);
        assertThat(wiederAktiv.get("active").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Eine Lehrkraft mit Klassenzuordnung, aber ohne Stunde, wird ebenfalls nicht geloescht")
    void deleteTeacherAssignedToClassButNotPlanned() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        JsonNode problem = call(delete("/api/teachers/" + daten.schulz), 409);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:in-use");
        assertThat(problem.get("detail").asText())
                .isEqualTo("Schulz ist 9c zugeordnet. Deaktivieren statt löschen.");
    }

    @Test
    @DisplayName("Ein doppeltes Kuerzel ist ein Eingabefehler, kein Konflikt")
    void duplicateShortNameIsValidationError() throws Exception {
        pflichtenheftGrunddaten();

        JsonNode problem = call(post("/api/teachers").content("""
                {"name":"Meier","shortName":"mey","weeklyHoursQuota":10,"subjectIds":[],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}"""), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("shortName");
    }

    @Test
    @DisplayName("Ein fehlender Name ergibt 400 mit dem Feldnamen")
    void missingNameIsValidationError() throws Exception {
        JsonNode problem = call(post("/api/teachers").content("""
                {"name":"","shortName":"XYZ","weeklyHoursQuota":10,"subjectIds":[],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}"""), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("name");
    }

    @Test
    @DisplayName("Eine unbekannte Nummer ergibt 404 im ProblemDetail-Format")
    void unknownTeacherIsNotFound() throws Exception {
        JsonNode problem = call(get("/api/teachers/999999"), 404);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:not-found");
    }

    // ---------------------------------------------------------------- Eingaben, die 500 ergaben

    /**
     * Ein leerer Eintrag in einer Liste von Kennungen. Die Bean-Validation prueft die Liste, nicht
     * ihre Eintraege — {@code existsById(null)} warf danach eine IllegalArgumentException, und der
     * Client bekam 500 statt 400 mit Feldliste.
     */
    @Test
    @DisplayName("Ein leerer Eintrag in subjectIds ergibt 400 mit Feldliste, nicht 500")
    void nullInsideSubjectIdsIsValidationError() throws Exception {
        pflichtenheftGrunddaten();

        JsonNode problem = call(post("/api/teachers").content("""
                {"name":"Neu","shortName":"NEU","weeklyHoursQuota":25,"subjectIds":[null],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}"""), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("subjectIds");
    }

    /** Dasselbe fuer die Sperrzeiten — sie laufen durch dieselbe Pruefung. */
    @Test
    @DisplayName("Ein leerer Eintrag in blockedTimeslotIds ergibt 400 mit Feldliste, nicht 500")
    void nullInsideBlockedTimeslotIdsIsValidationError() throws Exception {
        pflichtenheftGrunddaten();

        JsonNode problem = call(post("/api/teachers").content("""
                {"name":"Neu","shortName":"NEU","weeklyHoursQuota":25,"subjectIds":[],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[null],"active":true}"""), 400);

        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("blockedTimeslotIds");
    }

    /**
     * Zu lange Werte muss die Pruefung abfangen: {@code teacher.name} ist in V1 varchar(120), und ohne
     * Grenze im DTO scheiterte erst die Datenbank — mit 500 statt 400 mit Feldliste.
     */
    @Test
    @DisplayName("Ein zu langer Name ergibt 400 mit Feldliste, nicht 500")
    void tooLongNameIsValidationError() throws Exception {
        pflichtenheftGrunddaten();

        JsonNode problem = call(post("/api/teachers").content("""
                {"name":"%s","shortName":"NEU","weeklyHoursQuota":25,"subjectIds":[],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}"""
                .formatted("M".repeat(121))), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("name");
    }

    /** Eine Kennung, die keine Zahl ist: Fehler der Anfrage (400), kein Serverfehler. */
    @Test
    @DisplayName("GET /api/teachers/abc ergibt 400 und nicht 500")
    void nonNumericIdIsBadRequest() throws Exception {
        JsonNode problem = call(get("/api/teachers/abc"), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("id");
    }

    private static List<String> namen(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            out.add(array.get(i).get("name").asText());
        }
        return out;
    }

    private static List<Long> zahlen(JsonNode array) {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            out.add(array.get(i).asLong());
        }
        return out;
    }

    private static JsonNode eintrag(JsonNode array, String name) {
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).get("name").asText().equals(name)) {
                return array.get(i);
            }
        }
        throw new AssertionError("Kein Eintrag " + name);
    }
}
