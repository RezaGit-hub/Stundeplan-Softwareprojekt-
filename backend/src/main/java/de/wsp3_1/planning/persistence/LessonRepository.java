package de.wsp3_1.planning.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    /**
     * Alle Stunden eines Plans mit allem, was die Antwort braucht — in EINER Abfrage.
     *
     * <p>Ohne die {@code join fetch}-Zeilen laedt Hibernate je Stunde Klasse, Fach, Lehrkraft, Raum und
     * Zeitfenster einzeln nach (N+1); bei einem vollen Plan mit ueber 200 Stunden sind das mehr als
     * tausend Abfragen fuer ein einziges {@code GET /api/timetable}.</p>
     */
    @Query("""
            select l from Lesson l
              join fetch l.classSubject cs
              join fetch cs.schoolClass
              join fetch cs.subject
              join fetch cs.teacher
              left join fetch l.timeslot
              left join fetch l.room
            where l.timetable.id = :timetableId
            order by l.id
            """)
    List<Lesson> findAllOfTimetable(@Param("timetableId") Long timetableId);

    /** Eine einzelne Stunde eines Plans — dieselbe Vorabladung wie {@link #findAllOfTimetable}. */
    @Query("""
            select l from Lesson l
              join fetch l.classSubject cs
              join fetch cs.schoolClass
              join fetch cs.subject
              join fetch cs.teacher
              left join fetch l.timeslot
              left join fetch l.room
            where l.id = :id and l.timetable.id = :timetableId
            """)
    Optional<Lesson> findInTimetable(@Param("id") Long id, @Param("timetableId") Long timetableId);
}
