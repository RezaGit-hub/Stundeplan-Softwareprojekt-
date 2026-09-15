package de.wsp3_1.planning;

import de.wsp3_1.planning.domain.Assignment;
import de.wsp3_1.planning.domain.CheckContext;
import de.wsp3_1.planning.domain.CheckContext.ClassInfo;
import de.wsp3_1.planning.domain.CheckContext.RoomInfo;
import de.wsp3_1.planning.domain.CheckContext.SlotInfo;
import de.wsp3_1.planning.domain.CheckContext.SubjectInfo;
import de.wsp3_1.planning.domain.CheckContext.TeacherInfo;
import de.wsp3_1.planning.domain.ConflictChecker;
import de.wsp3_1.planning.domain.ConflictType;
import de.wsp3_1.planning.domain.ConflictView;
import de.wsp3_1.planning.domain.ObjectKind;
import de.wsp3_1.rules.domain.EffectiveRule;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.Weekday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Konfliktpruefung ohne Datenbank und ohne Spring (Teststrategie Ebene 1). Grundlage ist der
 * Testdatenbestand des Pflichtenhefts: Meyer (Ma, Ph), Schulz (Ma, Freitag 5./6. gesperrt), Weber (De);
 * Klassen 7a, 8b, 9c; Raeume R101, R204 (Klassenraum) und P1 (Fachraum Physik); Raster Mo–Fr mit sieben
 * Positionen, Position 3 ist die grosse Pause.
 *
 * <p>Zeitfenster-Kennung = {@code Tagindex * 7 + Position}: Montag 1. Stunde ist 1, Dienstag 1. Stunde
 * ist 8, Freitag 5. Stunde ist 34 — dieselben Zahlen wie im Vertrag und in der Abnahme-Checkliste.</p>
 */
class ConflictCheckerTest {

    private static final long MEYER = 1;
    private static final long SCHULZ = 2;
    private static final long WEBER = 3;
    private static final long MATHE = 1;
    private static final long DEUTSCH = 2;
    private static final long PHYSIK = 3;
    private static final long KLASSE_7A = 1;
    private static final long KLASSE_8B = 2;
    private static final long KLASSE_9C = 3;
    private static final long R101 = 1;
    private static final long R204 = 2;
    private static final long P1 = 3;

    /** Regel „hoechstens n Stunden pro Tag" fuer eine Klasse, wie der RuleResolver sie liefert. */
    private static EffectiveRule maxPerDay(long classId, int value, Severity severity) {
        return new EffectiveRule(10L, RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, classId, value,
                severity, 1, "Maximale Stunden pro Tag");
    }

    private static EffectiveRule maxPerWeekForTeacher(long teacherId, int value) {
        return new EffectiveRule(12L, RuleType.MAX_LESSONS_PER_WEEK, RuleScope.TEACHER, teacherId, value,
                Severity.SOFT, 1, "Maximale Stunden pro Woche");
    }

    private static EffectiveRule noLessonsInBreaks() {
        return new EffectiveRule(14L, RuleType.NO_LESSONS_IN_BREAKS, null, null, 1, Severity.HARD, 1,
                "Keine Stunden in Pausen");
    }

    // ------------------------------------------------------------------ T-W3.1 / T-W3.2

    @Test
    @DisplayName("T-W3.1: 9c in R101 zur selben Zeit wie 7a → genau ein Raumkonflikt, kein Lehrkraftkonflikt")
    void tW3_1_roomDoubleBooked_reportsExactlyOneConflict() {
        List<Assignment> plan = List.of(
                lesson(11, KLASSE_7A, MATHE, MEYER, R101, slot("MO", 1)),
                lesson(18, KLASSE_9C, MATHE, SCHULZ, R101, slot("MO", 1)));

        List<ConflictView> conflicts = ConflictChecker.check(plan, context(List.of()));

        assertThat(conflicts).hasSize(1);
        ConflictView conflict = conflicts.getFirst();
        assertThat(conflict.type()).isEqualTo(ConflictType.ROOM_DOUBLE_BOOKED);
        assertThat(conflict.severity()).isEqualTo(Severity.HARD);
        assertThat(conflict.lessonIds()).containsExactly(11L, 18L);
        assertThat(conflict.ruleId()).isNull();
        assertThat(conflict.objects()).containsExactly(
                new ConflictView.ConflictObjectView(ObjectKind.ROOM, R101, "R101"));
        assertThat(conflict.message())
                .isEqualTo("Raum R101 ist am Montag in der 1. Stunde doppelt belegt (7a, 9c).");
        assertThat(conflicts).noneMatch(c -> c.type() == ConflictType.TEACHER_DOUBLE_BOOKED);
    }

    @Test
    @DisplayName("T-W3.2: dieselbe Stunde auf Montag 5. Stunde verschoben → keine Konflikte mehr")
    void tW3_2_moveLesson_conflictGoneOnBoth() {
        List<Assignment> plan = List.of(
                lesson(11, KLASSE_7A, MATHE, MEYER, R101, slot("MO", 1)),
                lesson(18, KLASSE_9C, MATHE, SCHULZ, R101, slot("MO", 6)));

        assertThat(ConflictChecker.check(plan, context(List.of()))).isEmpty();
    }

    // ------------------------------------------------------------------ T-W1.2

    @Test
    @DisplayName("T-W1.2: Stunde in der gesperrten Zeit von Schulz → genau ein Konflikt mit Freitag 5. Stunde")
    void tW1_2_blockedTime_exactlyOneConflict_lessonSaved() {
        List<Assignment> plan = List.of(
                lesson(19, KLASSE_9C, MATHE, SCHULZ, R101, slot("FR", 6)));

        List<ConflictView> conflicts = ConflictChecker.check(plan, context(List.of()));

        assertThat(conflicts).hasSize(1);
        ConflictView conflict = conflicts.getFirst();
        assertThat(conflict.type()).isEqualTo(ConflictType.TEACHER_BLOCKED_TIME);
        assertThat(conflict.message()).isEqualTo("Gesperrte Zeit der Lehrkraft: Schulz, Freitag 5. Stunde");
        assertThat(conflict.objects()).containsExactly(
                new ConflictView.ConflictObjectView(ObjectKind.TEACHER, SCHULZ, "SCZ"));
    }

    // ------------------------------------------------------------------ T-W2.1

    @Test
    @DisplayName("T-W2.1: Regel hoechstens 3 pro Tag bei vier Montagsstunden der 7a → ein weicher Verstoss")
    void tW2_1_maxPerDay3_violationNamesRuleValueClassAndDay_fourLessonsKept() {
        List<Assignment> plan = List.of(
                lesson(11, KLASSE_7A, MATHE, MEYER, R101, slot("MO", 1)),
                lesson(12, KLASSE_7A, DEUTSCH, WEBER, R101, slot("MO", 2)),
                lesson(13, KLASSE_7A, PHYSIK, MEYER, P1, slot("MO", 4)),
                lesson(20, KLASSE_7A, DEUTSCH, WEBER, R101, slot("MO", 5)));

        List<ConflictView> conflicts = ConflictChecker.check(plan,
                context(List.of(maxPerDay(KLASSE_7A, 3, Severity.SOFT))));

        assertThat(conflicts).hasSize(1);
        ConflictView conflict = conflicts.getFirst();
        assertThat(conflict.type()).isEqualTo(ConflictType.RULE_VIOLATION);
        assertThat(conflict.severity()).isEqualTo(Severity.SOFT);
        assertThat(conflict.ruleId()).isEqualTo(10L);
        assertThat(conflict.message())
                .isEqualTo("Maximale Stunden pro Tag (3) überschritten: Klasse 7a, Montag: 4 Stunden");
        assertThat(conflict.objects()).containsExactly(
                new ConflictView.ConflictObjectView(ObjectKind.CLASS, KLASSE_7A, "7a"));
        assertThat(conflict.lessonIds()).containsExactly(11L, 12L, 13L, 20L);
    }

    @Test
    @DisplayName("Dieselbe Regel mit Wert 6 meldet nichts — der Wert ist Daten, kein Code")
    void maxPerDaySix_noViolation() {
        List<Assignment> plan = List.of(
                lesson(11, KLASSE_7A, MATHE, MEYER, R101, slot("MO", 1)),
                lesson(12, KLASSE_7A, DEUTSCH, WEBER, R101, slot("MO", 2)));

        assertThat(ConflictChecker.check(plan, context(List.of(maxPerDay(KLASSE_7A, 6, Severity.SOFT)))))
                .isEmpty();
    }

    @Test
    @DisplayName("Wochenregel je Lehrkraft: Meyer mit drei Stunden bei Hoechstwert 2")
    void maxPerWeekForTeacher_namesTeacherAndCount() {
        List<Assignment> plan = List.of(
                lesson(11, KLASSE_7A, MATHE, MEYER, R101, slot("MO", 1)),
                lesson(13, KLASSE_7A, PHYSIK, MEYER, P1, slot("MO", 4)),
                lesson(17, KLASSE_7A, MATHE, MEYER, R101, slot("TU", 1)));

        List<ConflictView> conflicts = ConflictChecker.check(plan,
                context(List.of(maxPerWeekForTeacher(MEYER, 2))));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().message())
                .isEqualTo("Maximale Stunden pro Woche (2) überschritten: Lehrkraft Meyer: 3 Stunden");
        assertThat(conflicts.getFirst().objects()).containsExactly(
                new ConflictView.ConflictObjectView(ObjectKind.TEACHER, MEYER, "MEY"));
    }

    // ------------------------------------------------------------------ Uebrige Konfliktarten

    @Test
    @DisplayName("Klasse doppelt belegt: zwei Faecher der 7a im selben Zeitfenster")
    void classDoubleBooked_namesBothSubjects() {
        List<Assignment> plan = List.of(
                lesson(11, KLASSE_7A, MATHE, MEYER, R101, slot("MO", 1)),
                lesson(12, KLASSE_7A, DEUTSCH, WEBER, R204, slot("MO", 1)));

        List<ConflictView> conflicts = ConflictChecker.check(plan, context(List.of()));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().type()).isEqualTo(ConflictType.CLASS_DOUBLE_BOOKED);
        assertThat(conflicts.getFirst().message())
                .isEqualTo("Klasse 7a ist am Montag in der 1. Stunde doppelt belegt (Ma, De).");
    }

    @Test
    @DisplayName("Lehrkraft doppelt belegt: Meyer zur selben Zeit in 7a und 8b")
    void teacherDoubleBooked_namesBothClasses() {
        List<Assignment> plan = List.of(
                lesson(11, KLASSE_7A, MATHE, MEYER, R101, slot("MO", 1)),
                lesson(14, KLASSE_8B, MATHE, MEYER, R204, slot("MO", 1)));

        List<ConflictView> conflicts = ConflictChecker.check(plan, context(List.of()));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().type()).isEqualTo(ConflictType.TEACHER_DOUBLE_BOOKED);
        assertThat(conflicts.getFirst().message())
                .isEqualTo("Lehrkraft Meyer ist am Montag in der 1. Stunde doppelt belegt (7a, 8b).");
    }

    @Test
    @DisplayName("Raumart: Physik im Klassenraum R101 statt im Fachraum P1")
    void roomTypeMismatch_namesSubjectAndRoom() {
        List<Assignment> plan = List.of(
                lesson(13, KLASSE_7A, PHYSIK, MEYER, R101, slot("MO", 4)));

        List<ConflictView> conflicts = ConflictChecker.check(plan, context(List.of()));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().type()).isEqualTo(ConflictType.ROOM_TYPE_MISMATCH);
        assertThat(conflicts.getFirst().message())
                .isEqualTo("Physik braucht Fachraum Physik, R101 ist Klassenraum");
    }

    @Test
    @DisplayName("#133: Stunde in der gesperrten Zeit von R204 → genau ein Konflikt ROOM_BLOCKED_TIME")
    void roomBlockedTime_namesRoomAndSlot() {
        List<Assignment> plan = List.of(
                lesson(14, KLASSE_8B, MATHE, MEYER, R204, slot("MO", 4)),
                lesson(11, KLASSE_7A, MATHE, MEYER, R101, slot("MO", 2)));

        List<ConflictView> conflicts = ConflictChecker.check(plan, context(List.of()));

        assertThat(conflicts).hasSize(1);
        ConflictView conflict = conflicts.getFirst();
        assertThat(conflict.type()).isEqualTo(ConflictType.ROOM_BLOCKED_TIME);
        assertThat(conflict.severity()).isEqualTo(Severity.HARD);
        assertThat(conflict.lessonIds()).containsExactly(14L);
        assertThat(conflict.message()).isEqualTo("Raum R204 ist am Montag in der 3. Stunde gesperrt.");
        assertThat(conflict.objects()).containsExactly(
                new ConflictView.ConflictObjectView(ObjectKind.ROOM, R204, "R204"));
    }

    @Test
    @DisplayName("Pausenregel: eine Stunde in der grossen Pause ist ein harter Verstoss")
    void breakRule_namesClassAndTime() {
        List<Assignment> plan = List.of(
                lesson(11, KLASSE_7A, MATHE, MEYER, R101, slot("MO", 3)));

        List<ConflictView> conflicts = ConflictChecker.check(plan, context(List.of(noLessonsInBreaks())));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().type()).isEqualTo(ConflictType.RULE_VIOLATION);
        assertThat(conflicts.getFirst().severity()).isEqualTo(Severity.HARD);
        assertThat(conflicts.getFirst().message())
                .isEqualTo("Stunde liegt in der Pause: 7a, Montag 09:35–09:55");
    }

    @Test
    @DisplayName("Offene Stunden erzeugen nie einen Konflikt")
    void openLessonsAreNeverInConflict() {
        List<Assignment> plan = List.of(
                new Assignment(11L, KLASSE_7A, MATHE, MEYER, null, null, false),
                new Assignment(12L, KLASSE_7A, DEUTSCH, WEBER, null, null, false));

        assertThat(ConflictChecker.check(plan, context(List.of(maxPerDay(KLASSE_7A, 1, Severity.HARD))))).isEmpty();
    }

    // ------------------------------------------------------------------ Testdatenbestand

    private static Assignment lesson(long id, long classId, long subjectId, long teacherId, long roomId,
                                     long timeslotId) {
        return new Assignment(id, classId, subjectId, teacherId, roomId, timeslotId, false);
    }

    /** Zeitfenster-Kennung aus Tag und Position (Position 3 ist die grosse Pause). */
    private static long slot(String day, int position) {
        return List.of("MO", "TU", "WE", "TH", "FR").indexOf(day) * 7L + position;
    }

    private static CheckContext context(List<EffectiveRule> rules) {
        return new CheckContext(timegrid(),
                Map.of(KLASSE_7A, new ClassInfo(KLASSE_7A, "7a", 7),
                        KLASSE_8B, new ClassInfo(KLASSE_8B, "8b", 8),
                        KLASSE_9C, new ClassInfo(KLASSE_9C, "9c", 9)),
                Map.of(MEYER, new TeacherInfo(MEYER, "Meyer", "MEY", Set.of()),
                        SCHULZ, new TeacherInfo(SCHULZ, "Schulz", "SCZ", Set.of(slot("FR", 6), slot("FR", 7))),
                        WEBER, new TeacherInfo(WEBER, "Weber", "WEB", Set.of())),
                Map.of(MATHE, new SubjectInfo(MATHE, "Mathematik", "Ma", null),
                        DEUTSCH, new SubjectInfo(DEUTSCH, "Deutsch", "De", null),
                        PHYSIK, new SubjectInfo(PHYSIK, "Physik", "Ph", "Fachraum Physik")),
                // R204 ist Montag 3. Stunde (Position 4) gesperrt (#133).
                Map.of(R101, new RoomInfo(R101, "R101", "Klassenraum", Set.of()),
                        R204, new RoomInfo(R204, "R204", "Klassenraum", Set.of(slot("MO", 4))),
                        P1, new RoomInfo(P1, "P1", "Fachraum Physik", Set.of())),
                rules);
    }

    /** Mo–Fr, sieben Positionen, Position 3 ist die grosse Pause (G-3). */
    private static Map<Long, SlotInfo> timegrid() {
        String[][] times = {
                {"08:00", "08:45"}, {"08:50", "09:35"}, {"09:35", "09:55"}, {"09:55", "10:40"},
                {"10:45", "11:30"}, {"11:50", "12:35"}, {"12:40", "13:25"}};
        List<Weekday> days = new ArrayList<>(List.of(Weekday.MO, Weekday.TU, Weekday.WE, Weekday.TH, Weekday.FR));
        Map<Long, SlotInfo> grid = new LinkedHashMap<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            int lessonNumber = 0;
            for (int position = 1; position <= times.length; position++) {
                boolean isBreak = position == 3;
                Integer number = null;
                if (!isBreak) {
                    lessonNumber++;
                    number = lessonNumber;
                }
                long id = dayIndex * 7L + position;
                grid.put(id, new SlotInfo(id, days.get(dayIndex), position, isBreak, number,
                        times[position - 1][0] + "–" + times[position - 1][1]));
            }
        }
        return grid;
    }
}
