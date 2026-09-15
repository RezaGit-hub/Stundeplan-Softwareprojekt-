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
    static final String VERSION = "1";

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
}