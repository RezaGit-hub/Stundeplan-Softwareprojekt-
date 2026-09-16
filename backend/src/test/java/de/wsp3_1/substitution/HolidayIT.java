package de.wsp3_1.substitution;

import de.wsp3_1.planning.PlanningITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/holidays**} und ihre Wirkung auf die Wochensicht (Kann, E-13; Karte T-110).
 *
 * <p>Der Kern des Testfalls steht in {@code rollen_aufgaben.md}: „Woche mit Feiertag hat am Feiertag
 * keine Eintraege und keinen Vertretungsbedarf". Genau das wird hier gemessen — mit einem Ausfall, der
 * an einem Unterrichtstag vier Stunden offen macht und am Feiertag keine.</p>
 */
class HolidayIT extends PlanningITBase {

    private static final LocalDate MONTAG = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1);
    private static final LocalDate FREITAG = MONTAG.plusDays(4);

    @Test
    @DisplayName("T-110: am Feiertag ist schoolDay false, es gibt keine Eintraege und keinen Bedarf")
    void aHolidayHasNoEntriesAndNoPendingLessons() throws Exception {
        Testdaten daten = planDerWoche();
        call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Krankmeldung"}""".formatted(daten.meyer, MONTAG, FREITAG)), 201);
        assertThat(getJson("/api/substitutions/pending?from=" + MONTAG + "&to=" + FREITAG)).hasSize(5);

        JsonNode feiertag = call(post("/api/holidays").content("""
                {"name":"Tag der Deutschen Einheit","dateFrom":"%s","dateTo":"%s",
                 "kind":"PUBLIC_HOLIDAY"}""".formatted(MONTAG, MONTAG)), 201);
        assertThat(feiertag.get("kind").asText()).isEqualTo("PUBLIC_HOLIDAY");

        JsonNode woche = getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL");
        assertThat(woche.get("days").get(0).get("schoolDay").asBoolean()).isFalse();
        assertThat(woche.get("days").get(0).get("entries")).isEmpty();
        assertThat(woche.get("days").get(1).get("schoolDay").asBoolean()).isTrue();
        assertThat(woche.get("days").get(1).get("entries")).isNotEmpty();

        // Am Feiertag entsteht kein Vertretungsbedarf; die Dienstagsstunde bleibt offen.
        JsonNode offen = getJson("/api/substitutions/pending?from=" + MONTAG + "&to=" + FREITAG);
        assertThat(offen).hasSize(1);
        assertThat(offen.get(0).get("date").asText()).isEqualTo(MONTAG.plusDays(1).toString());

        // Zurueckgenommen gilt der Montag wieder als Unterrichtstag.
        mvc.perform(authorized(delete("/api/holidays/" + feiertag.get("id").asLong())))
                .andExpect(status().isNoContent());
        assertThat(getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL")
                .get("days").get(0).get("schoolDay").asBoolean()).isTrue();
        assertThat(getJson("/api/substitutions/pending?from=" + MONTAG + "&to=" + FREITAG)).hasSize(5);
    }

    @Test
    @DisplayName("Ferien ueber mehrere Tage: jeder Tag des Zeitraums ist unterrichtsfrei")
    void aVacationCoversEveryDayOfItsPeriod() throws Exception {
        planDerWoche();

        call(post("/api/holidays").content("""
                {"name":"Herbstferien","dateFrom":"%s","dateTo":"%s","kind":"VACATION"}"""
                .formatted(MONTAG.plusDays(1), MONTAG.plusDays(3))), 201);

        JsonNode woche = getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL");
        assertThat(woche.get("days").get(0).get("schoolDay").asBoolean()).isTrue();
        for (int tag = 1; tag <= 3; tag++) {
            assertThat(woche.get("days").get(tag).get("schoolDay").asBoolean())
                    .as("Tag " + tag + " liegt in den Ferien").isFalse();
        }
        assertThat(woche.get("days").get(4).get("schoolDay").asBoolean()).isTrue();
        assertThat(getJson("/api/holidays")).hasSize(1);
    }

    @Test
    @DisplayName("Ein Zeitraum, der vor seinem Beginn endet, ist 400 mit Feldfehler an dateTo")
    void aPeriodMustNotEndBeforeItStarts() throws Exception {
        JsonNode problem = call(post("/api/holidays").content("""
                {"name":"rueckwaerts","dateFrom":"%s","dateTo":"%s","kind":"VACATION"}"""
                .formatted(FREITAG, MONTAG)), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("dateTo");
        assertThat(getJson("/api/holidays")).isEmpty();

        call(delete("/api/holidays/999999"), 404);
    }

    /** Grunddaten und Regelplan Montag/Dienstag, angenommen mit Gueltigkeit ab dem Montag dieser Woche. */
    private Testdaten planDerWoche() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG)), 200);
        return daten;
    }
}
