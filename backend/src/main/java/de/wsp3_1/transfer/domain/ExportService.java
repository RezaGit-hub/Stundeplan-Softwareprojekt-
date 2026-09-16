package de.wsp3_1.transfer.domain;

import de.wsp3_1.masterdata.domain.RoomService;
import de.wsp3_1.masterdata.domain.SchoolClassService;
import de.wsp3_1.masterdata.domain.SubjectService;
import de.wsp3_1.masterdata.domain.TeacherService;
import de.wsp3_1.masterdata.domain.TimegridService;
import de.wsp3_1.rules.api.RuleDtos.RuleDto;
import de.wsp3_1.rules.domain.RuleService;
import de.wsp3_1.rules.domain.RuleView;
import de.wsp3_1.substitution.api.SubstitutionDtos.HolidayDto;
import de.wsp3_1.substitution.domain.HolidayService;
import de.wsp3_1.substitution.domain.SubstitutionViews.HolidayView;
import de.wsp3_1.transfer.api.TransferDtos.MasterDataExport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.wsp3_1.masterdata.MasterdataDtos.ClassSubjectDto;
import de.wsp3_1.masterdata.MasterdataDtos.TeacherDto;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.transfer.api.TransferDtos.MasterDataSection;
import de.wsp3_1.transfer.api.TransferDtos.SectionRefs;
import de.wsp3_1.transfer.api.TransferDtos.SubjectRef;
import de.wsp3_1.transfer.api.TransferDtos.TeacherRef;
import de.wsp3_1.transfer.api.TransferDtos.TimeslotRef;
import de.wsp3_1.masterdata.MasterdataDtos.ClassDto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.Instant;

/**
 * Stellt alle Grunddaten als {@link MasterDataExport} zusammen (Format-Version 1).
 *
 * <p><b>Nicht enthalten</b> (Entscheidung vom 15.09.): Systemkonfiguration, Benutzerkonten,
 * Plaene, Entwuerfe, Ereignisse, Ausnahmen und Verlauf. Jene Daten sind umgebungsspezifisch
 * (Adresse, Bundesland) oder personenbezogen (Konten, Verlauf) und gehoeren nicht in eine
 * Datei, die eine Schule an eine andere weitergeben kann.</p>
 *
 * <p>Die ganze Zusammenstellung laeuft in einer Transaktion ({@code readOnly}), damit die
 * Listen aus einem konsistenten Stand kommen.</p>
 */
@Service
public class ExportService {

    /** Format-Version des Dokuments; siehe {@code MasterDataImport.version}. */
     /** Format-Version der vollständigen Sicherung. */
    static final String VERSION = "1";

    /** Format-Version der bereichsweisen Sicherung (teachers, classes, …). */
    static final String VERSION_2 = "2";

    private final SubjectService subjects;
    private final TeacherService teachers;
    private final RoomService rooms;
    private final SchoolClassService classes;
    private final TimegridService timegrid;
    private final RuleService rules;
    private final HolidayService holidays;

    public ExportService(SubjectService subjects, TeacherService teachers, RoomService rooms,
                         SchoolClassService classes, TimegridService timegrid,
                         RuleService rules, HolidayService holidays) {
        this.subjects = subjects;
        this.teachers = teachers;
        this.rooms = rooms;
        this.classes = classes;
        this.timegrid = timegrid;
        this.rules = rules;
        this.holidays = holidays;
    }

    @Transactional(readOnly = true)
    public MasterDataExport export() {
        return new MasterDataExport(
                VERSION,
                Instant.now(),
                subjects.list(),
                teachers.list(),
                rooms.list(),
                classes.list(),
                timegrid.get().slots(),
                rules.list().stream().map(ExportService::toRuleDto).toList(),
                holidays.list().stream().map(ExportService::toHolidayDto).toList());
    }

    private static RuleDto toRuleDto(RuleView view) {
        return RuleDto.of(view);
    }

    private static HolidayDto toHolidayDto(HolidayView view) {
        return HolidayDto.of(view);
    }

        @Transactional(readOnly = true)
    public MasterDataSection exportSection(String scope) {
        Instant now = Instant.now();
        return switch (scope) {
            case "subjects" -> new MasterDataSection(VERSION_2, "subjects", now,
                    subjects.list(), null, null, null, null, null, null, null);
            case "rooms" -> new MasterDataSection(VERSION_2, "rooms", now,
                    null, null, rooms.list(), null, null, null, null, null);
            case "timeslots" -> new MasterDataSection(VERSION_2, "timeslots", now,
                    null, null, null, null, timegrid.get().slots(), null, null, null);
            case "rules" -> new MasterDataSection(VERSION_2, "rules", now,
                    null, null, null, null, null,
                    rules.list().stream().map(ExportService::toRuleDto).toList(), null, null);
            case "holidays" -> new MasterDataSection(VERSION_2, "holidays", now,
                    null, null, null, null, null, null,
                    holidays.list().stream().map(ExportService::toHolidayDto).toList(), null);
            case "teachers" -> teachersSection(now);
            case "classes" -> classesSection(now);
            default -> throw new ValidationException("scope", "unbekannter Bereich");
        };
    }

    private MasterDataSection teachersSection(Instant now) {
        List<TeacherDto> teacherRows = teachers.list();
        Set<Long> subjectIds = new HashSet<>();
        Set<Long> slotIds = new HashSet<>();
        for (TeacherDto t : teacherRows) {
            subjectIds.addAll(t.subjectIds());
            slotIds.addAll(t.preferredTimeslotIds());
            slotIds.addAll(t.blockedTimeslotIds());
        }
        return new MasterDataSection(VERSION_2, "teachers", now,
                null, teacherRows, null, null, null, null, null,
                new SectionRefs(subjectRefs(subjectIds), null, timeslotRefs(slotIds)));
    }

    private MasterDataSection classesSection(Instant now) {
        List<ClassDto> classRows = classes.list();
        Set<Long> subjectIds = new HashSet<>();
        Set<Long> teacherIds = new HashSet<>();
        for (ClassDto c : classRows) {
            for (ClassSubjectDto row : c.subjects()) {
                subjectIds.add(row.subjectId());
                teacherIds.add(row.teacherId());
            }
        }
        return new MasterDataSection(VERSION_2, "classes", now,
                null, null, null, classRows, null, null, null,
                new SectionRefs(subjectRefs(subjectIds), teacherRefs(teacherIds), null));
    }

    private List<SubjectRef> subjectRefs(Set<Long> ids) {
        return subjects.list().stream()
                .filter(s -> ids.contains(s.id()))
                .map(s -> new SubjectRef(s.id(), s.shortName()))
                .toList();
    }

    private List<TeacherRef> teacherRefs(Set<Long> ids) {
        return teachers.list().stream()
                .filter(t -> ids.contains(t.id()))
                .map(t -> new TeacherRef(t.id(), t.shortName()))
                .toList();
    }

    private List<TimeslotRef> timeslotRefs(Set<Long> ids) {
        return timegrid.get().slots().stream()
                .filter(s -> ids.contains(s.id()))
                .map(s -> new TimeslotRef(s.id(), s.day(), s.position()))
                .toList();
    }
}