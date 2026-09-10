package de.wsp3_1.masterdata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Die Faecherliste einer Klasse (G-7 bis G-11): Pflichtangaben, Warnungen und was nach dem
 * Zuruecksetzen uebrig bleibt. Das ist die Ende-zu-Ende-Sicht auf T-W1.4 und T-W1.5 — die Rechnung
 * selbst prueft {@code WarningCheckerTest} ohne Datenbank.
 */
class ClassSubjectsIT extends MasterdataITBase {

    @Test
    @DisplayName("G-7: Die Faecherliste kommt mit Kuerzeln zurueck und meldet nichts")
    void classSubjectsAreStoredWithShortNames() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        JsonNode klasse7a = getJson("/api/classes/" + daten.klasse7a);
        assertThat(klasse7a.get("name").asText()).isEqualTo("7a");
        assertThat(klasse7a.get("grade").asInt()).isEqualTo(7);
        assertThat(klasse7a.get("subjects").size()).isEqualTo(3);
        JsonNode mathe = klasse7a.get("subjects").get(0);
        assertThat(mathe.get("subjectShort").asText()).isEqualTo("Ma");
        assertThat(mathe.get("weeklyHours").asInt()).isEqualTo(2);
        assertThat(mathe.get("teacherShort").asText()).isEqualTo("MEY");
        assertThat(klasse7a.get("warnings").size()).isZero();
        assertThat(getJson("/api/classes").size()).isEqualTo(3);
    }

    /**
     * G-8, Ausnahme 1 aus W-1: Wochenstunden und Lehrkraft sind Pflicht. Beide Fehler kommen in
     * einer Antwort, mit dem Pfad des Formularfeldes — und die Faecherliste bleibt, wie sie war.
     */
    @Test
    @DisplayName("G-8: Zeile ohne Wochenstunden und ohne Lehrkraft ergibt 400 mit zwei Feldfehlern")
    void missingHoursAndTeacherAreTwoFieldErrors() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        JsonNode problem = call(put("/api/classes/" + daten.klasse9c + "/subjects").content("""
                [{"subjectId":%d,"weeklyHours":1,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":null,"teacherId":null}]"""
                .formatted(daten.mathe, daten.schulz, daten.deutsch)), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(felder(problem.get("errors")))
                .contains("subjects[1].weeklyHours", "subjects[1].teacherId");

        // Die Faecherliste der 9c ist unveraendert: weiterhin genau Mathematik bei Schulz.
        JsonNode klasse9c = getJson("/api/classes/" + daten.klasse9c);
        assertThat(klasse9c.get("subjects").size()).isEqualTo(1);
        assertThat(klasse9c.get("subjects").get(0).get("teacherShort").asText()).isEqualTo("SCZ");
    }

    @Test
    @DisplayName("Dasselbe Fach zweimal in einer Liste ist ein Eingabefehler")
    void duplicateSubjectIsValidationError() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        JsonNode problem = call(put("/api/classes/" + daten.klasse9c + "/subjects").content("""
                [{"subjectId":%d,"weeklyHours":1,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":2,"teacherId":%d}]"""
                .formatted(daten.mathe, daten.schulz, daten.mathe, daten.schulz)), 400);

        assertThat(felder(problem.get("errors"))).contains("subjects[1].subjectId");
    }

    /** T-W1.4 ueber die Schnittstelle: Weber unterrichtet kein Mathematik. */
    @Test
    @DisplayName("G-10 / T-W1.4: Weber in 9c Mathematik ergibt genau eine Warnung, Zuruecksetzen loescht sie")
    void tW1_4_subjectNotTaughtOverTheApi() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        JsonNode mitWeber = call(put("/api/classes/" + daten.klasse9c + "/subjects").content("""
                [{"subjectId":%d,"weeklyHours":1,"teacherId":%d}]"""
                .formatted(daten.mathe, daten.weber)), 200);

        assertThat(mitWeber.get("warnings").size()).isEqualTo(1);
        JsonNode warnung = mitWeber.get("warnings").get(0);
        assertThat(warnung.get("code").asText()).isEqualTo("SUBJECT_NOT_TAUGHT");
        assertThat(warnung.get("teacherId").asLong()).isEqualTo(daten.weber);
        assertThat(warnung.get("message").asText()).isEqualTo("Weber unterrichtet das Fach Mathematik nicht.");
        // Gespeichert wird trotzdem: eine Warnung verhindert nichts.
        assertThat(mitWeber.get("subjects").get(0).get("teacherShort").asText()).isEqualTo("WEB");

        JsonNode zurueck = call(put("/api/classes/" + daten.klasse9c + "/subjects").content("""
                [{"subjectId":%d,"weeklyHours":1,"teacherId":%d}]"""
                .formatted(daten.mathe, daten.schulz)), 200);
        assertThat(zurueck.get("warnings").size()).isZero();
    }

    /** T-W1.5 ueber die Schnittstelle: Schulz kaeme auf 3 Wochenstunden bei Deputat 2. */
    @Test
    @DisplayName("G-11 / T-W1.5: Schulz in 7a Mathematik ergibt genau eine Deputat-Warnung")
    void tW1_5_quotaExceededOverTheApi() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();

        JsonNode mitSchulz = call(put("/api/classes/" + daten.klasse7a + "/subjects").content("""
                [{"subjectId":%d,"weeklyHours":2,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":1,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":1,"teacherId":%d}]"""
                .formatted(daten.mathe, daten.schulz, daten.deutsch, daten.weber, daten.physik, daten.meyer)), 200);

        assertThat(mitSchulz.get("warnings").size()).isEqualTo(1);
        JsonNode warnung = mitSchulz.get("warnings").get(0);
        assertThat(warnung.get("code").asText()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(warnung.get("teacherId").asLong()).isEqualTo(daten.schulz);
        assertThat(warnung.get("message").asText())
                .isEqualTo("Schulz ist mit 3 Wochenstunden über dem Deputat von 2.");
        // Dieselbe Warnung steht auch an der Lehrkraft.
        JsonNode schulz = getJson("/api/teachers/" + daten.schulz);
        assertThat(schulz.get("plannedHours").asInt()).isEqualTo(3);
        assertThat(schulz.get("warnings").size()).isEqualTo(1);

        JsonNode zurueck = call(put("/api/classes/" + daten.klasse7a + "/subjects").content("""
                [{"subjectId":%d,"weeklyHours":2,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":1,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":1,"teacherId":%d}]"""
                .formatted(daten.mathe, daten.meyer, daten.deutsch, daten.weber, daten.physik, daten.meyer)), 200);
        assertThat(zurueck.get("warnings").size()).isZero();
        assertThat(getJson("/api/teachers/" + daten.schulz).get("warnings").size()).isZero();
    }

    /**
     * Beim Ersetzen der Faecherliste bleibt die vorhandene Zeile bestehen — sonst wuerden die
     * Unterrichtsstunden mitgeloescht, die daran haengen.
     */
    @Test
    @DisplayName("Ein Lehrkraftwechsel behaelt die Stunden der Klasse")
    void changingTheTeacherKeepsTheLessons() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        aktivenPlanMoDiAnlegen(daten);
        Long vorher = jdbc.queryForObject("SELECT count(*) FROM lesson", Long.class);

        // 7a Mathematik hat drei Stunden im aktiven Plan (Mo 1., Mo 5. der 8b bleibt unberuehrt, Di 1.).
        call(put("/api/classes/" + daten.klasse7a + "/subjects").content("""
                [{"subjectId":%d,"weeklyHours":2,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":1,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":1,"teacherId":%d}]"""
                .formatted(daten.mathe, daten.schulz, daten.deutsch, daten.weber, daten.physik, daten.meyer)), 200);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM lesson", Long.class)).isEqualTo(vorher);
        Long stundenMitSchulz = jdbc.queryForObject("""
                SELECT count(*) FROM lesson l
                  JOIN class_subject cs ON cs.id = l.class_subject_id
                WHERE cs.teacher_id = ?""", Long.class, daten.schulz);
        assertThat(stundenMitSchulz).isEqualTo(2);
    }

    private static List<String> felder(JsonNode errors) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < errors.size(); i++) {
            out.add(errors.get(i).get("field").asText());
        }
        return out;
    }
}
