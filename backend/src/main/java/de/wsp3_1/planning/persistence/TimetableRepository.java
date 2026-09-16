package de.wsp3_1.planning.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    /**
     * Der eine Plan in diesem Zustand — <b>nur fuer {@code ACTIVE}</b>. Dass es hoechstens einen aktiven
     * Plan gibt, sichert der Teilindex {@code timetable_one_active} aus V3; deshalb steht hier
     * {@link Optional}. Fuer {@code DRAFT} ist die Methode seit V11 falsch: mehrere Entwuerfe ergaeben
     * eine {@code IncorrectResultSizeDataAccessException} — dafuer gibt es die drei Methoden darunter.
     */
    Optional<Timetable> findByStatus(TimetableStatus status);

    /** Der juengste Plan in diesem Zustand (hoechste Kennung) — das Ziel der Singular-Pfade (E-52). */
    Optional<Timetable> findFirstByStatusOrderByIdDesc(TimetableStatus status);

    /** Alle Plaene in diesem Zustand, juengster zuerst — die Entwurfsliste. */
    List<Timetable> findAllByStatusOrderByIdDesc(TimetableStatus status);

    /** Ein bestimmter Plan, aber nur in diesem Zustand: ein angenommener Entwurf ist kein Entwurf mehr. */
    Optional<Timetable> findByIdAndStatus(Long id, TimetableStatus status);

    /** Fuer „welcher Plan galt an diesem Tag": die archivierten Plaene, zuletzt angenommener zuerst. */
    List<Timetable> findByStatusOrderByAcceptedAtDesc(TimetableStatus status);
}
