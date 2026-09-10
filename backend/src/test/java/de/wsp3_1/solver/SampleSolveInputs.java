package de.wsp3_1.solver;

import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.Weekday;

import java.util.ArrayList;
import java.util.List;

/**
 * Die beiden Beispielbestaende als {@link SolveInput} — ohne Datenbank, mit denselben Zahlen wie
 * {@code sampledata.SampleDataLoader} (kleiner Bestand = Pflichtenheft-Testdaten, voller Bestand =
 * 12 Klassen / 24 Lehrkraefte / 18 Raeume). Kennungen: Zeitfenster {@code tag * 7 + position}
 * (Mo 1. Stunde = 1, Fr 6. Stunde = 35), sonst fortlaufend ab 1.
 *
 * <p>Regeln wie der Seed fuer Schleswig-Holstein (V2): je Klasse hoechstens 6 Stunden am Tag und 30 in
 * der Woche (weich), je Lehrkraft 6 am Tag und 26 in der Woche (weich), Pausenregel hart.</p>
 */
final class SampleSolveInputs {

    static final int PAUSE_POSITION = 3;
    static final int POSITIONEN = 7;
    static final Weekday[] TAGE = {Weekday.MO, Weekday.TU, Weekday.WE, Weekday.TH, Weekday.FR};

    private SampleSolveInputs() {
    }

    static Long timeslotId(Weekday day, int position) {
        return (long) (day.ordinal() * POSITIONEN + position);
    }

    /** Mo–Fr, sieben Positionen, Position 3 ist die grosse Pause: 35 Zeitfenster, 30 davon planbar. */
    static List<TimeslotFact> timeslots() {
        List<TimeslotFact> result = new ArrayList<>();
        for (Weekday day : TAGE) {
            for (int position = 1; position <= POSITIONEN; position++) {
                result.add(new TimeslotFact(timeslotId(day, position), day, position, position == PAUSE_POSITION));
            }
        }
        return result;
    }

    static List<RuleFact> rules(List<Long> classIds, List<Long> teacherIds) {
        List<RuleFact> rules = new ArrayList<>();
        for (Long classId : classIds) {
            rules.add(new RuleFact(RuleType.MAX_LESSONS_PER_DAY, RuleScope.CLASS, classId, 6, Severity.SOFT, 1));
            rules.add(new RuleFact(RuleType.MAX_LESSONS_PER_WEEK, RuleScope.CLASS, classId, 30, Severity.SOFT, 1));
        }
        for (Long teacherId : teacherIds) {
            rules.add(new RuleFact(RuleType.MAX_LESSONS_PER_DAY, RuleScope.TEACHER, teacherId, 6, Severity.SOFT, 1));
            rules.add(new RuleFact(RuleType.MAX_LESSONS_PER_WEEK, RuleScope.TEACHER, teacherId, 26, Severity.SOFT, 1));
        }
        rules.add(new RuleFact(RuleType.NO_LESSONS_IN_BREAKS, null, null, 1, Severity.HARD, 1));
        return rules;
    }

    // ---------------------------------------------------------------- kleiner Bestand (Pflichtenheft)

    static final Long MEYER = 1L;
    static final Long SCHULZ = 2L;
    static final Long WEBER = 3L;
    static final Long MATHEMATIK = 1L;
    static final Long DEUTSCH = 2L;
    static final Long PHYSIK = 3L;
    static final Long R101 = 1L;
    static final Long R204 = 2L;
    static final Long P1 = 3L;
    static final Long KLASSE_7A = 1L;
    static final Long KLASSE_8B = 2L;
    static final Long KLASSE_9C = 3L;

    /**
     * Acht offene, nicht festgelegte Stunden: 7a Ma 2, De 1, Ph 1 (Fachraum Physik); 8b Ma 2, De 1;
     * 9c Ma 1 (Schulz, Fr 5./6. Stunde gesperrt).
     */
    static SolveInput small() {
        List<SolveInput.LessonInput> lessons = new ArrayList<>();
        long id = 1;
        lessons.add(open(id++, KLASSE_7A, MATHEMATIK, MEYER, null));
        lessons.add(open(id++, KLASSE_7A, MATHEMATIK, MEYER, null));
        lessons.add(open(id++, KLASSE_7A, DEUTSCH, WEBER, null));
        lessons.add(open(id++, KLASSE_7A, PHYSIK, MEYER, "Fachraum Physik"));
        lessons.add(open(id++, KLASSE_8B, MATHEMATIK, MEYER, null));
        lessons.add(open(id++, KLASSE_8B, MATHEMATIK, MEYER, null));
        lessons.add(open(id++, KLASSE_8B, DEUTSCH, WEBER, null));
        lessons.add(open(id, KLASSE_9C, MATHEMATIK, SCHULZ, null));

        List<RoomFact> rooms = List.of(new RoomFact(R101, "Klassenraum", true),
                new RoomFact(R204, "Klassenraum", true), new RoomFact(P1, "Fachraum Physik", true));
        List<BlockedTimeFact> blocked = List.of(new BlockedTimeFact(SCHULZ, timeslotId(Weekday.FR, 6)),
                new BlockedTimeFact(SCHULZ, timeslotId(Weekday.FR, 7)));

        return new SolveInput(lessons, timeslots(), rooms,
                rules(List.of(KLASSE_7A, KLASSE_8B, KLASSE_9C), List.of(MEYER, SCHULZ, WEBER)), blocked, List.of());
    }

    // ---------------------------------------------------------------- voller Bestand (12/24/18)

    /** Raumart je Fach (Index wie in SampleDataLoader.FAECHER), leer = jeder Raum. */
    private static final String[] RAUMART = {null, null, null, "Fachraum Physik", "Fachraum Chemie", null,
            null, null, "Sporthalle", "Musikraum", null, null};
    private static final int[] WOCHENSTUNDEN = {4, 4, 4, 2, 2, 2, 2, 2, 3, 2, 2, 1};
    private static final int[][] FACHPAARE = {{0, 3}, {1, 6}, {2, 7}, {4, 5}, {8, 10}, {9, 11}};
    private static final String[] RAEUME = {"Klassenraum", "Klassenraum", "Klassenraum", "Klassenraum",
            "Klassenraum", "Klassenraum", "Klassenraum", "Klassenraum", "Klassenraum", "Klassenraum",
            "Klassenraum", "Klassenraum", "Fachraum Physik", "Fachraum Physik", "Fachraum Chemie",
            "Sporthalle", "Sporthalle", "Musikraum"};

    /** 12 Klassen x 30 Wochenstunden = 360 offene Stunden; Lehrkraft = Fachpaar x 4 + Klasse / 3. */
    static SolveInput full() {
        List<SolveInput.LessonInput> lessons = new ArrayList<>();
        List<Long> classIds = new ArrayList<>();
        List<Long> teacherIds = new ArrayList<>();
        for (int t = 0; t < 24; t++) {
            teacherIds.add((long) (t + 1));
        }
        long id = 1;
        for (int k = 0; k < 12; k++) {
            long classId = k + 1;
            classIds.add(classId);
            for (int paar = 0; paar < FACHPAARE.length; paar++) {
                long teacherId = paar * 4 + k / 3 + 1;
                for (int fach : FACHPAARE[paar]) {
                    for (int h = 0; h < WOCHENSTUNDEN[fach]; h++) {
                        lessons.add(open(id++, classId, (long) (fach + 1), teacherId, RAUMART[fach]));
                    }
                }
            }
        }
        List<RoomFact> rooms = new ArrayList<>();
        for (int r = 0; r < RAEUME.length; r++) {
            rooms.add(new RoomFact((long) (r + 1), RAEUME[r], true));
        }
        return new SolveInput(lessons, timeslots(), rooms, rules(classIds, teacherIds), List.of(), List.of());
    }

    static SolveInput.LessonInput open(long id, Long classId, Long subjectId, Long teacherId, String roomType) {
        return new SolveInput.LessonInput(id, classId, subjectId, teacherId, roomType, false, null, null);
    }
}
