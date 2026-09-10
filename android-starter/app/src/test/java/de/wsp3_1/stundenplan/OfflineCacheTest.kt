package de.wsp3_1.stundenplan

import de.wsp3_1.stundenplan.data.ApiFactory
import de.wsp3_1.stundenplan.data.OfflineCache
import de.wsp3_1.stundenplan.data.WeekDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** JSON-Dateien im Dateisystem: Roundtrip, Begrenzung auf vier Wochen, Loeschen beim Abmelden. */
class OfflineCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var dir: File

    private fun cache(maxWeeks: Int = 4): OfflineCache {
        dir = folder.newFolder("offline")
        return OfflineCache(dir, ApiFactory().json, maxWeeks)
    }

    @Test
    fun `gespeicherte Woche kommt samt Zeitstempel unveraendert zurueck`() {
        val cache = cache()
        cache.saveWeek(SampleWeek.week, 1_700_000_000_000L)
        val loaded = cache.loadWeek("2026-09-21")
        assertEquals(1_700_000_000_000L, loaded?.savedAtEpochMillis)
        assertEquals(SampleWeek.week, loaded?.week)
        assertNull(cache.loadWeek("2026-09-28"))
    }

    @Test
    fun `hoechstens vier Wochen, die aelteste fliegt raus`() {
        val cache = cache()
        val mondays = listOf("2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28")
        mondays.forEachIndexed { i, monday ->
            cache.saveWeek(WeekDto(monday, emptyList()), 1_000L + i)
            // Aenderungszeit explizit setzen: manche Dateisysteme loesen nur auf Sekunden auf.
            File(dir, "week-$monday.json").setLastModified(1_000_000_000_000L + i * 60_000L)
        }
        cache.saveWeek(WeekDto("2026-10-05", emptyList()), 2_000L)
        assertEquals(listOf("2026-09-14", "2026-09-21", "2026-09-28", "2026-10-05"), cache.storedMondays())
        assertNull(cache.loadWeek("2026-09-07"))
    }

    @Test
    fun `Raster wird getrennt gespeichert und clear entfernt alles`() {
        val cache = cache()
        cache.saveTimegrid(SampleWeek.timegrid, 42L)
        cache.saveWeek(SampleWeek.week, 43L)
        assertEquals(SampleWeek.timegrid, cache.loadTimegrid()?.timegrid)
        cache.clear()
        assertNull(cache.loadTimegrid())
        assertNull(cache.loadWeek("2026-09-21"))
        assertEquals(emptyList<String>(), cache.storedMondays())
    }

    @Test
    fun `unlesbare Datei wird verworfen statt zu werfen`() {
        val cache = cache()
        File(dir, "week-2026-09-21.json").writeText("kein json")
        assertNull(cache.loadWeek("2026-09-21"))
    }

    @Test
    fun `Ueberschreiben laesst keinen halben Stand zurueck`() {
        val cache = cache()
        cache.saveWeek(SampleWeek.week, 1L)
        cache.saveWeek(SampleWeek.week.copy(days = emptyList()), 2L)
        val loaded = cache.loadWeek("2026-09-21")
        assertEquals(2L, loaded?.savedAtEpochMillis)
        assertEquals(emptyList<Any>(), loaded?.week?.days)
        // Die Nebendatei des Schreibvorgangs bleibt nicht liegen und zaehlt nie als Woche.
        assertEquals(listOf("2026-09-21"), cache.storedMondays())
        assertEquals(emptyList<String>(), dir.list()!!.filter { it.endsWith(".tmp") })
    }

    @Test
    fun `eine liegengebliebene Nebendatei raeumt clear mit weg`() {
        val cache = cache()
        cache.saveWeek(SampleWeek.week, 1L)
        File(dir, "week-2026-09-28.json.tmp").writeText("halb geschrieben")
        assertEquals(listOf("2026-09-21"), cache.storedMondays())
        cache.clear()
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }
}
