package de.wsp3_1.substitution.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Lesender Zugriff auf die Vorlage (E-51). Es gibt bewusst keinen Schreibweg: die Zeilen kommen aus
 * der Migration, die {@code scripts/gen-holiday-presets.mjs} erzeugt.
 */
public interface HolidayPresetRepository extends JpaRepository<HolidayPreset, Long> {

    /** Alle Zeitraeume eines Bundeslands fuer ein Schuljahr, nach Beginn — die eine Abfrage beim Uebernehmen. */
    List<HolidayPreset> findByStateAndSchoolYearOrderByDateFromAscLabelAsc(String state, String schoolYear);

    /** Die Schuljahre, fuer die es zu diesem Bundesland ueberhaupt Zeilen gibt (aelteste zuerst). */
    @Query("SELECT DISTINCT p.schoolYear FROM HolidayPreset p WHERE p.state = :state ORDER BY p.schoolYear")
    List<String> findSchoolYears(String state);
}
