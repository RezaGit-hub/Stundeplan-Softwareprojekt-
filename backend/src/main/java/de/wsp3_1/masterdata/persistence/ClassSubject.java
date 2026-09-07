package de.wsp3_1.masterdata.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Eine Zeile der Faecherliste einer Klasse: welches Fach mit wie vielen Wochenstunden bei welcher
 * Lehrkraft. Beide Angaben sind Pflicht — ohne sie kein Speichern (400, Ausnahme 1 aus W-1).
 *
 * <p>Diese Zeile ist zugleich der Bezugspunkt jeder Unterrichtsstunde: {@code lesson.class_subject_id}
 * verweist hierher, und daraus folgen Klasse, Fach <b>und</b> Lehrkraft der Stunde.</p>
 */
@Entity
@Table(name = "class_subject")
public class ClassSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "weekly_hours", nullable = false)
    private int weeklyHours;

    protected ClassSubject() {
    }

    public ClassSubject(SchoolClass schoolClass, Subject subject, Teacher teacher, int weeklyHours) {
        this.schoolClass = schoolClass;
        this.subject = subject;
        this.teacher = teacher;
        this.weeklyHours = weeklyHours;
    }

    public Long getId() {
        return id;
    }

    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public Subject getSubject() {
        return subject;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public int getWeeklyHours() {
        return weeklyHours;
    }

    public void setWeeklyHours(int weeklyHours) {
        this.weeklyHours = weeklyHours;
    }
}
