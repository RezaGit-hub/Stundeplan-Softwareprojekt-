package de.wsp3_1.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uebersetzt zwischen den Records der Schnittstelle ({@link SolveInput}, {@link SolveResult}) und dem
 * Timefold-Modell ({@link SolverTimetable}). Zwei statische Methoden, kein Zustand.
 *
 * <p>Wichtig fuer die Gleichheit im Solver: je Zeitfenster und je Raum entsteht <b>eine</b> Instanz, die
 * sowohl im Wertebereich als auch an den Stunden haengt. Records vergleichen zwar per Feld, aber so ist
 * es eindeutig und billig (docs/SOLVER-PLAN.md Abschnitt 2 und 6).</p>
 */
public final class SolverTranslator {

    private SolverTranslator() {
    }

    /**
     * Baut das Planungsproblem. Wertebereiche: Zeitfenster ohne Pause, aktive Raeume.
     *
     * <p>Timefold 2.6.0 verlangt, dass jeder gesetzte Wert im Wertebereich liegt — auch bei festgelegten
     * Stunden (gemessen, {@code SmallDatasetSolveTest}: „outside of the related value range"). Liegt eine
     * gespeicherte Stunde auf einer Pause oder in einem inaktiven Raum (Handaufbau in die Pause,
     * Deaktivierung nach dem Speichern), gilt deshalb: <b>festgelegt</b> → das Zeitfenster bzw. der Raum
     * kommt zusaetzlich in den Wertebereich (die Stunde bleibt stehen, R3 bzw. H5 bewerten sie weiter);
     * <b>nicht festgelegt</b> → die Stunde wird offen uebergeben und vom Solver neu eingeplant.</p>
     *
     * <p>Nebenwirkung der ersten Regel: der zusaetzliche Wert steht im Wertebereich <b>aller</b> Stunden,
     * der Solver duerfte dort also auch andere hinsetzen. Dagegen stehen die harten Regeln
     * {@code movableLessonInBreak} (H6) und {@code movableLessonInInactiveRoom} (H7) im
     * {@link TimetableConstraintProvider} — sie gelten unabhaengig vom Regelwerk. Auf die Pausenregel R3
     * allein war kein Verlass: sie darf weich stehen (Befund 05.09.).</p>
     *
     * <p>Eine <b>festgelegte offene</b> Stunde (kein Zeitfenster, kein Raum) darf hier nicht ankommen:
     * Timefold lehnt sie ab („has a variable pinned to null, even though unassigned values are not
     * allowed"), weil der Raum keine leeren Werte erlaubt. {@code SolveService.buildInput} laesst solche
     * Stunden deshalb weg — sie bleiben unveraendert stehen.</p>
     */
    public static SolverTimetable toSolution(SolveInput input) {
        Map<Long, TimeslotFact> timeslotById = new HashMap<>();
        List<TimeslotFact> valueRangeTimeslots = new ArrayList<>();
        for (TimeslotFact timeslot : input.timeslots()) {
            timeslotById.put(timeslot.id(), timeslot);
            if (!timeslot.isBreak()) {
                valueRangeTimeslots.add(timeslot);
            }
        }

        Map<Long, RoomFact> roomById = new HashMap<>();
        List<RoomFact> valueRangeRooms = new ArrayList<>();
        for (RoomFact room : input.rooms()) {
            roomById.put(room.id(), room);
            if (room.active()) {
                valueRangeRooms.add(room);
            }
        }

        List<SolverLesson> lessons = new ArrayList<>();
        for (SolveInput.LessonInput lesson : input.lessons()) {
            TimeslotFact timeslot = null;
            RoomFact room = null;
            if (lesson.timeslotId() != null && lesson.roomId() != null) {
                timeslot = timeslotById.get(lesson.timeslotId());
                room = roomById.get(lesson.roomId());
                if (timeslot == null || room == null) {
                    throw new IllegalArgumentException("Stunde " + lesson.id() + " verweist auf ein unbekanntes "
                            + "Zeitfenster (" + lesson.timeslotId() + ") oder einen unbekannten Raum ("
                            + lesson.roomId() + "). SolveInput.timeslots und rooms muessen alle enthalten.");
                }
                boolean outsideValueRange = timeslot.isBreak() || !room.active();
                if (outsideValueRange && lesson.pinned()) {
                    if (timeslot.isBreak()) {
                        valueRangeTimeslots.add(timeslot);
                    }
                    if (!room.active()) {
                        valueRangeRooms.add(room);
                    }
                } else if (outsideValueRange) {
                    timeslot = null;
                    room = null;
                }
            }
            lessons.add(new SolverLesson(lesson.id(), lesson.classId(), lesson.subjectId(), lesson.teacherId(),
                    lesson.requiredRoomType(), lesson.pinned(), timeslot, room));
        }

        return new SolverTimetable(valueRangeTimeslots, valueRangeRooms, input.rules(), input.blockedTimes(),
                input.preferredTimes(), lessons);
    }

    /**
     * Liest die Lage jeder Stunde aus der Loesung. Hier entsteht die Invariante „belegt oder offen":
     * fehlt Zeitfenster <i>oder</i> Raum, ist die Stunde offen und beide Kennungen sind {@code null}.
     */
    public static SolveResult toResult(SolverTimetable solution) {
        List<SolveResult.Placement> placements = new ArrayList<>();
        for (SolverLesson lesson : solution.getLessons()) {
            boolean open = lesson.getTimeslot() == null || lesson.getRoom() == null;
            placements.add(new SolveResult.Placement(lesson.getId(),
                    open ? null : lesson.getTimeslot().id(),
                    open ? null : lesson.getRoom().id()));
        }
        String score = solution.getScore() == null ? null : solution.getScore().toString();
        return new SolveResult(placements, score);
    }
}
