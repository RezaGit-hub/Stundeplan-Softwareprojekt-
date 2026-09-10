package de.wsp3_1.planning.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    /**
     * Der eine Plan in diesem Zustand. Dass es hoechstens einen Entwurf und hoechstens einen aktiven
     * Plan gibt, sichern die Teilindizes aus V3 — deshalb steht hier {@link Optional} und keine Liste.
     */
    Optional<Timetable> findByStatus(TimetableStatus status);

    /** Fuer „welcher Plan galt an diesem Tag": die archivierten Plaene, zuletzt angenommener zuerst. */
    List<Timetable> findByStatusOrderByAcceptedAtDesc(TimetableStatus status);
}
