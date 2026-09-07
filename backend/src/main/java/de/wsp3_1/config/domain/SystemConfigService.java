package de.wsp3_1.config.domain;

import de.wsp3_1.config.persistence.SystemConfig;
import de.wsp3_1.config.persistence.SystemConfigRepository;
import de.wsp3_1.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Liest und schreibt die eine Zeile der Systemkonfiguration ({@code system_config}, Seed in V2).
 * Gibt bewusst keine Entity nach aussen (ArchUnit-Regel 2), sondern {@link SystemConfigData}.
 *
 * <p>Andere Module lesen hier nur: {@code rules} das Bundesland fuer {@code POST /api/rules/reset},
 * {@code planning} die Rechenzeit fuer den Solver. Geschrieben wird ausschliesslich ueber
 * {@code PUT /api/config}.</p>
 */
@Service
public class SystemConfigService {

    private final SystemConfigRepository repository;

    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    /** Die vollstaendige Konfiguration fuer {@code GET /api/config}. */
    @Transactional(readOnly = true)
    public SystemConfigData get() {
        SystemConfig zeile = laden();
        return new SystemConfigData(zeile.getSchoolName(), zeile.getState(), zeile.getSchoolType(),
                zeile.getMaxSolveSeconds());
    }

    /**
     * Uebernimmt alle vier Werte ({@code PUT /api/config} ersetzt, es gibt kein Teil-Update).
     * Geprueft hat die Eingabe schon die Bean-Validation am Controller.
     */
    @Transactional
    public SystemConfigData update(SystemConfigData neu) {
        SystemConfig zeile = laden();
        zeile.setSchoolName(neu.schoolName());
        zeile.setState(neu.state());
        zeile.setSchoolType(neu.schoolType());
        zeile.setMaxSolveSeconds(neu.maxSolveSeconds());
        repository.save(zeile);
        return get();
    }

    /** Schulname fuer {@code GET /api/health} — die App zeigt ihn nach dem Verbindungstest (A-3). */
    @Transactional(readOnly = true)
    public String schoolName() {
        return laden().getSchoolName();
    }

    /** Rechenzeit je Solver-Lauf in Sekunden; benutzt vom Modul {@code planning}. */
    @Transactional(readOnly = true)
    public int maxSolveSeconds() {
        return laden().getMaxSolveSeconds();
    }

    private SystemConfig laden() {
        return repository.findById(SystemConfigRepository.SINGLETON_ID)
                .orElseThrow(() -> new NotFoundException("Die Systemkonfiguration fehlt (Zeile id=1 in system_config)."));
    }
}
