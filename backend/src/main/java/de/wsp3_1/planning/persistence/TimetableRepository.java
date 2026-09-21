package de.wsp3_1.planning.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Wie {@link #findByIdAndStatus}, aber mit Schreibsperre auf der Zeile — fuer {@code accept}.
     *
     * <p>Ohne sie kommen zwei gleichzeitige {@code POST …/accept} auf denselben Entwurf beide durch die
     * Pruefungen: beide lesen ihn als {@code DRAFT}, beide stellen ihn auf {@code ACTIVE}, und der Plan
     * entsteht zweimal. {@code SELECT … FOR UPDATE} laesst die zweite Anfrage warten; sie findet den
     * Entwurf danach nicht mehr als Entwurf und antwortet 404 (Befund 16.09.).</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Timetable t where t.id = :id and t.status = :status")
    Optional<Timetable> findByIdAndStatusForUpdate(@Param("id") Long id,
                                                   @Param("status") TimetableStatus status);

    /**
     * Alle Plaene in diesem Zustand mit Schreibsperre, aelteste Kennung zuerst — die Vorgaengerpruefung
     * in {@code accept}.
     *
     * <p>Zwei gleichzeitige Annahmen mit sich ueberschneidenden Zeitraeumen bestuenden sonst beide die
     * Ueberlappungspruefung; erst die Ausschlussbedingung in der Datenbank faenge das ab — mit einer
     * Fehlermeldung statt einer Antwort. Die feste Reihenfolge nach Kennung haelt die Sperren beider
     * Anfragen in derselben Folge und verhindert damit ein Verklemmen.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Timetable t where t.status = :status order by t.id asc")
    List<Timetable> findAllByStatusForUpdate(@Param("status") TimetableStatus status);
}
