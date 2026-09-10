package de.wsp3_1.shared;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Startet fuer die Integrationstests ein Wegwerf-PostgreSQL im Container — dieselbe Version wie im Betrieb,
 * kein H2. Wer ein JDK und Docker hat, braucht sonst nichts vorzubereiten.
 *
 * <p>In der Pipeline laeuft das Profil {@code ci}: dort steht die Datenbank schon als Dienst bereit und
 * ihre Adresse kommt aus {@code SPRING_DATASOURCE_URL}. Deshalb ist diese Klasse dort abgeschaltet.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("!ci")
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
