package de.wsp3_1.masterdata.domain;

import de.wsp3_1.masterdata.MasterdataDtos.ClassDto;
import de.wsp3_1.masterdata.MasterdataDtos.ClassInput;
import de.wsp3_1.masterdata.MasterdataDtos.ClassSubjectDto;
import de.wsp3_1.masterdata.MasterdataDtos.ClassSubjectInput;
import de.wsp3_1.masterdata.domain.WarningChecker.TeachingAssignment;
import de.wsp3_1.masterdata.persistence.ClassSubject;
import de.wsp3_1.masterdata.persistence.ClassSubjectRepository;
import de.wsp3_1.masterdata.persistence.SchoolClass;
import de.wsp3_1.masterdata.persistence.SchoolClassRepository;
import de.wsp3_1.masterdata.persistence.Subject;
import de.wsp3_1.masterdata.persistence.SubjectRepository;
import de.wsp3_1.masterdata.persistence.Teacher;
import de.wsp3_1.masterdata.persistence.TeacherRepository;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.shared.domain.ValidationException.FieldError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Klassen und ihre Faecherlisten (W-1). Die Fächerliste kommt als Ganzes:
 * {@code PUT /api/classes/&#123;id&#125;/subjects} ersetzt sie.
 *
 * <p>Jede Zeile braucht Wochenstunden <b>und</b> eine Lehrkraft, sonst 400 mit Feldliste
 * ({@code subjects[1].weeklyHours}) — Ausnahme 1 aus W-1, Schritt G-8. Fehlende Lehrbefaehigung und
 * ueberschrittenes Deputat sind dagegen kein Fehler, sondern {@code warnings[]} in der Antwort
 * (T-W1.4, T-W1.5).</p>
 *
 * <p>Beim Ersetzen bleiben vorhandene Zeilen bestehen und werden nur geaendert. Das ist kein
 * Selbstzweck: an einer Zeile haengen die Unterrichtsstunden ({@code lesson.class_subject_id}).
 * Wer die Lehrkraft der 9c in Mathematik wechselt, soll den Plan behalten, nicht verlieren.</p>
 */
@Service
public class SchoolClassService {

    private final SchoolClassRepository classes;
    private final SubjectRepository subjects;
    private final TeacherRepository teachers;
    private final ClassSubjectRepository classSubjects;
    private final WarningChecker warnings;

    public SchoolClassService(SchoolClassRepository classes, SubjectRepository subjects, TeacherRepository teachers,
                              ClassSubjectRepository classSubjects, WarningChecker warnings) {
        this.classes = classes;
        this.subjects = subjects;
        this.teachers = teachers;
        this.classSubjects = classSubjects;
        this.warnings = warnings;
    }

    @Transactional(readOnly = true)
    public List<ClassDto> list() {
        List<TeachingAssignment> assignments = warnings.allAssignments();
        return classes.findAllByOrderByIdAsc().stream()
                .map(schoolClass -> toDto(schoolClass, assignments))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassDto get(Long id) {
        return toDto(require(id), warnings.allAssignments());
    }

    @Transactional
    public ClassDto create(ClassInput input) {
        validate(input, null);
        SchoolClass schoolClass = classes.save(new SchoolClass(input.name().trim(), input.grade(), input.active()));
        return toDto(schoolClass, warnings.allAssignments());
    }

    @Transactional
    public ClassDto update(Long id, ClassInput input) {
        SchoolClass schoolClass = require(id);
        validate(input, id);
        schoolClass.setName(input.name().trim());
        schoolClass.setGrade(input.grade());
        schoolClass.setActive(input.active());
        classes.save(schoolClass);
        return toDto(schoolClass, warnings.allAssignments());
    }

    @Transactional
    public void delete(Long id) {
        SchoolClass schoolClass = require(id);
        long lessons = classSubjects.countLessonsByClass(id);
        if (lessons > 0) {
            throw new ConflictException("in-use",
                    schoolClass.getName() + " hat " + lessons + " eingeplante Stunden. Deaktivieren statt löschen.");
        }
        try {
            classes.delete(schoolClass);
            classes.flush();
        } catch (DataIntegrityViolationException ex) {
            // Uebrig bleibt der Fall „es haengt noch ein Klassenkonto daran" (user_account.class_id,
            // Modul auth, E-50). Das Modul masterdata darf auth nicht kennen, also fangen wir den
            // Verstoss gegen den Fremdschluessel ab, statt in ihn hineinzulaufen (sonst 500 statt 409)
            // — genauso wie TeacherService.delete es fuer das Lehrkraftkonto tut.
            throw new ConflictException("in-use", schoolClass.getName()
                    + " wird noch verwendet (zum Beispiel von einem Klassenkonto). Deaktivieren statt löschen.");
        }
    }

    /** Ersetzt die Faecherliste einer Klasse vollstaendig (G-7, G-8, G-10, G-11). */
    @Transactional
    public ClassDto replaceSubjects(Long id, List<ClassSubjectInput> rows) {
        SchoolClass schoolClass = require(id);
        List<ClassSubjectInput> wanted = rows == null ? List.of() : rows;
        validateRows(wanted);

        Set<Long> wantedSubjectIds = new HashSet<>();
        for (ClassSubjectInput row : wanted) {
            wantedSubjectIds.add(row.subjectId());
            Subject subject = subjects.findById(row.subjectId()).orElseThrow();
            Teacher teacher = teachers.findById(row.teacherId()).orElseThrow();
            Optional<ClassSubject> existing = schoolClass.getSubjects().stream()
                    .filter(line -> line.getSubject().getId().equals(row.subjectId()))
                    .findFirst();
            if (existing.isPresent()) {
                existing.get().setTeacher(teacher);
                existing.get().setWeeklyHours(row.weeklyHours());
            } else {
                schoolClass.getSubjects().add(new ClassSubject(schoolClass, subject, teacher, row.weeklyHours()));
            }
        }

        List<ClassSubject> removed = schoolClass.getSubjects().stream()
                .filter(line -> !wantedSubjectIds.contains(line.getSubject().getId()))
                .toList();
        for (ClassSubject line : removed) {
            long lessons = line.getId() == null ? 0 : classSubjects.countLessonsByClassSubject(line.getId());
            if (lessons > 0) {
                throw new ConflictException("in-use", line.getSubject().getName() + " ist in "
                        + schoolClass.getName() + " noch in " + lessons + " Stunden eingeplant.");
            }
        }
        schoolClass.getSubjects().removeAll(removed);
        classes.saveAndFlush(schoolClass);
        return toDto(schoolClass, warnings.allAssignments());
    }

    private SchoolClass require(Long id) {
        return classes.findById(id)
                .orElseThrow(() -> new NotFoundException("Es gibt keine Klasse mit der Nummer " + id + "."));
    }

    private void validate(ClassInput input, Long selfId) {
        classes.findByNameIgnoreCase(input.name().trim())
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new ValidationException("name", "Name ist bereits vergeben");
                });
    }

    /**
     * Prueft alle Zeilen und meldet sie gemeinsam: die Oberflaeche markiert alle fehlenden Felder auf
     * einmal, nicht eines nach dem anderen. Die Feldnamen sind die Pfade des Formulars
     * ({@code subjects[1].weeklyHours}), wie im Vertrag und in G-8 gefordert.
     */
    private void validateRows(List<ClassSubjectInput> rows) {
        List<FieldError> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ClassSubjectInput row = rows.get(i);
            String prefix = "subjects[" + i + "].";
            // Eine leere Zeile im Array ({"subjects":[null]}) kommt durch die Bean-Validation durch;
            // ohne diesen Fall gaebe der erste Zugriff darauf 500 statt 400.
            if (row == null) {
                errors.add(new FieldError("subjects[" + i + "]", "darf nicht leer sein"));
                continue;
            }
            if (row.subjectId() == null || !subjects.existsById(row.subjectId())) {
                errors.add(new FieldError(prefix + "subjectId", "Fach fehlt"));
            } else if (indexOfSubject(rows, row.subjectId()) != i) {
                errors.add(new FieldError(prefix + "subjectId", "Fach ist doppelt"));
            }
            if (row.weeklyHours() == null || row.weeklyHours() < 1) {
                errors.add(new FieldError(prefix + "weeklyHours", "Wochenstunden fehlen"));
            }
            if (row.teacherId() == null || !teachers.existsById(row.teacherId())) {
                errors.add(new FieldError(prefix + "teacherId", "Lehrkraft fehlt"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static int indexOfSubject(List<ClassSubjectInput> rows, Long subjectId) {
        for (int i = 0; i < rows.size(); i++) {
            // Leere Zeilen ueberspringen: sie sind schon als eigener Fehler gemeldet.
            if (rows.get(i) != null && subjectId.equals(rows.get(i).subjectId())) {
                return i;
            }
        }
        return -1;
    }

    private static ClassDto toDto(SchoolClass schoolClass, List<TeachingAssignment> assignments) {
        List<ClassSubjectDto> lines = schoolClass.getSubjects().stream()
                .sorted(Comparator.comparing(line -> line.getSubject().getId()))
                .map(line -> new ClassSubjectDto(
                        line.getSubject().getId(), line.getSubject().getShortName(), line.getWeeklyHours(),
                        line.getTeacher().getId(), line.getTeacher().getShortName()))
                .toList();
        return new ClassDto(schoolClass.getId(), schoolClass.getName(), schoolClass.getGrade(),
                schoolClass.isActive(), lines,
                WarningChecker.forClass(schoolClass.getId(), assignments));
    }
}
