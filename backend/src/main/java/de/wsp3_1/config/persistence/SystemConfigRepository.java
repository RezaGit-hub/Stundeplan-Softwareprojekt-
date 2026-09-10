package de.wsp3_1.config.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    /** Die eine Zeile der Systemkonfiguration. */
    Long SINGLETON_ID = 1L;
}
