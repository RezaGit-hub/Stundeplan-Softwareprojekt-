package de.wsp3_1.planning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Der Lebenslauf des Plans gegen die echte Datenbank: kopieren, annehmen, archivieren, Verlauf
 * schreiben. Was hier steht, deckt die Checkliste P-6, D-4 und D-5 sowie die Kann-Option
 * „Gueltig ab" (E-40) ab.
 */
class DraftServiceIT extends PlanningITBase {

    // Die Zeitraeume liegen bewusst relativ zu heute: sonst haengt das Ergebnis daran, an welchem Tag
    // jemand die Tests startet (dieselbe Regel wie in WeekIT).
    private static final LocalDate HEUTE = LocalDate.now();
    private static final LocalDate IN_ZWEI_WOCHEN = HEUTE.plusWeeks(2);
    private static final LocalDate IN_SECHS_WOCHEN = HEUTE.plusWeeks(6);

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
                {"validFrom":"%s"}""".formatted(IN_ZWEI_WOCHEN)), 200);

        assertThat(jdbc.queryForObject("SELECT valid_from FROM timetable WHERE status = 'ACTIVE'",
                LocalDate.class)).isEqualTo(IN_ZWEI_WOCHEN);
        assertThat(jdbc.queryForObject("SELECT date FROM changelog_entry", LocalDate.class))
                .isEqualTo(IN_ZWEI_WOCHEN);

        // Seit dem 05.09. steht der Stichtag auch in der Antwort und in GET /api/timetable — vorher musste
        // die Web-App ihn aus acceptedAt raten und zeigte das falsche Datum (Befund PFLICHTENHEFT-6).
        JsonNode plan = getJson("/api/timetable");
        assertThat(plan.get("validFrom").asText()).isEqualTo(IN_ZWEI_WOCHEN.toString());
    }

    /**
     * Seit dem 16.09. steht der Beginn <b>immer</b> in der Zeile — bei „ab sofort" der heutige Tag.
     *
     * <p>Ein leeres {@code valid_from} war in PostgreSQL kein „ab heute": {@code daterange(NULL, …)} ist
     * nach unten offen, und die Ausschlussbedingung {@code timetable_active_no_overlap} sah damit eine
     * Ueberschneidung mit jedem anderen aktiven Plan. Der Zeitraum in der Antwort hat sich dadurch nicht
     * geaendert — nur ist er jetzt auch in der Datenbank derselbe.</p>
     */
    @Test
    @DisplayName("Ohne „Gueltig ab“ steht der heutige Tag als Beginn — in der Antwort und in der Zeile")
    void acceptWithoutValidFromStoresToday() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        JsonNode accepted = call(post("/api/draft/accept").content("{}"), 200);

        assertThat(accepted.get("validFrom").asText()).isEqualTo(HEUTE.toString());
        assertThat(jdbc.queryForObject("SELECT valid_from FROM timetable WHERE status = 'ACTIVE'",
                LocalDate.class)).isEqualTo(HEUTE);
        // Der Verlaufseintrag bleibt ohne Datum: „ab sofort" ist kein Stichtag, den jemand gewaehlt hat.
        assertThat(jdbc.queryForObject("SELECT date FROM changelog_entry", LocalDate.class)).isNull();
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
    @DisplayName("Ein Plan mit spaeterem Beginn behaelt seine Vertretungen (E-40)")
    void acceptLeavesExceptionsOfALaterStartingPlanAlone() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        // Plan A gilt erst in zwei Wochen — bis dahin gilt gar keiner, danach dieser.
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(IN_ZWEI_WOCHEN)), 200);
        long stundeInA = jdbc.queryForObject("SELECT min(id) FROM lesson", Long.class);
        Long adminId = jdbc.queryForObject("SELECT id FROM user_account WHERE username = 'admin'", Long.class);
        jdbc.update("""
                INSERT INTO lesson_exception (lesson_id, date, kind, created_at, created_by_id)
                VALUES (?, ?, 'CANCEL', now(), ?)""", stundeInA,
                java.sql.Date.valueOf(IN_ZWEI_WOCHEN.plusDays(1)), adminId);

        // Plan B gilt ab sofort bis zum Vortag von A. Damit ueberschneiden sich die Zeitraeume nicht,
        // A bleibt aktiv, und auf der Zeitleiste steht ab dem Beginn von A weiter A
        // (PlanReader.timetableIdAt) — die Vertretung am Tag danach darf also weder umgehaengt werden
        // noch als verfallen gelten. Seit #115 braucht es dafuer das Enddatum: ohne validUntil verlangte
        // B denselben Zeitraum wie A, und „jetzt aktivieren" legte A still.
        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        JsonNode accepted = call(post("/api/draft/accept").content("""
                {"validUntil":"%s"}""".formatted(IN_ZWEI_WOCHEN.minusDays(1))), 200);

        assertThat(accepted.get("warnings")).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM timetable WHERE status = 'ACTIVE'", Long.class))
                .as("beide Plaene bleiben aktiv — getrennte Zeitraeume")
                .isEqualTo(2L);
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
        assertThat(active.get("validUntil").isNull()).isTrue();
        assertThat(active.get("lessons")).isEmpty();
        assertThat(active.get("conflicts")).isEmpty();
        assertThat(getJson("/api/timetables")).isEmpty();
    }

    // ------------------------------------------------------------------ Gueltigkeitszeitraum (#115)

    /**
     * Der Kern von #115: zwei Plaene mit anschliessenden Zeitraeumen stehen beide auf {@code ACTIVE},
     * und jeder Tag bekommt den Plan, der an ihm gilt — auch genau an der Datumsgrenze.
     */
    @Test
    @DisplayName("Zwei Plaene mit anschliessenden Zeitraeumen bleiben beide aktiv; jeder Tag bekommt seinen")
    void twoPlansWithAdjoiningRangesBothStayActive() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        long a = call(post("/api/draft/accept").content("""
                {"validUntil":"%s"}""".formatted(IN_ZWEI_WOCHEN)), 200).get("id").asLong();

        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        long b = call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(IN_ZWEI_WOCHEN.plusDays(1))), 200).get("id").asLong();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ACTIVE'", Long.class))
                .as("der alte Plan bleibt bis zu seinem Ende gueltig")
                .isEqualTo(2L);

        JsonNode liste = getJson("/api/timetables");
        assertThat(liste).hasSize(2);
        assertThat(liste.get(0).get("id").asLong()).isEqualTo(a);
        assertThat(liste.get(0).get("validUntil").asText()).isEqualTo(IN_ZWEI_WOCHEN.toString());
        assertThat(liste.get(0).get("current").asBoolean()).as("heute gilt der erste").isTrue();
        assertThat(liste.get(1).get("id").asLong()).isEqualTo(b);
        assertThat(liste.get(1).get("validFrom").asText()).isEqualTo(IN_ZWEI_WOCHEN.plusDays(1).toString());
        assertThat(liste.get(1).get("validUntil").isNull()).isTrue();
        assertThat(liste.get(1).get("current").asBoolean()).isFalse();

        // „Der aktive Plan" ist der, der heute gilt.
        assertThat(getJson("/api/timetable").get("id").asLong()).isEqualTo(a);
        assertThat(getJson("/api/timetables/" + b).get("validFrom").asText())
                .isEqualTo(IN_ZWEI_WOCHEN.plusDays(1).toString());

        // Dass jeder Tag den Plan seines Zeitraums bekommt — auch genau an der Grenze —, prueft
        // WeekIT.threeValidFromDatesResolvePerDay gegen den echten WeekResolver.
    }

    @Test
    @DisplayName("Ein kuenftiger Beginn laesst den Vorgaenger aktiv und endet ihn am Vortag")
    void futureStartTruncatesThePredecessorInsteadOfArchivingIt() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        long alt = call(post("/api/draft/accept").content("{}"), 200).get("id").asLong();

        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(IN_ZWEI_WOCHEN)), 200);

        assertThat(jdbc.queryForObject(
                "SELECT valid_until FROM timetable WHERE id = ?", LocalDate.class, alt))
                .as("der alte Plan endet am Tag vor dem Beginn des neuen")
                .isEqualTo(IN_ZWEI_WOCHEN.minusDays(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ARCHIVED'", Long.class))
                .as("nichts wird archiviert, solange der alte Plan noch laeuft")
                .isZero();
    }

    @Test
    @DisplayName("Ein kuenftiger Plan im Weg ist 409 plan-overlap; nichts wird geaendert")
    void overlapWithAFuturePlanIsRejected() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(IN_SECHS_WOCHEN)), 200);

        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        JsonNode problem = call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(IN_ZWEI_WOCHEN)), 409);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:plan-overlap");
        assertThat(problem.get("detail").asText()).isEqualTo(
                "Ein angenommener Plan gilt bereits ab " + IN_SECHS_WOCHEN + ". "
                        + "Enddatum vor " + IN_SECHS_WOCHEN + " wählen oder den Zeitraum ändern.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ACTIVE'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'DRAFT'", Long.class))
                .as("der Entwurf steht noch")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT valid_until FROM timetable WHERE status = 'ACTIVE'""", LocalDate.class))
                .as("der kuenftige Plan wurde nicht angefasst")
                .isNull();

        // Mit Enddatum vor dem fremden Beginn geht es: zwei Zeitraeume nebeneinander.
        JsonNode ok = call(post("/api/draft/accept").content("""
                {"validFrom":"%s","validUntil":"%s"}"""
                .formatted(IN_ZWEI_WOCHEN, IN_SECHS_WOCHEN.minusDays(1))), 200);
        assertThat(ok.get("validUntil").asText()).isEqualTo(IN_SECHS_WOCHEN.minusDays(1).toString());
        assertThat(getJson("/api/timetables")).hasSize(2);
    }

    @Test
    @DisplayName("Annehmen ab sofort ueber einen erst kuenftig beginnenden Plan hinweg ist 409 plan-overlap")
    void acceptingNowOverAPlanThatHadNotStartedYetIsRejected() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        long kuenftig = call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(IN_SECHS_WOCHEN)), 200).get("id").asLong();

        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        JsonNode problem = call(post("/api/draft/accept").content("{}"), 409);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:plan-overlap");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ARCHIVED'", Long.class))
                .as("ein bewusst fuer spaeter angenommener Plan verschwindet nicht still")
                .isZero();
        assertThat(getJson("/api/timetables")).hasSize(1);
        assertThat(getJson("/api/timetable").get("id").asLong()).isEqualTo(kuenftig);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'DRAFT'", Long.class))
                .as("der Entwurf steht noch")
                .isEqualTo(1L);

        // Mit Enddatum vor dem kuenftigen Beginn geht es: bis dahin der neue, danach der kuenftige Plan.
        JsonNode ok = call(post("/api/draft/accept").content("""
                {"validUntil":"%s"}""".formatted(IN_SECHS_WOCHEN.minusDays(1))), 200);
        assertThat(ok.get("validUntil").asText()).isEqualTo(IN_SECHS_WOCHEN.minusDays(1).toString());
        assertThat(getJson("/api/timetables")).hasSize(2);
        assertThat(getJson("/api/timetable").get("id").asLong()).isEqualTo(ok.get("id").asLong());
    }

    /**
     * Drei Zeitraeume hintereinander: heute, die drei Tage danach, alles Weitere. Alle drei Plaene
     * bleiben aktiv, keiner wird archiviert, und {@code GET /api/timetables} zeigt sie in der
     * Reihenfolge ihrer Beginne.
     */
    @Test
    @DisplayName("Drei anschliessende Zeitraeume stehen nebeneinander; genau der heutige ist current")
    void threeAdjoiningRangesAllStayActive() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        long a = call(post("/api/draft/accept").content("{}"), 200).get("id").asLong();

        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        long b = call(post("/api/draft/accept").content("""
                {"validFrom":"%s","validUntil":"%s"}"""
                .formatted(HEUTE.plusDays(1), HEUTE.plusDays(3))), 200).get("id").asLong();

        call(post("/api/draft").content("""
                {"source":"COPY_ACTIVE"}"""), 201);
        long c = call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(HEUTE.plusDays(4))), 200).get("id").asLong();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ARCHIVED'", Long.class))
                .as("kein Zeitraum ist abgelaufen, also wird nichts archiviert")
                .isZero();

        JsonNode liste = getJson("/api/timetables");
        assertThat(liste).hasSize(3);
        assertThat(liste.get(0).get("id").asLong()).isEqualTo(a);
        assertThat(liste.get(0).get("validFrom").asText()).isEqualTo(HEUTE.toString());
        assertThat(liste.get(0).get("validUntil").asText()).isEqualTo(HEUTE.toString());
        assertThat(liste.get(0).get("current").asBoolean()).isTrue();
        assertThat(liste.get(1).get("id").asLong()).isEqualTo(b);
        assertThat(liste.get(1).get("current").asBoolean()).isFalse();
        assertThat(liste.get(2).get("id").asLong()).isEqualTo(c);
        assertThat(liste.get(2).get("validUntil").isNull()).isTrue();
    }

    /**
     * Der Befund vom 16.09.: ein <b>abgelaufener</b> Plan, der noch auf {@code ACTIVE} steht, blockierte
     * jedes weitere „ab sofort".
     *
     * <p>So entsteht er im Betrieb: Plan A gilt ab sofort, Plan B ab einem spaeteren Tag — A wird auf
     * den Vortag von B verkuerzt und bleibt aktiv, weil er noch laeuft. Wochen spaeter ist A abgelaufen,
     * steht aber unveraendert auf {@code ACTIVE}; es gibt keinen Zeitgeber, der ihn umstellte. Hier wird
     * dieser Zustand mit einem Schreibzugriff hergestellt, statt die Uhr weiterzustellen.</p>
     *
     * <p>Bis zum 16.09. schrieb das Annehmen ohne „Gueltig ab" ein leeres {@code valid_from}. In
     * PostgreSQL ist {@code daterange(NULL, NULL, '[]')} der ganze Zeitstrahl; die Ausschlussbedingung
     * {@code timetable_active_no_overlap} sah eine Ueberschneidung mit A und lehnte ab — waehrend die
     * Pruefung im Dienst A voellig zu Recht in Ruhe liess, weil sein Zeitraum laengst vorbei ist.</p>
     */
    @Test
    @DisplayName("Ein abgelaufener aktiver Plan blockiert „ab sofort“ nicht mehr — er wird archiviert")
    void anExpiredActivePlanIsSweptAwayInsteadOfBlocking() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        long abgelaufen = call(post("/api/draft/accept").content("{}"), 200).get("id").asLong();

        // Der Zustand nach einem Planwechsel, dessen Zeitraum inzwischen vorbei ist. Beide Grenzen
        // wandern in die Vergangenheit: der CHECK timetable_active_valid_range (V12) verbietet einem
        // aktiven Plan ein Ende vor seinem Beginn.
        jdbc.update("UPDATE timetable SET valid_from = ?, valid_until = ? WHERE id = ?",
                java.sql.Date.valueOf(HEUTE.minusDays(10)),
                java.sql.Date.valueOf(HEUTE.minusDays(5)), abgelaufen);
        assertThat(getJson("/api/timetables"))
                .as("bis zum Aufraeumen steht er weiter in der Liste — vergangene Wochen bleiben erreichbar")
                .hasSize(1);
        assertThat(getJson("/api/timetables").get(0).get("current").asBoolean())
                .as("heute gilt er aber nicht mehr")
                .isFalse();

        planMoDiInEntwurf(daten);
        JsonNode neu = call(post("/api/draft/accept").content("{}"), 200);

        assertThat(neu.get("validFrom").asText()).isEqualTo(HEUTE.toString());
        assertThat(jdbc.queryForObject("SELECT status FROM timetable WHERE id = ?", String.class, abgelaufen))
                .as("beim Annehmen wird der abgelaufene Plan nebenbei archiviert")
                .isEqualTo("ARCHIVED");
        assertThat(getJson("/api/timetables")).hasSize(1);
        assertThat(getJson("/api/timetable").get("id").asLong()).isEqualTo(neu.get("id").asLong());
    }

    /**
     * Derselbe Entwurf laesst sich nur einmal annehmen. Das ist der ruhige Teil der Absicherung gegen
     * zwei gleichzeitige Annahmen: {@code accept} liest den Entwurf seit dem 16.09. mit Schreibsperre
     * ({@code SELECT … FOR UPDATE}), die zweite Anfrage wartet und findet danach keinen Entwurf mehr.
     * Ein echtes Wettrennen laesst sich hier nicht verlaesslich herstellen; geprueft wird die Wirkung.
     */
    @Test
    @DisplayName("Ein zweites Annehmen desselben Entwurfs ist 404 — es entsteht kein zweiter Plan")
    void acceptingTheSameDraftTwiceIsNotFound() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        long id = getJson("/api/draft").get("id").asLong();

        call(post("/api/drafts/" + id + "/accept").content("{}"), 200);
        call(post("/api/drafts/" + id + "/accept").content("{}"), 404);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ACTIVE'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM changelog_entry", Long.class))
                .as("auch der Verlauf bekommt nur einen Eintrag")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Ein Beginn in der Vergangenheit ist 400 validation mit Feld validFrom")
    void aStartInThePastIsAValidationError() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        JsonNode problem = call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(HEUTE.minusDays(1))), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("validFrom");
        assertThat(problem.get("errors").get(0).get("message").asText())
                .isEqualTo("Der Beginn darf nicht in der Vergangenheit liegen");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ACTIVE'", Long.class))
                .as("nichts wurde angenommen")
                .isZero();
        // Heute selbst geht — die Grenze liegt beim gestrigen Tag.
        call(post("/api/draft/accept").content("""
                {"validFrom":"%s"}""".formatted(HEUTE)), 200);
    }

    @Test
    @DisplayName("validUntil vor validFrom ist 400 validation mit Feld validUntil")
    void endBeforeStartIsAValidationError() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);

        JsonNode problem = call(post("/api/draft/accept").content("""
                {"validFrom":"%s","validUntil":"%s"}"""
                .formatted(IN_ZWEI_WOCHEN, IN_ZWEI_WOCHEN.minusDays(1))), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("validUntil");
        assertThat(problem.get("errors").get(0).get("message").asText())
                .isEqualTo("Das Ende darf nicht vor dem Beginn liegen");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable WHERE status = 'ACTIVE'", Long.class))
                .isZero();
    }

    // ------------------------------------------------------------------ Ablage (#116)

    @Test
    @DisplayName("Die Ablage sperrt Bearbeiten und Berechnen (409 draft-finished); Annehmen und Oeffnen gehen")
    void aParkedDraftIsFrozenButStillAcceptable() throws Exception {
        Testdaten daten = pflichtenheftGrunddaten();
        planMoDiInEntwurf(daten);
        long id = getJson("/api/draft").get("id").asLong();
        long stunde = getJson("/api/draft").get("lessons").get(0).get("id").asLong();
        assertThat(getJson("/api/drafts").get(0).get("finished").asBoolean()).isFalse();

        JsonNode abgelegt = call(patch("/api/drafts/" + id).content("""
                {"finished":true}"""), 200);
        assertThat(abgelegt.get("finished").asBoolean()).isTrue();
        assertThat(abgelegt.get("name").asText()).as("der Name bleibt").isEqualTo("Entwurf 1");
        assertThat(getJson("/api/drafts").get(0).get("finished").asBoolean()).isTrue();

        JsonNode problem = call(post("/api/drafts/" + id + "/lessons").content("""
                {"classId":%d,"subjectId":%d,"timeslotId":%d,"roomId":%d}"""
                .formatted(daten.klasse9c, daten.mathe, daten.slot("WE", 1), daten.r101)), 409);
        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:draft-finished");
        assertThat(problem.get("detail").asText()).isEqualTo("Der Entwurf liegt in der Ablage. Erst wieder öffnen.");
        call(patch("/api/drafts/" + id + "/lessons/" + stunde).content("""
                {"pinned":false}"""), 409);
        call(delete("/api/drafts/" + id + "/lessons/" + stunde), 409);
        call(post("/api/drafts/" + id + "/solve").content("{}"), 409);
        call(post("/api/drafts/" + id + "/repair").content("""
                {"lessonId":%d}""".formatted(stunde)), 409);

        // Umbenennen geht weiter, Wiederoeffnen auch — und danach ist alles wieder erlaubt.
        assertThat(call(patch("/api/drafts/" + id).content("""
                {"name":"Halbjahr 2"}"""), 200).get("finished").asBoolean()).isTrue();
        assertThat(call(patch("/api/drafts/" + id).content("""
                {"finished":false}"""), 200).get("finished").asBoolean()).isFalse();
        call(delete("/api/drafts/" + id + "/lessons/" + stunde), 200);

        // Annehmen ist auch aus der Ablage heraus erlaubt; der angenommene Plan liegt nie in ihr.
        call(patch("/api/drafts/" + id).content("""
                {"finished":true}"""), 200);
        call(post("/api/drafts/" + id + "/accept").content("{}"), 200);
        assertThat(jdbc.queryForObject(
                "SELECT finished FROM timetable WHERE status = 'ACTIVE'", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("PATCH ohne Feld ist 400 validation")
    void emptyPatchBodyIsAValidationError() throws Exception {
        pflichtenheftGrunddaten();
        long id = call(post("/api/drafts").content("""
                {"source":"EMPTY"}"""), 201).get("id").asLong();

        JsonNode problem = call(patch("/api/drafts/" + id).content("{}"), 400);

        assertThat(problem.get("type").asText()).isEqualTo("urn:pushnpull:problem:validation");
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo("name");
        assertThat(problem.get("errors").get(0).get("message").asText()).isEqualTo("Name oder Ablage angeben");
    }

}
