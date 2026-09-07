package de.wsp3_1.config.api;

import de.wsp3_1.config.domain.SystemConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Erreichbarkeitspruefung fuer Web und App (A-3). Ohne Token erreichbar; liefert den Schulnamen,
 * damit die Anmeldeseite und der Verbindungstest der App zeigen koennen, mit welcher Schule sie sprechen.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final SystemConfigService systemConfig;

    public HealthController(SystemConfigService systemConfig) {
        this.systemConfig = systemConfig;
    }

    /** Feldnamen wie {@code HealthDto} in {@code frontend/src/api/types.ts}. */
    public record HealthDto(String status, String schoolName) {
    }

    @GetMapping
    public HealthDto health() {
        return new HealthDto("UP", systemConfig.schoolName());
    }
}
