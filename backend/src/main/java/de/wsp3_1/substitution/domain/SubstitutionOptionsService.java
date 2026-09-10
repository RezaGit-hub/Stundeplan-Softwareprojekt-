package de.wsp3_1.substitution.domain;

import de.wsp3_1.masterdata.persistence.Room;
import de.wsp3_1.masterdata.persistence.RoomRepository;
import de.wsp3_1.masterdata.persistence.Subject;
import de.wsp3_1.masterdata.persistence.SubjectRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.planning.domain.PlanReader;
import de.wsp3_1.planning.domain.PlanViews.LessonView;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.substitution.domain.SubstitutionViews.OptionsView;
import de.wsp3_1.substitution.domain.SubstitutionViews.RoomOptionView;
import de.wsp3_1.substitution.domain.SubstitutionViews.TeacherOptionView;
import de.wsp3_1.substitution.domain.WeekViews.DayView;
import de.wsp3_1.substitution.domain.WeekViews.EntryView;
import de.wsp3_1.substitution.persistence.ChangeEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vorschlaege fuer eine Vertretung (W-6): welche Lehrkraft und welcher Raum sind an diesem Tag in
 * diesem Zeitfenster frei.
 *
 * <p><b>Hier wird nichts berechnet und nichts entschieden</b> — es wird gefiltert und sortiert. Wer
 * die Vertretung uebernimmt, entscheidet die Planung; die Liste soll ihr nur die Arbeit abnehmen,
 * Kalender und Stundenplan von Hand zu vergleichen. Fachgleiche Lehrkraefte und passende Raumarten
 * stehen deshalb oben, ausgeschlossen wird nur, was an diesem Tag wirklich nicht kann.</p>
 *
 * <p>Die Auswahl selbst ist die statische Funktion {@link #choose(OptionsInput)} — ohne Datenbank
 * pruefbar ({@code SubstitutionOptionsTest}).</p>
 */
@Service
public class SubstitutionOptionsService {

    private final PlanReader planReader;
    private final WeekResolver weekResolver;
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final SubjectRepository subjects;
    private final ChangeEventRepository events;

    public SubstitutionOptionsService(PlanReader planReader, WeekResolver weekResolver,
                                      TeacherRepository teachers, RoomRepository rooms,
                                      SubjectRepository subjects, ChangeEventRepository events) {
        this.planReader = planReader;
        this.weekResolver = weekResolver;
        this.teachers = teachers;
        this.rooms = rooms;
        this.subjects = subjects;
        this.events = events;
    }

    /**
     * Freie Lehrkraefte und Raeume fuer diese Stunde an diesem Tag.
     *
     * @throws NotFoundException wenn es die Stunde im an diesem Tag geltenden Plan nicht gibt, sie dort
     *                           offen ist (ohne Zeitfenster gibt es nichts zu vertreten), sie an einem
     *                           anderen Wochentag liegt oder der Tag unterrichtsfrei ist
     */
    @Transactional(readOnly = true)
    public OptionsView options(LocalDate date, Long lessonId) {
        // Dieselben Bedingungen wie in ExceptionService.findLesson: Vorschlaege fuer einen Tag, an dem
        // die Stunde gar nicht liegt (Ferien, falscher Wochentag), fuehrten nur zu einer Eingabe,
        // die POST /api/exceptions danach mit 404 ablehnt.
        List<DayView> tage = weekResolver.days(date, date);
        boolean unterrichtstag = !tage.isEmpty() && tage.getFirst().schoolDay();
        LessonView lesson = planReader.plannedAt(date).lessons().stream()
                .filter(candidate -> unterrichtstag)
                .filter(candidate -> candidate.id().equals(lessonId))
                .filter(candidate -> candidate.timeslotId() != null)
                .filter(candidate -> candidate.day() == WeekResolver.weekdayOf(date))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Am " + date + " gibt es die Stunde " + lessonId + " nicht."));

        Set<Long> absentTeachers = new HashSet<>();
        Set<Long> blockedRooms = new HashSet<>();
        events.findByDateFromLessThanEqualAndDateToGreaterThanEqualOrderByIdAsc(date, date)
                .forEach(event -> {
                    if (event.getKind() == EventKind.TEACHER_ABSENCE) {
                        absentTeachers.add(event.getTeacherId());
                    }
                    if (event.getKind() == EventKind.ROOM_BLOCKED) {
                        blockedRooms.add(event.getRoomId());
                    }
                });

        Subject subject = subjects.findById(lesson.subjectId()).orElse(null);
        return choose(new OptionsInput(lesson, weekResolver.day(date),
                teachers.findAllByOrderByIdAsc().stream().map(TeacherFact::of).toList(),
                rooms.findAllByOrderByIdAsc().stream().map(RoomFact::of).toList(),
                subject == null ? null : subject.getRequiredRoomType(),
                absentTeachers, blockedRooms));
    }

    /** Die Auswahl als Funktion: filtern und sortieren, sonst nichts. */
    public static OptionsView choose(OptionsInput input) {
        LessonView lesson = input.lesson();
        // Feste Reihenfolge, damit die Liste bei zwei Aufrufen gleich aussieht: erst die passenden,
        // dann alphabetisch.
        Comparator<TeacherOptionView> fachgleicheZuerst =
                Comparator.comparing(TeacherOptionView::teachesSubject).reversed();
        Comparator<RoomOptionView> passendeRaumartZuerst =
                Comparator.comparing(RoomOptionView::matchesRequirement).reversed();
        // Was an diesem Tag in diesem Zeitfenster schon stattfindet — ohne die Stunde selbst und ohne
        // das, was ohnehin entfaellt.
        List<EntryView> busy = input.dayEntries().stream()
                .filter(entry -> entry.kind() != EntryKind.CANCELLED)
                .filter(entry -> lesson.timeslotId().equals(entry.timeslotId()))
                .filter(entry -> !lesson.id().equals(entry.lessonId()))
                .toList();

        List<TeacherOptionView> teacherOptions = input.teachers().stream()
                .filter(TeacherFact::active)
                .filter(teacher -> !teacher.id().equals(lesson.teacherId()))
                .filter(teacher -> !input.absentTeacherIds().contains(teacher.id()))
                .filter(teacher -> !teacher.blockedTimeslotIds().contains(lesson.timeslotId()))
                .filter(teacher -> busy.stream().noneMatch(entry -> teacher.id().equals(entry.teacherId())))
                .map(teacher -> new TeacherOptionView(teacher.id(), teacher.shortName(), teacher.name(),
                        teacher.subjectIds().contains(lesson.subjectId())))
                .sorted(fachgleicheZuerst.thenComparing(TeacherOptionView::name))
                .toList();

        List<RoomOptionView> roomOptions = input.rooms().stream()
                .filter(RoomFact::active)
                .filter(room -> !input.blockedRoomIds().contains(room.id()))
                .filter(room -> busy.stream().noneMatch(entry -> room.id().equals(entry.roomId())))
                .map(room -> new RoomOptionView(room.id(), room.name(), room.roomType(),
                        input.requiredRoomType() == null
                                || input.requiredRoomType().equals(room.roomType())))
                .sorted(passendeRaumartZuerst.thenComparing(RoomOptionView::name))
                .toList();

        return new OptionsView(teacherOptions, roomOptions);
    }

    // ------------------------------------------------------------------ Eingabe der Funktion

    /**
     * Alles, was die Auswahl braucht.
     *
     * @param lesson           die Stunde, fuer die vertreten wird
     * @param dayEntries       der aufgeloeste Tag (Sicht {@code SCHOOL})
     * @param requiredRoomType Raumanforderung des Fachs; {@code null} heisst „jeder Raum passt"
     */
    public record OptionsInput(LessonView lesson, List<EntryView> dayEntries, List<TeacherFact> teachers,
                               List<RoomFact> rooms, String requiredRoomType,
                               Set<Long> absentTeacherIds, Set<Long> blockedRoomIds) {
    }

    public record TeacherFact(Long id, String shortName, String name, boolean active,
                              Set<Long> subjectIds, Set<Long> blockedTimeslotIds) {

        static TeacherFact of(Teacher teacher) {
            return new TeacherFact(teacher.getId(), teacher.getShortName(), teacher.getName(),
                    teacher.isActive(), Set.copyOf(teacher.getSubjectIds()),
                    Set.copyOf(teacher.getBlockedTimeslotIds()));
        }
    }

    public record RoomFact(Long id, String name, String roomType, boolean active) {

        static RoomFact of(Room room) {
            return new RoomFact(room.getId(), room.getName(), room.getRoomType(), room.isActive());
        }
    }
}
