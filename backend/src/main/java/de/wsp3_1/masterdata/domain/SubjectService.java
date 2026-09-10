package de.wsp3_1.masterdata.domain;

import de.wsp3_1.masterdata.MasterdataDtos.SubjectDto;
import de.wsp3_1.masterdata.MasterdataDtos.SubjectInput;
import de.wsp3_1.masterdata.persistence.ClassSubjectRepository;
import de.wsp3_1.masterdata.persistence.Subject;
import de.wsp3_1.masterdata.persistence.SubjectRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.shared.domain.ValidationException.FieldError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Faecher anlegen, lesen, aendern, loeschen. Ein Fach hat kein {@code active}: es wird entweder
 * unterrichtet oder es gibt es nicht (Datenmodell). Geloescht wird nur, solange keine Klasse es in
 * ihrer Faecherliste fuehrt und keine Stunde es benutzt (Ausnahme 2 aus W-1, Schritt G-9).
 */
@Service
public class SubjectService {

    private final SubjectRepository subjects;
    private final TeacherRepository teachers;
    private final ClassSubjectRepository classSubjects;

    public SubjectService(SubjectRepository subjects, TeacherRepository teachers,
                          ClassSubjectRepository classSubjects) {
        this.subjects = subjects;
        this.teachers = teachers;
        this.classSubjects = classSubjects;
    }

    @Transactional(readOnly = true)
    public List<SubjectDto> list() {
        return subjects.findAllByOrderByIdAsc().stream().map(SubjectService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SubjectDto get(Long id) {
        return toDto(require(id));
    }

    @Transactional
    public SubjectDto create(SubjectInput input) {
        validate(input, null);
        Subject subject = new Subject(input.name().trim(), input.shortName().trim(),
                trimToNull(input.requiredRoomType()), input.color());
        return toDto(subjects.save(subject));
    }

    @Transactional
    public SubjectDto update(Long id, SubjectInput input) {
        Subject subject = require(id);
        validate(input, id);
        subject.setName(input.name().trim());
        subject.setShortName(input.shortName().trim());
        subject.setRequiredRoomType(trimToNull(input.requiredRoomType()));
        subject.setColor(input.color());
        return toDto(subjects.save(subject));
    }

    @Transactional
    public void delete(Long id) {
        Subject subject = require(id);
        List<String> classNames = classSubjects.findAllWithReferences().stream()
                .filter(row -> row.getSubject().getId().equals(id))
                .map(row -> row.getSchoolClass().getName())
                .distinct()
                .toList();
        if (!classNames.isEmpty()) {
            throw new ConflictException("in-use",
                    subject.getName() + " wird in " + String.join(", ", classNames) + " unterrichtet.");
        }
        if (classSubjects.countLessonsBySubject(id) > 0) {
            throw new ConflictException("in-use", subject.getName() + " ist noch in Stunden eingeplant.");
        }
        // Die Lehrbefaehigungen zeigen noch auf das Fach (teacher_subject). Sie sind kein Grund, das
        // Loeschen zu verweigern — sie verschwinden mit dem Fach, wie in der Attrappe.
        for (Teacher teacher : teachers.findAllByOrderByIdAsc()) {
            teacher.getSubjectIds().remove(id);
        }
        teachers.flush();
        subjects.delete(subject);
    }

    private Subject require(Long id) {
        return subjects.findById(id)
                .orElseThrow(() -> new NotFoundException("Es gibt kein Fach mit der Nummer " + id + "."));
    }

    private void validate(SubjectInput input, Long selfId) {
        List<FieldError> errors = new ArrayList<>();
        subjects.findByShortNameIgnoreCase(input.shortName().trim())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> errors.add(new FieldError("shortName", "Kürzel ist bereits vergeben")));
        subjects.findByNameIgnoreCase(input.name().trim())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> errors.add(new FieldError("name", "Name ist bereits vergeben")));
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static SubjectDto toDto(Subject subject) {
        return new SubjectDto(subject.getId(), subject.getName(), subject.getShortName(),
                subject.getRequiredRoomType(), subject.getColor());
    }
}
