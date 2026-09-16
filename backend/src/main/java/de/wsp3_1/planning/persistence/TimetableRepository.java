package de.wsp3_1.planning.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    /** Der juengste Plan in diesem Zustand (hoechste Kennung) — das Ziel der Singular-Pfade (E-52). */
    Optional<Timetable> findFirstByStatusOrderByIdDesc(TimetableStatus status);

    /** Alle Plaene in diesem Zustand, juengster zuerst — die Entwurfsliste. */
    List<Timetable> findAllByStatusOrderByIdDesc(TimetableStatus status);

    /**
     * Alle Plaene in diesem Zustand, aeltester zuerst.
     *
     * <p>Seit V12 (#115) gibt es mehrere {@code ACTIVE} nebeneinander — je einer je Zeitraum. Ein
     * {@code findByStatus}, das ein {@link Optional} liefert, waere seitdem falsch: es endete in einer
     * {@code IncorrectResultSizeDataAccessException}. Wer „den" aktiven Plan sucht, fragt den
     * {@code PlanReader}; der weiss, welcher Zeitraum heute gilt.</p>
     */
    List<Timetable> findAllByStatusOrderByIdAsc(TimetableStatus status);

    /** Ein bestimmter Plan, aber nur in diesem Zustand: ein angenommener Entwurf ist kein Entwurf mehr. */
    Optional<Timetable> findByIdAndStatus(Long id, TimetableStatus status);

    /** Fuer „welcher Plan galt an diesem Tag": die archivierten Plaene, zuletzt angenommener zuerst. */
    List<Timetable> findByStatusOrderByAcceptedAtDesc(TimetableStatus status);
}
