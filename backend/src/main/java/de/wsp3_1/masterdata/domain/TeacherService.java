package de.wsp3_1.masterdata.domain;

import de.wsp3_1.masterdata.MasterdataDtos.TeacherDto;
import de.wsp3_1.masterdata.MasterdataDtos.TeacherInput;
import de.wsp3_1.masterdata.domain.WarningChecker.TeachingAssignment;
import de.wsp3_1.masterdata.persistence.ClassSubjectRepository;
import de.wsp3_1.masterdata.persistence.SubjectRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.masterdata.persistence.TimeslotRepository;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.shared.domain.ValidationException.FieldError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lehrkraefte anlegen, lesen, aendern, deaktivieren, loeschen (W-1, T-W1.1, T-W1.3).
 *
 * <p>Loeschen geht nur ohne Verweise. Ist die Lehrkraft eingeplant oder einer Klasse zugeordnet,
 * kommt 409 {@code in-use} mit dem Hinweis auf das Deaktivieren — {@code active = false} laesst
 * bestehende Stunden unveraendert und nimmt die Lehrkraft nur aus den Auswahllisten.</p>
 */
@Service
public class TeacherService {

    private final TeacherRepository teachers;
    private final SubjectRepository subjects;
    private final TimeslotRepository timeslots;
    private final ClassSubjectRepository classSubjects;
    private final WarningChecker warnings;

    public TeacherService(TeacherRepository teachers, SubjectRepository subjects, TimeslotRepository timeslots,
                          ClassSubjectRepository classSubjects, WarningChecker warnings) {
        this.teachers = teachers;
        this.subjects = subjects;
        this.timeslots = timeslots;
        this.classSubjects = classSubjects;
        this.warnings = warnings;
    }

    @Transactional(readOnly = true)
    public List<TeacherDto> list() {
        List<TeachingAssignment> assignments = warnings.allAssignments();
        List<TeacherDto> out = new ArrayList<>();
        for (Teacher teacher : teachers.findAllByOrderByIdAsc()) {
            out.add(toDto(teacher, assignments));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public TeacherDto get(Long id) {
        return toDto(require(id), warnings.allAssignments());
    }

    @Transactional
    public TeacherDto create(TeacherInput input) {
        validate(input, null);
        Teacher teacher = new Teacher(input.name().trim(), shortNameOf(input), input.weeklyHoursQuota(),
                input.active());
        apply(input, teacher);
        teachers.save(teacher);
        return toDto(teacher, warnings.allAssignments());
    }

    @Transactional
    public TeacherDto update(Long id, TeacherInput input) {
        Teacher teacher = require(id);
        validate(input, id);
        teacher.setName(input.name().trim());
        teacher.setShortName(shortNameOf(input));
        teacher.setWeeklyHoursQuota(input.weeklyHoursQuota());
        teacher.setActive(input.active());
        apply(input, teacher);
        teachers.save(teacher);
        return toDto(teacher, warnings.allAssignments());
    }

    @Transactional
    public void delete(Long id) {
        Teacher teacher = require(id);
        long lessons = classSubjects.countLessonsByTeacher(id);
        if (lessons > 0) {
            throw new ConflictException("in-use",
                    teacher.getName() + " ist in " + lessons + " Stunden eingeplant. Deaktivieren statt löschen.");
        }
        List<String> classNames = classSubjects.findAllWithReferences().stream()
                .filter(row -> row.getTeacher().getId().equals(id))
                .map(row -> row.getSchoolClass().getName())
                .distinct()
                .toList();
        if (!classNames.isEmpty()) {
            throw new ConflictException("in-use", teacher.getName() + " ist " + String.join(", ", classNames)
                    + " zugeordnet. Deaktivieren statt löschen.");
        }
        try {
            teachers.delete(teacher);
            teachers.flush();
        } catch (DataIntegrityViolationException ex) {
            // Uebrig bleibt der Fall „es haengt noch ein Benutzerkonto daran" (user_account.teacher_id,
            // Modul auth). Das Modul masterdata darf auth nicht kennen, also fangen wir den Verstoss
            // gegen den Fremdschluessel ab, statt in ihn hineinzulaufen (sonst 500 statt 409).
            throw new ConflictException("in-use", teacher.getName()
                    + " wird noch verwendet (zum Beispiel von einem Benutzerkonto). Deaktivieren statt löschen.");
        }
    }

    private Teacher require(Long id) {
        return teachers.findById(id)
                .orElseThrow(() -> new NotFoundException("Es gibt keine Lehrkraft mit der Nummer " + id + "."));
    }

    /** Kuerzel immer in Grossbuchstaben — wie in der Attrappe, damit MEY und mey dieselbe Lehrkraft sind. */
    private static String shortNameOf(TeacherInput input) {
        return input.shortName().trim().toUpperCase();
    }

    private void validate(TeacherInput input, Long selfId) {
        List<FieldError> errors = new ArrayList<>();
        teachers.findByShortNameIgnoreCase(shortNameOf(input))
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> errors.add(new FieldError("shortName", "Kürzel ist bereits vergeben")));
        // Leere Eintraege in den Listen ({"subjectIds":[null]}) zuerst: existsById(null) wirft eine
        // IllegalArgumentException, und der Client bekaeme 500 statt 400.
        for (Long subjectId : nonNull(input.subjectIds())) {
            if (subjectId == null) {
                errors.add(new FieldError("subjectIds", "Es gibt einen leeren Eintrag in der Liste."));
            } else if (!subjects.existsById(subjectId)) {
                errors.add(new FieldError("subjectIds", "Es gibt kein Fach mit der Nummer " + subjectId + "."));
            }
        }
        for (Long timeslotId : nonNull(input.preferredTimeslotIds())) {
            if (timeslotId == null) {
                errors.add(new FieldError("preferredTimeslotIds", "Es gibt einen leeren Eintrag in der Liste."));
            } else if (!timeslots.existsById(timeslotId)) {
                errors.add(new FieldError("preferredTimeslotIds",
                        "Es gibt kein Zeitfenster mit der Nummer " + timeslotId + "."));
            }
        }
        for (Long timeslotId : nonNull(input.blockedTimeslotIds())) {
            if (timeslotId == null) {
                errors.add(new FieldError("blockedTimeslotIds", "Es gibt einen leeren Eintrag in der Liste."));
            } else if (!timeslots.existsById(timeslotId)) {
                errors.add(new FieldError("blockedTimeslotIds",
                        "Es gibt kein Zeitfenster mit der Nummer " + timeslotId + "."));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static void apply(TeacherInput input, Teacher teacher) {
        replace(teacher.getSubjectIds(), input.subjectIds());
        replace(teacher.getPreferredTimeslotIds(), input.preferredTimeslotIds());
        replace(teacher.getBlockedTimeslotIds(), input.blockedTimeslotIds());
    }

    private static void replace(Set<Long> target, List<Long> values) {
        target.clear();
        target.addAll(new LinkedHashSet<>(nonNull(values)));
    }

    private static List<Long> nonNull(List<Long> values) {
        return values == null ? List.of() : values;
    }

    private static TeacherDto toDto(Teacher teacher, List<TeachingAssignment> assignments) {
        long id = teacher.getId();
        return new TeacherDto(teacher.getId(), teacher.getName(), teacher.getShortName(),
                teacher.getWeeklyHoursQuota(),
                List.copyOf(teacher.getSubjectIds()),
                List.copyOf(teacher.getPreferredTimeslotIds()),
                List.copyOf(teacher.getBlockedTimeslotIds()),
                teacher.isActive(),
                WarningChecker.plannedHours(id, assignments),
                WarningChecker.forTeacher(id, teacher.getName(), teacher.getWeeklyHoursQuota(), assignments));
    }
}
