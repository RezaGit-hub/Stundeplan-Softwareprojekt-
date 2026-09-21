package de.wsp3_1.changelog;

import de.wsp3_1.planning.PlanningITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/changelog} gegen die echte Datenbank (Checkliste V-6, Testfall T-W7.1).
 *
 * <p>Jeder Eintrag entsteht ueber die Schnittstelle: {@code TIMETABLE_ACCEPTED} beim Annehmen eines
 * Entwurfs, {@code EXCEPTION_CREATED} und {@code EXCEPTION_DELETED} ueber {@code POST} und
 * {@code DELETE /api/exceptions}. Bis zur Bauphase backend3 riefen die Tests dazu
 * {@code ChangelogService} direkt auf („Platzhalter fuer substitution"); seit es die Endpunkte gibt,
 * ist der Platzhalter eingeloest — die Erwartungen sind dieselben geblieben.</p>
 *
 * <p>Der Kalendertag ist der Montag der kommenden Woche — der Tag der Checkliste, aber relativ
 * gerechnet, damit kein Ergebnis daran haengt, wann jemand die Tests startet.</p>
 */
class ChangelogIT extends PlanningITBase {

    /**
     * Der Kalendertag des Leitszenarios (Checkliste V-1 bis V-6): der Montag der kommenden Woche.
     *
     * <p>Bis zum 16.09. stand hier der 21.09.2026 fest. Ein fester Tag in der Vergangenheit traegt
     * nicht mehr: seitdem muss der Plan an ihm gelten, und sein Beginn darf nicht vor heute liegen.
     * Dieselbe Rechnung wie in {@code WeekIT}.</p>
     */
    private static final LocalDate MONTAG = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(1);

    /**
     * Beginn des Plans in den Tests mit Ausnahmen: heute. Ohne „Gueltig ab" begaenne der Plan ebenfalls
     * heute — der Stichtag steht hier trotzdem ausdruecklich, weil {@link #MONTAG} von ihm abhaengt.
     *
     * <p>Bis zum 16.09. stand hier der 01.09.2026, damit der Plan am 21.09. sicher galt. Seitdem ist ein
     * Beginn in der Vergangenheit 400 {@code validation}; stattdessen liegt {@link #MONTAG} jetzt
     * garantiert hinter dem Beginn.</p>
     */
    private static final LocalDate PLANBEGINN = LocalDate.now();

    @Test
    @DisplayName("Das Annehmen eines Plans schreibt genau einen Eintrag mit dem Verursacher")
    void acceptingADraftWritesOneEntry() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        assertThat(getJson("/api/changelog").get("total").asInt())
                .as("vor der ersten Annahme ist der Verlauf leer")
                .isZero();

        call(post("/api/draft/accept").content("{}"), 200);

        JsonNode page = getJson("/api/changelog");
        assertThat(page.get("total").asInt()).isEqualTo(1);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(50);

        JsonNode entry = page.get("items").get(0);
        assertThat(entry.get("action").asText()).isEqualTo("TIMETABLE_ACCEPTED");
        assertThat(entry.get("username").asText()).isEqualTo("admin");
        assertThat(entry.get("oldAssignment").asText()).isEqualTo("kein Plan");
        assertThat(entry.get("newAssignment").asText()).isEqualTo("Plan angenommen (7 Stunden)");
        assertThat(entry.get("timestamp").isNull()).isFalse();
        // Ohne „Gueltig ab" bleibt das Datum leer, und der Eintrag haengt an keiner einzelnen Stunde.
        assertThat(entry.get("date").isNull()).isTrue();
        assertThat(entry.get("position").isNull()).isTrue();
        assertThat(entry.get("classShort").isNull()).isTrue();
    }

    @Test
    @DisplayName("Kann (E-40): mit „Gueltig ab“ traegt der Eintrag den Stichtag")
    void validFromEndsUpInTheEntry() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG)), 200);

        JsonNode entry = getJson("/api/changelog").get("items").get(0);
        assertThat(entry.get("date").asText()).isEqualTo(MONTAG.toString());
    }

    @Test
    @DisplayName("Der Filter „from“ wirkt auf das Datum der Stunde, nicht auf den Zeitstempel")
    void fromFilterUsesTheLessonDate() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG)), 200);

        assertThat(getJson("/api/changelog?from=" + MONTAG.minusDays(1)).get("total").asInt()).isEqualTo(1);
        assertThat(getJson("/api/changelog?from=" + MONTAG.plusDays(1)).get("total").asInt()).isZero();
        assertThat(getJson("/api/changelog?to=" + MONTAG.minusDays(1)).get("total").asInt()).isZero();
    }

    /**
     * T-W7.1: der Verlauf ist unveraenderlich. Es gibt keine Route zum Loeschen und keine zum Aendern —
     * beide Anfragen finden nichts (404), nicht etwa „verboten" (403) oder „geht nicht" (405).
     */
    @Test
    @DisplayName("T-W7.1: es gibt weder DELETE noch PUT auf einen Eintrag")
    void thereIsNoWayToChangeOrDeleteAnEntry() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("{}"), 200);
        long id = getJson("/api/changelog").get("items").get(0).get("id").asLong();

        call(delete("/api/changelog/" + id), 404);
        call(put("/api/changelog/" + id).content("{}"), 404);
        // Auch die Liste selbst nimmt kein DELETE an.
        call(delete("/api/changelog"), 405);

        assertThat(getJson("/api/changelog").get("total").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Ohne Anmeldung bleibt der Verlauf verschlossen")
    void changelogNeedsAToken() throws Exception {
        // Die Rollentrennung Planung/Lehrkraft prueft auth.SecurityConfigTest an den Pfadregeln.
        mvc.perform(get("/api/changelog")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ Eintraege zu Ausnahmen (W-5/W-6)

    /**
     * <b>T-W7.1, Checkliste V-6.</b> Das Leitszenario: Meyer faellt am 21.09. aus („Krankmeldung") und
     * R101 ist gesperrt („Wasserschaden"); die 7a bekommt in der 1. Stunde Schulz in R204. Der Verlauf
     * zeigt zu dieser Klasse <b>genau einen</b> Eintrag, und der traegt <b>beide</b> Anlaesse.
     */
    @Test
    @DisplayName("T-W7.1: Filter Klasse 7a — genau ein Eintrag mit beiden Anlaessen")
    void tW7_1_filter7a_exactlyOneEntryWithBothReasons() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(PLANBEGINN)), 200);
        long stunde7aMo1 = lessonIdAt(getJson("/api/timetable"), daten.klasse7a, daten.slot("MO", 1));

        // Das Leitszenario ueber die Endpunkte: Ausfall Meyer, Sperrung R101, Vertretung Schulz/R204.
        long ausfall = ereignis("TEACHER_ABSENCE", "teacherId", daten.meyer, "Krankmeldung");
        long sperrung = ereignis("ROOM_BLOCKED", "roomId", daten.r101, "Wasserschaden");
        call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d,
                 "substituteRoomId":%d,"eventIds":[%d,%d]}"""
                .formatted(MONTAG, stunde7aMo1, daten.schulz, daten.r204, ausfall, sperrung)), 201);
        // Eine zweite Ausnahme derselben Woche, aber in der 8b — sie darf im Filter nicht auftauchen.
        call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"CANCEL","eventIds":[%d]}"""
                .formatted(MONTAG, lessonIdAt(getJson("/api/timetable"), daten.klasse8b,
                        daten.slot("MO", 2)), ausfall)), 201);

        JsonNode page = getJson("/api/changelog?classId=" + daten.klasse7a);
        assertThat(page.get("total").asInt()).isEqualTo(1);
        assertThat(page.get("items")).hasSize(1);

        JsonNode entry = page.get("items").get(0);
        assertThat(entry.get("action").asText()).isEqualTo("EXCEPTION_CREATED");
        assertThat(entry.get("date").asText()).isEqualTo(MONTAG.toString());
        assertThat(entry.get("position").asInt()).isEqualTo(1);
        assertThat(entry.get("classShort").asText()).isEqualTo("7a");
        assertThat(entry.get("oldAssignment").asText()).isEqualTo("Meyer / R101");
        assertThat(entry.get("newAssignment").asText()).isEqualTo("Schulz / R204");
        assertThat(entry.get("reasons").asText()).isEqualTo("Krankmeldung; Wasserschaden");
        assertThat(entry.get("username").asText()).isEqualTo("admin");
        assertThat(entry.get("timestamp").isNull()).isFalse();
        assertThat(entry.get("id").asLong()).isPositive();

        // Ohne Filter stehen alle drei Eintraege da, der neueste zuerst.
        JsonNode alle = getJson("/api/changelog");
        assertThat(alle.get("total").asInt()).isEqualTo(3);
        assertThat(alle.get("items").get(2).get("action").asText()).isEqualTo("TIMETABLE_ACCEPTED");
    }

    @Test
    @DisplayName("Die Ruecknahme einer Ausnahme legt einen zweiten Eintrag an — der erste bleibt stehen")
    void deletingAnExceptionAddsASecondEntry() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(PLANBEGINN)), 200);
        long stunde = lessonIdAt(getJson("/api/timetable"), daten.klasse7a, daten.slot("MO", 1));

        long ausfall = ereignis("TEACHER_ABSENCE", "teacherId", daten.meyer, "Krankmeldung");
        long ausnahme = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d,
                 "substituteRoomId":%d,"eventIds":[%d]}"""
                .formatted(MONTAG, stunde, daten.schulz, daten.r204, ausfall)), 201).get("id").asLong();
        mvc.perform(authorized(delete("/api/exceptions/" + ausnahme)))
                .andExpect(status().isNoContent());

        JsonNode page = getJson("/api/changelog?classId=" + daten.klasse7a);
        assertThat(page.get("total").asInt()).isEqualTo(2);
        assertThat(page.get("items").get(0).get("action").asText()).isEqualTo("EXCEPTION_DELETED");
        assertThat(page.get("items").get(1).get("action").asText()).isEqualTo("EXCEPTION_CREATED");
    }

    @Test
    @DisplayName("Die Filter Lehrkraft und Stunde greifen einzeln")
    void teacherAndLessonFilterWork() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(PLANBEGINN)), 200);
        JsonNode plan = getJson("/api/timetable");
        long mathe7aMo1 = lessonIdAt(plan, daten.klasse7a, daten.slot("MO", 1));
        long deutsch7aMo2 = lessonIdAt(plan, daten.klasse7a, daten.slot("MO", 2));

        long ausfallMeyer = ereignis("TEACHER_ABSENCE", "teacherId", daten.meyer, "Krankmeldung");
        long ausfallWeber = ereignis("TEACHER_ABSENCE", "teacherId", daten.weber, "Fortbildung");
        call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"CANCEL","eventIds":[%d]}"""
                .formatted(MONTAG, mathe7aMo1, ausfallMeyer)), 201);
        call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"CANCEL","eventIds":[%d]}"""
                .formatted(MONTAG, deutsch7aMo2, ausfallWeber)), 201);

        assertThat(getJson("/api/changelog?teacherId=" + daten.meyer).get("total").asInt()).isEqualTo(1);
        assertThat(getJson("/api/changelog?teacherId=" + daten.weber).get("total").asInt()).isEqualTo(1);
        assertThat(getJson("/api/changelog?teacherId=" + daten.schulz).get("total").asInt()).isZero();
        assertThat(getJson("/api/changelog?lessonId=" + deutsch7aMo2).get("items").get(0)
                .get("reasons").asText()).isEqualTo("Fortbildung");
        // Filter kombinieren: Klasse und Lehrkraft muessen beide passen.
        assertThat(getJson("/api/changelog?classId=" + daten.klasse8b + "&teacherId=" + daten.meyer)
                .get("total").asInt()).isZero();
    }

    /**
     * Paginierung: {@code total} zaehlt alle Treffer des Filters, {@code items} nur die Seite. Ohne die
     * feste Reihenfolge (Zeitstempel, dann Kennung) koennte derselbe Eintrag auf zwei Seiten stehen —
     * deshalb sammelt der Test alle Kennungen ein und prueft, dass keine doppelt vorkommt.
     */
    @Test
    @DisplayName("Seitenweise lesen: total zaehlt alles, jede Seite zeigt jeden Eintrag genau einmal")
    void pagingCountsAllAndRepeatsNothing() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(PLANBEGINN)), 200);
        long stunde = lessonIdAt(getJson("/api/timetable"), daten.klasse7a, daten.slot("MO", 1));

        // Fuenf Ausnahmen zu derselben Montagsstunde an fuenf aufeinanderfolgenden Montagen.
        for (int woche = 0; woche < 5; woche++) {
            call(post("/api/exceptions").content("""
                    {"date":"%s","lessonId":%d,"kind":"CANCEL"}"""
                    .formatted(MONTAG.plusWeeks(woche), stunde)), 201);
        }

        List<Long> gesehen = new ArrayList<>();
        for (int seite = 0; seite < 3; seite++) {
            JsonNode page = getJson("/api/changelog?classId=" + daten.klasse7a + "&page=" + seite + "&size=2");
            assertThat(page.get("total").asInt()).as("total ist auf jeder Seite gleich").isEqualTo(5);
            assertThat(page.get("page").asInt()).isEqualTo(seite);
            assertThat(page.get("size").asInt()).isEqualTo(2);
            page.get("items").forEach(item -> gesehen.add(item.get("id").asLong()));
        }
        assertThat(gesehen).hasSize(5).doesNotHaveDuplicates();

        // Eine Seite hinter dem Ende ist leer, aber kein Fehler.
        JsonNode leer = getJson("/api/changelog?classId=" + daten.klasse7a + "&page=9&size=2");
        assertThat(leer.get("items")).isEmpty();
        assertThat(leer.get("total").asInt()).isEqualTo(5);
    }

    /**
     * Der Vertrag verlangt <b>alle</b> Anlaesse. Sechs Ereignisse mit je 255 Zeichen sind laenger als
     * die 1000 Zeichen, die V4 der Spalte gab — seit Migration V6 steht dort {@code text}, und der
     * letzte Anlass steht vollstaendig da. Vorher fielen ganze spaetere Anlaesse weg.
     */
    @Test
    @DisplayName("Auch sechs lange Anlaesse stehen vollstaendig im Verlauf")
    void allReasonsSurviveEvenWhenTheyAreLong() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(PLANBEGINN)), 200);
        long stunde = lessonIdAt(getJson("/api/timetable"), daten.klasse7a, daten.slot("MO", 1));

        List<Long> anlaesse = new ArrayList<>();
        List<String> texte = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String text = ("Anlass " + i + " ").repeat(30).substring(0, 255);
            texte.add(text);
            anlaesse.add(ereignis("ROOM_BLOCKED", "roomId", daten.r101, text));
        }
        call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"CANCEL","eventIds":%s}"""
                .formatted(MONTAG, stunde, anlaesse)), 201);

        String reasons = getJson("/api/changelog?classId=" + daten.klasse7a)
                .get("items").get(0).get("reasons").asText();
        assertThat(reasons).isEqualTo(String.join("; ", texte));
        assertThat(reasons).hasSize(6 * 255 + 5 * 2);
    }

    /** Legt ein Ereignis mit einem Anlass an und liefert seine Kennung. */
    private long ereignis(String kind, String feld, long id, String reason) throws Exception {
        return call(post("/api/events").content("""
                {"kind":"%s","%s":%d,"dateFrom":"%s","dateTo":"%s","reason":"%s"}"""
                .formatted(kind, feld, id, MONTAG, MONTAG, reason)), 201).get("id").asLong();
    }
}
