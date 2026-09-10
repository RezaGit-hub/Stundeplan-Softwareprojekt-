package de.wsp3_1.shared;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Basisklasse aller Integrationstests (Teststrategie, Ebenen 3 und 4). Ein Spring-Kontext fuer alle,
 * kein {@code @DirtiesContext} — das haelt die Laufzeit klein.
 *
 * <p>Der Klassenname endet auf {@code IT} und die Klasse traegt {@code @Tag("integration")}:
 * {@code ./gradlew test} laesst diese Tests aus, {@code ./gradlew integrationTest} fuehrt genau sie aus.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Tag("integration")
@TestPropertySource(properties = "app.admin-initial-password=admin")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;
}
