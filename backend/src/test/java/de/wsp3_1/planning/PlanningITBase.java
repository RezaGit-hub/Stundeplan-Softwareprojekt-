package de.wsp3_1.planning;

import de.wsp3_1.shared.IntegrationTestBase;
import de.wsp3_1.solver.SolverRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gemeinsame Vorbereitung der Integrationstests rund um den Plan — auch fuer die Module
 * {@code substitution} und {@code changelog}, die auf demselben Testdatenbestand pruefen. Deshalb ist
 * die Klasse oeffentlich, obwohl sie im Paket {@code planning} liegt: der Bestand entsteht ueber die
 * Endpunkte von {@code masterdata} und {@code planning}, und den soll niemand ein zweites Mal tippen.
 *
 * <p>Im Einzelnen: leere Datenbank, angemeldete Planung und der
 * Testdatenbestand des Pflichtenhefts (Meyer/Weber/Schulz, 7a/8b/9c, R101/R204/P1, Raster Mo–Fr mit
 * sieben Positionen und der grossen Pause an Position 3).
 *
 * <p>Die Grunddaten entstehen ueber die eigene API — genau die Aufrufe der Abnahme-Checkliste G-1 bis
 * G-7. Der Plan entsteht dagegen ueber {@code /api/draft} (P-1 und P-6), also ueber genau die Wege, die
 * dieses Modul baut.</p>
 */
public abstract class PlanningITBase extends IntegrationTestBase {

    /** Wie lange vor einem Test auf das Ende eines fremden Solver-Laufs gewartet wird. */
    private static final long SOLVER_STOP_TIMEOUT_MS = 30_000;

    /** Reihenfolge von innen nach aussen; ohne CASCADE, damit das Konto {@code admin} bestehen bleibt. */
    private static final List<String> TABLES_TO_CLEAR = List.of(
            "exception_event", "lesson_exception", "change_event", "changelog_entry",
            "lesson", "timetable", "holiday",
            "class_subject", "teacher_subject", "teacher_preferred_timeslot", "teacher_blocked_timeslot",
            "teacher", "subject", "room_blocked_timeslot", "room", "school_class", "timeslot");

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected SolverRunner solverRunner;

    protected String token;

    @BeforeEach
    void resetAndLogin() throws Exception {
        stopRunningSolves();
        // Alles ausser der Planung: Lehrkraft- UND Klassenkonten (E-50). Muss vor school_class
        // laufen, sonst haelt user_account.class_id die Klassenzeile fest.
        jdbc.execute("DELETE FROM user_account WHERE role <> 'PLANNING'");
        for (String table : TABLES_TO_CLEAR) {
            jdbc.execute("DELETE FROM " + table);
        }
        token = loginAsPlanning();
    }

    private String loginAsPlanning() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    // ------------------------------------------------------------------ kleine Helfer

    protected MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
    }

    /** Fuehrt eine Anfrage aus, erwartet den Status und liefert die Antwort als Baum. */
    protected JsonNode call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        String body = mvc.perform(authorized(request))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    protected JsonNode getJson(String path) throws Exception {
        return call(get(path), 200);
    }

    protected long postId(String path, String body) throws Exception {
        return call(post(path).content(body), 201).get("id").asLong();
    }

    /**
     * Stellt die Rechenzeit auf die Vorbelegung zurueck. Alle Integrationstests teilen einen
     * Spring-Kontext <b>und</b> eine Datenbank: wer {@code system_config} aendert und sie stehen laesst,
     * bringt {@code ConfigIT} zu Fall (gemessen 05.09.). Genauso macht es {@code ConfigIT} selbst.
     */
    /**
     * Kein Solver-Lauf darf in den naechsten Test hineinreichen. Timefold rechnet in einem eigenen
     * Faden weiter, auch wenn der Test laengst zu Ende ist: sein Ergebnis landet dann in Tabellen,
     * die dieser Test gerade erst geleert hat, und mit {@code parallel-solver-count=1} wartet der
     * naechste Lauf ausserdem auf den alten. Das traf sporadisch fremde Testklassen — {@code WeekIT}
     * scheiterte schon beim Aufbau der Testdaten, waehrend der Fehler in {@code SolveServiceIT}
     * entstanden war (rote {@code backend:integration}-Laeufe, 17./18.09.).
     *
     * <p>Darum vor jedem Test: alles abbrechen, was noch laeuft, und abwarten, bis der Rueckruf durch
     * ist. Erst danach wird geleert. Laeuft nichts, kostet das nichts.</p>
     */
    private void stopRunningSolves() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM timetable", Long.class);
        ids.forEach(solverRunner::cancel);
        long deadline = System.currentTimeMillis() + SOLVER_STOP_TIMEOUT_MS;
        for (Long id : ids) {
            while (solverRunner.isSolving(id)) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("Ein Solver-Lauf aus einem frueheren Test laeuft"
                            + " nach " + SOLVER_STOP_TIMEOUT_MS / 1000 + " s noch (Entwurf " + id + ").");
                }
                sleepQuietly(50);
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Warten unterbrochen", ex);
        }
    }

    @AfterEach
    void rechenzeitZurueckstellen() {
        jdbc.update("UPDATE system_config SET max_solve_seconds = 60 WHERE id = 1");
    }

    /** Rechenzeit je Solver-Lauf; in Tests bewusst klein, damit ein Lauf Sekunden statt Minuten dauert. */
    protected void setMaxSolveSeconds(int seconds) throws Exception {
        JsonNode config = getJson("/api/config");
        call(put("/api/config").content("""
                {"schoolName":"%s","state":"%s","schoolType":"%s","maxSolveSeconds":%d}"""
                .formatted(config.get("schoolName").asText(), config.get("state").asText(),
                        config.get("schoolType").asText(), seconds)), 200);
    }

    // ------------------------------------------------------------------ Testdaten des Pflichtenhefts

    protected Testdaten pflichtenheftGrunddaten() throws Exception {
        Testdaten daten = new Testdaten();
        daten.mathe = postId("/api/subjects", """
                {"name":"Mathematik","shortName":"Ma","requiredRoomType":null,"color":"#1976D2"}""");
        daten.deutsch = postId("/api/subjects", """
                {"name":"Deutsch","shortName":"De","requiredRoomType":null,"color":"#388E3C"}""");
        daten.physik = postId("/api/subjects", """
                {"name":"Physik","shortName":"Ph","requiredRoomType":"Fachraum Physik","color":"#F57C00"}""");

        daten.r101 = postId("/api/rooms", """
                {"name":"R101","roomType":"Klassenraum","active":true}""");
        daten.r204 = postId("/api/rooms", """
                {"name":"R204","roomType":"Klassenraum","active":true}""");
        daten.p1 = postId("/api/rooms", """
                {"name":"P1","roomType":"Fachraum Physik","active":true}""");

        call(put("/api/timegrid").content(rasterMoBisFr()), 200);
        daten.timeslotIds = new ArrayList<>();
        JsonNode slots = getJson("/api/timegrid").get("slots");
        for (int i = 0; i < slots.size(); i++) {
            daten.timeslotIds.add(slots.get(i).get("id").asLong());
        }

        daten.meyer = postId("/api/teachers", """
                {"name":"Meyer","shortName":"MEY","weeklyHoursQuota":25,"subjectIds":[%d,%d],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}"""
                .formatted(daten.mathe, daten.physik));
        daten.weber = postId("/api/teachers", """
                {"name":"Weber","shortName":"WEB","weeklyHoursQuota":25,"subjectIds":[%d],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[],"active":true}"""
                .formatted(daten.deutsch));
        daten.schulz = postId("/api/teachers", """
                {"name":"Schulz","shortName":"SCZ","weeklyHoursQuota":2,"subjectIds":[%d],
                 "preferredTimeslotIds":[],"blockedTimeslotIds":[%d,%d],"active":true}"""
                .formatted(daten.mathe, daten.slot("FR", 6), daten.slot("FR", 7)));

        daten.klasse7a = postId("/api/classes", """
                {"name":"7a","grade":7,"active":true}""");
        daten.klasse8b = postId("/api/classes", """
                {"name":"8b","grade":8,"active":true}""");
        daten.klasse9c = postId("/api/classes", """
                {"name":"9c","grade":9,"active":true}""");

        call(put("/api/classes/" + daten.klasse7a + "/subjects").content("""
                [{"subjectId":%d,"weeklyHours":2,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":1,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":1,"teacherId":%d}]"""
                .formatted(daten.mathe, daten.meyer, daten.deutsch, daten.weber, daten.physik, daten.meyer)), 200);
        call(put("/api/classes/" + daten.klasse8b + "/subjects").content("""
                [{"subjectId":%d,"weeklyHours":2,"teacherId":%d},
                 {"subjectId":%d,"weeklyHours":1,"teacherId":%d}]"""
                .formatted(daten.mathe, daten.meyer, daten.deutsch, daten.weber)), 200);
        call(put("/api/classes/" + daten.klasse9c + "/subjects").content("""
                [{"subjectId":%d,"weeklyHours":1,"teacherId":%d}]"""
                .formatted(daten.mathe, daten.schulz)), 200);
        return daten;
    }

    /**
     * Der Regelplan Montag/Dienstag aus dem Pflichtenheft (sieben Stunden), von Hand in einen leeren
     * Entwurf eingetragen — Schritt P-1 der Abnahme-Checkliste.
     */
    protected void planMoDiInEntwurf(Testdaten daten) throws Exception {
        call(post("/api/draft").content("""
                {"source":"EMPTY"}"""), 201);
        addLesson(daten.klasse7a, daten.mathe, daten.slot("MO", 1), daten.r101);
        addLesson(daten.klasse7a, daten.deutsch, daten.slot("MO", 2), daten.r101);
        addLesson(daten.klasse7a, daten.physik, daten.slot("MO", 4), daten.p1);
        addLesson(daten.klasse8b, daten.mathe, daten.slot("MO", 2), daten.r204);
        addLesson(daten.klasse8b, daten.deutsch, daten.slot("MO", 4), daten.r204);
        addLesson(daten.klasse8b, daten.mathe, daten.slot("MO", 5), daten.r204);
        addLesson(daten.klasse7a, daten.mathe, daten.slot("TU", 1), daten.r101);
    }

    protected JsonNode addLesson(long classId, long subjectId, long timeslotId, long roomId) throws Exception {
        return call(post("/api/draft/lessons").content("""
                {"classId":%d,"subjectId":%d,"timeslotId":%d,"roomId":%d}"""
                .formatted(classId, subjectId, timeslotId, roomId)), 201);
    }

    /** Die Kennung der Stunde einer Klasse in einem Zeitfenster; {@code -1}, wenn es keine gibt. */
    protected static long lessonIdAt(JsonNode draft, long classId, long timeslotId) {
        for (JsonNode lesson : draft.get("lessons")) {
            if (lesson.get("classId").asLong() == classId
                    && !lesson.get("timeslotId").isNull()
                    && lesson.get("timeslotId").asLong() == timeslotId) {
                return lesson.get("id").asLong();
            }
        }
        return -1;
    }

    /** Mo–Fr, Position 1 bis 7, Position 3 ist die grosse Pause (G-3). */
    private static String rasterMoBisFr() {
        String[] days = {"MO", "TU", "WE", "TH", "FR"};
        String[][] times = {
                {"08:00", "08:45"}, {"08:50", "09:35"}, {"09:35", "09:55"}, {"09:55", "10:40"},
                {"10:45", "11:30"}, {"11:50", "12:35"}, {"12:40", "13:25"}};
        List<String> rows = new ArrayList<>();
        for (String day : days) {
            for (int position = 1; position <= times.length; position++) {
                rows.add("""
                        {"day":"%s","position":%d,"startTime":"%s","endTime":"%s","isBreak":%b}"""
                        .formatted(day, position, times[position - 1][0], times[position - 1][1], position == 3));
            }
        }
        return "{\"slots\":[" + String.join(",", rows) + "]}";
    }

    /** Die Kennungen des Testdatenbestands. Nie geraten — immer aus den Antworten gelesen. */
    public static final class Testdaten {
        public long mathe;
        public long deutsch;
        public long physik;
        public long r101;
        public long r204;
        public long p1;
        public long meyer;
        public long weber;
        public long schulz;
        public long klasse7a;
        public long klasse8b;
        public long klasse9c;
        public List<Long> timeslotIds;

        /** Zeitfenster-Kennung zu Tag und Position; das Raster hat sieben Positionen je Tag. */
        public long slot(String day, int position) {
            int dayIndex = List.of("MO", "TU", "WE", "TH", "FR").indexOf(day);
            return timeslotIds.get(dayIndex * 7 + position - 1);
        }
    }
}
