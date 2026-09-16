package de.wsp3_1.substitution;

import de.wsp3_1.planning.PlanningITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * {@code GET /api/timetable/week} in allen vier Sichten gegen die echte Datenbank (Checkliste P-7,
 * Testfall T-W4.1). Grundlage ist der Regelplan Montag/Dienstag aus dem Pflichtenheft — dieselben
 * sieben Stunden, die auch die Abnahme von Hand einplant.
 *
 * <p>Die Woche ist immer die <b>laufende</b>, und der Plan wird mit {@code validFrom} auf ihren Montag
 * angenommen. So haengt kein Testergebnis daran, an welchem Tag jemand die Tests startet — ein Plan
 * gilt erst ab seinem Beginn, und „heute" waere an einem Samstag schon zu spaet fuer den Montag
 * derselben Woche.</p>
 *
 * <p><b>Stand backend2 Teil 1:</b> jeder Eintrag ist {@code REGULAR}. Ereignisse und datierte Ausnahmen
 * (W-5, W-6) kommen in Teil 2; erst dann gibt es andere Arten.</p>
 */
class WeekIT extends PlanningITBase {

    private static final LocalDate MONTAG = LocalDate.now().with(DayOfWeek.MONDAY);

    /** Der Plan des Pflichtenhefts, angenommen mit Gueltigkeit ab dem Montag dieser Woche. */
    private Testdaten planDerWoche() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG)), 200);
        return daten;
    }

    @Test
    @DisplayName("P-7: die Woche beginnt am Montag und hat die fuenf Tage des Rasters")
    void weekStartsOnMondayAndHasTheDaysOfTheGrid() throws Exception {
        planDerWoche();

        JsonNode week = getJson("/api/timetable/week?date=" + MONTAG.plusDays(2) + "&view=SCHOOL");

        assertThat(week.get("monday").asText()).isEqualTo(MONTAG.toString());
        assertThat(week.get("days")).hasSize(5);
        for (int i = 0; i < 5; i++) {
            JsonNode day = week.get("days").get(i);
            assertThat(day.get("date").asText()).isEqualTo(MONTAG.plusDays(i).toString());
            assertThat(day.get("schoolDay").asBoolean()).isTrue();
        }
        // Mittwoch bis Freitag steht nichts im Plan.
        assertThat(week.get("days").get(2).get("entries")).isEmpty();
    }

    @Test
    @DisplayName("T-W4.1 (P-7): Klassensicht 7a — Montag 1. Stunde ist Ma bei MEY in R101, alles REGULAR")
    void tW4_1_classViewShowsTheLessonsOfThatClass() throws Exception {
        Testdaten daten = planDerWoche();

        JsonNode week = getJson("/api/timetable/week?date=" + MONTAG + "&view=CLASS&id=" + daten.klasse7a);
        List<JsonNode> montag = entriesOf(week, 0);

        assertThat(montag).hasSize(3);
        JsonNode erste = montag.get(0);
        assertThat(erste.get("position").asInt()).isEqualTo(1);
        assertThat(erste.get("day").asText()).isEqualTo("MO");
        assertThat(erste.get("date").asText()).isEqualTo(MONTAG.toString());
        assertThat(erste.get("subjectShort").asText()).isEqualTo("Ma");
        assertThat(erste.get("teacherShort").asText()).isEqualTo("MEY");
        assertThat(erste.get("roomShort").asText()).isEqualTo("R101");
        assertThat(erste.get("classShort").asText()).isEqualTo("7a");
        assertThat(erste.get("kind").asText()).isEqualTo("REGULAR");
        assertThat(erste.get("original").isNull()).isTrue();
        assertThat(erste.get("reasons")).isEmpty();
        assertThat(erste.get("exceptionId").isNull()).isTrue();

        for (JsonNode entry : montag) {
            assertThat(entry.get("classId").asLong()).isEqualTo(daten.klasse7a);
        }
    }

    @Test
    @DisplayName("T-W4.1 (P-7): Lehrkraftsicht Meyer — Montag genau vier Eintraege (1., 2., 4., 5.), Dienstag genau einer")
    void tW4_1_teacherViewShowsOnlyThatTeacher() throws Exception {
        Testdaten daten = planDerWoche();

        JsonNode week = getJson("/api/timetable/week?date=" + MONTAG + "&view=TEACHER&id=" + daten.meyer);

        List<JsonNode> montag = entriesOf(week, 0);
        assertThat(positionsOf(montag)).containsExactly(1, 2, 4, 5);
        assertThat(entriesOf(week, 1)).hasSize(1);
        for (JsonNode entry : montag) {
            assertThat(entry.get("teacherId").asLong()).isEqualTo(daten.meyer);
            assertThat(entry.get("kind").asText()).isEqualTo("REGULAR");
        }
    }

    @Test
    @DisplayName("T-W4.1: Raumsicht R101 (E-39) — Montag 1. und 2. Stunde belegt, ab der 4. nichts mehr")
    void tW4_1_roomViewShowsOnlyThatRoom() throws Exception {
        Testdaten daten = planDerWoche();

        JsonNode week = getJson("/api/timetable/week?date=" + MONTAG + "&view=ROOM&id=" + daten.r101);
        List<JsonNode> montag = entriesOf(week, 0);

        assertThat(positionsOf(montag)).containsExactly(1, 2);
        for (JsonNode entry : montag) {
            assertThat(entry.get("roomShort").asText()).isEqualTo("R101");
        }
    }

    @Test
    @DisplayName("T-W4.1 (P-7): Schulsicht — Montag 1. Stunde genau ein Eintrag, in der 2. zwei Klassen")
    void tW4_1_schoolViewShowsEverything() throws Exception {
        planDerWoche();

        List<JsonNode> montag = entriesOf(getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL"), 0);

        assertThat(positionsOf(montag)).containsExactly(1, 2, 2, 4, 4, 5);
        // In der 2. Stunde stehen 7a und 8b nebeneinander, nach Klassenbezeichnung sortiert.
        assertThat(montag.get(1).get("classShort").asText()).isEqualTo("7a");
        assertThat(montag.get(2).get("classShort").asText()).isEqualTo("8b");
    }

    @Test
    @DisplayName("Eine Sicht ohne Kennung ist 400, eine unbekannte Kennung 404")
    void missingOrUnknownIdIsRejected() throws Exception {
        planDerWoche();

        JsonNode problem = call(get("/api/timetable/week?date=" + MONTAG + "&view=CLASS"), 400);
        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("id");

        call(get("/api/timetable/week?date=" + MONTAG + "&view=ROOM&id=999999"), 404);
    }

    @Test
    @DisplayName("Ferien: an einem unterrichtsfreien Tag ist schoolDay false und die Liste leer")
    void holidaysHaveNoEntries() throws Exception {
        planDerWoche();
        jdbc.update("INSERT INTO holiday (name, date_from, date_to, kind) VALUES ('Testtag', ?, ?, 'VACATION')",
                java.sql.Date.valueOf(MONTAG), java.sql.Date.valueOf(MONTAG));

        JsonNode week = getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL");

        assertThat(week.get("days").get(0).get("schoolDay").asBoolean()).isFalse();
        assertThat(week.get("days").get(0).get("entries")).isEmpty();
        assertThat(week.get("days").get(1).get("schoolDay").asBoolean()).isTrue();
        assertThat(week.get("days").get(1).get("entries")).isNotEmpty();
        // Aufgeraeumt wird in PlanningITBase: „holiday" steht dort in der Liste der geleerten Tabellen.
    }

    /**
     * Kann-Option „Gueltig ab" (E-40) mit <b>zwei</b> Wechseln — der Fall, an dem die frueheren Zeilen
     * scheiterten (Befund 05.09.): gilt A ab Montag, B ab Mittwoch und C ab Freitag, dann nahm die alte
     * Regel fuer den Montag den zuletzt <i>archivierten</i> Plan (B) statt des Plans, der montags
     * wirklich gilt (A).
     */
    @Test
    @DisplayName("Drei Plaene mit Gueltigkeitsbeginn: jeder Tag bekommt den Plan, der an ihm gilt")
    void threeValidFromDatesResolvePerDay() throws Exception {
        Testdaten daten = planDerWoche();

        // Plan B ab Mittwoch: eine einzige Stunde, und die liegt montags.
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        addLesson(daten.klasse7a, daten.mathe, daten.slot("MO", 1), daten.r101);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG.plusDays(2))), 200);

        // Plan C ab Freitag: eine einzige Stunde, und die liegt freitags.
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        addLesson(daten.klasse7a, daten.mathe, daten.slot("FR", 1), daten.r101);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG.plusDays(4))), 200);

        JsonNode week = getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL");

        assertThat(entriesOf(week, 0)).as("Montag gilt Plan A").hasSize(6);
        assertThat(entriesOf(week, 1)).as("Dienstag gilt Plan A").hasSize(1);
        assertThat(entriesOf(week, 2)).as("Mittwoch gilt Plan B, dessen Stunde montags liegt").isEmpty();
        assertThat(entriesOf(week, 3)).as("Donnerstag gilt Plan B").isEmpty();
        assertThat(entriesOf(week, 4)).as("Freitag gilt Plan C mit seiner Freitagsstunde").hasSize(1);
    }

    /**
     * Das Gegenstueck zum Beginn (#115): ein Plan mit Enddatum gilt am letzten Tag noch und danach
     * nicht mehr. Steht kein Nachfolger bereit, bleibt die Woche leer — dass die Wochensicht dann den
     * zuletzt archivierten Plan nimmt, war genau der Fehler, den {@code timetableIdAt} nicht machen
     * darf.
     */
    @Test
    @DisplayName("Ein Enddatum gilt einschliesslich; danach bleibt die Woche leer")
    void aPlanWithAnEndDateStopsResolvingAfterIt() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        // Der Plan gilt Montag und Dienstag dieser Woche und endet am Dienstag.
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s","validUntil":"%s"}""".formatted(MONTAG, MONTAG.plusDays(1))), 200);

        JsonNode week = getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL");

        assertThat(entriesOf(week, 0)).as("Montag liegt im Zeitraum").hasSize(6);
        assertThat(entriesOf(week, 1)).as("Dienstag ist der letzte Tag — einschliesslich").hasSize(1);
        assertThat(entriesOf(week, 2)).as("Mittwoch liegt hinter dem Ende: kein Plan").isEmpty();

        assertThat(getJson("/api/timetables").get(0).get("validUntil").asText())
                .isEqualTo(MONTAG.plusDays(1).toString());
    }

    // ------------------------------------------------------------------ Helfer

    private static List<JsonNode> entriesOf(JsonNode week, int dayIndex) {
        List<JsonNode> entries = new ArrayList<>();
        week.get("days").get(dayIndex).get("entries").forEach(entries::add);
        return entries;
    }

    private static List<Integer> positionsOf(List<JsonNode> entries) {
        return entries.stream().map(entry -> entry.get("position").asInt()).toList();
    }
}
