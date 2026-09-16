package de.wsp3_1.masterdata;

import de.wsp3_1.shared.IntegrationTestBase;
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
 * Gemeinsame Vorbereitung der Grunddaten-Integrationstests: leere Datenbank, angemeldete Planung,
 * und auf Wunsch der Testdatenbestand des Pflichtenhefts (Meyer/Weber/Schulz, 7a/8b/9c,
 * R101/R204/P1, Raster Mo–Fr mit sechs Stunden und der grossen Pause nach der zweiten).
 *
 * <p>Die Testdaten entstehen ueber die eigene API — genau die Aufrufe der Abnahme-Checkliste
 * (G-1 bis G-7). So prueft schon der Aufbau, was die Abnahme spaeter von Hand macht.</p>
 */
abstract class MasterdataITBase extends IntegrationTestBase {

    /** Reihenfolge von innen nach aussen; ohne CASCADE, damit das Konto {@code admin} bestehen bleibt. */
    private static final List<String> TABLES_TO_CLEAR = List.of(
            "exception_event", "lesson_exception", "change_event", "changelog_entry",
            "lesson", "timetable",
            "class_subject", "teacher_subject", "teacher_preferred_timeslot", "teacher_blocked_timeslot",
            "teacher", "subject", "room_blocked_timeslot", "room", "school_class", "timeslot");

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    protected String token;

    @BeforeEach
    void resetAndLogin() throws Exception {
        jdbc.execute("DELETE FROM user_account WHERE role = 'TEACHER'");
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

    // ------------------------------------------------------------------ Testdaten des Pflichtenhefts

    /** Faecher, Raeume, Raster, drei Lehrkraefte, drei Klassen mit Faecherliste (G-1 bis G-7). */
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
     * Der aktive Plan Montag/Dienstag aus dem Pflichtenheft (sieben Stunden). Er entsteht hier per SQL:
     * die Tabellen {@code timetable} und {@code lesson} gehoeren dem Modul {@code planning}, das in
     * dieser Bauphase noch nicht existiert. Geprueft wird damit genau das, was T-W1.3 braucht — dass
     * eine eingeplante Lehrkraft nicht geloescht, sondern deaktiviert wird.
     */
    protected void aktivenPlanMoDiAnlegen(Testdaten daten) {
        jdbc.update("INSERT INTO timetable (status, created_at, accepted_at) VALUES ('ACTIVE', now(), now())");
        Long plan = jdbc.queryForObject("SELECT id FROM timetable WHERE status = 'ACTIVE'", Long.class);
        stundeAnlegen(plan, daten.klasse7a, daten.mathe, daten.slot("MO", 1), daten.r101);
        stundeAnlegen(plan, daten.klasse7a, daten.deutsch, daten.slot("MO", 2), daten.r101);
        stundeAnlegen(plan, daten.klasse7a, daten.physik, daten.slot("MO", 4), daten.p1);
        stundeAnlegen(plan, daten.klasse8b, daten.mathe, daten.slot("MO", 2), daten.r204);
        stundeAnlegen(plan, daten.klasse8b, daten.deutsch, daten.slot("MO", 4), daten.r204);
        stundeAnlegen(plan, daten.klasse8b, daten.mathe, daten.slot("MO", 5), daten.r204);
        stundeAnlegen(plan, daten.klasse7a, daten.mathe, daten.slot("TU", 1), daten.r101);
    }

    private void stundeAnlegen(Long plan, long classId, long subjectId, long timeslotId, long roomId) {
        Long classSubjectId = jdbc.queryForObject(
                "SELECT id FROM class_subject WHERE class_id = ? AND subject_id = ?",
                Long.class, classId, subjectId);
        jdbc.update("""
                INSERT INTO lesson (timetable_id, class_subject_id, pinned, timeslot_id, room_id)
                VALUES (?, ?, true, ?, ?)""", plan, classSubjectId, timeslotId, roomId);
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

    /** Die IDs des Testdatenbestands. Nie geraten — immer aus den Antworten gelesen. */
    protected static final class Testdaten {
        long mathe;
        long deutsch;
        long physik;
        long r101;
        long r204;
        long p1;
        long meyer;
        long weber;
        long schulz;
        long klasse7a;
        long klasse8b;
        long klasse9c;
        List<Long> timeslotIds;

        /** Zeitfenster-ID zu Tag und Position; das Raster hat sieben Positionen je Tag. */
        long slot(String day, int position) {
            int dayIndex = List.of("MO", "TU", "WE", "TH", "FR").indexOf(day);
            return timeslotIds.get(dayIndex * 7 + position - 1);
        }
    }
}
