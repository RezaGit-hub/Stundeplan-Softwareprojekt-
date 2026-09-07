package de.wsp3_1.substitution.domain;

import de.wsp3_1.auth.persistence.UserAccount;
import de.wsp3_1.masterdata.persistence.RoomRepository;
import de.wsp3_1.masterdata.persistence.SchoolClassRepository;
import de.wsp3_1.masterdata.persistence.SubjectRepository;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.masterdata.persistence.TimeslotRepository;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.shared.domain.ValidationException.FieldError;
import de.wsp3_1.substitution.domain.SubstitutionViews.AffectedView;
import de.wsp3_1.substitution.domain.SubstitutionViews.EventView;
import de.wsp3_1.substitution.domain.SubstitutionViews.ExtraLessonView;
import de.wsp3_1.substitution.domain.SubstitutionViews.NewEvent;
import de.wsp3_1.substitution.domain.WeekViews.DayView;
import de.wsp3_1.substitution.domain.WeekViews.EntryView;
import de.wsp3_1.substitution.persistence.ChangeEvent;
import de.wsp3_1.substitution.persistence.ChangeEventRepository;
import de.wsp3_1.substitution.persistence.LessonException;
import de.wsp3_1.substitution.persistence.LessonExceptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Aenderungsereignisse (W-5): Ausfall einer Lehrkraft, Sperrung eines Raums, Zusatzstunde.
 *
 * <p><b>Ein Ereignis aendert den Regelplan nicht.</b> Es sagt nur, was an bestimmten Kalendertagen
 * anders ist; welche Stunden es trifft, rechnet der {@link WeekResolver} bei jedem Lesen neu
 * ({@code affected}, E-31). Deshalb genuegt zum Zuruecknehmen ein {@code DELETE}: die
 * Vertretungsbedarfe verschwinden von selbst.</p>
 *
 * <p>Die Zusatzstunde ist der eine Sonderfall: sie ist eine Stunde, keine Absage. Das Ereignis legt
 * deshalb zugleich eine Ausnahme der Art {@code ADDED} an — und nimmt sie beim Loeschen wieder mit.</p>
 */
@Service
public class ChangeEventService {


    private final ChangeEventRepository events;
    private final LessonExceptionRepository exceptions;
    private final Actors actors;
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final SchoolClassRepository classes;
    private final SubjectRepository subjects;
    private final TimeslotRepository timeslots;
    private final WeekResolver weekResolver;

    public ChangeEventService(ChangeEventRepository events, LessonExceptionRepository exceptions,
                              Actors actors, TeacherRepository teachers,
                              RoomRepository rooms, SchoolClassRepository classes,
                              SubjectRepository subjects, TimeslotRepository timeslots,
                              WeekResolver weekResolver) {
        this.events = events;
        this.exceptions = exceptions;
        this.actors = actors;
        this.teachers = teachers;
        this.rooms = rooms;
        this.classes = classes;
        this.subjects = subjects;
        this.timeslots = timeslots;
        this.weekResolver = weekResolver;
    }

    /**
     * Alle Ereignisse, deren Zeitraum den Bereich beruehrt; ohne Bereich alle.
     *
     * <p>Die betroffenen Stunden kommen aus <b>einer</b> Aufloesung ueber den Gesamtzeitraum aller
     * gefundenen Ereignisse. Vorher loeste jedes Ereignis seinen Zeitraum selbst auf — bei n
     * Ereignissen also n vollstaendige Neuladungen von Raster, Ferien, Grunddaten, Ereignissen und
     * Ausnahmen. Spannen die Ereignisse zusammen mehr Tage auf, als der {@link WeekResolver} auf
     * einmal aufloest ({@link WeekResolver#MAX_DAYS}), bleibt es beim Weg je Ereignis.</p>
     */
    @Transactional(readOnly = true)
    public List<EventView> list(LocalDate from, LocalDate to, Long teacherId, Long roomId) {
        List<ChangeEvent> rows = events.findAllWithCreator().stream()
                .filter(event -> from == null || !event.getDateTo().isBefore(from))
                .filter(event -> to == null || !event.getDateFrom().isAfter(to))
                .filter(event -> teacherId == null || teacherId.equals(event.getTeacherId()))
                .filter(event -> roomId == null || roomId.equals(event.getRoomId()))
                .toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        LocalDate von = rows.stream().map(ChangeEvent::getDateFrom).min(LocalDate::compareTo).orElseThrow();
        LocalDate bis = rows.stream().map(ChangeEvent::getDateTo).max(LocalDate::compareTo).orElseThrow();
        List<DayView> gemeinsam = ChronoUnit.DAYS.between(von, bis) < WeekResolver.MAX_DAYS
                ? weekResolver.days(von, bis)
                : null;
        return rows.stream().map(event -> view(event, daysOf(event, gemeinsam))).toList();
    }

    /** Erfasst ein Ereignis. Bei {@code EXTRA_LESSON} entsteht zugleich die Ausnahme {@code ADDED}. */
    @Transactional
    public EventView create(NewEvent input) {
        validate(input);
        UserAccount actor = actors.current();
        Instant now = Instant.now();
        boolean extraLesson = input.kind() == EventKind.EXTRA_LESSON;
        LocalDate from = extraLesson ? input.extra().date() : input.dateFrom();
        LocalDate to = extraLesson ? input.extra().date() : input.dateTo();

        ChangeEvent event = events.saveAndFlush(new ChangeEvent(input.kind(),
                input.kind() == EventKind.TEACHER_ABSENCE ? input.teacherId() : null,
                input.kind() == EventKind.ROOM_BLOCKED ? input.roomId() : null,
                from, to, input.reason().trim(), now, actor));

        if (extraLesson) {
            ExtraLessonView extra = input.extra();
            LessonException added = LessonException.added(extra.date(), extra.timeslotId(),
                    extra.classId(), extra.subjectId(), extra.teacherId(), extra.roomId(), now, actor);
            added.getEventIds().add(event.getId());
            exceptions.saveAndFlush(added);
        }
        return view(event);
    }

    /**
     * Nimmt ein Ereignis zurueck. Vorhandene Ausnahmen bleiben bestehen und verlieren nur diesen
     * Anlass (die Verbindungszeile geht per {@code ON DELETE CASCADE} mit, V4); die Zusatzstunde eines
     * {@code EXTRA_LESSON} wird dagegen mit geloescht — ohne ihr Ereignis stuende sie grundlos da.
     */
    @Transactional
    public void delete(Long id) {
        ChangeEvent event = events.findById(id)
                .orElseThrow(() -> new NotFoundException("Das Ereignis " + id + " gibt es nicht."));
        if (event.getKind() == EventKind.EXTRA_LESSON) {
            exceptions.deleteAll(exceptions.findByKindAndEventId(ExceptionKind.ADDED, id));
        }
        events.delete(event);
    }

    // ------------------------------------------------------------------ Antwort bauen

    private EventView view(ChangeEvent event) {
        return view(event, daysOf(event, null));
    }

    /**
     * Die Tage im Zeitraum eines Ereignisses. {@code gemeinsam} ist {@code null}, wenn es keine
     * gemeinsame Aufloesung gibt — dann loest dieses Ereignis seinen Zeitraum selbst auf.
     */
    private List<DayView> daysOf(ChangeEvent event, List<DayView> gemeinsam) {
        if (event.getKind() == EventKind.EXTRA_LESSON) {
            return List.of();
        }
        if (gemeinsam == null) {
            return weekResolver.days(event.getDateFrom(), event.getDateTo());
        }
        return gemeinsam.stream()
                .filter(day -> !day.date().isBefore(event.getDateFrom())
                        && !day.date().isAfter(event.getDateTo()))
                .toList();
    }

    private EventView view(ChangeEvent event, List<DayView> days) {
        List<AffectedView> affected = affected(event, days);
        return new EventView(event.getId(), event.getKind(), event.getTeacherId(), event.getRoomId(),
                event.getDateFrom(), event.getDateTo(), event.getReason(), event.getCreatedAt(),
                event.getCreatedBy().getUsername(), affected, hint(event, affected), extraOf(event));
    }

    /**
     * Die Stunden des geltenden Plans, die dieses Ereignis in seinem Zeitraum trifft — auch die, die
     * schon vertreten sind (dort steht die ausgefallene Lehrkraft in {@code original}).
     */
    private List<AffectedView> affected(ChangeEvent event, List<DayView> days) {
        if (event.getKind() == EventKind.EXTRA_LESSON) {
            return List.of();
        }
        List<AffectedView> affected = new ArrayList<>();
        for (DayView day : days) {
            for (EntryView entry : day.entries()) {
                if (entry.lessonId() == null || entry.kind() == EntryKind.CANCELLED) {
                    continue;
                }
                if (hits(event, entry)) {
                    affected.add(new AffectedView(day.date(), entry.lessonId(), entry.classShort(),
                            entry.position()));
                }
            }
        }
        return affected;
    }

    private static boolean hits(ChangeEvent event, EntryView entry) {
        Long originalTeacher = entry.original() == null ? null : entry.original().teacherId();
        Long originalRoom = entry.original() == null ? null : entry.original().roomId();
        return switch (event.getKind()) {
            case TEACHER_ABSENCE -> event.getTeacherId().equals(entry.teacherId())
                    || event.getTeacherId().equals(originalTeacher);
            case ROOM_BLOCKED -> event.getRoomId().equals(entry.roomId())
                    || event.getRoomId().equals(originalRoom);
            case EXTRA_LESSON -> false;
        };
    }

    /** Trifft ein Ereignis keine Stunde, sagt die Antwort das — die Ausnahme aus W-5. */
    private String hint(ChangeEvent event, List<AffectedView> affected) {
        if (event.getKind() == EventKind.EXTRA_LESSON) {
            ExtraLessonView extra = extraOf(event);
            String className = extra == null ? "?" : classes.findById(extra.classId())
                    .map(schoolClass -> schoolClass.getName()).orElse("?");
            return "Zusatzstunde für " + className + " am " + SubstitutionText.date(event.getDateFrom())
                    + " angelegt.";
        }
        return affected.isEmpty() ? "Das Ereignis betrifft keine eingeplante Stunde." : null;
    }

    /**
     * Die Angaben der Zusatzstunde. Sie stehen in der Ausnahme {@code ADDED}, die zusammen mit dem
     * Ereignis entstanden ist — eine Zusatzstunde ist eine Stunde, und Stunden eines Kalendertags
     * stehen in {@code lesson_exception}.
     */
    private ExtraLessonView extraOf(ChangeEvent event) {
        if (event.getKind() != EventKind.EXTRA_LESSON) {
            return null;
        }
        return exceptions.findByKindAndEventId(ExceptionKind.ADDED, event.getId()).stream()
                .findFirst()
                .map(exception -> new ExtraLessonView(exception.getDate(), exception.getNewTimeslotId(),
                        exception.getAddedClassId(), exception.getAddedSubjectId(),
                        exception.getSubstituteTeacherId(), exception.getSubstituteRoomId()))
                .orElse(null);
    }

    // ------------------------------------------------------------------ Pruefungen

    private void validate(NewEvent input) {
        List<FieldError> errors = new ArrayList<>();
        if (input.kind() == EventKind.TEACHER_ABSENCE
                && (input.teacherId() == null || teachers.findById(input.teacherId()).isEmpty())) {
            errors.add(new FieldError("teacherId", "Lehrkraft fehlt"));
        }
        if (input.kind() == EventKind.ROOM_BLOCKED
                && (input.roomId() == null || rooms.findById(input.roomId()).isEmpty())) {
            errors.add(new FieldError("roomId", "Raum fehlt"));
        }
        if (input.kind() == EventKind.EXTRA_LESSON) {
            validateExtra(input.extra(), errors);
        } else if (input.dateFrom() != null && input.dateTo() != null) {
            if (input.dateTo().isBefore(input.dateFrom())) {
                errors.add(new FieldError("dateTo", "muss nach dem Beginn liegen"));
            } else if (input.dateFrom().plusDays(WeekResolver.MAX_DAYS - 1L).isBefore(input.dateTo())) {
                // `affected` loest jeden Tag des Zeitraums auf und hoert nach MAX_DAYS Tagen auf. Ohne
                // diese Grenze bekaeme ein laengeres Ereignis eine still gekuerzte Liste zurueck.
                errors.add(new FieldError("dateTo",
                        "höchstens " + WeekResolver.MAX_DAYS + " Tage auf einmal"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void validateExtra(ExtraLessonView extra, List<FieldError> errors) {
        if (extra == null) {
            errors.add(new FieldError("extra", "Angaben zur Zusatzstunde fehlen"));
            return;
        }
        if (extra.date() == null) {
            errors.add(new FieldError("extra.date", "darf nicht leer sein"));
        }
        if (extra.timeslotId() == null || timeslots.findById(extra.timeslotId()).isEmpty()) {
            errors.add(new FieldError("extra.timeslotId", "Zeitfenster fehlt"));
        }
        if (extra.classId() == null || classes.findById(extra.classId()).isEmpty()) {
            errors.add(new FieldError("extra.classId", "Klasse fehlt"));
        }
        if (extra.subjectId() == null || subjects.findById(extra.subjectId()).isEmpty()) {
            errors.add(new FieldError("extra.subjectId", "Fach fehlt"));
        }
        if (extra.teacherId() == null || teachers.findById(extra.teacherId()).isEmpty()) {
            errors.add(new FieldError("extra.teacherId", "Lehrkraft fehlt"));
        }
        if (extra.roomId() == null || rooms.findById(extra.roomId()).isEmpty()) {
            errors.add(new FieldError("extra.roomId", "Raum fehlt"));
        }
    }

}
