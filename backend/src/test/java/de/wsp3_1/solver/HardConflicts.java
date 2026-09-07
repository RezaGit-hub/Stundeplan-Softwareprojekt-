package de.wsp3_1.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Zaehlt harte Verstoesse in einem {@link SolveResult} unabhaengig vom Solver — mit Mengen statt
 * Constraint Streams, damit der Test nicht dasselbe prueft, was er pruefen soll. Deckt H1–H5 ab
 * (Doppelbelegung Raum/Lehrkraft/Klasse, Sperrzeit, Raumart) plus die Invariante „belegt oder offen".
 */
final class HardConflicts {

    private HardConflicts() {
    }

    static List<String> of(SolveInput input, SolveResult result) {
        Map<Long, SolveInput.LessonInput> lessonById = new HashMap<>();
        for (SolveInput.LessonInput lesson : input.lessons()) {
            lessonById.put(lesson.id(), lesson);
        }
        Map<Long, RoomFact> roomById = new HashMap<>();
        for (RoomFact room : input.rooms()) {
            roomById.put(room.id(), room);
        }
        Set<String> blocked = new HashSet<>();
        for (BlockedTimeFact fact : input.blockedTimes()) {
            blocked.add(fact.teacherId() + "@" + fact.timeslotId());
        }

        List<String> problems = new ArrayList<>();
        Set<String> roomSlots = new HashSet<>();
        Set<String> teacherSlots = new HashSet<>();
        Set<String> classSlots = new HashSet<>();
        for (SolveResult.Placement placement : result.placements()) {
            if ((placement.timeslotId() == null) != (placement.roomId() == null)) {
                problems.add("Stunde " + placement.lessonId() + " ist weder belegt noch offen");
                continue;
            }
            if (placement.timeslotId() == null) {
                continue;
            }
            SolveInput.LessonInput lesson = lessonById.get(placement.lessonId());
            Long slot = placement.timeslotId();
            if (!roomSlots.add(placement.roomId() + "@" + slot)) {
                problems.add("Raum " + placement.roomId() + " doppelt in Zeitfenster " + slot);
            }
            if (!teacherSlots.add(lesson.teacherId() + "@" + slot)) {
                problems.add("Lehrkraft " + lesson.teacherId() + " doppelt in Zeitfenster " + slot);
            }
            if (!classSlots.add(lesson.classId() + "@" + slot)) {
                problems.add("Klasse " + lesson.classId() + " doppelt in Zeitfenster " + slot);
            }
            if (blocked.contains(lesson.teacherId() + "@" + slot)) {
                problems.add("Lehrkraft " + lesson.teacherId() + " in gesperrtem Zeitfenster " + slot);
            }
            String roomType = roomById.get(placement.roomId()).roomType();
            if (lesson.requiredRoomType() != null && !lesson.requiredRoomType().equals(roomType)) {
                problems.add("Stunde " + lesson.id() + " braucht " + lesson.requiredRoomType() + ", liegt in " + roomType);
            }
        }
        return problems;
    }

    static long open(SolveResult result) {
        return result.placements().stream().filter(p -> p.timeslotId() == null).count();
    }
}
