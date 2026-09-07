package de.wsp3_1.masterdata.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeslotRepository extends JpaRepository<Timeslot, Long> {

    /**
     * Alle Zeitfenster. Sortiert wird bewusst nicht hier: der Wochentag steht als Text in der
     * Datenbank, alphabetisch kaeme FR vor MO. Die Raster-Reihenfolge macht {@code TimegridService}.
     */
    List<Timeslot> findAll();
}
