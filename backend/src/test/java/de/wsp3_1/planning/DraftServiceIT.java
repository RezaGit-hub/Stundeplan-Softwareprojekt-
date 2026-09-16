package de.wsp3_1.planning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Der Lebenslauf des Plans gegen die echte Datenbank: kopieren, annehmen, archivieren, Verlauf
 * schreiben. Was hier steht, deckt die Checkliste P-6, D-4 und D-5 sowie die Kann-Option
 * „Gueltig ab" (E-40) ab.
 */
class DraftServiceIT extends PlanningITBase {

    @Test
    @DisplayName("COPY_ACTIVE legt konfliktfreie Stunden fest und laesst die konfliktbehafteten frei (W-1)")
    void copyActivePinsOnlyConflictFreeLessons() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("{}"), 200);
        // Der Konflikt entsteht erst NACH dem Annehmen durch eine Grunddatenaenderung (der Muss-Ablauf
        // des Pflichtenhefts): R101 wird Montag 1. Stunde gesperrt (#133) — die 7a-Mathestunde des
        // aktiven Plans liegt genau dort. Vor #103 kam der Konflikt beim Annehmen mit hinein; seit
        // #103 lehnt das Annehmen harte Konflikte ab (409 draft-has-conflicts, DraftsControllerIT).
        call(put("/api/rooms/" + daten.r101).content("""
                {"name":"R101","roomType":"Klassenraum","active":true,"blockedTimeslotIds":[%d]}"""
                .formatted(daten.slot("MO", 1))), 200);

        JsonNode copy = call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);

        assertThat(copy.get("lessons")).hasSize(7);
        long unpinned = 0;
        for (JsonNode lesson : copy.get("lessons")) {
            boolean inConflictSlot = !lesson.get("timeslotId").isNull()
                    && lesson.get("timeslotId").asLong() == daten.slot("MO", 1)
                    && lesson.get("roomId").asLong() == daten.r101;
            assertThat(lesson.get("pinned").asBoolean()).isEqualTo(!inConflictSlot);
            if (!lesson.get("pinned").asBoolean()) {
                unpinned++;
            }
        }
        assertThat(unpinned).isEqualTo(1);
        assertThat(copy.get("conflicts")).hasSize(1);
        assertThat(copy.get("conflicts").get(0).get("type").asText()).isEqualTo("ROOM_BLOCKED_TIME");
    }

    @Test
    @DisplayName("Annehmen archiviert den alten Plan und schreibt genau einen Verlaufseintrag (W-7)")
    void acceptArchivesThePreviousPlanAndWritesTheChangelogEntry() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("{}"), 200);

        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        call(post("/api/draft/accept").content("{}"), 200);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ARCHIVED'", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ACTIVE'", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'DRAFT'", Long.class))
                .isEqualTo(0);

        List<Map<String, Object>> entries = jdbc.queryForList(
                "SELECT action, old_assignment, new_assignment, date FROM changelog_entry ORDER BY id");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0)).containsEntry("action", "TIMETABLE_ACCEPTED")
                .containsEntry("old_assignment", "kein Plan")
                .containsEntry("new_assignment", "Plan angenommen (7 Stunden)");
        assertThat(entries.get(0).get("date")).isNull();
        assertThat((String) entries.get(1).get("old_assignment")).startsWith("Plan vom ");
    }

    @Test
    @DisplayName("Kann (E-40): validFrom wird gespeichert und steht im Verlaufseintrag")
    void acceptWithValidFromIsStored() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        call(post("/api/draft/accept").content("""
                {"validFrom":"2026-09-21"}"""), 200);

        assertThat(jdbc.queryForObject("SELECT valid_from FROM timetable WHERE status = 'ACTIVE'",
                LocalDate.class)).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(jdbc.queryForObject("SELECT date FROM changelog_entry", LocalDate.class))
                .isEqualTo(LocalDate.of(2026, 9, 21));

        // Seit dem 05.09. steht der Stichtag auch in der Antwort und in GET /api/timetable — vorher musste
        // die Web-App ihn aus acceptedAt raten und zeigte das falsche Datum (Befund PFLICHTENHEFT-6).
        JsonNode plan = getJson("/api/timetable");
        assertThat(plan.get("validFrom").asText()).isEqualTo("2026-09-21");
    }

    @Test
    @DisplayName("Eine unveraenderte Kopie behaelt die kuenftigen Vertretungen (PFLICHTENHEFT-9)")
    void acceptCopyKeepsFutureSubstitutions() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("{}"), 200);
        long alteStunde = vertretungAnfangsstunde();

        // Der Muss-Ablauf des Pflichtenhefts: Grunddaten korrigieren, neu rechnen, annehmen. Bis zum
        // 05.09. loeschte allein dieser Schritt den gesamten Vertretungsplan der Folgewochen, weil
        // COPY_ACTIVE neue Stunden-Zeilen anlegt und die Ausnahme auf der alten Kennung sitzen blieb.
        JsonNode kopie = call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        assertThat(kopie.get("expiringExceptions").asLong())
                .as("die Vorschau im Entwurf sagt dasselbe wie die Antwort beim Annehmen")
                .isZero();

        JsonNode accepted = call(post("/api/draft/accept").content("{}"), 200);

        assertThat(accepted.get("warnings")).isEmpty();
        Long jetzt = jdbc.queryForObject("SELECT lesson_id FROM lesson_exception", Long.class);
        assertThat(jetzt).isNotEqualTo(alteStunde);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM lesson_exception e
                JOIN lesson l ON l.id = e.lesson_id
                JOIN timetable t ON t.id = l.timetable_id
                WHERE t.status = 'ACTIVE'""", Long.class))
                .as("die Vertretung haengt jetzt an der Stunde des geltenden Plans")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Faellt die Stunde im neuen Plan weg, verfaellt ihre Vertretung — die Antwort sagt es")
    void acceptWarnsAboutExpiringSubstitutions() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("{}"), 200);
        vertretungAnfangsstunde();

        JsonNode kopie = call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        // Genau die vertretene Stunde wird aus dem Entwurf genommen: 7a Mathematik am Montag, 1. Stunde.
        long imEntwurf = lessonIdAt(kopie, daten.klasse7a, daten.slot("MO", 1));
        JsonNode ohne = call(delete("/api/draft/lessons/" + imEntwurf), 200);
        assertThat(ohne.get("expiringExceptions").asLong()).isEqualTo(1L);

        JsonNode accepted = call(post("/api/draft/accept").content("{}"), 200);

        assertThat(accepted.get("warnings")).hasSize(1);
        assertThat(accepted.get("warnings").get(0).asText())
                .isEqualTo("Eine Vertretung ab heute verfaellt: ihre Stunde gibt es im neuen Plan nicht mehr.");
    }

    @Test
    @DisplayName("Ein archivierter Plan mit spaeterem Beginn behaelt seine Vertretungen (E-40)")
    void acceptLeavesExceptionsOfALaterStartingPlanAlone() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        // Plan A gilt erst ab dem 21.09. — bis dahin gilt gar keiner, danach dieser.
        call(post("/api/draft/accept").content("""
                {"validFrom":"2026-09-21"}"""), 200);
        long stundeInA = jdbc.queryForObject("SELECT min(id) FROM lesson", Long.class);
        Long adminId = jdbc.queryForObject("SELECT id FROM user_account WHERE username = 'admin'", Long.class);
        jdbc.update("""
                INSERT INTO lesson_exception (lesson_id, date, kind, created_at, created_by_id)
                VALUES (?, ?, 'CANCEL', now(), ?)""", stundeInA, java.sql.Date.valueOf(LocalDate.of(2026, 9, 22)), adminId);

        // Plan B gilt ab sofort. Auf der Zeitleiste steht ab dem 21.09. weiter A (PlanReader.timetableIdAt),
        // also darf die Vertretung am 22.09. weder umgehaengt werden noch als verfallen gelten.
        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        JsonNode accepted = call(post("/api/draft/accept").content("{}"), 200);

        assertThat(accepted.get("warnings")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT lesson_id FROM lesson_exception", Long.class))
                .as("die Ausnahme haengt weiter an dem Plan, der an ihrem Tag gilt")
                .isEqualTo(stundeInA);
    }

    /**
     * Traegt eine datierte Ausnahme an der ersten Stunde des aktiven Plans ein (7a Mathematik, Montag
     * 1. Stunde) und liefert deren Stunden-Kennung. Geschrieben wird direkt in die Tabelle aus V4 — die
     * Ausnahme selbst gehoert dem Modul {@code substitution}, hier zaehlt nur ihre Wirkung auf den
     * Planwechsel.
     */
    private long vertretungAnfangsstunde() {
        Long lessonId = jdbc.queryForObject("SELECT min(id) FROM lesson", Long.class);
        Long adminId = jdbc.queryForObject("SELECT id FROM user_account WHERE username = 'admin'", Long.class);
        jdbc.update("""
                INSERT INTO lesson_exception (lesson_id, date, kind, created_at, created_by_id)
                VALUES (?, ?, 'CANCEL', now(), ?)""", lessonId, java.sql.Date.valueOf(LocalDate.now()), adminId);
        return lessonId;
    }

    @Test
    @DisplayName("Ueberzaehlige Stunden sind eine Warnung, kein Fehler")
    void moreLessonsThanWeeklyHoursIsAWarning() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        // 9c hat eine Wochenstunde Mathematik — hier stehen zwei.
        addLesson(daten.klasse9c, daten.mathe, daten.slot("WE", 1), daten.r101);
        JsonNode draft = addLesson(daten.klasse9c, daten.mathe, daten.slot("WE", 2), daten.r101);

        assertThat(draft.get("openLessons")).isEmpty();
        assertThat(draft.get("warnings")).hasSize(1);
        assertThat(draft.get("warnings").get(0).asText())
                .isEqualTo("9c Ma: 2 Stunden geplant, 1 vorgesehen");
    }

    @Test
    @DisplayName("Ohne aktiven Plan antwortet GET /api/timetable mit leeren Listen, nicht mit 404")
    void emptyActivePlanIsAState() throws Exception {
        JsonNode active = getJson("/api/timetable");
        assertThat(active.get("id").isNull()).isTrue();
        assertThat(active.get("acceptedAt").isNull()).isTrue();
        assertThat(active.get("lessons")).isEmpty();
        assertThat(active.get("conflicts")).isEmpty();
    }
}
