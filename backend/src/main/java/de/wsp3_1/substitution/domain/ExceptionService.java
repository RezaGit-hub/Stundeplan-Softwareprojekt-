package de.wsp3_1.substitution.domain;

import de.wsp3_1.auth.persistence.UserAccount;
import de.wsp3_1.changelog.domain.ChangelogAction;
import de.wsp3_1.changelog.domain.ChangelogService;
import de.wsp3_1.changelog.domain.ChangelogViews.NewEntry;
import de.wsp3_1.masterdata.persistence.RoomRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.masterdata.persistence.TimeslotRepository;
import de.wsp3_1.planning.domain.Assignment;
import de.wsp3_1.planning.domain.ConflictService;
import de.wsp3_1.planning.domain.ConflictView;
import de.wsp3_1.planning.domain.PlanReader;
import de.wsp3_1.planning.domain.PlanViews.LessonView;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.shared.domain.ValidationException.FieldError;
import de.wsp3_1.substitution.domain.DayFacts.EventFact;
import de.wsp3_1.substitution.domain.DayFacts.SlotFact;
import de.wsp3_1.substitution.domain.SubstitutionViews.ExceptionView;
import de.wsp3_1.substitution.domain.SubstitutionViews.NewException;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Datierte Ausnahmen (W-6, W-7): Vertretung, Raumwechsel, Verlegung, Entfall.
 *
 * <p>Eine Ausnahme gilt fuer <b>einen Kalendertag</b> und laesst den Regelplan unberuehrt — am
 * naechsten Montag steht wieder, was im Plan steht (Leitszenario, V-5). Je Stunde und Tag gibt es
 * hoechstens eine; eine zweite ist 409 {@code exception-exists} und keine stille Ersetzung, sonst
 * verschwaende eine unbeabsichtigte Eingabe die erste Vertretung.</p>
 *
 * <p>Beim Anlegen entstehen drei Dinge: die Zeile selbst, ein Verlaufseintrag mit alter und neuer
 * Belegung (T-W7.1) und die <b>Folgekonflikte des Tages</b> — Daten, kein Fehler: gespeichert wird
 * immer, die Planung entscheidet. Geprueft wird mit derselben Pruefung wie im Regelplan
 * ({@code planning.ConflictService}), damit Plan und Vertretung nicht auseinanderlaufen.</p>
 */
@Service
public class ExceptionService {


    private final LessonExceptionRepository exceptions;
    private final ChangeEventRepository events;
    private final Actors actors;
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final TimeslotRepository timeslots;
    private final PlanReader planReader;
    private final ConflictService conflictService;
    private final ChangelogService changelog;
    private final WeekResolver weekResolver;

    public ExceptionService(LessonExceptionRepository exceptions, ChangeEventRepository events,
                            Actors actors, TeacherRepository teachers,
                            RoomRepository rooms, TimeslotRepository timeslots,
                            PlanReader planReader, ConflictService conflictService,
                            ChangelogService changelog, WeekResolver weekResolver) {
        this.exceptions = exceptions;
        this.events = events;
        this.actors = actors;
        this.teachers = teachers;
        this.rooms = rooms;
        this.timeslots = timeslots;
        this.planReader = planReader;
        this.conflictService = conflictService;
        this.changelog = changelog;
        this.weekResolver = weekResolver;
    }

    /** Die Ausnahmen eines Zeitraums; {@code conflicts} bleibt hier leer (nur {@code POST} fuellt es). */
    @Transactional(readOnly = true)
    public List<ExceptionView> list(LocalDate from, LocalDate to) {
        return exceptions.findByDateBetweenOrderByIdAsc(from, to).stream()
                .map(exception -> view(exception, List.of()))
                .toList();
    }

    /**
     * Legt eine Ausnahme an, schreibt den Verlaufseintrag und meldet die Folgekonflikte des Tages.
     *
     * <p>Der Kalendertag wird genau <b>zweimal</b> aufgeloest: einmal vor dem Speichern und einmal
     * danach. Das ist keine Sparmassnahme, sondern die Fachlogik selbst — der Verlaufseintrag
     * vergleicht „vorher" mit „nachher", und beide Zustaende stehen dadurch als eigene Groesse im
     * Code statt verstreut in sechs Helfern (bis 05.09. loeste ein einziger Aufruf denselben Tag bis
     * zu sechsmal vollstaendig auf).</p>
     */
    @Transactional
    public ExceptionView create(NewException input) {
        validate(input);

        DayView vorher = resolvedDay(input.date());
        LessonView lesson = lessonOf(vorher, input.lessonId());
        exceptions.findByLessonIdAndDate(lesson.id(), input.date()).ifPresent(existing -> {
            throw new ConflictException("exception-exists", "Für diese Stunde gibt es am "
                    + SubstitutionText.date(input.date())
                    + " schon eine Ausnahme. Erst aufheben, dann neu anlegen.");
        });
        List<EventFact> tagesereignisse = weekResolver.eventsOn(input.date());
        validateAgainstLesson(input, lesson, tagesereignisse);

        String oldAssignment = assignmentText(entryOf(vorher, lesson.id()).orElse(null));

        UserAccount actor = actors.current();
        LessonException exception = LessonException.forLesson(lesson.id(), input.date(), input.kind(),
                input.substituteTeacherId(), input.substituteRoomId(), input.newDate(),
                input.newTimeslotId(), Instant.now(), actor);
        exception.getEventIds().addAll(eventIds(input, lesson, vorher, tagesereignisse));
        exceptions.saveAndFlush(exception);

        DayView nachher = resolvedDay(input.date());
        String newAssignment = newAssignmentText(input, lesson, nachher);
        changelog.record(new NewEntry(ChangelogAction.EXCEPTION_CREATED, input.date(),
                lesson.position(), oldAssignment, newAssignment, reasonsText(exception),
                actor.getId(), lesson.id(), lesson.classId(), lesson.teacherId()));

        return view(exception, conflictsAround(input, lesson, nachher));
    }

    /**
     * Hebt eine Ausnahme auf. Die Stunde wird damit wieder vertretungsbeduerftig, solange das
     * ausloesende Ereignis besteht — den Bedarf rechnet der {@link WeekResolver} ohnehin neu.
     *
     * <p>Eine Zusatzstunde ({@code ADDED}) geht diesen Weg <b>nicht</b>: sie gehoert zu ihrem Ereignis
     * und wird mit ihm zurueckgenommen ({@code DELETE /api/events/{id}}, SU-12). Wer sie hier loeschte,
     * liesse ein Ereignis {@code EXTRA_LESSON} ohne Stunde zurueck.</p>
     */
    @Transactional
    public void delete(Long id) {
        LessonException exception = exceptions.findById(id)
                .orElseThrow(() -> new NotFoundException("Die Ausnahme " + id + " gibt es nicht."));
        if (exception.getKind() == ExceptionKind.ADDED) {
            throw new ValidationException("id",
                    "Zusatzstunden werden über DELETE /api/events/{id} zurückgenommen");
        }
        DayView vorher = resolvedDay(exception.getDate());
        LessonView lesson = exception.getLessonId() == null ? null
                : findLesson(vorher, exception.getLessonId()).orElse(null);

        // Der alte Text je Art — dieselben Faelle wie in newAssignmentText. Bei einer Verlegung steht
        // am Ursprungstag ein CANCELLED-Eintrag mit unveraenderter Lehrkraft und unveraendertem Raum;
        // „Meyer / R204 → Meyer / R204" saegte den Verlaufseintrag sinnlos. Gesagt werden muss, was
        // aufgehoben wird: das Ziel der Verlegung.
        String oldAssignment = switch (exception.getKind()) {
            case CANCEL -> "entfällt";
            case MOVE -> moveTargetText(exception.getNewTimeslotId());
            default -> assignmentText(entryOf(vorher, exception.getLessonId()).orElse(null));
        };
        String reasons = reasonsText(exception);
        UserAccount actor = actors.current();

        exceptions.delete(exception);
        exceptions.flush();

        String newAssignment = assignmentText(
                entryOf(resolvedDay(exception.getDate()), exception.getLessonId()).orElse(null));
        changelog.record(new NewEntry(ChangelogAction.EXCEPTION_DELETED, exception.getDate(),
                lesson == null ? null : lesson.position(), oldAssignment, newAssignment, reasons,
                actor.getId(), lesson == null ? null : lesson.id(),
                lesson == null ? null : lesson.classId(), lesson == null ? null : lesson.teacherId()));
    }

    // ------------------------------------------------------------------ Texte des Verlaufs

    /** „Meyer / R101" — der Textschnappschuss, der auch nach einer Umbenennung lesbar bleibt. */
    private String assignmentText(EntryView entry) {
        if (entry == null) {
            return "—";
        }
        String teacher = entry.teacherId() == null ? null : teachers.findById(entry.teacherId())
                .map(Teacher::getName).orElse(entry.teacherShort());
        return (teacher == null ? "—" : teacher) + " / "
                + (entry.roomShort() == null ? "—" : entry.roomShort());
    }

    /** Was nach der Aenderung dasteht: eine Belegung, „entfällt" oder das Ziel der Verlegung. */
    private String newAssignmentText(NewException input, LessonView lesson, DayView nachher) {
        return switch (input.kind()) {
            case CANCEL -> "entfällt";
            case MOVE -> moveTargetText(input.newTimeslotId());
            default -> assignmentText(entryOf(nachher, lesson.id()).orElse(null));
        };
    }

    /** „Di, 3. Stunde" — Wochentag und angezeigte Stundennummer des Zielzeitfensters. */
    private String moveTargetText(Long timeslotId) {
        Map<Long, SlotFact> slots = weekResolver.slotFacts();
        SlotFact slot = slots.get(timeslotId);
        if (slot == null) {
            return "verlegt";
        }
        return slot.day().shortLabel() + ", "
                + (slot.lessonNumber() == null ? "Pause" : slot.lessonNumber() + ". Stunde");
    }

    /**
     * Alle Anlaesse, mit „; " getrennt — so steht es im Verlauf (V-6). Gelesen wird ueber die Kennungen
     * der Ausnahme, nicht ueber die Ereignisse des Tages: ein Anlass darf auch von einem Ereignis
     * kommen, das an diesem Tag nicht als Ausfall wirkt.
     */
    private String reasonsText(LessonException exception) {
        List<Long> ids = exception.getEventIds().stream().sorted().toList();
        Map<Long, String> reasons = new LinkedHashMap<>();
        for (ChangeEvent event : events.findAllById(ids)) {
            reasons.put(event.getId(), event.getReason());
        }
        return ids.stream().map(reasons::get).filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
    }

    // ------------------------------------------------------------------ Folgekonflikte

    /**
     * Die Folgekonflikte des Tages, die diese Stunde betreffen — bei einer Verlegung auch die des
     * Zieltages.
     *
     * <p>Geprueft wird die aufgeloeste Woche, nicht der Regelplan: eine Vertretung kann eine Lehrkraft
     * doppelt belegen, ohne dass am Plan etwas falsch waere.</p>
     */
    private List<ConflictView> conflictsAround(NewException input, LessonView lesson, DayView nachher) {
        List<ConflictView> conflicts = new ArrayList<>(dayConflicts(nachher));
        if (input.kind() == ExceptionKind.MOVE && input.newDate() != null
                && !input.newDate().equals(input.date())) {
            conflicts.addAll(dayConflicts(resolvedDay(input.newDate())));
        }
        return conflicts.stream()
                .filter(conflict -> conflict.lessonIds().contains(lesson.id()))
                .toList();
    }

    /**
     * Die Konflikte eines aufgeloesten Tages. Zusatzstunden gehen mit ein — sonst duerfte eine
     * Vertretung die Lehrkraft einer Zusatzstunde doppelt belegen und die Antwort meldete
     * {@code conflicts: []}. Da eine Zusatzstunde keine Stundenkennung hat, bekommt sie hier eine
     * <b>kuenstliche negative</b> (die ihrer Ausnahme, negiert); echte Kennungen sind positiv, also
     * kollidiert nichts. Aus der Antwort fallen sie wieder heraus ({@link #onlyRealLessons}) — nach
     * aussen geht keine Kennung, die es nicht gibt. Genannt wird die Zusatzstunde dann ueber
     * {@code objects} (Lehrkraft, Raum, Klasse) und den Meldungstext.
     */
    private List<ConflictView> dayConflicts(DayView day) {
        List<Assignment> assignments = day.entries().stream()
                .filter(entry -> entry.kind() != EntryKind.CANCELLED)
                .filter(entry -> entry.lessonId() != null || entry.exceptionId() != null)
                .map(entry -> new Assignment(checkId(entry), entry.classId(), entry.subjectId(),
                        entry.teacherId(), entry.roomId(), entry.timeslotId(), false))
                .toList();
        return conflictService.check(assignments).stream()
                .map(ExceptionService::onlyRealLessons)
                .toList();
    }

    private static Long checkId(EntryView entry) {
        return entry.lessonId() != null ? entry.lessonId() : -entry.exceptionId();
    }

    private static ConflictView onlyRealLessons(ConflictView conflict) {
        List<Long> real = conflict.lessonIds().stream().filter(id -> id > 0).toList();
        return real.size() == conflict.lessonIds().size() ? conflict
                : new ConflictView(conflict.type(), conflict.severity(), conflict.message(), real,
                        conflict.ruleId(), conflict.objects());
    }

    // ------------------------------------------------------------------ Pruefungen und Helfer

    /**
     * Alles, was sich ohne die Stunde pruefen laesst — in zwei Stufen, weil die zweite gueltige
     * Kennungen braucht: erst die Pflichtfelder je Art und die Kennungen, dann Zeitfenster und Tag.
     */
    private void validate(NewException input) {
        List<FieldError> errors = new ArrayList<>();
        if (input.kind() == ExceptionKind.ADDED) {
            errors.add(new FieldError("kind", "Zusatzstunden werden über Ereignisse angelegt"));
        }
        if (input.kind() == ExceptionKind.SUBSTITUTION
                && input.substituteTeacherId() == null && input.substituteRoomId() == null) {
            errors.add(new FieldError("substituteTeacherId", "Lehrkraft oder Raum wählen"));
        }
        if (input.kind() == ExceptionKind.MOVE) {
            if (input.newDate() == null) {
                errors.add(new FieldError("newDate", "darf nicht leer sein"));
            }
            if (input.newTimeslotId() == null) {
                errors.add(new FieldError("newTimeslotId", "darf nicht leer sein"));
            }
        }
        // Kennungen, die es nicht gibt. Ohne diese Pruefung liefe die Zeile in den Fremdschluessel, und
        // der Handler antwortete 409 „in-use" („wird noch verwendet und kann nicht entfernt werden") —
        // fuer eine Neuanlage die falsche Meldung. Dieselbe Bauweise wie ChangeEventService.validateExtra.
        if (input.substituteTeacherId() != null && teachers.findById(input.substituteTeacherId()).isEmpty()) {
            errors.add(new FieldError("substituteTeacherId", "Lehrkraft fehlt"));
        }
        if (input.substituteRoomId() != null && rooms.findById(input.substituteRoomId()).isEmpty()) {
            errors.add(new FieldError("substituteRoomId", "Raum fehlt"));
        }
        if (input.newTimeslotId() != null && timeslots.findById(input.newTimeslotId()).isEmpty()) {
            errors.add(new FieldError("newTimeslotId", "Zeitfenster fehlt"));
        }
        if (input.eventIds() != null && !input.eventIds().isEmpty()
                && events.findAllById(input.eventIds()).size() != Set.copyOf(input.eventIds()).size()) {
            errors.add(new FieldError("eventIds", "Ereignis fehlt"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // Verlegung: das Zielzeitfenster liegt an einem festen Wochentag. Passt der nicht zu newDate,
        // stuende die Stunde je nach Feld an zwei verschiedenen Tagen.
        if (input.kind() == ExceptionKind.MOVE) {
            SlotFact slot = weekResolver.slotFacts().get(input.newTimeslotId());
            if (slot != null && slot.day() != WeekResolver.weekdayOf(input.newDate())) {
                errors.add(new FieldError("newTimeslotId", "passt nicht zum Wochentag von „newDate“"));
            }
            if (!schoolDay(input.newDate())) {
                errors.add(new FieldError("newDate", "ist kein Unterrichtstag"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Die Pruefungen, die die Stunde brauchen: Eine Vertretung muss etwas aendern, und wer vertritt,
     * muss an diesem Tag koennen.
     */
    private void validateAgainstLesson(NewException input, LessonView lesson,
                                       List<EventFact> tagesereignisse) {
        if (input.kind() != ExceptionKind.SUBSTITUTION) {
            return;
        }
        List<FieldError> errors = new ArrayList<>();
        boolean neueLehrkraft = input.substituteTeacherId() != null
                && !input.substituteTeacherId().equals(lesson.teacherId());
        boolean neuerRaum = input.substituteRoomId() != null
                && !input.substituteRoomId().equals(lesson.roomId());
        if (!neueLehrkraft && !neuerRaum) {
            // Sonst waere die Stunde SUBSTITUTED und fiele aus den offenen Stunden heraus, obwohl
            // weiter dieselbe Lehrkraft im selben Raum unterrichten soll.
            errors.add(new FieldError("substituteTeacherId", "Lehrkraft oder Raum muss sich ändern"));
        }

        // Wer an diesem Tag selbst ausfaellt, kann nicht vertreten; ein gesperrter Raum ist kein
        // Ausweichraum. Beides waere sonst ein Vertretungsplan, den niemand als offen sieht.
        if (input.substituteTeacherId() != null && tagesereignisse.stream()
                .anyMatch(event -> event.kind() == EventKind.TEACHER_ABSENCE
                        && input.substituteTeacherId().equals(event.teacherId()))) {
            errors.add(new FieldError("substituteTeacherId", "ist an diesem Tag nicht verfügbar"));
        }
        if (input.substituteRoomId() != null && tagesereignisse.stream()
                .anyMatch(event -> event.kind() == EventKind.ROOM_BLOCKED
                        && input.substituteRoomId().equals(event.roomId()))) {
            errors.add(new FieldError("substituteRoomId", "ist an diesem Tag nicht verfügbar"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** Die Stunde muss es im Plan geben, der an diesem Tag gilt — und sie muss eingeplant sein. */
    private LessonView lessonOf(DayView tag, Long lessonId) {
        return findLesson(tag, lessonId).orElseThrow(() -> new NotFoundException(
                "Am " + SubstitutionText.date(tag.date()) + " gibt es die Stunde " + lessonId
                        + " nicht."));
    }

    /**
     * Die Stunde im Plan dieses Tages — und nur, wenn sie an diesem Kalendertag wirklich liegt:
     * derselbe Wochentag wie ihr Zeitfenster, und der Tag ist Unterrichtstag. Ohne diese beiden
     * Bedingungen liesse sich eine Montagsstunde fuer einen Dienstag oder fuer einen Ferientag
     * bearbeiten; die Zeile stuende in der Datenbank, wirkte in keiner Wochensicht und blockierte
     * ueber UNIQUE (lesson_id, date) die richtige Eingabe.
     */
    private Optional<LessonView> findLesson(DayView tag, Long lessonId) {
        if (!tag.schoolDay()) {
            return Optional.empty();
        }
        return planReader.plannedAt(tag.date()).lessons().stream()
                .filter(lesson -> lesson.id().equals(lessonId))
                .filter(lesson -> lesson.timeslotId() != null)
                .filter(lesson -> lesson.day() == WeekResolver.weekdayOf(tag.date()))
                .findFirst();
    }

    /**
     * Der Kalendertag, einmal aufgeloest. Jede Aufloesung liest Raster, Ferien, Grunddaten, Ereignisse
     * und Ausnahmen neu und fragt den geltenden Plan — deshalb wird sie einmal gemacht und
     * weitergereicht. Ein Tag ohne Zeitfenster (Sonntag) kommt in der Antwort des
     * {@link WeekResolver} gar nicht vor und ist hier ein leerer, unterrichtsfreier Tag.
     */
    private DayView resolvedDay(LocalDate date) {
        List<DayView> days = weekResolver.days(date, date);
        return days.isEmpty() ? new DayView(date, false, List.of()) : days.getFirst();
    }

    /** Ist an diesem Kalendertag Unterricht? Ferien, Feiertage und der Sonntag sind es nicht. */
    private boolean schoolDay(LocalDate date) {
        return resolvedDay(date).schoolDay();
    }

    private static Optional<EntryView> entryOf(DayView tag, Long lessonId) {
        if (lessonId == null) {
            return Optional.empty();
        }
        return tag.entries().stream()
                .filter(entry -> lessonId.equals(entry.lessonId()))
                .findFirst();
    }

    /** Anlaesse: die angegebenen, sonst alle Ereignisse, die diese Stunde an diesem Tag betreffen. */
    private static List<Long> eventIds(NewException input, LessonView lesson, DayView vorher,
                                       List<EventFact> tagesereignisse) {
        if (input.eventIds() != null && !input.eventIds().isEmpty()) {
            return input.eventIds();
        }
        return entryOf(vorher, lesson.id())
                .map(entry -> WeekResolver.eventIdsFor(tagesereignisse, entry))
                .orElse(List.of());
    }

    private ExceptionView view(LessonException exception, List<ConflictView> conflicts) {
        return new ExceptionView(exception.getId(), exception.getDate(), exception.getLessonId(),
                exception.getKind(), exception.getSubstituteTeacherId(), exception.getSubstituteRoomId(),
                exception.getNewDate(), exception.getNewTimeslotId(),
                List.copyOf(exception.getEventIds()), conflicts);
    }

}
