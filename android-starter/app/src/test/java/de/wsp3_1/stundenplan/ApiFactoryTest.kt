package de.wsp3_1.stundenplan

import de.wsp3_1.stundenplan.data.ApiFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reiner JVM-Test, laeuft ohne Geraet und ohne Emulator:
 * Android Studio -> Rechtsklick auf die Datei -> Run.
 * Erster Beweis dafuer, dass der Testpfad des Projekts funktioniert.
 */
class ApiFactoryTest {

    @Test
    fun `Basisadresse bekommt einen Schraegstrich`() {
        assertEquals("http://10.0.2.2:8080/", ApiFactory.withTrailingSlash("http://10.0.2.2:8080"))
    }

    @Test
    fun `vorhandener Schraegstrich bleibt einfach`() {
        assertEquals("http://10.0.2.2:8080/", ApiFactory.withTrailingSlash("http://10.0.2.2:8080/"))
    }

    @Test
    fun `Adresse ohne Schema wird abgelehnt`() {
        assertFalse(ApiFactory.looksLikeHttpUrl("10.0.2.2:8080"))
        assertTrue(ApiFactory.looksLikeHttpUrl("http://10.0.2.2:8080"))
        assertTrue(ApiFactory.looksLikeHttpUrl("https://schule.example/"))
    }

    @Test
    fun `ein getipptes api am Ende faellt weg - sonst entstuende api-api`() {
        assertEquals("http://134.245.1.240:1500", ApiFactory.stripApiSuffix("http://134.245.1.240:1500/api"))
        assertEquals("http://134.245.1.240:1500", ApiFactory.stripApiSuffix("http://134.245.1.240:1500/api/"))
        assertEquals("http://134.245.1.240:1500", ApiFactory.stripApiSuffix(" http://134.245.1.240:1500/API "))
        assertEquals("https://schule.example/plan", ApiFactory.stripApiSuffix("https://schule.example/plan/api"))
    }

    @Test
    fun `eine gewoehnliche Adresse bleibt unveraendert`() {
        assertEquals("http://10.0.2.2:1500", ApiFactory.stripApiSuffix("http://10.0.2.2:1500"))
        assertEquals("http://10.0.2.2:1500", ApiFactory.stripApiSuffix("http://10.0.2.2:1500/"))
        assertEquals("http://api", ApiFactory.stripApiSuffix("http://api")) // Rechner heisst api, kein Pfad
        assertEquals("10.0.2.2:1500", ApiFactory.stripApiSuffix("10.0.2.2:1500")) // ohne Schema unangetastet
    }

    @Test
    fun `normalize liefert die Basis-Adresse fuer Retrofit`() {
        assertEquals("http://10.0.2.2:1500/", ApiFactory.normalize("http://10.0.2.2:1500/api"))
        assertEquals("http://10.0.2.2:1500/", ApiFactory.normalize("http://10.0.2.2:1500"))
    }
}
