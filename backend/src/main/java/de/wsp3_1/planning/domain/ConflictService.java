package de.wsp3_1.planning.domain;

import de.wsp3_1.masterdata.domain.Timegrid;
import de.wsp3_1.masterdata.persistence.Room;
import de.wsp3_1.masterdata.persistence.RoomRepository;
import de.wsp3_1.masterdata.persistence.SchoolClass;
import de.wsp3_1.masterdata.persistence.SchoolClassRepository;
import de.wsp3_1.masterdata.persistence.Subject;
import de.wsp3_1.masterdata.persistence.SubjectRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.masterdata.persistence.Timeslot;
import de.wsp3_1.masterdata.persistence.TimeslotRepository;
import de.wsp3_1.planning.domain.CheckContext.ClassInfo;
import de.wsp3_1.planning.domain.CheckContext.RoomInfo;
import de.wsp3_1.planning.domain.CheckContext.SlotInfo;
import de.wsp3_1.planning.domain.CheckContext.SubjectInfo;
import de.wsp3_1.planning.domain.CheckContext.TeacherInfo;
import de.wsp3_1.rules.domain.RuleResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Baut den {@link CheckContext} aus den Grunddaten und dem Regelwerk und laesst den
 * {@link ConflictChecker} darauf laufen. Das ist der Einstieg fuer alle, die Konflikte brauchen:
 * {@code GET /api/timetable}, jede Entwurfsantwort und spaeter das Modul {@code substitution}, das
 * eine geplante Vertretung pruefen will, bevor sie gespeichert wird.
 *
 * <p>Es wird nichts zwischengespeichert (E-31). Der Kontext entsteht einmal je Aufruf aus fuenf
 * Listen; bei den Groessen dieser Anwendung (Dutzende Klassen, Hunderte Zeitfenster) ist das guenstiger
 * als jede Form von Zwischenspeicher, die man wieder ungueltig machen muesste.</p>
 */
@Service
public class ConflictService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final TimeslotRepository timeslots;
    private final SchoolClassRepository classes;
    private final TeacherRepository teachers;
    private final SubjectRepository subjects;
    private final RoomRepository rooms;
    private final RuleResolver ruleResolver;

    public ConflictService(TimeslotRepository timeslots, SchoolClassRepository classes,
                           TeacherRepository teachers, SubjectRepository subjects, RoomRepository rooms,
                           RuleResolver ruleResolver) {
        this.timeslots = timeslots;
        this.classes = classes;
        this.teachers = teachers;
        this.subjects = subjects;
        this.rooms = rooms;
        this.ruleResolver = ruleResolver;
    }

    /** Die Konflikte dieser Belegungen — die einzige Stelle, an der Konflikte entstehen. */
    @Transactional(readOnly = true)
    public List<ConflictView> check(List<Assignment> assignments) {
        return ConflictChecker.check(assignments, context(assignments));
    }

    /** Der Kontext allein — {@code SolveService} baut daraus die Regeln fuer den Solver. */
    @Transactional(readOnly = true)
    public CheckContext context(List<Assignment> assignments) {
        List<Timeslot> allSlots = timeslots.findAll();
        List<SchoolClass> allClasses = classes.findAllByOrderByIdAsc();

        // Regeln je Objekt: fuer die aktiven Klassen und fuer die Lehrkraefte, die im Plan vorkommen
        // (docs/SOLVER-PLAN.md Abschnitt 9). Genau dieselbe Liste bekommt der Solver.
        List<RuleResolver.ClassRef> classRefs = allClasses.stream()
                .filter(SchoolClass::isActive)
                .map(c -> new RuleResolver.ClassRef(c.getId(), c.getGrade()))
                .toList();
        List<Long> teacherIds = assignments.stream()
                .map(Assignment::teacherId)
                .distinct()
                .sorted()
                .toList();

        return new CheckContext(
                slotInfos(allSlots),
                index(allClasses, SchoolClass::getId,
                        c -> new ClassInfo(c.getId(), c.getName(), c.getGrade())),
                index(teachers.findAllByOrderByIdAsc(), Teacher::getId,
                        t -> new TeacherInfo(t.getId(), t.getName(), t.getShortName(),
                                Set.copyOf(t.getBlockedTimeslotIds()))),
                index(subjects.findAllByOrderByIdAsc(), Subject::getId,
                        s -> new SubjectInfo(s.getId(), s.getName(), s.getShortName(), s.getRequiredRoomType())),
                index(rooms.findAllByOrderByIdAsc(), Room::getId,
                        r -> new RoomInfo(r.getId(), r.getName(), r.getRoomType(),
                                Set.copyOf(r.getBlockedTimeslotIds()))),
                ruleResolver.resolve(classRefs, teacherIds));
    }

    /**
     * Die Zeitfenster mit ihrer angezeigten Stundennummer. Gezaehlt wird in
     * {@link de.wsp3_1.masterdata.domain.Timegrid} — dieselbe Stelle, aus der auch die Vertretung ihre
     * Stundennummern nimmt; hier kommt nur die Uhrzeitspanne fuer den Text der Pausenregel dazu.
     */
    private static Map<Long, SlotInfo> slotInfos(List<Timeslot> allSlots) {
        Map<Long, Integer> numbers = Timegrid.lessonNumbers(allSlots);
        Map<Long, SlotInfo> infos = new LinkedHashMap<>();
        for (Timeslot slot : Timegrid.sortedByGrid(allSlots)) {
            infos.put(slot.getId(), new SlotInfo(slot.getId(), slot.getDay(), slot.getPosition(),
                    slot.isBreak(), numbers.get(slot.getId()),
                    slot.getStartTime().format(TIME) + "–" + slot.getEndTime().format(TIME)));
        }
        return infos;
    }

    private static <E, V> Map<Long, V> index(List<E> entities, Function<E, Long> id, Function<E, V> value) {
        Map<Long, V> map = new LinkedHashMap<>();
        for (E entity : entities) {
            map.put(id.apply(entity), value.apply(entity));
        }
        return map;
    }
}
