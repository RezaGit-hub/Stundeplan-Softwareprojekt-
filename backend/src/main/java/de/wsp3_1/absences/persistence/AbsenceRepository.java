package de.wsp3_1.absences.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {

    /** Alle Meldungen, neueste zuerst — Sicht der Planung. */
    List<Absence> findAllByOrderByDateFromDesc();

    /** Nur die eigenen — Sicht der Lehrkraft. */
    List<Absence> findByTeacherIdOrderByDateFromDesc(Long teacherId);
}