package de.wsp3_1.masterdata.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findAllByOrderByIdAsc();

    Optional<Subject> findByShortNameIgnoreCase(String shortName);

    Optional<Subject> findByNameIgnoreCase(String name);
}
