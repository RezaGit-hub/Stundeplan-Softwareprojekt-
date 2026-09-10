package de.wsp3_1.substitution.domain;

import de.wsp3_1.masterdata.domain.Timegrid;
import de.wsp3_1.masterdata.persistence.Room;
import de.wsp3_1.masterdata.persistence.RoomRepository;
import de.wsp3_1.masterdata.persistence.SchoolClass;
import de.wsp3_1.masterdata.persistence.SchoolClassRepository;
import de.wsp3_1.masterdata.persistence.SubjectRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.masterdata.persistence.Timeslot;
import de.wsp3_1.masterdata.persistence.TimeslotRepository;
import de.wsp3_1.planning.domain.PlanReader;
import de.wsp3_1.planning.domain.PlanReader.PlanPeriod;
import de.wsp3_1.planning.domain.PlanViews.LessonView;
import de.wsp3_1.shared.Weekday;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.substitution.domain.DayFacts.EventFact;
import de.wsp3_1.substitution.domain.DayFacts.ExceptionFact;
import de.wsp3_1.substitution.domain.DayFacts.SlotFact;
import de.wsp3_1.substitution.domain.DayFacts.SubjectFact;
import de.wsp3_1.substitution.domain.SubstitutionViews.PendingRowView;
import de.wsp3_1.substitution.domain.WeekViews.DayView;
import de.wsp3_1.substitution.domain.WeekViews.EntryView;
import de.wsp3_1.substitution.domain.WeekViews.OriginalView;
import de.wsp3_1.substitution.domain.WeekViews.WeekView;
import de.wsp3_1.substitution.persistence.ChangeEvent;
import de.wsp3_1.substitution.persistence.ChangeEventRepository;
import de.wsp3_1.substitution.persistence.Holiday;
import de.wsp3_1.substitution.persistence.HolidayRepository;
import de.wsp3_1.substitution.persistence.LessonException;
import de.wsp3_1.substitution.persistence.LessonExceptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loest Kalendertage auf: welcher Plan gilt, was ist an diesem Tag anders, und was steht am Ende in
 * jeder Zelle (W-4 bis W-7). Die Antwort ist fertig — Web und App faerben und beschriften nur.
 *
 * <p>Der Weg ist immer derselbe:</p>
 * <ol>
 *   <li>Regelplan des Tages ({@link PlanReader#plannedAt(LocalDate)}) → lauter {@code REGULAR}</li>
 *   <li>Ereignisse (Ausfall, Raumsperrung) → {@code NEEDS_SUBSTITUTION} plus Anlass</li>
 *   <li>Ausnahmen des Tages → {@code SUBSTITUTED}, {@code CANCELLED} oder (beim Ziel einer
 *       Verlegung) ein zusaetzlicher Eintrag {@code MOVED}</li>
 *   <li>Zusatzstunden → {@code ADDED} ohne Stundenkennung</li>
 *   <li>Ereignisse noch einmal, jetzt gegen die <b>aufgeloeste</b> Belegung: faellt die
 *       Vertretungslehrkraft am selben Tag ebenfalls aus, ist die Stunde wieder offen</li>
 * </ol>
 *
 * <p><b>Gespeichert wird davon nichts</b> (E-31): jeder Aufruf rechnet neu. Deshalb verschwindet ein
 * Vertretungsbedarf von selbst, sobald das Ereignis geloescht wird, und der Regelplan bleibt in jedem
 * Fall unberuehrt — am naechsten Montag steht wieder, was im Plan steht.</p>
 *
 * <p><b>Warum je Tag ein eigener Plan?</b> Weil „Gueltig ab" (E-40) mitten in einer Woche greifen darf.
 * {@link PlanReader#plannedAt(LocalDate)} beantwortet fuer jeden Tag „welcher Plan gilt hier".</p>
 *
 * <p>Die eigentliche Aufloesung ist die <b>statische</b> Methode {@link #resolveDay(DayFacts)} — eine
 * Funktion ohne Datenbank, ohne Spring, ohne Uhrzeit; sie laesst sich wie der {@code ConflictChecker}
 * ohne PostgreSQL pruefen ({@code WeekResolverTest}). Der Dienst traegt nur die Tatsachen zusammen.</p>
 */
@Service
public class WeekResolver {

    /**
     * Obergrenze fuer einen Zeitraum, damit ein Tippfehler in {@code from}/{@code to} nicht endlos
     * rechnet — gezaehlt werden Kalendertage <b>einschliesslich</b> beider Enden. Wer mehr verlangt,
     * bekommt 400 und keine still gekuerzte Antwort ({@code SubstitutionController.requireRange},
     * {@code ChangeEventService.validate}).
     */
    public static final int MAX_DAYS = 366;

    private final PlanReader planReader;
    private final TimeslotRepository timeslots;
    private final HolidayRepository holidays;
    private final SchoolClassRepository classes;
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final SubjectRepository subjects;
    private final ChangeEventRepository events;
    private final LessonExceptionRepository exceptions;

    public WeekResolver(PlanReader planReader, TimeslotRepository timeslots, HolidayRepository holidays,
                        SchoolClassRepository classes, TeacherRepository teachers, RoomRepository rooms,
                        SubjectRepository subjects, ChangeEventRepository events,
                        LessonExceptionRepository exceptions) {
        this.planReader = planReader;
        this.timeslots = timeslots;
        this.holidays = holidays;
        this.classes = classes;
        this.teachers = teachers;
        this.rooms = rooms;
        this.subjects = subjects;
        this.events = events;
        this.exceptions = exceptions;
    }

    // ------------------------------------------------------------------ oeffentliche Sichten

    /**
     * Die Woche zu einem beliebigen Tag, immer ab Montag.
     *
     * @param date ein Tag der gewuenschten Woche
     * @param view Sicht; {@code SCHOOL} zeigt alles
     * @param id   Klasse, Lehrkraft oder Raum — Pflicht bei jeder Sicht ausser {@code SCHOOL}
     * @throws ValidationException wenn {@code id} fehlt, obwohl die Sicht eine braucht (400)
     * @throws NotFoundException   wenn es das Objekt hinter {@code id} nicht gibt (404)
     */
    @Transactional(readOnly = true)
    public WeekView week(LocalDate date, View view, Long id) {
        requireId(view, id);

        LocalDate monday = date.with(DayOfWeek.MONDAY);
        List<Weekday> schoolWeekdays = schoolWeekdays();
        if (schoolWeekdays.isEmpty()) {
            return new WeekView(monday, List.of());
        }
        LocalDate lastDay = monday.plusDays(schoolWeekdays.getLast().ordinal());

        List<DayView> days = days(monday, lastDay).stream()
                .map(day -> new DayView(day.date(), day.schoolDay(), filterView(day.entries(), view, id)))
                .toList();
        return new WeekView(monday, days);
    }

    /**
     * Die Unterrichtstage eines Zeitraums mit allen Eintraegen (Sicht {@code SCHOOL}). Tage, an denen
     * das Raster kein Zeitfenster hat — in der Regel der Sonntag —, kommen gar nicht vor;
     * unterrichtsfreie Tage stehen mit {@code schoolDay false} und ohne Eintraege in der Liste.
     *
     * <p>Grunddaten, Ereignisse und Ausnahmen werden <b>einmal</b> geladen und dann Tag fuer Tag
     * ausgewertet; nur den geltenden Plan fragt jeder Tag fuer sich (E-40).</p>
     */
    @Transactional(readOnly = true)
    public List<DayView> days(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return List.of();
        }
        Map<Long, SlotFact> slots = slotFacts();
        Set<Weekday> schoolWeekdays = EnumSet.noneOf(Weekday.class);
        slots.values().forEach(slot -> schoolWeekdays.add(slot.day()));

        List<Holiday> periods = holidays.findByDateToGreaterThanEqualAndDateFromLessThanEqual(from, to);
        Map<Long, String> teacherShorts = names(teachers.findAllByOrderByIdAsc(), Teacher::getId,
                Teacher::getShortName);
        Map<Long, String> roomNames = names(rooms.findAllByOrderByIdAsc(), Room::getId, Room::getName);
        Map<Long, String> classNames = names(classes.findAllByOrderByIdAsc(), SchoolClass::getId,
                SchoolClass::getName);
        Map<Long, SubjectFact> subjectFacts = new LinkedHashMap<>();
        subjects.findAllByOrderByIdAsc().forEach(subject -> subjectFacts.put(subject.getId(),
                new SubjectFact(subject.getId(), subject.getShortName(), subject.getColor())));

        List<EventFact> allEvents = eventFactsBetween(from, to);
        List<ExceptionFact> allExceptions = exceptionFactsBetween(from, to);
        Map<Long, String> eventReasons = reasonsFor(allEvents, allExceptions);
        PlanCache plans = new PlanCache();

        List<DayView> result = new ArrayList<>();
        LocalDate day = from;
        for (int counted = 0; !day.isAfter(to) && counted < MAX_DAYS; day = day.plusDays(1), counted++) {
            Weekday weekday = weekdayOf(day);
            if (weekday == null || !schoolWeekdays.contains(weekday)) {
                continue;
            }
            if (isHoliday(periods, day)) {
                result.add(new DayView(day, false, List.of()));
                continue;
            }
            List<ExceptionFact> dayExceptions = exceptionsOf(allExceptions, day);
            DayFacts facts = new DayFacts(day, weekday,
                    plans.at(day),
                    eventsOf(allEvents, day),
                    dayExceptions,
                    eventReasons,
                    movedOrigins(plans, dayExceptions, day),
                    slots, teacherShorts, roomNames, classNames, subjectFacts);
            result.add(new DayView(day, true, resolveDay(facts)));
        }
        return result;
    }

    /** Ein einzelner Tag in der Sicht {@code SCHOOL}; an einem unterrichtsfreien Tag leer. */
    @Transactional(readOnly = true)
    public List<EntryView> day(LocalDate date) {
        List<DayView> days = days(date, date);
        return days.isEmpty() ? List.of() : days.getFirst().entries();
    }

    /**
     * Die vertretungsbeduerftigen Stunden eines Zeitraums, sortiert nach Datum, Position und Klasse
     * (W-6, „naechste offene Stunde"). Sobald eine Vertretung eingetragen ist, faellt die Zeile
     * heraus — der Eintrag heisst dann {@code SUBSTITUTED} und nicht mehr {@code NEEDS_SUBSTITUTION}.
     */
    @Transactional(readOnly = true)
    public List<PendingRowView> pending(LocalDate from, LocalDate to) {
        List<EventFact> allEvents = eventFactsBetween(from, to);
        List<PendingRowView> rows = new ArrayList<>();
        for (DayView day : days(from, to)) {
            List<EventFact> dayEvents = eventsOf(allEvents, day.date());
            for (EntryView entry : day.entries()) {
                if (entry.kind() != EntryKind.NEEDS_SUBSTITUTION || entry.lessonId() == null) {
                    continue;
                }
                rows.add(new PendingRowView(day.date(), entry.lessonId(), entry.classShort(),
                        entry.subjectShort(), entry.position(),
                        entry.teacherShort() == null ? "—" : entry.teacherShort(),
                        entry.roomShort() == null ? "—" : entry.roomShort(),
                        entry.reasons(), eventIdsFor(dayEvents, entry)));
            }
        }
        return rows.stream()
                .sorted(Comparator.comparing(PendingRowView::date)
                        .thenComparing(PendingRowView::position)
                        .thenComparing(PendingRowView::classShort))
                .toList();
    }

    /**
     * Die Ereignisse, die diese Stunde an diesem Tag betreffen — die Anlaesse, die eine neue Ausnahme
     * ohne eigene Angabe uebernimmt ({@code POST /api/exceptions} ohne {@code eventIds}).
     */
    public static List<Long> eventIdsFor(List<EventFact> dayEvents, EntryView entry) {
        return dayEvents.stream()
                .filter(event -> (event.kind() == EventKind.TEACHER_ABSENCE
                        && event.teacherId() != null && event.teacherId().equals(entry.teacherId()))
                        || (event.kind() == EventKind.ROOM_BLOCKED
                        && event.roomId() != null && event.roomId().equals(entry.roomId())))
                .map(EventFact::id)
                .toList();
    }

    /** Die Ereignisse, deren Zeitraum diesen Tag enthaelt — ohne die Zusatzstunden. */
    @Transactional(readOnly = true)
    public List<EventFact> eventsOn(LocalDate day) {
        return eventsOf(eventFactsBetween(day, day), day);
    }

    // ------------------------------------------------------------------ die Aufloesung als Funktion

    /**
     * Die Eintraege eines Unterrichtstags, nach Stundenposition und Klasse sortiert — ohne Datenbank.
     *
     * <p>Die Reihenfolge der Schritte ist wichtig: erst der Plan, dann die Ereignisse, dann die
     * Ausnahmen. Nur so sieht der Ereignisschritt noch die <i>urspruengliche</i> Lehrkraft und den
     * urspruenglichen Raum — und die Vertretung traegt am Ende beide Anlaesse (T-A2.1). Weil derselbe
     * Schritt die <i>neue</i> Belegung dann nicht mehr prueft, laufen die Ereignisse am Ende ein
     * zweites Mal (Schritt 5): sonst bliebe eine Stunde vertreten, deren Vertretung selbst ausfaellt.</p>
     */
    public static List<EntryView> resolveDay(DayFacts facts) {
        List<Entry> entries = new ArrayList<>();

        // 1. Regelplan: jede Stunde dieses Wochentags als REGULAR. Offene Stunden (ohne Zeitfenster)
        //    liegen an keinem Tag und erscheinen deshalb in keiner Wochensicht.
        for (LessonView lesson : facts.lessons()) {
            if (lesson.timeslotId() != null && lesson.day() == facts.weekday()) {
                entries.add(Entry.regular(lesson, facts.date()));
            }
        }

        // 2. Ereignisse: Ausfall der Lehrkraft und Sperrung des Raums machen die Stunde vertretungsbeduerftig.
        for (EventFact event : facts.events()) {
            for (Entry entry : entries) {
                if (hits(event, entry.teacherId, entry.roomId)) {
                    entry.kind = EntryKind.NEEDS_SUBSTITUTION;
                    entry.addReason(event.reason());
                }
            }
        }

        // 3. Ausnahmen dieses Tages: Vertretung, Entfall, Ursprung einer Verlegung.
        for (ExceptionFact exception : facts.exceptions()) {
            if (!facts.date().equals(exception.date()) || exception.lessonId() == null) {
                continue;
            }
            Entry entry = entries.stream()
                    .filter(candidate -> exception.lessonId().equals(candidate.lessonId))
                    .findFirst().orElse(null);
            if (entry == null) {
                continue;
            }
            entry.exceptionId = exception.id();
            reasonsOf(exception, facts).forEach(entry::addReason);
            switch (exception.kind()) {
                case SUBSTITUTION -> applySubstitution(entry, exception, facts);
                case CANCEL -> entry.kind = EntryKind.CANCELLED;
                case MOVE -> applyMoveOrigin(entry, exception, facts);
                case ADDED -> {
                    // Eine Zusatzstunde haengt an keiner Regelplanstunde; sie entsteht in Schritt 4.
                }
            }
        }

        // 4. Ziele von Verlegungen und Zusatzstunden, die auf diesen Tag fallen.
        for (ExceptionFact exception : facts.exceptions()) {
            if (exception.kind() == ExceptionKind.MOVE && facts.date().equals(exception.newDate())) {
                movedTarget(exception, facts).ifPresent(entries::add);
            }
            if (exception.kind() == ExceptionKind.ADDED && facts.date().equals(exception.date())) {
                addedLesson(exception, facts).ifPresent(entries::add);
            }
        }

        // 5. Dieselben Ereignisse noch einmal gegen die AUFGELOESTE Belegung. Schritt 2 sieht die
        //    urspruengliche Lehrkraft und den urspruenglichen Raum — genau das braucht er fuer die
        //    Anlaesse (T-A2.1). Wer aber die Vertretung uebernommen hat, kann am selben Tag ebenfalls
        //    ausfallen (Schulz vertritt die 7a, dann faellt Schulz aus): dann ist die Stunde wieder
        //    offen, sonst stuende ein Vertretungsplan mit abwesender Lehrkraft da, den niemand sieht.
        //    Die Ausnahme bleibt an der Zelle stehen (exceptionId) — die Planung hebt sie auf und
        //    traegt eine neue ein.
        //    Aussen vor bleiben: entfallene Stunden (die braucht niemand zu vertreten), Zusatzstunden
        //    (ohne Stundenkennung) und die Ziele von Verlegungen — fuer die gaebe es an diesem
        //    Kalendertag keine Ausnahme, die die Planung eintragen koennte (die Verlegung haengt am
        //    Ursprungstag, UNIQUE (lesson_id, date)). So zeigt die Woche nur das als offen an, was
        //    ueber POST /api/exceptions auch wirklich zu schliessen ist.
        for (EventFact event : facts.events()) {
            for (Entry entry : entries) {
                if (entry.lessonId == null || entry.kind == EntryKind.CANCELLED
                        || entry.kind == EntryKind.MOVED) {
                    continue;
                }
                if (hits(event, entry.teacherId, entry.roomId)) {
                    entry.kind = EntryKind.NEEDS_SUBSTITUTION;
                    entry.addReason(event.reason());
                }
            }
        }

        return entries.stream()
                .sorted(Comparator.comparingInt((Entry entry) -> entry.position)
                        .thenComparing(entry -> entry.classShort))
                .map(Entry::toView)
                .toList();
    }

    /**
     * Filtert die Eintraege eines Tages auf eine Sicht. In der Lehrkraftsicht kommt die eigene Stunde,
     * die eine andere Lehrkraft uebernommen hat, als {@code GIVEN_AWAY} dazu — sonst waere sie aus dem
     * eigenen Plan spurlos verschwunden (T-A2.1).
     */
    public static List<EntryView> filterView(List<EntryView> entries, View view, Long id) {
        if (view == View.SCHOOL) {
            return entries;
        }
        if (id == null) {
            return List.of();
        }
        List<EntryView> result = new ArrayList<>();
        for (EntryView entry : entries) {
            switch (view) {
                case CLASS -> {
                    if (id.equals(entry.classId())) {
                        result.add(entry);
                    }
                }
                case ROOM -> {
                    if (id.equals(entry.roomId())) {
                        result.add(entry);
                    }
                }
                case TEACHER -> {
                    if (id.equals(entry.teacherId())) {
                        result.add(entry);
                    } else if (entry.original() != null && id.equals(entry.original().teacherId())) {
                        result.add(givenAway(entry));
                    }
                }
                case SCHOOL -> result.add(entry);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ Schritte der Aufloesung

    /** Trifft dieses Ereignis diese Belegung? Ein Ausfall die Lehrkraft, eine Sperrung den Raum. */
    private static boolean hits(EventFact event, Long teacherId, Long roomId) {
        return (event.kind() == EventKind.TEACHER_ABSENCE
                && event.teacherId() != null && event.teacherId().equals(teacherId))
                || (event.kind() == EventKind.ROOM_BLOCKED
                && event.roomId() != null && event.roomId().equals(roomId));
    }

    private static void applySubstitution(Entry entry, ExceptionFact exception, DayFacts facts) {
        entry.kind = EntryKind.SUBSTITUTED;
        Long newTeacher = exception.substituteTeacherId();
        if (newTeacher != null && !newTeacher.equals(entry.teacherId)) {
            entry.originalTeacherId = entry.teacherId;
            entry.originalTeacherShort = entry.teacherShort;
            entry.teacherId = newTeacher;
            entry.teacherShort = facts.teacherShorts().get(newTeacher);
        }
        Long newRoom = exception.substituteRoomId();
        if (newRoom != null && !newRoom.equals(entry.roomId)) {
            entry.originalRoomId = entry.roomId;
            entry.originalRoomShort = entry.roomShort;
            entry.roomId = newRoom;
            entry.roomShort = facts.roomNames().get(newRoom);
        }
    }

    /** Der Ursprung einer Verlegung entfaellt an seinem Tag; der Grund nennt das Ziel. */
    private static void applyMoveOrigin(Entry entry, ExceptionFact exception, DayFacts facts) {
        entry.kind = EntryKind.CANCELLED;
        entry.reasons.addFirst("verlegt → " + slotLabel(exception.newTimeslotId(), facts));
    }

    /** Das Ziel einer Verlegung: dieselbe Stunde an einem anderen Tag oder in einem anderen Zeitfenster. */
    private static Optional<Entry> movedTarget(ExceptionFact exception, DayFacts facts) {
        LessonView lesson = facts.movedOrigins().get(exception.lessonId());
        SlotFact target = facts.slots().get(exception.newTimeslotId());
        if (lesson == null || target == null) {
            return Optional.empty();
        }
        Entry entry = Entry.regular(lesson, facts.date());
        entry.timeslotId = target.id();
        entry.day = target.day();
        entry.position = target.position();
        entry.kind = EntryKind.MOVED;
        entry.exceptionId = exception.id();
        entry.originalDate = exception.date();
        entry.originalPosition = positionOf(lesson.timeslotId(), facts);
        entry.reasons = new ArrayList<>(reasonsOf(exception, facts));
        return Optional.of(entry);
    }

    /** Eine Zusatzstunde: sie steht in keinem Plan, deshalb bleibt {@code lessonId} leer. */
    private static Optional<Entry> addedLesson(ExceptionFact exception, DayFacts facts) {
        SlotFact slot = facts.slots().get(exception.newTimeslotId());
        SubjectFact subject = facts.subjects().get(exception.addedSubjectId());
        String className = facts.classNames().get(exception.addedClassId());
        if (slot == null || subject == null || className == null) {
            return Optional.empty();
        }
        Entry entry = new Entry();
        entry.date = facts.date();
        entry.timeslotId = slot.id();
        entry.day = slot.day();
        entry.position = slot.position();
        entry.classId = exception.addedClassId();
        entry.classShort = className;
        entry.subjectId = subject.id();
        entry.subjectShort = subject.shortName();
        entry.subjectColor = subject.color();
        entry.teacherId = exception.substituteTeacherId();
        entry.teacherShort = facts.teacherShorts().get(exception.substituteTeacherId());
        entry.roomId = exception.substituteRoomId();
        entry.roomShort = facts.roomNames().get(exception.substituteRoomId());
        entry.kind = EntryKind.ADDED;
        entry.exceptionId = exception.id();
        entry.reasons = new ArrayList<>(reasonsOf(exception, facts));
        return Optional.of(entry);
    }

    private static EntryView givenAway(EntryView entry) {
        return new EntryView(entry.date(), entry.timeslotId(), entry.day(), entry.position(),
                entry.lessonId(), entry.classId(), entry.classShort(), entry.subjectId(),
                entry.subjectShort(), entry.subjectColor(), entry.teacherId(), entry.teacherShort(),
                entry.roomId(), entry.roomShort(), EntryKind.GIVEN_AWAY, entry.original(),
                entry.reasons(), entry.exceptionId());
    }

    /**
     * Die Anlaesse einer Ausnahme in der Reihenfolge ihrer Ereignisse. Gelesen wird aus
     * {@code eventReasons} ueber <b>alle</b> Ereignisse: eine Zusatzstunde nennt ihr
     * {@code EXTRA_LESSON}, und das wirkt an keinem Tag als Ausfall.
     */
    private static List<String> reasonsOf(ExceptionFact exception, DayFacts facts) {
        List<String> reasons = new ArrayList<>();
        for (Long eventId : exception.eventIds()) {
            String reason = facts.eventReasons().get(eventId);
            if (reason != null) {
                reasons.add(reason);
            }
        }
        return reasons;
    }

    /** „Mi 1." — Wochentag und angezeigte Stundennummer eines Zeitfensters. */
    private static String slotLabel(Long timeslotId, DayFacts facts) {
        SlotFact slot = facts.slots().get(timeslotId);
        if (slot == null) {
            return "?";
        }
        return slot.day().shortLabel() + " " + (slot.lessonNumber() == null ? "Pause" : slot.lessonNumber() + ".");
    }

    private static Integer positionOf(Long timeslotId, DayFacts facts) {
        SlotFact slot = facts.slots().get(timeslotId);
        return slot == null ? null : slot.position();
    }

    // ------------------------------------------------------------------ Tatsachen aus der Datenbank

    /**
     * Die Zeitfenster mit ihrer angezeigten Stundennummer (Pausen zaehlen nicht mit). Reihenfolge und
     * Nummerierung kommen aus {@link Timegrid} — derselben Stelle, aus der die Planung sie nimmt.
     */
    public Map<Long, SlotFact> slotFacts() {
        List<Timeslot> all = timeslots.findAll();
        Map<Long, Integer> numbers = Timegrid.lessonNumbers(all);
        Map<Long, SlotFact> facts = new LinkedHashMap<>();
        for (Timeslot slot : Timegrid.sortedByGrid(all)) {
            facts.put(slot.getId(), new SlotFact(slot.getId(), slot.getDay(), slot.getPosition(),
                    slot.isBreak(), numbers.get(slot.getId())));
        }
        return facts;
    }

    /**
     * Die Ereignisse, deren Zeitraum den Bereich beruehrt. Bis 05.09. las die Aufloesung <b>alle</b>
     * Ereignisse und warf die uebrigen in Java weg — die Bereichsabfrage lag im Repository schon bereit.
     */
    private List<EventFact> eventFactsBetween(LocalDate from, LocalDate to) {
        return events.findByDateFromLessThanEqualAndDateToGreaterThanEqualOrderByIdAsc(to, from)
                .stream().map(WeekResolver::eventFact).toList();
    }

    static EventFact eventFact(ChangeEvent event) {
        return new EventFact(event.getId(), event.getKind(), event.getTeacherId(), event.getRoomId(),
                event.getReason(), event.getDateFrom(), event.getDateTo());
    }

    /**
     * Die Ausnahmen, die den Bereich beruehren — nach ihrem Tag <b>oder</b> ihrem Zieltag: eine
     * Verlegung wirkt an beiden ({@link #exceptionsOf}).
     */
    private List<ExceptionFact> exceptionFactsBetween(LocalDate from, LocalDate to) {
        return exceptions.findTouching(from, to).stream().map(WeekResolver::exceptionFact).toList();
    }

    /**
     * Die Anlasstexte, die die Aufloesung nennen kann. Die Ereignisse des Bereichs stehen ohnehin da;
     * eine Ausnahme darf aber einen Anlass tragen, dessen Ereignis ausserhalb liegt (eine Verlegung an
     * einen Tag im Bereich, deren Ausfall davor begann). Diese wenigen kommen gezielt dazu.
     */
    private Map<Long, String> reasonsFor(List<EventFact> events2, List<ExceptionFact> exceptions2) {
        Map<Long, String> reasons = new LinkedHashMap<>();
        events2.forEach(event -> reasons.put(event.id(), event.reason()));
        Set<Long> fehlend = exceptions2.stream()
                .flatMap(exception -> exception.eventIds().stream())
                .filter(id -> !reasons.containsKey(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!fehlend.isEmpty()) {
            events.findAllById(fehlend).forEach(event -> reasons.put(event.getId(), event.getReason()));
        }
        return reasons;
    }

    static ExceptionFact exceptionFact(LessonException exception) {
        return new ExceptionFact(exception.getId(), exception.getLessonId(), exception.getDate(),
                exception.getKind(), exception.getSubstituteTeacherId(), exception.getSubstituteRoomId(),
                exception.getNewDate(), exception.getNewTimeslotId(), exception.getAddedClassId(),
                exception.getAddedSubjectId(), List.copyOf(exception.getEventIds()));
    }

    /**
     * Die Ereignisse, deren Zeitraum diesen Tag enthaelt. Zusatzstunden ({@code EXTRA_LESSON}) bleiben
     * draussen: sie machen keine Stunde vertretungsbeduerftig, sie legen eine neue an.
     */
    private static List<EventFact> eventsOf(List<EventFact> all, LocalDate day) {
        return all.stream()
                .filter(event -> event.kind() != EventKind.EXTRA_LESSON)
                .filter(event -> !day.isBefore(event.dateFrom()) && !day.isAfter(event.dateTo()))
                .toList();
    }

    /** Die Ausnahmen dieses Tages und die Verlegungen, deren Ziel er ist. */
    private static List<ExceptionFact> exceptionsOf(List<ExceptionFact> all, LocalDate day) {
        return all.stream()
                .filter(exception -> day.equals(exception.date()) || day.equals(exception.newDate()))
                .toList();
    }

    /**
     * Die Plaene eines Aufloesungslaufs: die Zeitleiste einmal geholt, die Stunden je <b>Plan</b> einmal
     * gelesen. Vorher stand der Zwischenspeicher am Datum — dieselbe Woche las denselben Plan damit
     * fuenf- bis sechsmal, ein Jahr bis zu 366-mal (jedes Mal alle angenommenen Plaene und alle
     * Stunden des gewaehlten).
     */
    private final class PlanCache {

        private final List<PlanPeriod> timeline = planReader.acceptedTimeline();
        private final Map<Long, List<LessonView>> lessonsByPlan = new HashMap<>();

        /** Die Stunden des Plans, der an diesem Tag gilt; leer, wenn nie ein Plan angenommen wurde. */
        List<LessonView> at(LocalDate day) {
            Long id = PlanReader.timetableIdAt(timeline, day);
            return id == null ? List.of() : lessonsByPlan.computeIfAbsent(id, planReader::lessonsOf);
        }
    }

    /**
     * Die Ursprungsstunden der Verlegungen, deren Ziel dieser Tag ist. Eine verlegte Stunde steht im
     * Plan ihres <b>urspruenglichen</b> Tages — dort wird sie gesucht, nicht im Plan des Zieltages.
     */
    private Map<Long, LessonView> movedOrigins(PlanCache plans,
                                               List<ExceptionFact> dayExceptions, LocalDate day) {
        Map<Long, LessonView> origins = new HashMap<>();
        for (ExceptionFact exception : dayExceptions) {
            if (exception.kind() != ExceptionKind.MOVE || !day.equals(exception.newDate())
                    || exception.lessonId() == null) {
                continue;
            }
            plans.at(exception.date()).stream()
                    .filter(lesson -> lesson.id().equals(exception.lessonId()))
                    // Am Zieltag muss dieselbe Stunde noch im dort geltenden Plan stehen. Wird zwischen
                    // Ursprungs- und Zieltag ein neuer Plan angenommen („Gueltig ab", E-40), gehoert die
                    // verlegte Stunde zu einem abgeloesten Plan — sie am Zieltag zu zeigen brachte den
                    // alten Plan zurueck. Alte Ausnahmen verfallen mit ihrem Plan (Warnung beim Annehmen).
                    .filter(lesson -> plans.at(day).stream()
                            .anyMatch(current -> current.id().equals(lesson.id())))
                    .findFirst()
                    .ifPresent(lesson -> origins.put(lesson.id(), lesson));
        }
        return origins;
    }

    /**
     * Die Wochentage, an denen das Raster ueberhaupt Zeitfenster hat. {@link EnumSet} zaehlt sie in der
     * Reihenfolge des Enums auf — also Mo, Di, … wie im Raster (Vertrag: „Sa nur wenn im Raster").
     */
    private List<Weekday> schoolWeekdays() {
        Set<Weekday> found = EnumSet.noneOf(Weekday.class);
        timeslots.findAll().forEach(slot -> found.add(slot.getDay()));
        return new ArrayList<>(found);
    }

    /** Der Wochentag eines Datums; der Sonntag gehoert zu keinem Raster und ist {@code null}. */
    public static Weekday weekdayOf(LocalDate date) {
        int day = date.getDayOfWeek().getValue();
        return day <= Weekday.values().length ? Weekday.values()[day - 1] : null;
    }

    private static boolean isHoliday(List<Holiday> periods, LocalDate day) {
        return periods.stream()
                .anyMatch(holiday -> !day.isBefore(holiday.getDateFrom()) && !day.isAfter(holiday.getDateTo()));
    }

    private static <E> Map<Long, String> names(List<E> entities, Function<E, Long> id,
                                               Function<E, String> name) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (E entity : entities) {
            map.put(id.apply(entity), name.apply(entity));
        }
        return map;
    }

    /**
     * Jede Sicht ausser {@code SCHOOL} braucht ein Objekt — und zwar eines, das es gibt. Fehlt der
     * Parameter, ist das ein Eingabefehler (400); zeigt er ins Leere, ist es 404.
     */
    private void requireId(View view, Long id) {
        if (view == View.SCHOOL) {
            return;
        }
        if (id == null) {
            throw new ValidationException("id", "Diese Sicht braucht eine Kennung");
        }
        boolean exists = switch (view) {
            case CLASS -> classes.findById(id).isPresent();
            case TEACHER -> teachers.findById(id).isPresent();
            case ROOM -> rooms.findById(id).isPresent();
            case SCHOOL -> true;
        };
        if (!exists) {
            throw new NotFoundException("Zu dieser Sicht gibt es die Kennung " + id + " nicht.");
        }
    }

    // ------------------------------------------------------------------ Eintrag im Bau

    /**
     * Ein Eintrag, solange er noch veraendert wird. Die Aufloesung geht in vier Schritten ueber
     * dieselbe Liste; mit unveraenderlichen Records muesste jeder Schritt sie neu bauen, und das waere
     * schwerer zu lesen als diese eine kurzlebige Klasse. Nach aussen geht nur {@link #toView()}.
     */
    private static final class Entry {

        private LocalDate date;
        private Long timeslotId;
        private Weekday day;
        private int position;
        private Long lessonId;
        private Long classId;
        private String classShort;
        private Long subjectId;
        private String subjectShort;
        private String subjectColor;
        private Long teacherId;
        private String teacherShort;
        private Long roomId;
        private String roomShort;
        private EntryKind kind = EntryKind.REGULAR;
        private Long exceptionId;
        private List<String> reasons = new ArrayList<>();
        private Long originalTeacherId;
        private String originalTeacherShort;
        private Long originalRoomId;
        private String originalRoomShort;
        private LocalDate originalDate;
        private Integer originalPosition;

        static Entry regular(LessonView lesson, LocalDate date) {
            Entry entry = new Entry();
            entry.date = date;
            entry.timeslotId = lesson.timeslotId();
            entry.day = lesson.day();
            entry.position = lesson.position();
            entry.lessonId = lesson.id();
            entry.classId = lesson.classId();
            entry.classShort = lesson.classShort();
            entry.subjectId = lesson.subjectId();
            entry.subjectShort = lesson.subjectShort();
            entry.subjectColor = lesson.subjectColor();
            entry.teacherId = lesson.teacherId();
            entry.teacherShort = lesson.teacherShort();
            entry.roomId = lesson.roomId();
            entry.roomShort = lesson.roomShort();
            return entry;
        }

        void addReason(String reason) {
            if (!reasons.contains(reason)) {
                reasons.add(reason);
            }
        }

        /** {@code original} steht nur da, wo sich wirklich etwas geaendert hat. */
        private OriginalView original() {
            if (originalTeacherId == null && originalRoomId == null && originalDate == null) {
                return null;
            }
            return new OriginalView(originalTeacherId, originalTeacherShort, originalRoomId,
                    originalRoomShort, originalDate, originalPosition);
        }

        EntryView toView() {
            return new EntryView(date, timeslotId, day, position, lessonId, classId, classShort,
                    subjectId, subjectShort, subjectColor, teacherId, teacherShort, roomId, roomShort,
                    kind, original(), List.copyOf(reasons), exceptionId);
        }
    }
}
