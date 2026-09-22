package de.wsp3_1.absences.domain;

import de.wsp3_1.absences.api.AbsenceDtos.AbsenceDto;
import de.wsp3_1.absences.api.AbsenceDtos.ConvertedAbsenceDto;
import de.wsp3_1.absences.api.AbsenceDtos.CreateAbsenceDto;
import de.wsp3_1.absences.persistence.Absence;
import de.wsp3_1.absences.persistence.AbsenceRepository;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.substitution.domain.ChangeEventService;
import de.wsp3_1.substitution.domain.EventKind;
import de.wsp3_1.substitution.domain.SubstitutionViews.EventView;
import de.wsp3_1.substitution.domain.SubstitutionViews.NewEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AbsencesService {

    private static final int MAX_REASON_LENGTH = 255;

    private final AbsenceRepository absences;
    private final ChangeEventService changeEvents;

    public AbsencesService(AbsenceRepository absences, ChangeEventService changeEvents) {
        this.absences = absences;
        this.changeEvents = changeEvents;
    }

    @Transactional(readOnly = true)
    public List<AbsenceDto> listAll() {
        return absences.findAllByOrderByDateFromDesc().stream().map(AbsenceDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<AbsenceDto> listForTeacher(Long teacherId) {
        return absences.findByTeacherIdOrderByDateFromDesc(teacherId).stream().map(AbsenceDto::of).toList();
    }

    @Transactional
    public AbsenceDto create(Long teacherId, CreateAbsenceDto input) {
        checkRange(input);
        Absence neu = new Absence(teacherId, input.dateFrom(), input.dateTo(), input.reason(),
                input.active() == null || input.active(), false);
        return AbsenceDto.of(absences.save(neu));
    }

    @Transactional
    public AbsenceDto update(Long teacherId, Long id, CreateAbsenceDto input) {
        checkRange(input);
        Absence a = absences.findById(id).orElseThrow(() -> new NotFoundException("Diese Meldung gibt es nicht."));
        if (!a.getTeacherId().equals(teacherId)) {
            throw new NotFoundException("Diese Meldung gibt es nicht.");
        }
        a.setDateFrom(input.dateFrom());
        a.setDateTo(input.dateTo());
        a.setReason(input.reason());
        a.setActive(input.active() == null || input.active());
        a.setSeen(false);
        return AbsenceDto.of(a);
    }

    /**
     * Meldung annehmen (Planung, #116): legt ein {@code change_event} der Art {@code TEACHER_ABSENCE}
     * an und verknuepft es mit der Meldung. Der Regelplan bleibt unveraendert — das Ereignis haengt
     * am Kalendertag, welche Stunden es trifft, rechnet der {@code WeekResolver} (E-31).
     *
     * <p>Die Meldung gilt danach als gesehen ({@code seen=true}). {@code active} bleibt unangetastet.</p>
     *
     * <p>Zurueck kommt beides: die aktualisierte Meldung <b>und</b> das neue Ereignis
     * ({@link ConvertedAbsenceDto}).</p>
     *
     * @throws ConflictException {@code already-converted}, wenn schon ein Ereignis dranhaengt
     */
    @Transactional
    public ConvertedAbsenceDto convert(Long id) {
        Absence a = absences.findById(id)
                .orElseThrow(() -> new NotFoundException("Diese Meldung gibt es nicht."));
        if (a.getEventId() != null) {
            throw new ConflictException("already-converted", "Diese Meldung wurde bereits umgewandelt.");
        }
        NewEvent input = new NewEvent(
                EventKind.TEACHER_ABSENCE,
                a.getTeacherId(),
                null,
                a.getDateFrom(),
                a.getDateTo(),
                trimReason(a.getReason()),
                null);
        EventView created = changeEvents.create(input);
        a.setEventId(created.id());
        a.setSeen(true);
        return new ConvertedAbsenceDto(AbsenceDto.of(a), created);
    }

    private static String trimReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    private void checkRange(CreateAbsenceDto input) {
        if (input.dateTo().isBefore(input.dateFrom())) {
            throw new ValidationException("dateTo", "Das Bis-Datum darf nicht vor dem Von-Datum liegen.");
        }
    }
}