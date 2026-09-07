package de.wsp3_1.substitution;

import de.wsp3_1.planning.domain.PlanViews.LessonView;
import de.wsp3_1.shared.Weekday;
import de.wsp3_1.substitution.domain.DayFacts;
import de.wsp3_1.substitution.domain.DayFacts.EventFact;
import de.wsp3_1.substitution.domain.DayFacts.ExceptionFact;
import de.wsp3_1.substitution.domain.DayFacts.SlotFact;
import de.wsp3_1.substitution.domain.DayFacts.SubjectFact;
import de.wsp3_1.substitution.domain.EntryKind;
import de.wsp3_1.substitution.domain.EventKind;
import de.wsp3_1.substitution.domain.ExceptionKind;
import de.wsp3_1.substitution.domain.View;
import de.wsp3_1.substitution.domain.WeekResolver;
import de.wsp3_1.substitution.domain.WeekViews.EntryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Wochenaufloesung als Funktion (Teststrategie Ebene 1): Tatsachen hinein, Eintraege heraus —
 * ohne Datenbank, ohne Spring. Grundlage ist der Testdatenbestand des Pflichtenhefts und das
 * Leitszenario aus {@code docs/ABNAHME-CHECKLISTE.md} Abschnitt 1.6.
 *
 * <p>Was hier <b>nicht</b> steht, weil es den Dienst und die Datenbank braucht: Ferientage
 * ({@code WeekIT.holidaysHaveNoEntries}), die Wahl des Plans ueber „Gueltig ab"
 * ({@code WeekIT.threeValidFromDatesResolvePerDay}) und die Raumsicht auf echten Daten
 * ({@code WeekIT.roomViewShowsOnlyThatRoom}).</p>
 */
class WeekResolverTest {

    // Testdaten des Pflichtenhefts: Lehrkraefte Meyer 1, Schulz 2, Weber 3 · Klassen 7a 1, 8b 2, 9c 3
    // · Faecher Ma 1, De 2, Ph 3 · Raeume R101 1, R204 2, P1 3.
    private static final long MEYER = 1;
    private static final long SCHULZ = 2;
    private static final long WEBER = 3;
    private static final long R101 = 1;
    private static final long R204 = 2;
    private static final long P1 = 3;

    private static final LocalDate MONTAG = LocalDate.of(2026, 9, 21);
    private static final LocalDate DIENSTAG = MONTAG.plusDays(1);
    private static final LocalDate MITTWOCH = MONTAG.plusDays(2);
    private static final LocalDate NAECHSTER_MONTAG = MONTAG.plusDays(7);

    private static final EventFact KRANKMELDUNG =
            new EventFact(1L, EventKind.TEACHER_ABSENCE, MEYER, null, "Krankmeldung", MONTAG, MONTAG);
    private static final EventFact WASSERSCHADEN =
            new EventFact(2L, EventKind.ROOM_BLOCKED, null, R101, "Wasserschaden", MONTAG, MONTAG);
    private static final EventFact NACHHOLSTUNDE =
            new EventFact(3L, EventKind.EXTRA_LESSON, null, null, "Nachholstunde", MITTWOCH, MITTWOCH);

    // ------------------------------------------------------------------ W-4: Regelplan

    @Test
    @DisplayName("Ohne Ereignisse ist jeder Eintrag REGULAR; Stunden anderer Wochentage fehlen")
    void plainPlanIsAllRegular() {
        List<EntryView> montag = WeekResolver.resolveDay(facts(MONTAG, List.of(), List.of()));

        assertThat(montag).hasSize(6);
        assertThat(montag).allSatisfy(entry -> {
            assertThat(entry.kind()).isEqualTo(EntryKind.REGULAR);
            assertThat(entry.original()).isNull();
            assertThat(entry.reasons()).isEmpty();
            assertThat(entry.exceptionId()).isNull();
            assertThat(entry.day()).isEqualTo(Weekday.MO);
        });
        // Nach Position, bei gleicher Position nach Klasse.
        assertThat(positionen(montag)).containsExactly(1, 2, 2, 4, 4, 5);
        assertThat(montag.get(1).classShort()).isEqualTo("7a");
        assertThat(montag.get(2).classShort()).isEqualTo("8b");
    }

    // ------------------------------------------------------------------ T-W5.1

    @Test
    @DisplayName("T-W5.1: Ausfall Meyer am 21.09. — genau vier NEEDS_SUBSTITUTION, am 22.09. keiner")
    void tW5_1_absence21st_fourPending_noneOn22nd_regularPlanUnchanged() {
        List<EntryView> montag =
                WeekResolver.resolveDay(facts(MONTAG, List.of(KRANKMELDUNG), List.of()));

        List<EntryView> offen = montag.stream()
                .filter(entry -> entry.kind() == EntryKind.NEEDS_SUBSTITUTION).toList();
        assertThat(positionen(offen)).containsExactly(1, 2, 4, 5);
        assertThat(offen).allSatisfy(entry -> {
            assertThat(entry.teacherShort()).isEqualTo("MEY");
            assertThat(entry.reasons()).containsExactly("Krankmeldung");
        });

        // Die Deutschstunde der 7a in der 2. Stunde gehoert Weber und bleibt unberuehrt.
        EntryView weberStunde = montag.stream()
                .filter(entry -> "WEB".equals(entry.teacherShort()) && entry.position() == 2)
                .findFirst().orElseThrow();
        assertThat(weberStunde.kind()).isEqualTo(EntryKind.REGULAR);
        assertThat(weberStunde.reasons()).isEmpty();

        // Am Dienstag wirkt das Ereignis nicht: sein Zeitraum ist nur der 21.09.
        List<EntryView> dienstag = WeekResolver.resolveDay(facts(DIENSTAG, List.of(), List.of()));
        assertThat(dienstag).hasSize(1);
        assertThat(dienstag.getFirst().kind()).isEqualTo(EntryKind.REGULAR);
    }

    @Test
    @DisplayName("Zwei Ereignisse auf derselben Stunde: beide Anlaesse, in der Reihenfolge der Ereignisse")
    void twoEventsGiveTwoReasons() {
        List<EntryView> montag =
                WeekResolver.resolveDay(facts(MONTAG, List.of(KRANKMELDUNG, WASSERSCHADEN), List.of()));

        assertThat(montag.stream().filter(e -> e.kind() == EntryKind.NEEDS_SUBSTITUTION)).hasSize(5);
        assertThat(eintrag(montag, 1L).reasons()).containsExactly("Krankmeldung", "Wasserschaden");
        // 7a Deutsch in der 2. Stunde liegt in R101 und ist nur wegen des Wasserschadens offen.
        assertThat(eintrag(montag, 2L).reasons()).containsExactly("Wasserschaden");
    }

    // ------------------------------------------------------------------ T-A2.1

    @Test
    @DisplayName("T-A2.1: Vertretung — SUBSTITUTED fuer Schulz, GIVEN_AWAY fuer Meyer, am 28.09. wieder REGULAR")
    void tA2_1_kinds_substitutedForSchulz_givenAwayAndPendingForMeyer_regularOn28th() {
        ExceptionFact vertretung = new ExceptionFact(1L, 1L, MONTAG, ExceptionKind.SUBSTITUTION,
                SCHULZ, R204, null, null, null, null, List.of(1L, 2L));
        List<EntryView> montag = WeekResolver.resolveDay(
                facts(MONTAG, List.of(KRANKMELDUNG, WASSERSCHADEN), List.of(vertretung)));

        EntryView vertreten = eintrag(montag, 1L);
        assertThat(vertreten.kind()).isEqualTo(EntryKind.SUBSTITUTED);
        assertThat(vertreten.teacherShort()).isEqualTo("SCZ");
        assertThat(vertreten.roomShort()).isEqualTo("R204");
        assertThat(vertreten.original().teacherShort()).isEqualTo("MEY");
        assertThat(vertreten.original().roomShort()).isEqualTo("R101");
        assertThat(vertreten.reasons()).containsExactly("Krankmeldung", "Wasserschaden");
        assertThat(vertreten.exceptionId()).isEqualTo(1L);

        // Schulz sieht die uebernommene Stunde in ihrem Plan.
        List<EntryView> beiSchulz = WeekResolver.filterView(montag, View.TEACHER, SCHULZ);
        assertThat(beiSchulz).hasSize(1);
        assertThat(beiSchulz.getFirst().kind()).isEqualTo(EntryKind.SUBSTITUTED);
        assertThat(beiSchulz.getFirst().classShort()).isEqualTo("7a");

        // Meyer sieht sie als abgegeben — und ihre drei uebrigen Stunden als offen.
        List<EntryView> beiMeyer = WeekResolver.filterView(montag, View.TEACHER, MEYER);
        assertThat(positionen(beiMeyer)).containsExactly(1, 2, 4, 5);
        assertThat(beiMeyer.getFirst().kind()).isEqualTo(EntryKind.GIVEN_AWAY);
        assertThat(beiMeyer.getFirst().teacherShort()).isEqualTo("SCZ");
        assertThat(beiMeyer.getFirst().original().teacherShort()).isEqualTo("MEY");
        assertThat(beiMeyer.subList(1, 4)).allSatisfy(entry ->
                assertThat(entry.kind()).isEqualTo(EntryKind.NEEDS_SUBSTITUTION));

        // Eine Woche spaeter gilt wieder der Regelplan: dieselbe Stunde, Meyer, R101.
        List<EntryView> spaeter = WeekResolver.resolveDay(facts(NAECHSTER_MONTAG, List.of(), List.of()));
        assertThat(spaeter).allSatisfy(entry -> assertThat(entry.kind()).isEqualTo(EntryKind.REGULAR));
        assertThat(eintrag(spaeter, 1L).teacherShort()).isEqualTo("MEY");
        assertThat(eintrag(spaeter, 1L).roomShort()).isEqualTo("R101");
    }

    /**
     * Der Fall, der die Reihenfolge der Schritte braucht: Schulz vertritt Meyer, und danach faellt
     * Schulz aus. Ohne den fuenften Schritt (Ereignisse gegen die <i>aufgeloeste</i> Belegung) stuende
     * die Stunde weiter als vertreten da, obwohl niemand unterrichtet.
     */
    @Test
    @DisplayName("Faellt die Vertretungslehrkraft ebenfalls aus, ist die Stunde wieder offen")
    void substituteAbsentReopensTheLesson() {
        EventFact schulzFaelltAus = new EventFact(4L, EventKind.TEACHER_ABSENCE, SCHULZ, null,
                "Fortbildung", MONTAG, MONTAG);
        ExceptionFact vertretung = new ExceptionFact(1L, 1L, MONTAG, ExceptionKind.SUBSTITUTION,
                SCHULZ, R204, null, null, null, null, List.of(1L));

        EntryView eintrag = eintrag(WeekResolver.resolveDay(
                facts(MONTAG, List.of(KRANKMELDUNG, schulzFaelltAus), List.of(vertretung))), 1L);

        assertThat(eintrag.kind()).isEqualTo(EntryKind.NEEDS_SUBSTITUTION);
        assertThat(eintrag.teacherShort()).isEqualTo("SCZ");
        assertThat(eintrag.reasons()).containsExactly("Krankmeldung", "Fortbildung");
        // Die Ausnahme bleibt an der Zelle: die Planung hebt sie auf und traegt eine neue ein.
        assertThat(eintrag.exceptionId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Ist der Vertretungsraum gesperrt, ist die Stunde ebenfalls wieder offen")
    void blockedSubstituteRoomReopensTheLesson() {
        EventFact r204Gesperrt = new EventFact(5L, EventKind.ROOM_BLOCKED, null, R204, "Malerarbeiten",
                MONTAG, MONTAG);
        ExceptionFact vertretung = new ExceptionFact(1L, 1L, MONTAG, ExceptionKind.SUBSTITUTION,
                SCHULZ, R204, null, null, null, null, List.of(2L));

        EntryView eintrag = eintrag(WeekResolver.resolveDay(
                facts(MONTAG, List.of(WASSERSCHADEN, r204Gesperrt), List.of(vertretung))), 1L);

        assertThat(eintrag.kind()).isEqualTo(EntryKind.NEEDS_SUBSTITUTION);
        assertThat(eintrag.roomShort()).isEqualTo("R204");
        assertThat(eintrag.reasons()).contains("Malerarbeiten");
    }

    @Test
    @DisplayName("Bleibt der gesperrte Raum stehen und wechselt nur die Lehrkraft, bleibt die Stunde offen")
    void teacherOnlySubstitutionInABlockedRoomStaysPending() {
        // Meyer wird durch Schulz ersetzt, R101 bleibt — und R101 ist gesperrt.
        ExceptionFact nurLehrkraft = new ExceptionFact(1L, 1L, MONTAG, ExceptionKind.SUBSTITUTION,
                SCHULZ, null, null, null, null, null, List.of(1L, 2L));

        EntryView eintrag = eintrag(WeekResolver.resolveDay(
                facts(MONTAG, List.of(KRANKMELDUNG, WASSERSCHADEN), List.of(nurLehrkraft))), 1L);

        assertThat(eintrag.kind()).isEqualTo(EntryKind.NEEDS_SUBSTITUTION);
        assertThat(eintrag.teacherShort()).isEqualTo("SCZ");
        assertThat(eintrag.roomShort()).isEqualTo("R101");
        assertThat(eintrag.reasons()).containsExactly("Krankmeldung", "Wasserschaden");
    }

    @Test
    @DisplayName("Nur der Raum wechselt: SUBSTITUTED, aber original nennt allein den Raum")
    void roomOnlySubstitutionKeepsTheTeacher() {
        ExceptionFact raumwechsel = new ExceptionFact(4L, 1L, MONTAG, ExceptionKind.SUBSTITUTION,
                null, R204, null, null, null, null, List.of(2L));
        EntryView eintrag = eintrag(WeekResolver.resolveDay(
                facts(MONTAG, List.of(WASSERSCHADEN), List.of(raumwechsel))), 1L);

        assertThat(eintrag.kind()).isEqualTo(EntryKind.SUBSTITUTED);
        assertThat(eintrag.teacherShort()).isEqualTo("MEY");
        assertThat(eintrag.roomShort()).isEqualTo("R204");
        assertThat(eintrag.original().teacherShort()).isNull();
        assertThat(eintrag.original().roomShort()).isEqualTo("R101");
    }

    // ------------------------------------------------------------------ Entfall und Verlegung

    @Test
    @DisplayName("Entfall: die Stunde bleibt sichtbar und heisst CANCELLED")
    void cancellationStaysVisible() {
        ExceptionFact entfall = new ExceptionFact(5L, 1L, MONTAG, ExceptionKind.CANCEL,
                null, null, null, null, null, null, List.of(1L));

        EntryView eintrag = eintrag(
                WeekResolver.resolveDay(facts(MONTAG, List.of(KRANKMELDUNG), List.of(entfall))), 1L);

        assertThat(eintrag.kind()).isEqualTo(EntryKind.CANCELLED);
        assertThat(eintrag.reasons()).containsExactly("Krankmeldung");
    }

    @Test
    @DisplayName("Verlegung: am Ursprungstag CANCELLED mit Ziel im Grund, am Zieltag MOVED")
    void moveCreatesCancelledAndMoved() {
        // Die Deutschstunde der 7a (Montag, 2. Stunde) wandert auf Mittwoch, 1. Stunde.
        ExceptionFact verlegung = new ExceptionFact(6L, 2L, MONTAG, ExceptionKind.MOVE,
                null, null, MITTWOCH, slot(Weekday.WE, 1), null, null, List.of());

        EntryView ursprung = eintrag(
                WeekResolver.resolveDay(facts(MONTAG, List.of(), List.of(verlegung))), 2L);
        assertThat(ursprung.kind()).isEqualTo(EntryKind.CANCELLED);
        assertThat(ursprung.reasons()).containsExactly("verlegt → Mi 1.");

        DayFacts mittwoch = facts(MITTWOCH, List.of(), List.of(verlegung));
        List<EntryView> ziel = WeekResolver.resolveDay(mittwoch);
        assertThat(ziel).hasSize(1);
        EntryView verlegt = ziel.getFirst();
        assertThat(verlegt.kind()).isEqualTo(EntryKind.MOVED);
        assertThat(verlegt.lessonId()).isEqualTo(2L);
        assertThat(verlegt.day()).isEqualTo(Weekday.WE);
        assertThat(verlegt.position()).isEqualTo(1);
        assertThat(verlegt.subjectShort()).isEqualTo("De");
        assertThat(verlegt.original().date()).isEqualTo(MONTAG);
        assertThat(verlegt.original().position()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ Zusatzstunde

    @Test
    @DisplayName("Zusatzstunde: ADDED ohne Stundenkennung, mit dem Anlass des Ereignisses")
    void extraLessonBecomesAdded() {
        ExceptionFact zusatz = new ExceptionFact(7L, null, MITTWOCH, ExceptionKind.ADDED,
                SCHULZ, R204, MITTWOCH, slot(Weekday.WE, 2), 3L, 1L, List.of(3L));

        List<EntryView> mittwoch = WeekResolver.resolveDay(facts(MITTWOCH, List.of(), List.of(zusatz)));

        assertThat(mittwoch).hasSize(1);
        EntryView eintrag = mittwoch.getFirst();
        assertThat(eintrag.kind()).isEqualTo(EntryKind.ADDED);
        assertThat(eintrag.lessonId()).isNull();
        assertThat(eintrag.classShort()).isEqualTo("9c");
        assertThat(eintrag.subjectShort()).isEqualTo("Ma");
        assertThat(eintrag.teacherShort()).isEqualTo("SCZ");
        assertThat(eintrag.roomShort()).isEqualTo("R204");
        assertThat(eintrag.position()).isEqualTo(2);
        assertThat(eintrag.reasons()).containsExactly("Nachholstunde");
    }

    // ------------------------------------------------------------------ Sichten

    @Test
    @DisplayName("Klassensicht und Raumsicht zeigen nur ihre eigenen Zellen")
    void classAndRoomViewsFilter() {
        List<EntryView> montag = WeekResolver.resolveDay(facts(MONTAG, List.of(), List.of()));

        assertThat(WeekResolver.filterView(montag, View.CLASS, 1L))
                .allSatisfy(entry -> assertThat(entry.classShort()).isEqualTo("7a"))
                .hasSize(3);
        assertThat(positionen(WeekResolver.filterView(montag, View.ROOM, R101))).containsExactly(1, 2);
        assertThat(positionen(WeekResolver.filterView(montag, View.ROOM, P1))).containsExactly(4);
        assertThat(WeekResolver.filterView(montag, View.SCHOOL, null)).hasSize(6);
    }

    // ------------------------------------------------------------------ Testdaten

    /** Der Regelplan Montag/Dienstag des Pflichtenhefts — dieselben sieben Stunden wie in P-1. */
    private static List<LessonView> plan() {
        return List.of(
                lesson(1, 1, "7a", 1, "Ma", MEYER, "MEY", R101, "R101", Weekday.MO, 1),
                lesson(2, 1, "7a", 2, "De", WEBER, "WEB", R101, "R101", Weekday.MO, 2),
                lesson(3, 1, "7a", 3, "Ph", MEYER, "MEY", P1, "P1", Weekday.MO, 4),
                lesson(4, 2, "8b", 1, "Ma", MEYER, "MEY", R204, "R204", Weekday.MO, 2),
                lesson(5, 2, "8b", 2, "De", WEBER, "WEB", R204, "R204", Weekday.MO, 4),
                lesson(6, 2, "8b", 1, "Ma", MEYER, "MEY", R204, "R204", Weekday.MO, 5),
                lesson(7, 1, "7a", 1, "Ma", MEYER, "MEY", R101, "R101", Weekday.TU, 1));
    }

    private static LessonView lesson(long id, long classId, String classShort, long subjectId,
                                     String subjectShort, long teacherId, String teacherShort,
                                     long roomId, String roomShort, Weekday day, int position) {
        return new LessonView(id, classId, classShort, subjectId, subjectShort, "#1976D2", teacherId,
                teacherShort, roomId, roomShort, slot(day, position), day, position, true);
    }

    /**
     * Die Tatsachen eines Tages. Der Plan ist immer derselbe — „welcher Plan gilt an diesem Tag"
     * beantwortet der {@code PlanReader}, und das gehoert in einen Integrationstest ({@code WeekIT}).
     */
    private static DayFacts facts(LocalDate date, List<EventFact> events, List<ExceptionFact> exceptions) {
        Map<Long, String> eventReasons = new LinkedHashMap<>();
        for (EventFact event : List.of(KRANKMELDUNG, WASSERSCHADEN, NACHHOLSTUNDE)) {
            eventReasons.put(event.id(), event.reason());
        }
        // Ereignisse, die nur ein einzelner Test kennt, bringen ihren Anlass selbst mit.
        for (EventFact event : events) {
            eventReasons.put(event.id(), event.reason());
        }
        Map<Long, LessonView> movedOrigins = new LinkedHashMap<>();
        for (ExceptionFact exception : exceptions) {
            if (exception.kind() == ExceptionKind.MOVE) {
                plan().stream().filter(lesson -> lesson.id().equals(exception.lessonId()))
                        .findFirst().ifPresent(lesson -> movedOrigins.put(lesson.id(), lesson));
            }
        }
        return new DayFacts(date, WeekResolver.weekdayOf(date), plan(), events, exceptions, eventReasons,
                movedOrigins, slots(),
                Map.of(MEYER, "MEY", SCHULZ, "SCZ", WEBER, "WEB"),
                Map.of(R101, "R101", R204, "R204", P1, "P1"),
                Map.of(1L, "7a", 2L, "8b", 3L, "9c"),
                Map.of(1L, new SubjectFact(1L, "Ma", "#1976D2"),
                        2L, new SubjectFact(2L, "De", "#388E3C"),
                        3L, new SubjectFact(3L, "Ph", "#F57C00")));
    }

    /** Raster Mo–Fr mit sieben Positionen; Position 3 ist die grosse Pause (G-3). */
    private static Map<Long, SlotFact> slots() {
        Map<Long, SlotFact> slots = new LinkedHashMap<>();
        Weekday[] days = {Weekday.MO, Weekday.TU, Weekday.WE, Weekday.TH, Weekday.FR};
        for (Weekday day : days) {
            int lessonNumber = 0;
            for (int position = 1; position <= 7; position++) {
                boolean isBreak = position == 3;
                Integer number = null;
                if (!isBreak) {
                    lessonNumber++;
                    number = lessonNumber;
                }
                long id = slot(day, position);
                slots.put(id, new SlotFact(id, day, position, isBreak, number));
            }
        }
        return slots;
    }

    /** Die Kennung eines Zeitfensters — wie im Testdatenbestand: Mo 1–7, Di 8–14, Mi 15–21, … */
    private static long slot(Weekday day, int position) {
        return day.ordinal() * 7L + position;
    }

    private static EntryView eintrag(List<EntryView> entries, Long lessonId) {
        return entries.stream().filter(entry -> lessonId.equals(entry.lessonId()))
                .findFirst().orElseThrow();
    }

    private static List<Integer> positionen(List<EntryView> entries) {
        return entries.stream().map(EntryView::position).toList();
    }
}
