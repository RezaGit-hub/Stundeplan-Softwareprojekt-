package de.wsp3_1.substitution;

import de.wsp3_1.planning.PlanningITBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Das Leitszenario „Ausfall und Vertretung" von Ende zu Ende gegen die echte Datenbank — die Schritte
 * V-1 bis V-6 und A-4/A-5 der Abnahme-Checkliste, Testfaelle T-W5.1, T-W6.1, T-W7.1 und T-A2.1.
 *
 * <p>Gearbeitet wird in der <b>laufenden</b> Woche, nicht am 21.09.2026: der Plan wird mit
 * {@code validFrom} auf ihren Montag angenommen, damit kein Testergebnis daran haengt, an welchem Tag
 * jemand die Tests startet (wie in {@code WeekIT}).</p>
 */
class SubstitutionServiceIT extends PlanningITBase {

    private static final LocalDate MONTAG = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1);
    private static final LocalDate FREITAG = MONTAG.plusDays(4);
    private static final LocalDate NAECHSTER_MONTAG = MONTAG.plusDays(7);

    // ------------------------------------------------------------------ V-1, T-W5.1

    @Test
    @DisplayName("V-1/T-W5.1: Ausfall Meyer — vier betroffene Stunden, vier NEEDS_SUBSTITUTION, Regelplan unveraendert")
    void tW5_1_absenceMarksFourLessons() throws Exception {
        Szenario szenario = planDerWoche();
        JsonNode plan = getJson("/api/timetable");

        JsonNode event = call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Krankmeldung"}"""
                .formatted(szenario.daten.meyer, MONTAG, MONTAG)), 201);

        assertThat(event.get("affected")).hasSize(4);
        assertThat(betroffen(event)).containsExactly("7a/1", "8b/2", "7a/4", "8b/5");
        assertThat(event.get("hint").isNull()).isTrue();

        JsonNode woche = getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL");
        assertThat(artenAm(woche, 0, "NEEDS_SUBSTITUTION")).hasSize(4);
        assertThat(artenAm(woche, 1, "NEEDS_SUBSTITUTION")).isEmpty();
        // Die Deutschstunde der 7a in der 2. Stunde gehoert Weber und bleibt REGULAR.
        assertThat(eintragBei(woche, 0, szenario.daten.klasse7a, 2).get("kind").asText())
                .isEqualTo("REGULAR");
        assertThat(getJson("/api/timetable").toString())
                .as("der Regelplan bleibt unveraendert").isEqualTo(plan.toString());
    }

    // ------------------------------------------------------------------ V-2 bis V-6, T-W6.1, T-W7.1

    @Test
    @DisplayName("T-W6.1: ueber Optionen zur Vertretung — Schulz/R204 statt Meyer/R101, ohne Konflikt")
    void tW6_1_assign_markedAsSubstitution_noConflict_regularPlanKeepsMeyerR101() throws Exception {
        Szenario szenario = leitszenarioBisV2();

        // V-2: nach der Raumsperrung sind es fuenf offene Stunden.
        JsonNode woche = getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL");
        assertThat(artenAm(woche, 0, "NEEDS_SUBSTITUTION")).hasSize(5);
        assertThat(reasons(eintragBei(woche, 0, szenario.daten.klasse7a, 1)))
                .containsExactly("Krankmeldung", "Wasserschaden");
        assertThat(reasons(eintragBei(woche, 0, szenario.daten.klasse7a, 2)))
                .containsExactly("Wasserschaden");

        // V-3: die erste offene Stunde ist die 1. Stunde der 7a; die Optionen dazu.
        JsonNode offen = getJson("/api/substitutions/pending?from=" + MONTAG + "&to=" + FREITAG);
        assertThat(offen).hasSize(5);
        assertThat(offen.get(0).get("classShort").asText()).isEqualTo("7a");
        assertThat(offen.get(0).get("position").asInt()).isEqualTo(1);
        assertThat(offen.get(0).get("lessonId").asLong()).isEqualTo(szenario.stunde7aMo1);
        assertThat(namen(offen.get(0).get("reasons"))).containsExactly("Krankmeldung", "Wasserschaden");

        JsonNode optionen = getJson("/api/substitutions/options?date=" + MONTAG
                + "&lessonId=" + szenario.stunde7aMo1);
        assertThat(namen(optionen.get("teachers"), "name")).containsExactly("Schulz", "Weber");
        assertThat(optionen.get("teachers").get(0).get("teachesSubject").asBoolean()).isTrue();
        assertThat(ids(optionen.get("teachers"))).doesNotContain(szenario.daten.meyer);
        assertThat(ids(optionen.get("rooms"))).contains(szenario.daten.r204)
                .doesNotContain(szenario.daten.r101);

        // V-4: Schulz und R204 zuweisen — ein Arbeitsgang, eine Zeile.
        JsonNode ausnahme = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d,
                 "substituteRoomId":%d,"eventIds":[%d,%d]}"""
                .formatted(MONTAG, szenario.stunde7aMo1, szenario.daten.schulz, szenario.daten.r204,
                        szenario.ausfallEvent, szenario.sperrungEvent)), 201);
        assertThat(ausnahme.get("conflicts")).isEmpty();

        JsonNode danach = eintragBei(getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL"), 0,
                szenario.daten.klasse7a, 1);
        assertThat(danach.get("kind").asText()).isEqualTo("SUBSTITUTED");
        assertThat(danach.get("teacherShort").asText()).isEqualTo("SCZ");
        assertThat(danach.get("roomShort").asText()).isEqualTo("R204");
        assertThat(danach.get("original").get("teacherShort").asText()).isEqualTo("MEY");
        assertThat(danach.get("original").get("roomShort").asText()).isEqualTo("R101");

        assertThat(getJson("/api/substitutions/pending?from=" + MONTAG + "&to=" + FREITAG)).hasSize(4);

        // V-4 Ende: der Regelplan kennt weiter Meyer in R101.
        JsonNode stunde = stundeDesPlans(getJson("/api/timetable"), szenario.stunde7aMo1);
        assertThat(stunde.get("teacherShort").asText()).isEqualTo("MEY");
        assertThat(stunde.get("roomShort").asText()).isEqualTo("R101");

        // V-5: eine Woche spaeter steht wieder der Regelplan.
        JsonNode spaeter = getJson("/api/timetable/week?date=" + NAECHSTER_MONTAG
                + "&view=CLASS&id=" + szenario.daten.klasse7a);
        JsonNode ersteStunde = spaeter.get("days").get(0).get("entries").get(0);
        assertThat(ersteStunde.get("kind").asText()).isEqualTo("REGULAR");
        assertThat(ersteStunde.get("teacherShort").asText()).isEqualTo("MEY");
        assertThat(ersteStunde.get("roomShort").asText()).isEqualTo("R101");
    }

    @Test
    @DisplayName("V-6/T-W7.1: die Vertretung steht mit beiden Anlaessen im Verlauf")
    void tW7_1_changelogHasOneEntryWithBothReasons() throws Exception {
        Szenario szenario = leitszenarioMitVertretung();

        JsonNode seite = getJson("/api/changelog?classId=" + szenario.daten.klasse7a);

        assertThat(seite.get("total").asInt()).isEqualTo(1);
        JsonNode eintrag = seite.get("items").get(0);
        assertThat(eintrag.get("action").asText()).isEqualTo("EXCEPTION_CREATED");
        assertThat(eintrag.get("date").asText()).isEqualTo(MONTAG.toString());
        assertThat(eintrag.get("position").asInt()).isEqualTo(1);
        assertThat(eintrag.get("classShort").asText()).isEqualTo("7a");
        assertThat(eintrag.get("oldAssignment").asText()).isEqualTo("Meyer / R101");
        assertThat(eintrag.get("newAssignment").asText()).isEqualTo("Schulz / R204");
        assertThat(eintrag.get("reasons").asText()).isEqualTo("Krankmeldung; Wasserschaden");
        assertThat(eintrag.get("username").asText()).isEqualTo("admin");
        assertThat(eintrag.get("timestamp").isNull()).isFalse();
    }

    @Test
    @DisplayName("Eine zweite Ausnahme fuer dieselbe Stunde am selben Tag ist 409 exception-exists")
    void secondExceptionOnTheSameDayIsRejected() throws Exception {
        Szenario szenario = leitszenarioMitVertretung();

        JsonNode problem = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, szenario.daten.weber)), 409);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:exception-exists");
        assertThat(problem.get("detail").asText()).contains("schon eine Ausnahme");
    }

    @Test
    @DisplayName("Aufheben: die Stunde ist wieder offen und der Verlauf haelt EXCEPTION_DELETED fest")
    void deletingAnExceptionMakesTheLessonPendingAgain() throws Exception {
        Szenario szenario = leitszenarioMitVertretung();

        mvc.perform(authorized(delete("/api/exceptions/" + szenario.ausnahme)))
                .andExpect(status().isNoContent());

        JsonNode eintrag = eintragBei(getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL"), 0,
                szenario.daten.klasse7a, 1);
        assertThat(eintrag.get("kind").asText()).isEqualTo("NEEDS_SUBSTITUTION");
        assertThat(eintrag.get("teacherShort").asText()).isEqualTo("MEY");
        assertThat(eintrag.get("exceptionId").isNull()).isTrue();

        JsonNode seite = getJson("/api/changelog?classId=" + szenario.daten.klasse7a);
        assertThat(seite.get("total").asInt()).isEqualTo(2);
        JsonNode neuester = seite.get("items").get(0);
        assertThat(neuester.get("action").asText()).isEqualTo("EXCEPTION_DELETED");
        assertThat(neuester.get("oldAssignment").asText()).isEqualTo("Schulz / R204");
        assertThat(neuester.get("newAssignment").asText()).isEqualTo("Meyer / R101");
    }

    @Test
    @DisplayName("Ohne eigene Angabe uebernimmt der Server die Anlaesse der Ereignisse des Tages")
    void eventIdsAreTakenFromTheDayWhenNotGiven() throws Exception {
        Szenario szenario = leitszenarioBisV2();

        JsonNode ausnahme = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d,
                 "substituteRoomId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, szenario.daten.schulz, szenario.daten.r204)),
                201);

        assertThat(zahlen(ausnahme.get("eventIds")))
                .containsExactlyInAnyOrder(szenario.ausfallEvent, szenario.sperrungEvent);
    }

    // ------------------------------------------------------------------ A-4, A-5, T-A2.1

    @Test
    @DisplayName("A-4/A-5/T-A2.1: Schulz sieht SUBSTITUTED, Meyer GIVEN_AWAY — am naechsten Montag wieder REGULAR")
    void tA2_1_ownWeekShowsSubstitutedAndGivenAway() throws Exception {
        Szenario szenario = leitszenarioMitVertretung();
        String schulzToken = lehrkraftkonto("schulz", szenario.daten.schulz);
        String meyerToken = lehrkraftkonto("meyer", szenario.daten.meyer);

        JsonNode beiSchulz = alsLehrkraft(schulzToken, "/api/me/timetable/week?date=" + MONTAG);
        JsonNode uebernommen = beiSchulz.get("days").get(0).get("entries").get(0);
        assertThat(beiSchulz.get("days").get(0).get("entries")).hasSize(1);
        assertThat(uebernommen.get("kind").asText()).isEqualTo("SUBSTITUTED");
        assertThat(uebernommen.get("classShort").asText()).isEqualTo("7a");
        assertThat(uebernommen.get("roomShort").asText()).isEqualTo("R204");
        assertThat(namen(uebernommen.get("reasons")))
                .containsExactly("Krankmeldung", "Wasserschaden");
        assertThat(uebernommen.get("original").get("teacherShort").asText()).isEqualTo("MEY");

        JsonNode beiMeyer = alsLehrkraft(meyerToken, "/api/me/timetable/week?date=" + MONTAG);
        List<JsonNode> montag = eintraege(beiMeyer, 0);
        assertThat(montag).hasSize(4);
        assertThat(montag.getFirst().get("kind").asText()).isEqualTo("GIVEN_AWAY");
        assertThat(montag.getFirst().get("teacherShort").asText()).isEqualTo("SCZ");
        assertThat(montag.getFirst().get("original").get("teacherShort").asText()).isEqualTo("MEY");
        assertThat(montag.subList(1, 4)).allSatisfy(entry ->
                assertThat(entry.get("kind").asText()).isEqualTo("NEEDS_SUBSTITUTION"));

        // Eine Woche spaeter sehen beide Konten nur noch den Regelplan.
        assertThat(eintraege(alsLehrkraft(schulzToken,
                "/api/me/timetable/week?date=" + NAECHSTER_MONTAG), 0)).isEmpty();
        assertThat(eintraege(alsLehrkraft(meyerToken,
                "/api/me/timetable/week?date=" + NAECHSTER_MONTAG), 0))
                .allSatisfy(entry -> assertThat(entry.get("kind").asText()).isEqualTo("REGULAR"));
    }

    // ------------------------------------------------------------------ Verlegung und Entfall

    @Test
    @DisplayName("Verlegung: am Ursprungstag CANCELLED, am Zieltag MOVED; der Verlauf nennt das Ziel")
    void moveShowsCancelledAndMoved() throws Exception {
        Szenario szenario = planDerWoche();
        long ziel = szenario.daten.slot("WE", 1);

        JsonNode ausnahme = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"MOVE","newDate":"%s","newTimeslotId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, MONTAG.plusDays(2), ziel)), 201);
        assertThat(ausnahme.get("kind").asText()).isEqualTo("MOVE");

        JsonNode woche = getJson("/api/timetable/week?date=" + MONTAG + "&view=CLASS&id="
                + szenario.daten.klasse7a);
        JsonNode ursprung = eintragBei(woche, 0, szenario.daten.klasse7a, 1);
        assertThat(ursprung.get("kind").asText()).isEqualTo("CANCELLED");
        assertThat(namen(ursprung.get("reasons"))).containsExactly("verlegt → Mi 1.");

        List<JsonNode> mittwoch = eintraege(woche, 2);
        assertThat(mittwoch).hasSize(1);
        assertThat(mittwoch.getFirst().get("kind").asText()).isEqualTo("MOVED");
        assertThat(mittwoch.getFirst().get("position").asInt()).isEqualTo(1);
        assertThat(mittwoch.getFirst().get("original").get("date").asText()).isEqualTo(MONTAG.toString());

        assertThat(getJson("/api/changelog?lessonId=" + szenario.stunde7aMo1)
                .get("items").get(0).get("newAssignment").asText()).isEqualTo("Mi, 1. Stunde");
    }

    /**
     * Alte Ausnahmen verfallen mit ihrem Plan. Wird zwischen Ursprungs- und Zieltag ein neuer Plan
     * angenommen, darf die verlegte Stunde am Zieltag nicht wieder auftauchen — dort gilt ein anderer
     * Plan, und die Stunde des abgelösten wäre ein Wiedergänger.
     */
    @Test
    @DisplayName("Ein Planwechsel zwischen Ursprungs- und Zieltag blendet die verlegte Stunde nicht zurueck")
    void aMoveDoesNotResurrectASupersededPlan() throws Exception {
        Szenario szenario = planDerWoche();
        call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"MOVE","newDate":"%s","newTimeslotId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, MONTAG.plusDays(2),
                        szenario.daten.slot("WE", 1))), 201);

        // Ein neuer Plan ab Dienstag: am Mittwoch gilt er, am Montag noch der alte.
        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG.plusDays(1))), 200);

        JsonNode woche = getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL");
        assertThat(eintragBei(woche, 0, szenario.daten.klasse7a, 1).get("kind").asText())
                .as("am Montag gilt noch der alte Plan").isEqualTo("CANCELLED");
        assertThat(eintraege(woche, 2))
                .as("am Mittwoch gilt der neue Plan — die alte Stunde steht dort nicht").isEmpty();
    }

    @Test
    @DisplayName("Entfall: die Stunde bleibt sichtbar, der Verlauf sagt „entfällt“")
    void cancelKeepsTheLessonVisible() throws Exception {
        Szenario szenario = planDerWoche();

        call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"CANCEL"}"""
                .formatted(MONTAG, szenario.stunde7aMo1)), 201);

        assertThat(eintragBei(getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL"), 0,
                szenario.daten.klasse7a, 1).get("kind").asText()).isEqualTo("CANCELLED");
        assertThat(getJson("/api/changelog?lessonId=" + szenario.stunde7aMo1)
                .get("items").get(0).get("newAssignment").asText()).isEqualTo("entfällt");
    }

    @Test
    @DisplayName("Eine Vertretung, die eine Lehrkraft doppelt belegt, wird gespeichert und als Konflikt gemeldet")
    void substitutionWithADoubleBookingIsSavedAndReported() throws Exception {
        Szenario szenario = planDerWoche();
        // Weber unterrichtet montags in der 2. Stunde die 7a; wer ihn zusaetzlich in die 2. Stunde der
        // 8b setzt, belegt ihn doppelt.
        long stunde8bMo2 = stundeDerKlasse(szenario.daten.klasse8b, szenario.daten.slot("MO", 2));

        JsonNode ausnahme = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d}"""
                .formatted(MONTAG, stunde8bMo2, szenario.daten.weber)), 201);

        assertThat(ausnahme.get("conflicts")).isNotEmpty();
        assertThat(ausnahme.get("conflicts").get(0).get("type").asText())
                .isEqualTo("TEACHER_DOUBLE_BOOKED");
        assertThat(ausnahme.get("conflicts").get(0).get("message").asText()).contains("Weber");
        // Gespeichert wird trotzdem: Konflikte sind Daten, kein Fehler.
        assertThat(getJson("/api/exceptions?from=" + MONTAG + "&to=" + MONTAG)).hasSize(1);
    }

    // ------------------------------------------------------------------ Pruefungen beim Anlegen (05.09.)

    /**
     * Der Fall aus der Pruefung: Schulz vertritt die 7a, danach faellt Schulz aus. Die Stunde muss
     * wieder offen sein — sonst stuende ein Vertretungsplan mit abwesender Lehrkraft da.
     */
    @Test
    @DisplayName("Faellt die Vertretungslehrkraft danach aus, ist die Stunde wieder offen")
    void absenceOfTheSubstituteReopensTheLesson() throws Exception {
        Szenario szenario = leitszenarioMitVertretung();

        JsonNode ereignis = call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Fortbildung"}""".formatted(szenario.daten.schulz, MONTAG, MONTAG)), 201);
        assertThat(betroffen(ereignis)).containsExactly("7a/1");

        JsonNode eintrag = eintragBei(getJson("/api/timetable/week?date=" + MONTAG + "&view=SCHOOL"), 0,
                szenario.daten.klasse7a, 1);
        assertThat(eintrag.get("kind").asText()).isEqualTo("NEEDS_SUBSTITUTION");
        assertThat(namen(eintrag.get("reasons"))).contains("Fortbildung");
        // Die Ausnahme bleibt an der Zelle — die Planung hebt sie auf und traegt eine neue ein.
        assertThat(eintrag.get("exceptionId").asLong()).isEqualTo(szenario.ausnahme);

        JsonNode offen = getJson("/api/substitutions/pending?from=" + MONTAG + "&to=" + FREITAG);
        assertThat(offen).hasSize(5);
        assertThat(offen.get(0).get("lessonId").asLong()).isEqualTo(szenario.stunde7aMo1);

        // Und in „Mein Plan" von Schulz steht sie nicht mehr als eigene Stunde.
        String schulzToken = lehrkraftkonto("schulz", szenario.daten.schulz);
        JsonNode beiSchulz = alsLehrkraft(schulzToken, "/api/me/timetable/week?date=" + MONTAG);
        assertThat(eintraege(beiSchulz, 0)).allSatisfy(entry ->
                assertThat(entry.get("kind").asText()).isNotEqualTo("SUBSTITUTED"));
    }

    @Test
    @DisplayName("Eine Vertretung durch eine Lehrkraft, die an dem Tag ausfaellt, ist 400")
    void anAbsentSubstituteIsRejected() throws Exception {
        Szenario szenario = leitszenarioBisV2();
        call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Fortbildung"}""".formatted(szenario.daten.schulz, MONTAG, MONTAG)), 201);

        JsonNode problem = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, szenario.daten.schulz)), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(feld(problem, "substituteTeacherId")).isEqualTo("ist an diesem Tag nicht verfügbar");
    }

    @Test
    @DisplayName("Ein gesperrter Raum als Vertretungsraum ist 400")
    void aBlockedSubstituteRoomIsRejected() throws Exception {
        Szenario szenario = leitszenarioBisV2();

        JsonNode problem = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d,
                 "substituteRoomId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, szenario.daten.schulz, szenario.daten.r101)),
                400);

        assertThat(feld(problem, "substituteRoomId")).isEqualTo("ist an diesem Tag nicht verfügbar");
    }

    @Test
    @DisplayName("Eine Vertretung, die nichts aendert, ist 400")
    void aSubstitutionThatChangesNothingIsRejected() throws Exception {
        Szenario szenario = leitszenarioBisV2();

        JsonNode problem = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, szenario.daten.meyer)), 400);

        assertThat(feld(problem, "substituteTeacherId")).isEqualTo("Lehrkraft oder Raum muss sich ändern");
    }

    /**
     * Unbekannte Kennungen liefen frueher in den Fremdschluessel und ergaben 409 {@code in-use}
     * („wird noch verwendet und kann nicht entfernt werden") — fuer eine Neuanlage die falsche Meldung.
     */
    @Test
    @DisplayName("Unbekannte Kennungen im Rumpf sind 400 mit dem Feldnamen, nicht 409")
    void unknownReferencesAreValidationErrors() throws Exception {
        Szenario szenario = leitszenarioBisV2();
        long fehlt = 999_999L;

        assertThat(feld(call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, fehlt)), 400), "substituteTeacherId"))
                .isEqualTo("Lehrkraft fehlt");

        assertThat(feld(call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteRoomId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, fehlt)), 400), "substituteRoomId"))
                .isEqualTo("Raum fehlt");

        assertThat(feld(call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"MOVE","newDate":"%s","newTimeslotId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, MONTAG.plusDays(2), fehlt)), 400),
                "newTimeslotId")).isEqualTo("Zeitfenster fehlt");

        assertThat(feld(call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"CANCEL","eventIds":[%d]}"""
                .formatted(MONTAG, szenario.stunde7aMo1, fehlt)), 400), "eventIds"))
                .isEqualTo("Ereignis fehlt");
    }

    @Test
    @DisplayName("Eine Ausnahme fuer einen Tag, an dem die Stunde nicht liegt, ist 404")
    void anExceptionForAWrongWeekdayIsNotFound() throws Exception {
        Szenario szenario = planDerWoche();

        // Die Stunde liegt am Montag; der Dienstag hat sie nicht.
        JsonNode problem = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"CANCEL"}"""
                .formatted(MONTAG.plusDays(1), szenario.stunde7aMo1)), 404);
        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:not-found");
        assertThat(getJson("/api/exceptions?from=" + MONTAG + "&to=" + FREITAG)).isEmpty();
    }

    @Test
    @DisplayName("An einem Ferientag gibt es weder Vorschlaege noch eine Ausnahme")
    void holidaysHaveNoOptionsAndNoExceptions() throws Exception {
        Szenario szenario = planDerWoche();
        call(post("/api/holidays").content("""
                {"name":"Beweglicher Ferientag","dateFrom":"%s","dateTo":"%s","kind":"VACATION"}"""
                .formatted(MONTAG, MONTAG)), 201);

        call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"CANCEL"}"""
                .formatted(MONTAG, szenario.stunde7aMo1)), 404);
        call(get("/api/substitutions/options?date=" + MONTAG + "&lessonId=" + szenario.stunde7aMo1), 404);
    }

    @Test
    @DisplayName("Bei einer Verlegung muss das Zielzeitfenster zum Wochentag von newDate passen")
    void moveTargetSlotMustMatchTheTargetWeekday() throws Exception {
        Szenario szenario = planDerWoche();

        // Mittwoch als Zieltag, aber ein Zeitfenster des Dienstags.
        JsonNode problem = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"MOVE","newDate":"%s","newTimeslotId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, MONTAG.plusDays(2),
                        szenario.daten.slot("TU", 1))), 400);

        assertThat(feld(problem, "newTimeslotId")).isEqualTo("passt nicht zum Wochentag von „newDate“");
    }

    @Test
    @DisplayName("Das Aufheben einer Verlegung nennt im Verlauf das aufgehobene Ziel")
    void deletingAMoveNamesTheTargetInTheChangelog() throws Exception {
        Szenario szenario = planDerWoche();
        long ausnahme = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"MOVE","newDate":"%s","newTimeslotId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, MONTAG.plusDays(2),
                        szenario.daten.slot("WE", 1))), 201).get("id").asLong();

        mvc.perform(authorized(delete("/api/exceptions/" + ausnahme)))
                .andExpect(status().isNoContent());

        JsonNode eintrag = getJson("/api/changelog?lessonId=" + szenario.stunde7aMo1).get("items").get(0);
        assertThat(eintrag.get("action").asText()).isEqualTo("EXCEPTION_DELETED");
        assertThat(eintrag.get("oldAssignment").asText()).isEqualTo("Mi, 1. Stunde");
        assertThat(eintrag.get("newAssignment").asText()).isEqualTo("Meyer / R101");
    }

    @Test
    @DisplayName("Eine Zusatzstunde laesst sich nicht ueber DELETE /api/exceptions zuruecknehmen")
    void anAddedLessonIsOnlyRemovedWithItsEvent() throws Exception {
        Szenario szenario = planDerWoche();
        long ereignis = call(post("/api/events").content("""
                {"kind":"EXTRA_LESSON","dateFrom":"%s","dateTo":"%s","reason":"Nachholstunde",
                 "extra":{"date":"%s","timeslotId":%d,"classId":%d,"subjectId":%d,
                          "teacherId":%d,"roomId":%d}}"""
                .formatted(MONTAG.plusDays(2), MONTAG.plusDays(2), MONTAG.plusDays(2),
                        szenario.daten.slot("WE", 1), szenario.daten.klasse9c, szenario.daten.mathe,
                        szenario.daten.schulz, szenario.daten.r204)), 201).get("id").asLong();
        long ausnahme = getJson("/api/exceptions?from=" + MONTAG + "&to=" + FREITAG)
                .get(0).get("id").asLong();

        JsonNode problem = call(delete("/api/exceptions/" + ausnahme), 400);
        assertThat(feld(problem, "id"))
                .isEqualTo("Zusatzstunden werden über DELETE /api/events/{id} zurückgenommen");
        // Das Ereignis behaelt seine Zusatzstunde.
        assertThat(getJson("/api/events").get(0).get("extra").isNull()).isFalse();

        mvc.perform(authorized(delete("/api/events/" + ereignis))).andExpect(status().isNoContent());
        assertThat(getJson("/api/exceptions?from=" + MONTAG + "&to=" + FREITAG)).isEmpty();
    }

    /**
     * Zusatzstunden gehen in die Folgekonfliktpruefung mit ein. Sie haben keine Stundenkennung; in der
     * Antwort steht deshalb nur die betroffene Regelstunde, den Rest sagen Meldung und {@code objects}.
     */
    @Test
    @DisplayName("Eine Vertretung, die eine Zusatzstunde doppelt belegt, meldet den Konflikt")
    void aSubstitutionClashingWithAnExtraLessonIsReported() throws Exception {
        Szenario szenario = planDerWoche();
        // Zusatzstunde der 9c am Montag in der 1. Stunde bei Schulz.
        call(post("/api/events").content("""
                {"kind":"EXTRA_LESSON","dateFrom":"%s","dateTo":"%s","reason":"Nachholstunde",
                 "extra":{"date":"%s","timeslotId":%d,"classId":%d,"subjectId":%d,
                          "teacherId":%d,"roomId":%d}}"""
                .formatted(MONTAG, MONTAG, MONTAG, szenario.daten.slot("MO", 1), szenario.daten.klasse9c,
                        szenario.daten.mathe, szenario.daten.schulz, szenario.daten.r204)), 201);

        // Genau dieselbe Stunde jetzt auch Schulz geben: Schulz waere doppelt belegt.
        JsonNode ausnahme = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d}"""
                .formatted(MONTAG, szenario.stunde7aMo1, szenario.daten.schulz)), 201);

        assertThat(ausnahme.get("conflicts")).isNotEmpty();
        JsonNode konflikt = ausnahme.get("conflicts").get(0);
        assertThat(konflikt.get("type").asText()).isEqualTo("TEACHER_DOUBLE_BOOKED");
        assertThat(konflikt.get("message").asText()).contains("Schulz");
        // Nach aussen geht nur die echte Stundenkennung — die Zusatzstunde hat keine.
        assertThat(zahlen(konflikt.get("lessonIds"))).containsExactly(szenario.stunde7aMo1);
    }

    // ------------------------------------------------------------------ Szenario und Helfer

    /** Grunddaten, Regelplan Montag/Dienstag, angenommen mit Gueltigkeit ab dem Montag dieser Woche. */
    private Szenario planDerWoche() throws Exception {
        Szenario szenario = new Szenario();
        szenario.daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(szenario.daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(MONTAG)), 200);
        szenario.stunde7aMo1 = stundeDerKlasse(szenario.daten.klasse7a, szenario.daten.slot("MO", 1));
        return szenario;
    }

    /** V-1 und V-2: Ausfall Meyer und Sperrung R101, beide am Montag. */
    private Szenario leitszenarioBisV2() throws Exception {
        Szenario szenario = planDerWoche();
        szenario.ausfallEvent = call(post("/api/events").content("""
                {"kind":"TEACHER_ABSENCE","teacherId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Krankmeldung"}"""
                .formatted(szenario.daten.meyer, MONTAG, MONTAG)), 201).get("id").asLong();
        JsonNode sperrung = call(post("/api/events").content("""
                {"kind":"ROOM_BLOCKED","roomId":%d,"dateFrom":"%s","dateTo":"%s",
                 "reason":"Wasserschaden"}"""
                .formatted(szenario.daten.r101, MONTAG, MONTAG)), 201);
        assertThat(betroffen(sperrung)).containsExactly("7a/1", "7a/2");
        szenario.sperrungEvent = sperrung.get("id").asLong();
        return szenario;
    }

    /** V-1 bis V-4: dazu die Vertretung durch Schulz in R204. */
    private Szenario leitszenarioMitVertretung() throws Exception {
        Szenario szenario = leitszenarioBisV2();
        szenario.ausnahme = call(post("/api/exceptions").content("""
                {"date":"%s","lessonId":%d,"kind":"SUBSTITUTION","substituteTeacherId":%d,
                 "substituteRoomId":%d,"eventIds":[%d,%d]}"""
                .formatted(MONTAG, szenario.stunde7aMo1, szenario.daten.schulz, szenario.daten.r204,
                        szenario.ausfallEvent, szenario.sperrungEvent)), 201).get("id").asLong();
        return szenario;
    }

    /** Die Kennungen dieses Durchlaufs. Nie geraten — immer aus den Antworten gelesen. */
    private static final class Szenario {
        private Testdaten daten;
        private long stunde7aMo1;
        private long ausfallEvent;
        private long sperrungEvent;
        private long ausnahme;
    }

    private long stundeDerKlasse(long classId, long timeslotId) throws Exception {
        for (JsonNode lesson : getJson("/api/timetable").get("lessons")) {
            if (lesson.get("classId").asLong() == classId
                    && !lesson.get("timeslotId").isNull()
                    && lesson.get("timeslotId").asLong() == timeslotId) {
                return lesson.get("id").asLong();
            }
        }
        throw new IllegalStateException("Stunde nicht gefunden: Klasse " + classId
                + ", Zeitfenster " + timeslotId);
    }

    private static JsonNode stundeDesPlans(JsonNode plan, long lessonId) {
        for (JsonNode lesson : plan.get("lessons")) {
            if (lesson.get("id").asLong() == lessonId) {
                return lesson;
            }
        }
        throw new IllegalStateException("Stunde " + lessonId + " fehlt im Plan.");
    }

    /** „7a/1" — Klasse und Position je betroffener Stunde, in der Reihenfolge der Antwort. */
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

    private static List<JsonNode> artenAm(JsonNode week, int dayIndex, String kind) {
        return eintraege(week, dayIndex).stream()
                .filter(entry -> entry.get("kind").asText().equals(kind))
                .toList();
    }

    private static JsonNode eintragBei(JsonNode week, int dayIndex, long classId, int position) {
        return eintraege(week, dayIndex).stream()
                .filter(entry -> entry.get("classId").asLong() == classId
                        && entry.get("position").asInt() == position)
                .findFirst().orElseThrow();
    }

    private static List<String> reasons(JsonNode entry) {
        return namen(entry.get("reasons"));
    }

    private static List<String> namen(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static List<String> namen(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.get(field).asText()));
        return values;
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> values = new ArrayList<>();
        array.forEach(value -> values.add(value.get("id").asLong()));
        return values;
    }

    private static List<Long> zahlen(JsonNode array) {
        List<Long> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asLong()));
        return values;
    }

    /** Die Meldung zu einem Feld aus einer 400-Antwort; leer, wenn das Feld nicht vorkommt. */
    private static String feld(JsonNode problem, String field) {
        for (JsonNode error : problem.get("errors")) {
            if (error.get("field").asText().equals(field)) {
                return error.get("message").asText();
            }
        }
        return "";
    }

    /** Legt ein Konto fuer eine Lehrkraft an und meldet es an; zurueck kommt dessen Token. */
    private String lehrkraftkonto(String benutzername, long teacherId) throws Exception {
        call(post("/api/users").content("""
                {"username":"%s","password":"geheim-2026","role":"TEACHER","teacherId":%d}"""
                .formatted(benutzername, teacherId)), 201);
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"geheim-2026"}""".formatted(benutzername)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private JsonNode alsLehrkraft(String token, String path) throws Exception {
        String body = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }
}
