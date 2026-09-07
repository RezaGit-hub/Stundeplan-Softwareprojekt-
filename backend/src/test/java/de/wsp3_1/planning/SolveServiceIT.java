package de.wsp3_1.planning;

import de.wsp3_1.sampledata.domain.SampleDataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Die Berechnung eines Vorschlags gegen den echten Solver und die echte Datenbank (W-8, W-9):
 * Vorbedingungen, Lauf, Ergebnis, Festlegung. Rechenzeit je Lauf sind hier zwei Sekunden statt der
 * voreingestellten sechzig — der Wert kommt aus {@code GET /api/config} und ist genau deshalb Daten.
 */
class SolveServiceIT extends PlanningITBase {

    /**
     * Reserve auf die Rechenzeit, bevor der Test aufgibt. Die Checkliste (S-2) erwartet im Betrieb
     * „Rechenzeit + 5 s"; hier steht mehr, weil der erste Lauf eines Testdurchgangs noch den
     * Score-Director baut und die JVM nichts uebersetzt hat. Wer die Zahl bewusst klein haelt, bekommt
     * keinen schaerferen Test, sondern einen unzuverlaessigen.
     */
    private static final int WAIT_RESERVE_SECONDS = 20;

    /** Rechenzeit fuer den vollen Bestand (360 Stunden) — gemessen: 0 harte Konflikte (VERTRAG, solver). */
    private static final int FULL_SOLVE_SECONDS = 10;

    @Autowired
    private SampleDataLoader sampleData;

    @Test
    @DisplayName("Vorbedingung: eine aktive Klasse ohne Fach verhindert den Lauf (409 master-data-incomplete)")
    void classWithoutSubjectsBlocksTheRun() throws Exception {
        pflichtenheftGrunddaten();
        postId("/api/classes", """
                {"name":"10d","grade":10,"active":true}""");
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);

        JsonNode problem = call(post("/api/draft/solve").content("{}"), 409);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:master-data-incomplete");
        assertThat(problem.get("detail").asText()).isEqualTo("Vor der Berechnung fehlen Angaben.");
        assertThat(problem.get("missing").toString()).contains("Klasse 10d hat kein Fach");
    }

    @Test
    @DisplayName("Vorbedingung: widerspruechliche festgelegte Stunden verhindern den Lauf (409 pinned-contradiction)")
    void contradictingPinnedLessonsBlockTheRun() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        addLesson(daten.klasse9c, daten.mathe, daten.slot("MO", 1), daten.r101);

        JsonNode problem = call(post("/api/draft/solve").content("{}"), 409);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:pinned-contradiction");
        assertThat(problem.get("detail").asText())
                .isEqualTo("Festgelegte Stunden stehen im Konflikt. Bitte auflösen oder Festlegung aufheben.");
        assertThat(problem.get("conflicts")).hasSize(1);
        assertThat(problem.get("conflicts").get(0).get("type").asText()).isEqualTo("ROOM_DOUBLE_BOOKED");
    }

    @Test
    @DisplayName("Kann (E-40): eine unbekannte Klasse in classId ist 404")
    void unknownClassIdIs404() throws Exception {
        pflichtenheftGrunddaten();
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);

        call(post("/api/draft/solve").content("""
                {"classId":999999}"""), 404);
    }

    @Test
    @DisplayName("T-W8.1: jede Stunde ist belegt oder offen, keine Doppelbelegung, aktiver Plan bleibt leer bis zum Annehmen")
    void tW8_1_everyLessonAssignedOrOpen_noDoubleBooking_activePlanEmptyUntilAccept() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        setMaxSolveSeconds(2);
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);

        call(post("/api/draft/solve").content("{}"), 202);
        JsonNode draft = waitForRunToFinish(2);

        // syncRequiredLessons hat genau die Differenz ergaenzt: 4 + 3 + 1 Wochenstunden.
        assertThat(draft.get("lessons")).hasSize(8);
        assertThat(draft.get("status").asText()).isEqualTo("EDITING");
        assertThat(draft.get("solve").get("lastScore").isNull()).isFalse();
        assertThat(draft.get("solve").get("limitSeconds").asInt()).isEqualTo(2);
        assertThat(draft.get("solve").get("error").isNull()).isTrue();

        for (JsonNode lesson : draft.get("lessons")) {
            assertThat(lesson.get("timeslotId").isNull())
                    .as("belegt oder offen, kein dritter Zustand")
                    .isEqualTo(lesson.get("roomId").isNull());
        }
        assertThat(typesOf(draft))
                .doesNotContain("ROOM_DOUBLE_BOOKED", "TEACHER_DOUBLE_BOOKED", "CLASS_DOUBLE_BOOKED");

        // S-3: der aktive Plan bleibt leer, solange nicht angenommen wurde.
        assertThat(getJson("/api/timetable").get("lessons")).isEmpty();

        // S-4: nach dem Annehmen stehen genau die Stunden des Entwurfs im aktiven Plan.
        JsonNode accepted = call(post("/api/draft/accept").content("{}"), 200);
        assertThat(accepted.get("lessons")).hasSize(draft.get("lessons").size());
        assertThat(getJson("/api/timetable").get("lessons")).hasSize(draft.get("lessons").size());
    }

    @Test
    @DisplayName("T-W9.1: eine festgelegte Stunde ueberlebt die Neuberechnung; pinned:false hebt das auf")
    void tW9_1_pinnedLessonSurvivesResolve_unpinRemovesFlag() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        setMaxSolveSeconds(2);
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        call(post("/api/draft/solve").content("{}"), 202);
        JsonNode afterFirstRun = waitForRunToFinish(2);

        long lessonId = firstPlacedUnpinnedLesson(afterFirstRun);
        long freeSlot = firstCompletelyFreeSlot(afterFirstRun, daten);

        // Verschieben legt die Stunde fest, ohne dass „pinned" im Rumpf steht.
        JsonNode afterMove = call(patch("/api/draft/lessons/" + lessonId).content("""
                {"timeslotId":%d}""".formatted(freeSlot)), 200);
        JsonNode moved = lessonById(afterMove, lessonId);
        assertThat(moved.get("pinned").asBoolean()).isTrue();
        assertThat(moved.get("timeslotId").asLong()).isEqualTo(freeSlot);
        long pinnedRoom = moved.get("roomId").asLong();

        call(post("/api/draft/solve").content("{}"), 202);
        JsonNode afterSecondRun = waitForRunToFinish(2);

        JsonNode unchanged = lessonById(afterSecondRun, lessonId);
        assertThat(unchanged.get("timeslotId").asLong()).isEqualTo(freeSlot);
        assertThat(unchanged.get("roomId").asLong()).isEqualTo(pinnedRoom);
        assertThat(unchanged.get("pinned").asBoolean()).isTrue();
        assertThat(typesOf(afterSecondRun))
                .doesNotContain("ROOM_DOUBLE_BOOKED", "TEACHER_DOUBLE_BOOKED", "CLASS_DOUBLE_BOOKED");

        JsonNode afterUnpin = call(patch("/api/draft/lessons/" + lessonId).content("""
                {"pinned":false}"""), 200);
        assertThat(lessonById(afterUnpin, lessonId).get("pinned").asBoolean()).isFalse();
    }

    /**
     * Die Ergaenzung zu T-W9.1 aus der Pruefung 05.09.: in einem vollen Plan gibt es kein freies
     * Zeitfenster der Klasse mehr. Dann verschiebt man auf das Zeitfenster einer <b>anderen,
     * ungepinnten</b> Stunde derselben Klasse. Die Antwort meldet den Konflikt sofort, der Lauf startet
     * trotzdem (nur festgelegte Stunden zaehlen fuer {@code pinned-contradiction}) — und er loest ihn,
     * indem er die ungepinnte Stunde wegzieht, nicht die festgelegte.
     */
    @Test
    @DisplayName("T-W9.1: auf ein belegtes Zeitfenster derselben Klasse verschieben — die andere Stunde weicht, die festgelegte bleibt")
    void tW9_1_moveOntoOccupiedSlotOfSameClass_pinnedStays_otherOneMoves() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        setMaxSolveSeconds(2);
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        call(post("/api/draft/solve").content("{}"), 202);
        JsonNode afterFirstRun = waitForRunToFinish(2);

        List<Long> lessonsOf7a = placedUnpinnedLessonsOfClass(afterFirstRun, daten.klasse7a);
        assertThat(lessonsOf7a).as("die 7a hat vier Wochenstunden, der Lauf plant sie ein").hasSizeGreaterThan(1);
        long x = lessonsOf7a.get(0);
        long y = lessonsOf7a.get(1);
        long slotOfY = lessonById(afterFirstRun, y).get("timeslotId").asLong();

        JsonNode afterMove = call(patch("/api/draft/lessons/" + x).content("""
                {"timeslotId":%d}""".formatted(slotOfY)), 200);
        assertThat(lessonById(afterMove, x).get("pinned").asBoolean()).isTrue();
        assertThat(typesOf(afterMove))
                .as("zwei Stunden derselben Klasse im selben Zeitfenster")
                .contains("CLASS_DOUBLE_BOOKED");

        call(post("/api/draft/solve").content("{}"), 202);
        JsonNode afterSecondRun = waitForRunToFinish(2);

        assertThat(lessonById(afterSecondRun, x).get("timeslotId").asLong())
                .as("die festgelegte Stunde bleibt liegen")
                .isEqualTo(slotOfY);
        assertThat(lessonById(afterSecondRun, y).get("timeslotId").asLong())
                .as("die ungepinnte Stunde weicht aus")
                .isNotEqualTo(slotOfY);
        assertThat(typesOf(afterSecondRun))
                .doesNotContain("ROOM_DOUBLE_BOOKED", "TEACHER_DOUBLE_BOOKED", "CLASS_DOUBLE_BOOKED");
    }

    /**
     * T-W8.1 auf dem vollen Beispielbestand (12 Klassen, 360 Stunden) — die Vorbedingung, die die
     * Abnahme-Checkliste fuer T-W8.1 nennt („FULL geladen (D-1)"). Der kleine Bestand des Pflichtenhefts
     * hat acht Stunden; er belegt den Weg, aber nicht, dass der Solver eine ganze Schule schafft.
     */
    @Test
    @DisplayName("T-W8.1 auf dem vollen Bestand: 360 Stunden, keine Doppelbelegung, Ende innerhalb der Rechenzeit")
    void tW8_1_fullSample_terminatesByLimit_noHardConflict_everyLessonAssignedOrOpen_activeEmptyUntilAccept()
            throws Exception {
        sampleData.loadFullMasterData();
        setMaxSolveSeconds(FULL_SOLVE_SECONDS);
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);

        long begonnen = System.nanoTime();
        call(post("/api/draft/solve").content("{}"), 202);
        JsonNode draft = waitForRunToFinish(FULL_SOLVE_SECONDS);
        long dauerSekunden = (System.nanoTime() - begonnen) / 1_000_000_000L;

        assertThat(draft.get("status").asText()).isEqualTo("EDITING");
        assertThat(draft.get("solve").get("error").isNull()).isTrue();
        assertThat(draft.get("solve").get("lastScore").isNull()).isFalse();
        // 12 Klassen mal 30 Wochenstunden.
        assertThat(draft.get("lessons")).hasSize(360);

        int belegt = 0;
        for (JsonNode lesson : draft.get("lessons")) {
            assertThat(lesson.get("timeslotId").isNull())
                    .as("belegt oder offen, kein dritter Zustand")
                    .isEqualTo(lesson.get("roomId").isNull());
            if (!lesson.get("timeslotId").isNull()) {
                belegt++;
            }
        }
        assertThat(belegt)
                .as("nach %d s sind %d von 360 Stunden belegt", dauerSekunden, belegt)
                .isGreaterThanOrEqualTo(200);
        assertThat(typesOf(draft))
                .doesNotContain("ROOM_DOUBLE_BOOKED", "TEACHER_DOUBLE_BOOKED", "CLASS_DOUBLE_BOOKED");

        // Die Summe der offenen Stunden aus openLessons deckt sich mit den offenen Zeilen.
        int offenLautListe = 0;
        for (JsonNode open : draft.get("openLessons")) {
            offenLautListe += open.get("missing").asInt();
        }
        assertThat(offenLautListe).isEqualTo(360 - belegt);

        // S-3 und S-4 wie im kleinen Bestand — hier zusaetzlich Stunde fuer Stunde: das Annehmen darf
        // keine einzige Lage verschieben, sonst zeigte die Vorschau etwas anderes als der aktive Plan.
        assertThat(getJson("/api/timetable").get("lessons")).isEmpty();
        call(post("/api/draft/accept").content("{}"), 200);
        JsonNode active = getJson("/api/timetable");
        assertThat(active.get("lessons")).hasSize(360);
        assertThat(placementsOf(active)).isEqualTo(placementsOf(draft));
    }

    /**
     * Kann-Option E-40. Der Lauf fuer eine Klasse bekommt alle uebrigen Stunden als festgelegt — auch
     * die <b>offenen</b>. Timefold lehnt eine festgelegte Stunde ohne Raum ab; ohne den Filter in
     * {@code SolveService.buildInput} scheiterte dieser Lauf mit {@code FAILED} (Befund 05.09.).
     */
    @Test
    @DisplayName("Kann (E-40): classId bewegt nur diese Klasse — die offenen Stunden der anderen bleiben liegen")
    void startWithClassId_onlyThatClassMoves() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        setMaxSolveSeconds(2);
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);

        call(post("/api/draft/solve").content("""
                {"classId":%d}""".formatted(daten.klasse7a)), 202);
        JsonNode draft = waitForRunToFinish(2);

        assertThat(draft.get("status").asText()).as("der Lauf ist nicht gescheitert").isEqualTo("EDITING");
        assertThat(draft.get("solve").get("error").isNull()).isTrue();

        for (JsonNode lesson : draft.get("lessons")) {
            if (lesson.get("classId").asLong() == daten.klasse7a) {
                assertThat(lesson.get("timeslotId").isNull())
                        .as("die gewaehlte Klasse wird eingeplant")
                        .isFalse();
            } else {
                assertThat(lesson.get("timeslotId").isNull())
                        .as("keine fremde Stunde wird angefasst")
                        .isTrue();
            }
        }
    }

    /**
     * Der aktive Plan darf offene Stunden enthalten (angenommen wird auch ein unvollstaendiger Plan).
     * {@code COPY_ACTIVE} darf sie nicht festlegen: „festgelegt und offen" heisst „bleib fuer immer
     * offen", und Timefold lehnt es ab. Ohne die Korrektur vom 05.09. scheiterte der Lauf danach.
     */
    @Test
    @DisplayName("COPY_ACTIVE legt offene Stunden nicht fest — der naechste Lauf plant sie ein")
    void copyActiveLeavesOpenLessonsUnpinned() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        setMaxSolveSeconds(2);
        // Ein aktiver Plan mit offenen Stunden: nur die 7a wird gerechnet, 8b und 9c bleiben offen.
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        call(post("/api/draft/solve").content("""
                {"classId":%d}""".formatted(daten.klasse7a)), 202);
        waitForRunToFinish(2);
        call(post("/api/draft/accept").content("{}"), 200);

        JsonNode copy = call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        for (JsonNode lesson : copy.get("lessons")) {
            if (lesson.get("timeslotId").isNull()) {
                assertThat(lesson.get("pinned").asBoolean())
                        .as("eine offene Stunde kommt unfestgelegt in den Entwurf")
                        .isFalse();
            }
        }

        call(post("/api/draft/solve").content("{}"), 202);
        JsonNode afterRun = waitForRunToFinish(2);

        assertThat(afterRun.get("status").asText()).isEqualTo("EDITING");
        assertThat(afterRun.get("solve").get("error").isNull()).isTrue();
        for (JsonNode lesson : afterRun.get("lessons")) {
            assertThat(lesson.get("timeslotId").isNull())
                    .as("der Lauf hat die kopierten offenen Stunden eingeplant")
                    .isFalse();
        }
    }

    /**
     * Zwei Klicks auf „Berechnen". Vor der Vormerkung im {@code SolverRunner} bestanden beide Anfragen
     * die Pruefung {@code requireNotSolving} — die zweite flog danach mit
     * {@code IllegalStateException: already solving} als 500 heraus (gemessen 05.09.).
     */
    @Test
    @DisplayName("Zwei gleichzeitige Berechnungen: genau ein 202 und ein 409 solving — nie 500")
    void twoConcurrentStarts_exactlyOneRunsTheOtherIsConflict() throws Exception {
        pflichtenheftGrunddaten();
        setMaxSolveSeconds(60);
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            CyclicBarrier gleichzeitig = new CyclicBarrier(2);
            List<Future<Integer>> antworten = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                antworten.add(pool.submit(() -> {
                    gleichzeitig.await(10, TimeUnit.SECONDS);
                    return mvc.perform(authorized(post("/api/draft/solve").content("{}")))
                            .andReturn().getResponse().getStatus();
                }));
            }
            for (Future<Integer> antwort : antworten) {
                statuses.add(antwort.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(202, 409);

        call(delete("/api/draft/solve"), 202);
        waitForRunToFinish(60);
    }

    @Test
    @DisplayName("Abbrechen antwortet auch dann 202, wenn gar nichts laeuft")
    void cancellingWithoutARunIsStill202() throws Exception {
        pflichtenheftGrunddaten();
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);

        call(delete("/api/draft/solve"), 202);
        assertThat(getJson("/api/draft").get("status").asText()).isEqualTo("EDITING");
    }

    @Test
    @DisplayName("T-W8.1: vorzeitig beenden liefert das bis dahin beste Ergebnis, statt es wegzuwerfen")
    void cancellingARunKeepsTheBestResultSoFar() throws Exception {
        pflichtenheftGrunddaten();
        setMaxSolveSeconds(60);
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        call(post("/api/draft/solve").content("{}"), 202);

        call(delete("/api/draft/solve"), 202);
        JsonNode draft = waitForRunToFinish(2);

        assertThat(draft.get("status").asText()).isEqualTo("EDITING");
        assertThat(draft.get("solve").get("lastScore").isNull())
                .as("das bis zum Abbruch beste Ergebnis ist uebernommen")
                .isFalse();
        assertThat(draft.get("lessons")).hasSize(8);
    }

    @Test
    @DisplayName("Waehrend eines Laufs wird am Entwurf nichts geaendert (409 solving)")
    void mutationsAreRejectedWhileTheSolverRuns() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        setMaxSolveSeconds(60);
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        call(post("/api/draft/solve").content("{}"), 202);

        JsonNode problem = call(post("/api/draft/lessons").content("""
                {"classId":%d,"subjectId":%d,"timeslotId":%d,"roomId":%d}"""
                .formatted(daten.klasse7a, daten.mathe, daten.slot("WE", 1), daten.r101)), 409);
        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:solving");
        assertThat(problem.get("detail").asText())
                .isEqualTo("Der Entwurf wird gerade berechnet. Bitte warten oder die Berechnung abbrechen.");
        call(post("/api/draft/accept").content("{}"), 409);
        call(delete("/api/draft"), 409);
        assertThat(getJson("/api/draft").get("status").asText()).isEqualTo("SOLVING");

        call(delete("/api/draft/solve"), 202);
        waitForRunToFinish(60);
    }

    @Test
    @DisplayName("Ohne Rumpf ist der Lauf der ganze Plan — curl ohne -d muss durchgehen")
    void startingWithoutABodyIsAllowed() throws Exception {
        pflichtenheftGrunddaten();
        setMaxSolveSeconds(2);
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);

        call(post("/api/draft/solve"), 202);
        assertThat(waitForRunToFinish(2).get("lessons")).hasSize(8);
    }

    // ------------------------------------------------------------------ Helfer

    /**
     * Fragt den Entwurf im Sekundentakt ab, bis kein Lauf mehr laeuft — wie die Weboberflaeche. Die
     * Geduld haengt an der eingestellten Rechenzeit: ein Lauf, der viel laenger braucht als sein Limit,
     * ist ein Befund und kein Grund zu warten.
     */
    private JsonNode waitForRunToFinish(int limitSeconds) throws Exception {
        int maxSeconds = limitSeconds + WAIT_RESERVE_SECONDS;
        for (int second = 0; second < maxSeconds; second++) {
            JsonNode draft = getJson("/api/draft");
            if (!"SOLVING".equals(draft.get("status").asText())) {
                return draft;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Die Berechnung war nach " + maxSeconds + " Sekunden nicht fertig"
                + " (Rechenzeit " + limitSeconds + " s).");
    }

    private static List<String> typesOf(JsonNode draft) {
        List<String> types = new ArrayList<>();
        for (JsonNode conflict : draft.get("conflicts")) {
            types.add(conflict.get("type").asText());
        }
        return types;
    }

    private static JsonNode lessonById(JsonNode draft, long lessonId) {
        for (JsonNode lesson : draft.get("lessons")) {
            if (lesson.get("id").asLong() == lessonId) {
                return lesson;
            }
        }
        throw new AssertionError("Stunde " + lessonId + " fehlt im Entwurf.");
    }

    /** Je Stunde ihre Lage: {@code Kennung -> "zeitfenster/raum"} bzw. {@code "offen"}. */
    private static Map<Long, String> placementsOf(JsonNode plan) {
        Map<Long, String> lagen = new HashMap<>();
        for (JsonNode lesson : plan.get("lessons")) {
            lagen.put(lesson.get("id").asLong(), lesson.get("timeslotId").isNull()
                    ? "offen"
                    : lesson.get("timeslotId").asLong() + "/" + lesson.get("roomId").asLong());
        }
        return lagen;
    }

    /** Alle eingeplanten, nicht festgelegten Stunden einer Klasse — in der Reihenfolge der Antwort. */
    private static List<Long> placedUnpinnedLessonsOfClass(JsonNode draft, long classId) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode lesson : draft.get("lessons")) {
            if (lesson.get("classId").asLong() == classId
                    && !lesson.get("pinned").asBoolean()
                    && !lesson.get("timeslotId").isNull()) {
                ids.add(lesson.get("id").asLong());
            }
        }
        return ids;
    }

    private static long firstPlacedUnpinnedLesson(JsonNode draft) {
        for (JsonNode lesson : draft.get("lessons")) {
            if (!lesson.get("pinned").asBoolean() && !lesson.get("timeslotId").isNull()) {
                return lesson.get("id").asLong();
            }
        }
        throw new AssertionError("Der Lauf hat keine einzige Stunde eingeplant.");
    }

    /** Ein Zeitfenster ohne Pause, in dem keine einzige Stunde liegt — dort entsteht kein Konflikt. */
    private static long firstCompletelyFreeSlot(JsonNode draft, Testdaten daten) {
        Set<Long> used = new HashSet<>();
        for (JsonNode lesson : draft.get("lessons")) {
            if (!lesson.get("timeslotId").isNull()) {
                used.add(lesson.get("timeslotId").asLong());
            }
        }
        for (int index = 0; index < daten.timeslotIds.size(); index++) {
            long slotId = daten.timeslotIds.get(index);
            boolean isBreak = index % 7 == 2;
            if (!isBreak && !used.contains(slotId)) {
                return slotId;
            }
        }
        throw new AssertionError("Im Raster ist kein Zeitfenster frei.");
    }
}
