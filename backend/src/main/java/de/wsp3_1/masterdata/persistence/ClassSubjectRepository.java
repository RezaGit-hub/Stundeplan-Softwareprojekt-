package de.wsp3_1.masterdata.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Faecherlisten aller Klassen — und die Zaehlung, wie oft eine Grunddate noch eingeplant ist.
 *
 * <p><b>Warum stehen hier native Abfragen auf {@code lesson}?</b> Loeschen ist nur ohne Verweise
 * erlaubt (T-W1.3): dafuer muss {@code masterdata} wissen, wie viele Unterrichtsstunden an einer
 * Lehrkraft, einem Fach, einem Raum, einer Klasse oder einem Zeitfenster haengen. Die Tabelle
 * {@code lesson} gehoert dem Modul {@code planning}. Eine Java-Abhaengigkeit dorthin waere gegen die
 * Modultabelle in docs/VERTRAG.md (masterdata darf nur {@code shared}), deshalb zaehlen wir mit SQL
 * direkt in der Datenbank. Archivierte Plaene zaehlen nicht mit — sie sind Geschichte, kein Verweis,
 * der ein Loeschen verhindern soll (gleiche Regel wie in der Attrappe {@code lessonsInUse}).</p>
 */
public interface ClassSubjectRepository extends JpaRepository<ClassSubject, Long> {

    /** Alle Zeilen samt Klasse, Fach und Lehrkraft in einem Zug — Grundlage der Warnungen. */
    @Query("""
            select cs from ClassSubject cs
              join fetch cs.schoolClass
              join fetch cs.subject
              join fetch cs.teacher
            order by cs.schoolClass.id, cs.subject.id
            """)
    List<ClassSubject> findAllWithReferences();

    List<ClassSubject> findBySchoolClassIdOrderBySubjectIdAsc(Long classId);

    @Query(value = """
            SELECT count(*) FROM lesson l
              JOIN class_subject cs ON cs.id = l.class_subject_id
              JOIN timetable t      ON t.id = l.timetable_id
            WHERE cs.teacher_id = :teacherId AND t.status <> 'ARCHIVED'
            """, nativeQuery = true)
    long countLessonsByTeacher(@Param("teacherId") Long teacherId);

    @Query(value = """
            SELECT count(*) FROM lesson l
              JOIN class_subject cs ON cs.id = l.class_subject_id
              JOIN timetable t      ON t.id = l.timetable_id
            WHERE cs.subject_id = :subjectId AND t.status <> 'ARCHIVED'
            """, nativeQuery = true)
    long countLessonsBySubject(@Param("subjectId") Long subjectId);

    @Query(value = """
            SELECT count(*) FROM lesson l
              JOIN class_subject cs ON cs.id = l.class_subject_id
              JOIN timetable t      ON t.id = l.timetable_id
            WHERE cs.class_id = :classId AND t.status <> 'ARCHIVED'
            """, nativeQuery = true)
    long countLessonsByClass(@Param("classId") Long classId);

    @Query(value = """
            SELECT count(*) FROM lesson l
              JOIN class_subject cs ON cs.id = l.class_subject_id
              JOIN timetable t      ON t.id = l.timetable_id
            WHERE cs.id = :classSubjectId AND t.status <> 'ARCHIVED'
            """, nativeQuery = true)
    long countLessonsByClassSubject(@Param("classSubjectId") Long classSubjectId);

    @Query(value = """
            SELECT count(*) FROM lesson l
              JOIN timetable t ON t.id = l.timetable_id
            WHERE l.room_id = :roomId AND t.status <> 'ARCHIVED'
            """, nativeQuery = true)
    long countLessonsByRoom(@Param("roomId") Long roomId);

    @Query(value = """
            SELECT count(*) FROM lesson l
              JOIN timetable t ON t.id = l.timetable_id
            WHERE l.timeslot_id = :timeslotId AND t.status <> 'ARCHIVED'
            """, nativeQuery = true)
    long countLessonsByTimeslot(@Param("timeslotId") Long timeslotId);

    /**
     * Verlegungen (W-6) zeigen mit {@code new_timeslot_id} auf ein Zeitfenster; auch sie halten es fest.
     */
    @Query(value = """
            SELECT count(*) FROM lesson_exception WHERE new_timeslot_id = :timeslotId
            """, nativeQuery = true)
    long countExceptionsByNewTimeslot(@Param("timeslotId") Long timeslotId);

    /**
     * Stunden archivierter Plaene halten ein Zeitfenster nicht fest, stehen aber per Fremdschluessel
     * noch darauf. Vor dem Loeschen eines Zeitfensters raeumt das Raster sie weg — der Verlauf (W-7)
     * haelt seine Belegungen als Text und braucht sie nicht.
     */
    @Modifying
    @Query(value = """
            DELETE FROM lesson l USING timetable t
            WHERE t.id = l.timetable_id AND t.status = 'ARCHIVED' AND l.timeslot_id = :timeslotId
            """, nativeQuery = true)
    int deleteArchivedLessonsByTimeslot(@Param("timeslotId") Long timeslotId);
}
