package de.wsp3_1.rules.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RulePresetRepository extends JpaRepository<RulePreset, Long> {

    /** Die Voreinstellung eines Bundeslands, in der Reihenfolge, in der V2 sie eingetragen hat. */
    List<RulePreset> findByStateOrderByIdAsc(String state);
}
