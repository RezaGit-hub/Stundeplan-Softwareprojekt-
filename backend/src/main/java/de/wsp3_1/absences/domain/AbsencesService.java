package de.wsp3_1.absences.domain;

import de.wsp3_1.absences.api.AbsenceDtos.AbsenceDto;
import de.wsp3_1.absences.api.AbsenceDtos.CreateAbsenceDto;
import de.wsp3_1.absences.persistence.Absence;
import de.wsp3_1.absences.persistence.AbsenceRepository;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Abwesenheitsmeldungen (#115, #116). Die Lehrkraft sieht und pflegt nur die eigenen; die Planung
 * sieht alle und wandelt eine Meldung in ein Ereignis (siehe {@link #convert}).
 */
@Service
public class AbsencesService {

    private final AbsenceRepository absences;

    public AbsencesService(AbsenceRepository absences) {
        this.absences = absences;
    }

    @Transactional(readOnly = true)
    public List<AbsenceDto> listAll() {
        return absences.findAllByOrderByDateFromDesc().stream().map(AbsenceDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<AbsenceDto> listForTeacher(Long teacherId) {
        return absences.findByTeacherIdOrderByDateFromDesc(teacherId).stream().map(AbsenceDto::of).toList();
    }

    /** Neue Meldung der eingeloggten Lehrkraft. Ungelesen ({@code seen=false}). */
    @Transactional
    public AbsenceDto create(Long teacherId, CreateAbsenceDto input) {
        checkRange(input);
        Absence neu = new Absence(teacherId, input.dateFrom(), input.dateTo(), input.reason(),
                input.active() == null || input.active(), false);
        return AbsenceDto.of(absences.save(neu));
    }

    /**
     * Bearbeiten der eigenen Meldung. Eine Bearbeitung setzt sie wieder auf ungelesen — die Planung
     * hat die alte Fassung gesehen, nicht die neue (so steht es in der Beschreibung von {@code seen}).
     */
    @Transactional
    public AbsenceDto update(Long teacherId, Long id, CreateAbsenceDto input) {
        checkRange(input);
        Absence a = absences.findById(id).orElseThrow(() -> new NotFoundException("Diese Meldung gibt es nicht."));
        if (!a.getTeacherId().equals(teacherId)) {
            // Keine fremde Meldung — auch nicht verraten, dass es sie gibt.
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
     * Meldung akzeptieren (Planung). Aktuell wird die Meldung unveraendert zurueckgegeben —
     * das Anlegen des zugehoerigen Ereignisses kommt spaeter (#116).
     */
    @Transactional
    public AbsenceDto convert(Long id) {
        Absence a = absences.findById(id).orElseThrow(() -> new NotFoundException("Diese Meldung gibt es nicht."));
        // TODO #116: Ereignis aus dieser Abwesenheit erzeugen.
        return AbsenceDto.of(a);
    }

    private void checkRange(CreateAbsenceDto input) {
        if (input.dateTo().isBefore(input.dateFrom())) {
            throw new ValidationException("dateTo", "Das Bis-Datum darf nicht vor dem Von-Datum liegen.");
        }
    }
}