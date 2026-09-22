package de.wsp3_1.absences.domain;

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
    public List<AbsenceView> listAll() {
        return absences.findAllByOrderByDateFromDesc().stream().map(AbsencesService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<AbsenceView> listForTeacher(Long teacherId) {
        return absences.findByTeacherIdOrderByDateFromDesc(teacherId).stream()
                .map(AbsencesService::toView).toList();
    }

    @Transactional
    public AbsenceView create(Long teacherId, AbsenceInput input) {
        checkRange(input);
        Absence neu = new Absence(teacherId, input.dateFrom(), input.dateTo(), input.reason(),
                input.active() == null || input.active(), false);
        return toView(absences.save(neu));
    }

    @Transactional
    public AbsenceView update(Long teacherId, Long id, AbsenceInput input) {
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
        return toView(a);
    }

    @Transactional
    public ConvertedAbsenceView convert(Long id) {
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
        return new ConvertedAbsenceView(toView(a), created);
    }

    private static AbsenceView toView(Absence a) {
        return new AbsenceView(a.getId(), a.getTeacherId(), a.getDateFrom(), a.getDateTo(),
                a.getReason(), a.isActive(), a.isSeen(), a.getEventId());
    }

    private static String trimReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    private void checkRange(AbsenceInput input) {
        if (input.dateTo().isBefore(input.dateFrom())) {
            throw new ValidationException("dateTo", "Das Bis-Datum darf nicht vor dem Von-Datum liegen.");
        }
    }
}