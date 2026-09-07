package de.wsp3_1.substitution.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /**
     * Alle Zeitraeume, die sich mit {@code [von, bis]} ueberschneiden — eine Abfrage je Wochensicht.
     * Gelesen als: der Zeitraum endet nicht vor dem Montag und beginnt nicht nach dem letzten Tag.
     */
    List<Holiday> findByDateToGreaterThanEqualAndDateFromLessThanEqual(LocalDate von, LocalDate bis);
}
