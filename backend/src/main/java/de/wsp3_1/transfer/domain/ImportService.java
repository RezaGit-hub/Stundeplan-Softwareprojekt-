package de.wsp3_1.transfer.domain;

import de.wsp3_1.masterdata.MasterdataDtos.ClassDto;
import de.wsp3_1.masterdata.MasterdataDtos.ClassInput;
import de.wsp3_1.masterdata.MasterdataDtos.ClassSubjectDto;
import de.wsp3_1.masterdata.MasterdataDtos.ClassSubjectInput;
import de.wsp3_1.masterdata.MasterdataDtos.RoomDto;
import de.wsp3_1.masterdata.MasterdataDtos.RoomInput;
import de.wsp3_1.masterdata.MasterdataDtos.SubjectDto;
import de.wsp3_1.masterdata.MasterdataDtos.SubjectInput;
import de.wsp3_1.masterdata.MasterdataDtos.TeacherDto;
import de.wsp3_1.masterdata.MasterdataDtos.TeacherInput;
import de.wsp3_1.masterdata.MasterdataDtos.TimegridDto;
import de.wsp3_1.masterdata.MasterdataDtos.TimegridInput;
import de.wsp3_1.masterdata.MasterdataDtos.TimeslotDto;
import de.wsp3_1.masterdata.MasterdataDtos.TimeslotInput;
import de.wsp3_1.masterdata.domain.RoomService;
import de.wsp3_1.masterdata.domain.SchoolClassService;
import de.wsp3_1.masterdata.domain.SubjectService;
import de.wsp3_1.masterdata.domain.TeacherService;
import de.wsp3_1.masterdata.domain.TimegridService;
import de.wsp3_1.rules.api.RuleDtos.RuleDto;
import de.wsp3_1.rules.domain.RuleService;
import de.wsp3_1.rules.domain.RuleView;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.shared.domain.ValidationException.FieldError;
import de.wsp3_1.substitution.api.SubstitutionDtos.HolidayDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.HolidayInput;
import de.wsp3_1.substitution.domain.SubstitutionViews.NewHoliday;
import de.wsp3_1.substitution.domain.HolidayService;
import de.wsp3_1.transfer.api.TransferDtos.MasterDataImport;
import de.wsp3_1.transfer.api.TransferDtos.MasterDataImportResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * Spielt ein {@link MasterDataImport} ein: prueft, leert (bei {@code replace: true}) und legt in
 * Abhaengigkeitsreihenfolge neu an. Die ganze Arbeit laeuft in <b>einer</b> Transaktion —
 * schlaegt irgendetwas fehl, bleibt der alte Bestand unveraendert.
 *
 * <p><b>Strategie.</b> Der Import ruft die bestehenden Anlege-Methoden der Module auf
 * ({@code SubjectService.create}, {@code TeacherService.create}, …), statt die Entities selbst
 * zu schreiben. Zwei Gruende: (1) die fachlichen Pruefungen (Kuerzel doppelt, Deputat,
 * Klassenfach) bleiben an einer Stelle und koennen nicht zwischen Einzelanlage und Import
 * auseinanderlaufen; (2) die Fehler tragen dieselben Feldnamen wie bei den einzelnen
 * Endpunkten, und der {@code ApiExceptionHandler} kennt sie schon.</p>
 *
 * <p><b>Kennungen.</b> Die Datei traegt die Kennungen des exportierenden Systems. Beim Import
 * werden alle Eintraege neu angelegt, also neue Kennungen vergeben; die Verweise innerhalb der
 * Datei ({@code teacherId}, {@code subjectId}, {@code classId}, {@code timeslotId}) werden
 * ueber {@link IdMap} umgeschrieben (Format-Version 1).</p>
 *
 * <p><b>Leeren.</b> Gesaeubert werden alle Tabellen ausser {@code system_config},
 * {@code rule_preset}, {@code holiday_preset} und dem Konto der Planung — dieselbe Liste, die
 * auch {@code SampleDataLoader.clear()} nutzt. Das Konto {@code admin} bleibt, sonst sperrt
 * sich die Planung selbst aus. Konten von Lehrkraeften und Klassenkonten gehen mit (sie
 * verweisen auf die geloeschten Stammdaten und waeren sonst Waisen).</p>
 */
@Service
public class ImportService {

    /**
     * Tabellen, die vor dem Einspielen geleert werden — Kind- vor Elterntabellen, ohne CASCADE
     * (sonst faellt {@code user_account} mit, siehe unten).
     */
    private static final List<String> TABLES_TO_CLEAR = List.of(
            "exception_event", "changelog_entry", "lesson_exception", "change_event",
            "lesson", "timetable",
            "class_subject", "teacher_subject", "teacher_preferred_timeslot", "teacher_blocked_timeslot",
            "teacher", "subject", "room", "school_class", "timeslot", "holiday", "rule");

    /** Sequenzen zuruecksetzen, damit ein Import nach dem Leeren bei 1 beginnt. */
    private static final List<String> SEQUENCES = List.of(
            "teacher", "subject", "room", "school_class", "timeslot", "class_subject",
            "timetable", "lesson", "change_event", "lesson_exception", "changelog_entry",
            "holiday", "rule");

    private final SubjectService subjects;
    private final RoomService rooms;
    private final TeacherService teachers;
    private final SchoolClassService classes;
    private final TimegridService timegrid;
    private final RuleService rules;
    private final HolidayService holidays;
    private final JdbcTemplate jdbc;

    public ImportService(SubjectService subjects, RoomService rooms, TeacherService teachers,
                         SchoolClassService classes, TimegridService timegrid, RuleService rules,
                         HolidayService holidays, JdbcTemplate jdbc) {
        this.subjects = subjects;
        this.rooms = rooms;
        this.teachers = teachers;
        this.classes = classes;
        this.timegrid = timegrid;
        this.rules = rules;
        this.holidays = holidays;
        this.jdbc = jdbc;
    }

    @Transactional
    public MasterDataImportResult importMasterData(MasterDataImport input) {
        validateReferences(input);

        boolean hasData = hasMasterData();
        if (hasData && !Boolean.TRUE.equals(input.replace())) {
            throw new ConflictException("data-exists",
                    "Die Datenbank enthält bereits Grunddaten. Zum Ersetzen „replace“ setzen.");
        }
        if (hasData) {
            clear();
        }

        IdMap ids = new IdMap();

        importTimegrid(input.timeslots(), ids);
        importSubjects(input.subjects(), ids);
        importRooms(input.rooms(), ids);
        importTeachers(input.teachers(), ids);
        importClasses(input.classes(), ids);
        importRules(input.rules());
        importHolidays(input.holidays());

        return new MasterDataImportResult(
                input.subjects().size(),
                input.teachers().size(),
                input.rooms().size(),
                input.classes().size(),
                input.timeslots().size(),
                input.rules().size(),
                input.holidays().size(),
                List.of());
    }

    // ------------------------------------------------------------------ Pruefung der Referenzen

    /**
     * Prueft, dass jede Kennung in der Datei eindeutig ist und dass jeder Verweis auf eine
     * Kennung zeigt, die in derselben Datei steht. Feldnamen im Fehler sind die Pfade aus dem
     * Vertrag ({@code classes[2].subjects[1].teacherId}); die Oberflaeche kann sie am
     * Dialogfeld markieren.
     */
    private void validateReferences(MasterDataImport in) {
        List<FieldError> errors = new ArrayList<>();

        Set<Long> subjectIds = unique(in.subjects(), SubjectDto::id, "subjects", errors);
        Set<Long> teacherIds = unique(in.teachers(), TeacherDto::id, "teachers", errors);
        Set<Long> roomIds    = unique(in.rooms(),    RoomDto::id,    "rooms",    errors);
        Set<Long> classIds   = unique(in.classes(),  ClassDto::id,   "classes",  errors);
        Set<Long> slotIds    = unique(in.timeslots(), TimeslotDto::id, "timeslots", errors);

        for (int i = 0; i < in.teachers().size(); i++) {
            TeacherDto t = in.teachers().get(i);
            for (Long id : t.subjectIds()) {
                if (!subjectIds.contains(id)) {
                    errors.add(new FieldError("teachers[" + i + "].subjectIds",
                            "Fach " + id + " gibt es in der Datei nicht"));
                }
            }
            for (Long id : t.preferredTimeslotIds()) {
                if (!slotIds.contains(id)) {
                    errors.add(new FieldError("teachers[" + i + "].preferredTimeslotIds",
                            "Zeitfenster " + id + " gibt es in der Datei nicht"));
                }
            }
            for (Long id : t.blockedTimeslotIds()) {
                if (!slotIds.contains(id)) {
                    errors.add(new FieldError("teachers[" + i + "].blockedTimeslotIds",
                            "Zeitfenster " + id + " gibt es in der Datei nicht"));
                }
            }
        }
        for (int i = 0; i < in.classes().size(); i++) {
            ClassDto c = in.classes().get(i);
            for (int j = 0; j < c.subjects().size(); j++) {
                ClassSubjectDto s = c.subjects().get(j);
                if (!subjectIds.contains(s.subjectId())) {
                    errors.add(new FieldError("classes[" + i + "].subjects[" + j + "].subjectId",
                            "Fach " + s.subjectId() + " gibt es in der Datei nicht"));
                }
                if (!teacherIds.contains(s.teacherId())) {
                    errors.add(new FieldError("classes[" + i + "].subjects[" + j + "].teacherId",
                            "Lehrkraft " + s.teacherId() + " gibt es in der Datei nicht"));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static <T> Set<Long> unique(List<T> rows, java.util.function.Function<T, Long> id,
                                        String feld, List<FieldError> errors) {
        Set<Long> gesehen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            Long k = id.apply(rows.get(i));
            if (k == null || !gesehen.add(k)) {
                errors.add(new FieldError(feld + "[" + i + "].id",
                        k == null ? "Kennung fehlt" : "Kennung " + k + " ist doppelt"));
            }
        }
        return gesehen;
    }

    // ------------------------------------------------------------------ Leeren

    /** Ist schon irgendeine Grunddate da? (Fuer die 409-Vorbedingung.) */
    @Transactional(readOnly = true)
    public boolean hasMasterData() {
        Integer n = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM teacher)
                     + (SELECT count(*) FROM subject)
                     + (SELECT count(*) FROM room)
                     + (SELECT count(*) FROM school_class)
                     + (SELECT count(*) FROM timeslot)
                """, Integer.class);
        return n != null && n > 0;
    }

    /**
     * Leert Grunddaten, Plaene, Ereignisse, Ausnahmen und Verlauf. Regelwerk-Zeilen ebenfalls,
     * die Vorlagen ({@code rule_preset}, {@code holiday_preset}) und die Systemkonfiguration
     * bleiben. Das Konto der Planung bleibt, alle uebrigen Konten gehen mit.
     */
    @Transactional
    public void clear() {
        // Reihenfolge: erst die Konten, die auf teacher/school_class zeigen, dann die
        // Stammdaten. Ohne die Zeile scheitert das Loeschen an einem Fremdschluessel.
        jdbc.update("DELETE FROM user_account WHERE role <> 'PLANNING'");
        for (String t : TABLES_TO_CLEAR) {
            jdbc.update("DELETE FROM " + t);
        }
        for (String s : SEQUENCES) {
            jdbc.update("ALTER SEQUENCE IF EXISTS " + s + "_id_seq RESTART");
        }
    }

    // ------------------------------------------------------------------ Einspielen

    private void importTimegrid(List<TimeslotDto> slots, IdMap ids) {
        // Ein einziger Aufruf: `replace` regelt Upsert nach (day, position), Duplikate,
        // Uhrzeit-Reihenfolge und 409 bei belegten Slots. Nach dem Leeren kann 409 nicht
        // auftreten.
        TimegridDto neu = timegrid.replace(new TimegridInput(
                slots.stream()
                        .map(s -> new TimeslotInput(null, s.day(), s.position(),
                                s.startTime(), s.endTime(), s.isBreak()))
                        .toList()));
        // Die Kennungen kommen neu aus der Datenbank. Alte Kennung → neue Kennung ueber
        // (day, position) abbilden; darauf verweisen spaeter die Wunsch- und Sperrzeiten
        // der Lehrkraefte.
        for (TimeslotDto alt : slots) {
            TimeslotDto passend = neu.slots().stream()
                    .filter(n -> n.day() == alt.day() && n.position() == alt.position())
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("timeslots",
                            "Zeitfenster " + alt.id() + " wurde nicht angelegt"));
            ids.timeslot(alt.id(), passend.id());
        }
    }

    private void importSubjects(List<SubjectDto> rows, IdMap ids) {
        for (SubjectDto s : rows) {
            SubjectDto neu = subjects.create(new SubjectInput(
                    s.name(), s.shortName(), s.requiredRoomType(), s.color()));
            ids.subject(s.id(), neu.id());
        }
    }

    private void importRooms(List<RoomDto> rows, IdMap ids) {
        for (RoomDto r : rows) {
            RoomDto neu = rooms.create(new RoomInput(r.name(), r.roomType(), r.active()));
            ids.room(r.id(), neu.id());
        }
    }

    private void importTeachers(List<TeacherDto> rows, IdMap ids) {
        for (TeacherDto t : rows) {
            TeacherInput in = new TeacherInput(
                    t.name(), t.shortName(), t.weeklyHoursQuota(),
                    t.subjectIds().stream().map(ids::subject).toList(),
                    t.preferredTimeslotIds().stream().map(ids::timeslot).toList(),
                    t.blockedTimeslotIds().stream().map(ids::timeslot).toList(),
                    t.active());
            TeacherDto neu = teachers.create(in);
            ids.teacher(t.id(), neu.id());
        }
    }

    private void importClasses(List<ClassDto> rows, IdMap ids) {
        for (ClassDto c : rows) {
            ClassDto neu = classes.create(new ClassInput(c.name(), c.grade(), c.active()));
            List<ClassSubjectInput> zeilen = c.subjects().stream()
                    .map(s -> new ClassSubjectInput(
                            ids.subject(s.subjectId()),
                            s.weeklyHours(),
                            ids.teacher(s.teacherId())))
                    .toList();
            classes.replaceSubjects(neu.id(), zeilen);
            ids.klass(c.id(), neu.id());
        }
    }

    private void importRules(List<RuleDto> rows) {
        // `RuleService.replaceAll` ist eine reine Service-Methode ohne Endpunkt — nur der
        // Import setzt das Regelwerk als Ganzes. Die Kennungen aus der Datei werden
        // verworfen, `label` ebenfalls (wird aus dem Typ erneut gebildet).
        List<RuleView> views = rows.stream()
                .map(r -> new RuleView(null, r.type(), r.scope(), r.grade(),
                        r.value(), r.severity(), r.weight(), r.active(), r.label()))
                .toList();
        rules.replaceAll(views);
    }

        private void importHolidays(List<HolidayDto> rows) {
        for (HolidayDto h : rows) {
            holidays.create(new NewHoliday(h.name(), h.dateFrom(), h.dateTo(), h.kind()));
        }
    }
        // ------------------------------------------------------------------ Bereichsweise Sicherung (Version 2)

    @Transactional
    public SectionImportResult importSection(MasterDataSection input) {
        return switch (input.scope()) {
            case "subjects" -> importSubjectsSection(input);
            case "teachers" -> importTeachersSection(input);
            case "rooms" -> importRoomsSection(input);
            case "classes" -> importClassesSection(input);
            case "timeslots" -> importTimeslotsSection(input);
            case "rules" -> importRulesSection(input);
            case "holidays" -> importHolidaysSection(input);
            default -> throw new ValidationException("scope", "unbekannter Bereich");
        };
    }

    private SectionImportResult importSubjectsSection(MasterDataSection in) {
        int created = 0, updated = 0;
        for (SubjectDto s : in.subjects()) {
            Optional<SubjectDto> vorhanden = subjects.list().stream()
                    .filter(x -> x.shortName().equalsIgnoreCase(s.shortName()))
                    .findFirst();
            if (vorhanden.isPresent()) {
                subjects.update(vorhanden.get().id(),
                        new SubjectInput(s.name(), s.shortName(), s.requiredRoomType(), s.color()));
                updated++;
            } else {
                subjects.create(new SubjectInput(s.name(), s.shortName(), s.requiredRoomType(), s.color()));
                created++;
            }
        }
        return new SectionImportResult("subjects", created, updated, List.of());
    }

    private SectionImportResult importRoomsSection(MasterDataSection in) {
        int created = 0, updated = 0;
        for (RoomDto r : in.rooms()) {
            Optional<RoomDto> vorhanden = rooms.list().stream()
                    .filter(x -> x.name().equalsIgnoreCase(r.name()))
                    .findFirst();
            if (vorhanden.isPresent()) {
                rooms.update(vorhanden.get().id(), new RoomInput(r.name(), r.roomType(), r.active()));
                updated++;
            } else {
                rooms.create(new RoomInput(r.name(), r.roomType(), r.active()));
                created++;
            }
        }
        return new SectionImportResult("rooms", created, updated, List.of());
    }

    private SectionImportResult importTimeslotsSection(MasterDataSection in) {
        // Ersetzt das ganze Raster — dieselbe Semantik wie PUT /api/timegrid.
        timegrid.replace(new TimegridInput(in.timeslots().stream()
                .map(s -> new TimeslotInput(null, s.day(), s.position(), s.startTime(), s.endTime(), s.isBreak()))
                .toList()));
        return new SectionImportResult("timeslots", in.timeslots().size(), 0, List.of());
    }

    private SectionImportResult importRulesSection(MasterDataSection in) {
        List<RuleView> rows = in.rules().stream()
                .map(r -> new RuleView(null, r.type(), r.scope(), r.grade(),
                        r.value(), r.severity(), r.weight(), r.active(), r.label()))
                .toList();
        rules.replaceAll(rows);
        return new SectionImportResult("rules", rows.size(), 0, List.of());
    }

    private SectionImportResult importHolidaysSection(MasterDataSection in) {
        int created = 0, skipped = 0;
        Set<String> vorhanden = holidays.list().stream()
                .map(h -> key(h.name(), h.dateFrom().toString(), h.dateTo().toString()))
                .collect(Collectors.toSet());
        for (HolidayDto h : in.holidays()) {
            if (vorhanden.add(key(h.name(), h.dateFrom().toString(), h.dateTo().toString()))) {
                holidays.create(new NewHoliday(h.name(), h.dateFrom(), h.dateTo(), h.kind()));
                created++;
            } else {
                skipped++;
            }
        }
        return new SectionImportResult("holidays", created, 0,
                skipped > 0 ? List.of(skipped + " Zeiträume standen schon") : List.of());
    }

    private SectionImportResult importTeachersSection(MasterDataSection in) {
        if (in.teachers() == null) {
            throw new ValidationException("teachers", "fehlt");
        }
        // 1. Referenzen auflösen (alter Datei-Wert → Zielwert)
        Map<Long, Long> subjectMap = resolveSubjects(in.refs());
        Map<Long, Long> slotMap = resolveTimeslots(in.refs());

        int created = 0, updated = 0;
        // 2. Vorhandene Lehrkräfte nach Kürzel auflösen
        Map<String, Long> byShort = teachers.list().stream()
                .collect(Collectors.toMap(t -> t.shortName().toUpperCase(), TeacherDto::id, (a, b) -> a));

        for (TeacherDto t : in.teachers()) {
            List<Long> subjectIds = mapRefs(t.subjectIds(), subjectMap, "teachers", t.shortName(), "subjectIds");
            List<Long> preferred = mapRefs(t.preferredTimeslotIds(), slotMap, "teachers", t.shortName(), "preferredTimeslotIds");
            List<Long> blocked = mapRefs(t.blockedTimeslotIds(), slotMap, "teachers", t.shortName(), "blockedTimeslotIds");
            TeacherInput body = new TeacherInput(t.name(), t.shortName(), t.weeklyHoursQuota(),
                    subjectIds, preferred, blocked, t.active());

            Long vorhanden = byShort.get(t.shortName().toUpperCase(Locale.GERMAN));
            if (vorhanden != null) {
                teachers.update(vorhanden, body);
                updated++;
            } else {
                teachers.create(body);
                created++;
            }
        }
        return new SectionImportResult("teachers", created, updated, List.of());
    }

    private SectionImportResult importClassesSection(MasterDataSection in) {
        Map<Long, Long> subjectMap = resolveSubjects(in.refs());
        Map<Long, Long> teacherMap = resolveTeachers(in.refs());

        int created = 0, updated = 0;
        Map<String, Long> byName = classes.list().stream()
                .collect(Collectors.toMap(c -> c.name().toLowerCase(Locale.GERMAN), ClassDto::id, (a, b) -> a));

        for (ClassDto c : in.classes()) {
            List<ClassSubjectInput> rows = new ArrayList<>();
            for (int i = 0; i < c.subjects().size(); i++) {
                ClassSubjectDto s = c.subjects().get(i);
                Long subjId = subjectMap.get(s.subjectId());
                Long teachId = teacherMap.get(s.teacherId());
                List<FieldError> fehler = new ArrayList<>();
                if (subjId == null) fehler.add(new FieldError(
                        "classes[" + i + "].subjects[" + i + "].subjectId",
                        "Fach " + s.subjectShort() + " gibt es hier nicht"));
                if (teachId == null) fehler.add(new FieldError(
                        "classes[" + i + "].subjects[" + i + "].teacherId",
                        "Lehrkraft " + s.teacherShort() + " gibt es hier nicht"));
                if (!fehler.isEmpty()) throw new ValidationException(fehler);
                rows.add(new ClassSubjectInput(subjId, s.weeklyHours(), teachId));
            }
            Long vorhanden = byName.get(c.name().toLowerCase(Locale.GERMAN));
            if (vorhanden != null) {
                classes.update(vorhanden, new ClassInput(c.name(), c.grade(), c.active()));
                classes.replaceSubjects(vorhanden, rows);
                updated++;
            } else {
                ClassDto neu = classes.create(new ClassInput(c.name(), c.grade(), c.active()));
                classes.replaceSubjects(neu.id(), rows);
                created++;
            }
        }
        return new SectionImportResult("classes", created, updated, List.of());
    }

    // ------------------------------------------------------------------ Auflösung der Referenzen

    private Map<Long, Long> resolveSubjects(SectionRefs refs) {
        if (refs == null || refs.subjects() == null) return Map.of();
        Map<String, Long> nachKuerzel = subjects.list().stream()
                .collect(Collectors.toMap(s -> s.shortName().toUpperCase(Locale.GERMAN), SubjectDto::id, (a, b) -> a));
        Map<Long, Long> map = new HashMap<>();
        List<FieldError> errors = new ArrayList<>();
        for (SubjectRef r : refs.subjects()) {
            Long neu = nachKuerzel.get(r.shortName().toUpperCase(Locale.GERMAN));
            if (neu == null) {
                errors.add(new FieldError("refs.subjects[" + r.id() + "]", "Fach " + r.shortName() + " gibt es hier nicht"));
            } else {
                map.put(r.id(), neu);
            }
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
        return map;
    }

    private Map<Long, Long> resolveTeachers(SectionRefs refs) {
        if (refs == null || refs.teachers() == null) return Map.of();
        Map<String, Long> nachKuerzel = teachers.list().stream()
                .collect(Collectors.toMap(t -> t.shortName().toUpperCase(Locale.GERMAN), TeacherDto::id, (a, b) -> a));
        Map<Long, Long> map = new HashMap<>();
        List<FieldError> errors = new ArrayList<>();
        for (TeacherRef r : refs.teachers()) {
            Long neu = nachKuerzel.get(r.shortName().toUpperCase(Locale.GERMAN));
            if (neu == null) {
                errors.add(new FieldError("refs.teachers[" + r.id() + "]", "Lehrkraft " + r.shortName() + " gibt es hier nicht"));
            } else {
                map.put(r.id(), neu);
            }
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
        return map;
    }

    private Map<Long, Long> resolveTimeslots(SectionRefs refs) {
        if (refs == null || refs.timeslots() == null) return Map.of();
        // Nach (Tag, Position) — dieselbe Identität, die der PUT /api/timegrid-Upsert nutzt.
        Map<String, Long> nachOrt = timegrid.get().slots().stream()
                .collect(Collectors.toMap(s -> s.day() + "|" + s.position(), TimeslotDto::id, (a, b) -> a));
        Map<Long, Long> map = new HashMap<>();
        List<FieldError> errors = new ArrayList<>();
        for (TimeslotRef r : refs.timeslots()) {
            Long neu = nachOrt.get(r.day() + "|" + r.position());
            if (neu == null) {
                errors.add(new FieldError("refs.timeslots[" + r.id() + "]",
                        "Zeitfenster " + r.day() + " " + r.position() + " gibt es hier nicht"));
            } else {
                map.put(r.id(), neu);
            }
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
        return map;
    }

    private static List<Long> mapRefs(List<Long> refs, Map<Long, Long> map,
                                       String entity, String label, String feld) {
        List<FieldError> errors = new ArrayList<>();
        List<Long> out = new ArrayList<>();
        for (Long id : refs) {
            Long neu = map.get(id);
            if (neu == null) {
                errors.add(new FieldError(entity + "[" + label + "]." + feld,
                        "Verweis " + id + " ließ sich nicht auflösen"));
            } else {
                out.add(neu);
            }
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
        return out;
    }

    private static String key(String name, String from, String to) {
        return name + '|' + from + '|' + to;
    }
}