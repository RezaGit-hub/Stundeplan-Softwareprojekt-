package de.wsp3_1.masterdata.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    List<SchoolClass> findAllByOrderByIdAsc();

    Optional<SchoolClass> findByNameIgnoreCase(String name);
}
