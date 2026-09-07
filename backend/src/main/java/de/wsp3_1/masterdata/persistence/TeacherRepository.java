package de.wsp3_1.masterdata.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    List<Teacher> findAllByOrderByIdAsc();

    /** Kuerzel sind eindeutig (V1). Fuer die Pruefung beim Speichern: dieselbe Zeile darf sich selbst haben. */
    Optional<Teacher> findByShortNameIgnoreCase(String shortName);
}
