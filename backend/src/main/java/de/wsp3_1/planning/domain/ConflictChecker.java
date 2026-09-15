package de.wsp3_1.planning.domain;

import de.wsp3_1.planning.domain.CheckContext.ClassInfo;
import de.wsp3_1.planning.domain.CheckContext.RoomInfo;
import de.wsp3_1.planning.domain.CheckContext.SlotInfo;
import de.wsp3_1.planning.domain.CheckContext.SubjectInfo;
import de.wsp3_1.planning.domain.CheckContext.TeacherInfo;
import de.wsp3_1.planning.domain.ConflictView.ConflictObjectView;
import de.wsp3_1.rules.domain.EffectiveRule;
import de.wsp3_1.shared.RuleScope;
import de.wsp3_1.shared.RuleType;
import de.wsp3_1.shared.Severity;
import de.wsp3_1.shared.Weekday;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Die Konfliktpruefung des Regelplans (W-3, W-4). Sie ist eine reine Funktion:
 * {@link Assignment}s hinein, Konflikte heraus — keine Datenbank, kein Spring, nichts Gespeichertes.
 * Konflikte werden bei jedem Lesen neu berechnet (E-31), damit eine Grunddatenaenderung sofort sichtbar
 * ist.
 *
 * <p>Geprueft wird in dieser Reihenfolge (Wortlaut der Meldungen wie in
 * {@code frontend/src/mock/logic/conflicts.ts}, dort stehen die abgestimmten Texte):</p>
 *
 * <ol>
 *   <li>Doppelbelegung von Raum, Lehrkraft und Klasse — je Zeitfenster</li>
 *   <li>gesperrte Zeit der Lehrkraft</li>
 *   <li>Raumanforderung des Fachs</li>
 *   <li>gesperrte Zeit des Raums (#133)</li>
 *   <li>Regelwerk: Pausenregel, Hoechststunden je Tag und je Woche</li>
 * </ol>
 *
 * <p>Offene Stunden (ohne Zeitfenster) erzeugen nie einen Konflikt. Der Solver bewertet dieselben
 * Sachverhalte mit denselben aufgeloesten Regeln ({@code docs/SOLVER-PLAN.md} Abschnitt 9); weicht
 * eines von beiden ab, ist das ein Fehler und keine Geschmacksfrage.</p>
 */
public final class ConflictChecker {

    private ConflictChecker() {
    }

    public static List<ConflictView> check(List<Assignment> assignments, CheckContext context) {
        List<Assignment> placed = assignments.stream()
                .filter(Assignment::isPlaced)
                .sorted(Comparator.comparing(Assignment::lessonId))
                .toList();

        List<ConflictView> conflicts = new ArrayList<>();
        addDoubleBookings(placed, context, conflicts);
        addBlockedTimes(placed, context, conflicts);
        addRoomTypeMismatches(placed, context, conflicts);
        addRoomBlockedTimes(placed, context, conflicts);
        addRuleViolations(placed, context, conflicts);
        return conflicts;
    }

    // ------------------------------------------------------------------ 1. Doppelbelegung

    private static void addDoubleBookings(List<Assignment> placed, CheckContext context,
                                          List<ConflictView> out) {
        for (Map.Entry<String, List<Assignment>> group :
                groupBy(placed, a -> a.roomId() == null ? null : a.timeslotId() + ":" + a.roomId()).entrySet()) {
            List<Assignment> lessons = group.getValue();
            if (lessons.size() < 2) {
                continue;
            }
            RoomInfo room = context.rooms().get(lessons.getFirst().roomId());
            String label = room == null ? "?" : room.name();
            out.add(new ConflictView(ConflictType.ROOM_DOUBLE_BOOKED, Severity.HARD,
                    "Raum " + label + " ist " + slotPhrase(lessons.getFirst(), context) + " doppelt belegt ("
                            + classShorts(lessons, context) + ").",
                    lessonIds(lessons), null,
                    List.of(new ConflictObjectView(ObjectKind.ROOM, lessons.getFirst().roomId(), label))));
        }

        for (Map.Entry<String, List<Assignment>> group :
                groupBy(placed, a -> a.timeslotId() + ":" + a.teacherId()).entrySet()) {
            List<Assignment> lessons = group.getValue();
            if (lessons.size() < 2) {
                continue;
            }
            TeacherInfo teacher = context.teachers().get(lessons.getFirst().teacherId());
            out.add(new ConflictView(ConflictType.TEACHER_DOUBLE_BOOKED, Severity.HARD,
                    "Lehrkraft " + (teacher == null ? "?" : teacher.name()) + " ist "
                            + slotPhrase(lessons.getFirst(), context) + " doppelt belegt ("
                            + classShorts(lessons, context) + ").",
                    lessonIds(lessons), null,
                    List.of(new ConflictObjectView(ObjectKind.TEACHER, lessons.getFirst().teacherId(),
                            teacher == null ? "?" : teacher.shortName()))));
        }

        for (Map.Entry<String, List<Assignment>> group :
                groupBy(placed, a -> a.timeslotId() + ":" + a.classId()).entrySet()) {
            List<Assignment> lessons = group.getValue();
            if (lessons.size() < 2) {
                continue;
            }
            ClassInfo schoolClass = context.classes().get(lessons.getFirst().classId());
            String label = schoolClass == null ? "?" : schoolClass.name();
            String subjects = lessons.stream()
                    .map(a -> shortNameOfSubject(a, context))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            out.add(new ConflictView(ConflictType.CLASS_DOUBLE_BOOKED, Severity.HARD,
                    "Klasse " + label + " ist " + slotPhrase(lessons.getFirst(), context)
                            + " doppelt belegt (" + subjects + ").",
                    lessonIds(lessons), null,
                    List.of(new ConflictObjectView(ObjectKind.CLASS, lessons.getFirst().classId(), label))));
        }
    }

    // ------------------------------------------------------------------ 2. Gesperrte Zeit

    private static void addBlockedTimes(List<Assignment> placed, CheckContext context, List<ConflictView> out) {
        for (Assignment assignment : placed) {
            TeacherInfo teacher = context.teachers().get(assignment.teacherId());
            SlotInfo slot = context.timeslots().get(assignment.timeslotId());
            if (teacher == null || slot == null || !teacher.blockedTimeslotIds().contains(assignment.timeslotId())) {
                continue;
            }
            out.add(new ConflictView(ConflictType.TEACHER_BLOCKED_TIME, Severity.HARD,
                    "Gesperrte Zeit der Lehrkraft: " + teacher.name() + ", " + slot.day().label() + " "
                            + (slot.lessonNumber() == null ? "Pause" : slot.lessonNumber() + ". Stunde"),
                    List.of(assignment.lessonId()), null,
                    List.of(new ConflictObjectView(ObjectKind.TEACHER, teacher.id(), teacher.shortName()))));
        }
    }

    // ------------------------------------------------------------------ 3. Raumanforderung des Fachs

    private static void addRoomTypeMismatches(List<Assignment> placed, CheckContext context,
                                              List<ConflictView> out) {
        for (Assignment assignment : placed) {
            SubjectInfo subject = context.subjects().get(assignment.subjectId());
            RoomInfo room = context.rooms().get(assignment.roomId());
            if (subject == null || subject.requiredRoomType() == null || room == null
                    || subject.requiredRoomType().equals(room.roomType())) {
                continue;
            }
            out.add(new ConflictView(ConflictType.ROOM_TYPE_MISMATCH, Severity.HARD,
                    subject.name() + " braucht " + subject.requiredRoomType() + ", " + room.name()
                            + " ist " + room.roomType(),
                    List.of(assignment.lessonId()), null,
                    List.of(new ConflictObjectView(ObjectKind.ROOM, room.id(), room.name()))));
        }
    }

    // ------------------------------------------------------------------ 4. Gesperrte Zeit des Raums

    private static void addRoomBlockedTimes(List<Assignment> placed, CheckContext context, List<ConflictView> out) {
        for (Assignment assignment : placed) {
            RoomInfo room = context.rooms().get(assignment.roomId());
            if (room == null || !room.blockedTimeslotIds().contains(assignment.timeslotId())) {
                continue;
            }
            out.add(new ConflictView(ConflictType.ROOM_BLOCKED_TIME, Severity.HARD,
                    "Raum " + room.name() + " ist " + slotPhrase(assignment, context) + " gesperrt.",
                    List.of(assignment.lessonId()), null,
                    List.of(new ConflictObjectView(ObjectKind.ROOM, room.id(), room.name()))));
        }
    }

    // ------------------------------------------------------------------ 5. Regelwerk

    private static void addRuleViolations(List<Assignment> placed, CheckContext context, List<ConflictView> out) {
        for (EffectiveRule rule : context.rules()) {
            if (rule.type() == RuleType.NO_LESSONS_IN_BREAKS) {
                addBreakViolations(placed, context, rule, out);
            }
        }
        for (EffectiveRule rule : context.rules()) {
            if (rule.type() == RuleType.MAX_LESSONS_PER_DAY) {
                addMaxPerDay(placed, context, rule, out);
            }
        }
        for (EffectiveRule rule : context.rules()) {
            if (rule.type() == RuleType.MAX_LESSONS_PER_WEEK) {
                addMaxPerWeek(placed, context, rule, out);
            }
        }
    }

    private static void addBreakViolations(List<Assignment> placed, CheckContext context, EffectiveRule rule,
                                           List<ConflictView> out) {
        for (Assignment assignment : placed) {
            SlotInfo slot = context.timeslots().get(assignment.timeslotId());
            if (slot == null || !slot.isBreak()) {
                continue;
            }
            ClassInfo schoolClass = context.classes().get(assignment.classId());
            out.add(new ConflictView(ConflictType.RULE_VIOLATION, rule.severity(),
                    "Stunde liegt in der Pause: " + (schoolClass == null ? "?" : schoolClass.name()) + ", "
                            + slot.day().label() + " " + slot.timeRange(),
                    List.of(assignment.lessonId()), rule.ruleId(),
                    schoolClass == null ? List.of()
                            : List.of(new ConflictObjectView(ObjectKind.CLASS, schoolClass.id(), schoolClass.name()))));
        }
    }

    private static void addMaxPerDay(List<Assignment> placed, CheckContext context, EffectiveRule rule,
                                     List<ConflictView> out) {
        List<Assignment> own = ownLessons(placed, rule);
        for (Map.Entry<String, List<Assignment>> group : groupBy(own, a -> {
            SlotInfo slot = context.timeslots().get(a.timeslotId());
            return slot == null ? null : slot.day().name();
        }).entrySet()) {
            List<Assignment> lessons = group.getValue();
            if (lessons.size() <= rule.value()) {
                continue;
            }
            Weekday day = context.timeslots().get(lessons.getFirst().timeslotId()).day();
            out.add(ruleConflict(rule, context,
                    ownerLabel(rule, context) + ", " + day.label() + ": " + lessons.size() + " Stunden",
                    lessonIds(lessons)));
        }
    }

    private static void addMaxPerWeek(List<Assignment> placed, CheckContext context, EffectiveRule rule,
                                      List<ConflictView> out) {
        List<Assignment> own = ownLessons(placed, rule);
        if (own.size() <= rule.value()) {
            return;
        }
        out.add(ruleConflict(rule, context, ownerLabel(rule, context) + ": " + own.size() + " Stunden",
                lessonIds(own)));
    }

    private static ConflictView ruleConflict(EffectiveRule rule, CheckContext context, String tail,
                                             List<Long> lessonIds) {
        return new ConflictView(ConflictType.RULE_VIOLATION, rule.severity(),
                rule.label() + " (" + rule.value() + ") überschritten: " + tail,
                lessonIds, rule.ruleId(), List.of(ruleObject(rule, context)));
    }

    /** Die Stunden, die zu dieser aufgeloesten Regel gehoeren — je Klasse oder je Lehrkraft. */
    private static List<Assignment> ownLessons(List<Assignment> placed, EffectiveRule rule) {
        return placed.stream()
                .filter(a -> rule.scope() == RuleScope.CLASS
                        ? a.classId().equals(rule.ownerId())
                        : a.teacherId().equals(rule.ownerId()))
                .toList();
    }

    private static String ownerLabel(EffectiveRule rule, CheckContext context) {
        if (rule.scope() == RuleScope.CLASS) {
            ClassInfo schoolClass = context.classes().get(rule.ownerId());
            return "Klasse " + (schoolClass == null ? "?" : schoolClass.name());
        }
        TeacherInfo teacher = context.teachers().get(rule.ownerId());
        return "Lehrkraft " + (teacher == null ? "?" : teacher.name());
    }

    private static ConflictObjectView ruleObject(EffectiveRule rule, CheckContext context) {
        if (rule.scope() == RuleScope.CLASS) {
            ClassInfo schoolClass = context.classes().get(rule.ownerId());
            return new ConflictObjectView(ObjectKind.CLASS, rule.ownerId(),
                    schoolClass == null ? "?" : schoolClass.name());
        }
        TeacherInfo teacher = context.teachers().get(rule.ownerId());
        return new ConflictObjectView(ObjectKind.TEACHER, rule.ownerId(),
                teacher == null ? "?" : teacher.shortName());
    }

    // ------------------------------------------------------------------ kleine Helfer

    /** „am Montag in der 1. Stunde" bzw. „am Montag in der Pause". */
    private static String slotPhrase(Assignment assignment, CheckContext context) {
        SlotInfo slot = context.timeslots().get(assignment.timeslotId());
        if (slot == null) {
            return "zu einer unbekannten Zeit";
        }
        return "am " + slot.day().label() + " in der "
                + (slot.lessonNumber() == null ? "Pause" : slot.lessonNumber() + ". Stunde");
    }

    /** Die Klassen einer Gruppe, jede einmal, in der Reihenfolge der Stunden. */
    private static String classShorts(List<Assignment> lessons, CheckContext context) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Assignment assignment : lessons) {
            ClassInfo schoolClass = context.classes().get(assignment.classId());
            names.add(schoolClass == null ? "?" : schoolClass.name());
        }
        return String.join(", ", names);
    }

    private static String shortNameOfSubject(Assignment assignment, CheckContext context) {
        SubjectInfo subject = context.subjects().get(assignment.subjectId());
        return subject == null ? "?" : subject.shortName();
    }

    private static List<Long> lessonIds(List<Assignment> lessons) {
        return lessons.stream().map(Assignment::lessonId).toList();
    }

    /** Gruppieren unter Beibehaltung der Reihenfolge; ein leerer Schluessel faellt heraus. */
    private static <T> Map<String, List<T>> groupBy(List<T> items, Function<T, String> key) {
        Map<String, List<T>> grouped = new LinkedHashMap<>();
        for (T item : items) {
            String value = key.apply(item);
            if (value != null) {
                grouped.computeIfAbsent(value, ignored -> new ArrayList<>()).add(item);
            }
        }
        return grouped;
    }

}
