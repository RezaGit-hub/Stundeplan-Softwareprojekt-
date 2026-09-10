package de.wsp3_1.masterdata.domain;

import de.wsp3_1.masterdata.MasterdataDtos.WarningCode;
import de.wsp3_1.masterdata.MasterdataDtos.WarningDto;
import de.wsp3_1.masterdata.persistence.ClassSubject;
import de.wsp3_1.masterdata.persistence.ClassSubjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Die beiden Warnungen der Grunddaten (T-W1.4, T-W1.5). Warnungen sind Daten in einer 200-Antwort
 * und verhindern nie das Speichern — die Planung entscheidet selbst.
 *
 * <ul>
 *   <li>{@code SUBJECT_NOT_TAUGHT}: die Lehrkraft der Zeile hat das Fach nicht in ihrer Faecherliste.</li>
 *   <li>{@code QUOTA_EXCEEDED}: die Summe ihrer Wochenstunden <b>ueber alle Klassen</b> liegt ueber
 *       dem Deputat. Je Lehrkraft hoechstens einmal je Antwort.</li>
 * </ul>
 *
 * <p>Nichts davon wird gespeichert; jede Antwort rechnet neu (E-31). Die Rechnung selbst steht in
 * den statischen Methoden und braucht keine Datenbank — deshalb prueft {@code WarningCheckerTest}
 * sie ohne Spring. Die Meldungstexte sind woertlich die der Attrappe
 * ({@code frontend/src/mock/logic/warnings.ts}), damit Attrappe und Server dasselbe sagen.</p>
 */
@Component
public class WarningChecker {

    /**
     * Eine Zeile „Klasse unterrichtet Fach bei Lehrkraft" in der flachen Form, die die Rechnung
     * braucht. Bewusst ohne Entities: so ist die Rechnung ohne Datenbank pruefbar.
     */
    public record TeachingAssignment(long classId, long subjectId, String subjectName,
                                     long teacherId, String teacherName, int teacherQuota,
                                     boolean teacherMayTeachSubject, int weeklyHours) {
    }

    private final ClassSubjectRepository classSubjects;

    public WarningChecker(ClassSubjectRepository classSubjects) {
        this.classSubjects = classSubjects;
    }

    /** Alle Faecherlisten aller Klassen — die Grundlage jeder Warnung und von {@code plannedHours}. */
    @Transactional(readOnly = true)
    public List<TeachingAssignment> allAssignments() {
        List<TeachingAssignment> out = new ArrayList<>();
        for (ClassSubject row : classSubjects.findAllWithReferences()) {
            out.add(new TeachingAssignment(
                    row.getSchoolClass().getId(),
                    row.getSubject().getId(),
                    row.getSubject().getName(),
                    row.getTeacher().getId(),
                    row.getTeacher().getName(),
                    row.getTeacher().getWeeklyHoursQuota(),
                    row.getTeacher().getSubjectIds().contains(row.getSubject().getId()),
                    row.getWeeklyHours()));
        }
        return out;
    }

    /** Summe der Wochenstunden dieser Lehrkraft ueber alle Klassen. */
    public static int plannedHours(long teacherId, List<TeachingAssignment> all) {
        int sum = 0;
        for (TeachingAssignment a : all) {
            if (a.teacherId() == teacherId) {
                sum += a.weeklyHours();
            }
        }
        return sum;
    }

    /** Warnungen zu einer Klasse: je Zeile die fehlende Lehrbefaehigung, je Lehrkraft einmal das Deputat. */
    public static List<WarningDto> forClass(long classId, List<TeachingAssignment> all) {
        List<WarningDto> out = new ArrayList<>();
        Set<Long> quotaAlreadyReported = new HashSet<>();
        for (TeachingAssignment a : all) {
            if (a.classId() != classId) {
                continue;
            }
            if (!a.teacherMayTeachSubject()) {
                out.add(new WarningDto(WarningCode.SUBJECT_NOT_TAUGHT,
                        a.teacherName() + " unterrichtet das Fach " + a.subjectName() + " nicht.",
                        a.teacherId()));
            }
            int planned = plannedHours(a.teacherId(), all);
            if (planned > a.teacherQuota() && quotaAlreadyReported.add(a.teacherId())) {
                out.add(quotaWarning(a.teacherId(), a.teacherName(), a.teacherQuota(), planned));
            }
        }
        return out;
    }

    /** Warnungen zu einer Lehrkraft: nur das Deputat — welches Fach sie unterrichtet, sieht man an der Klasse. */
    public static List<WarningDto> forTeacher(long teacherId, String teacherName, int quota,
                                              List<TeachingAssignment> all) {
        int planned = plannedHours(teacherId, all);
        if (planned <= quota) {
            return List.of();
        }
        return List.of(quotaWarning(teacherId, teacherName, quota, planned));
    }

    private static WarningDto quotaWarning(long teacherId, String teacherName, int quota, int planned) {
        return new WarningDto(WarningCode.QUOTA_EXCEEDED,
                teacherName + " ist mit " + planned + " Wochenstunden über dem Deputat von " + quota + ".",
                teacherId);
    }
}
