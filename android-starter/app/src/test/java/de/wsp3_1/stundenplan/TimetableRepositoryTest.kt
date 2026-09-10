package de.wsp3_1.stundenplan

import de.wsp3_1.stundenplan.data.ApiFactory
import de.wsp3_1.stundenplan.data.EntryKind
import de.wsp3_1.stundenplan.data.HealthResult
import de.wsp3_1.stundenplan.data.HttpTimetableRepository
import de.wsp3_1.stundenplan.data.LoginResult
import de.wsp3_1.stundenplan.data.OfflineCache
import de.wsp3_1.stundenplan.data.WeekResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/**
 * MockWebServer gegen das echte Retrofit: 200 -> Online (und Cache geschrieben), 401 ->
 * Unauthorized (T-607), 404 -> NoTeacher, Server aus -> Offline; Login 200/401; Health.
 */
class TimetableRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var settings: FakeSettings
    private lateinit var cache: OfflineCache
    private lateinit var repository: HttpTimetableRepository
    private val monday = LocalDate.of(2026, 9, 7)

    private fun problem(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/problem+json").setBody(body)

    private fun json(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Before
    fun setUp() {
        server.start()
        settings = FakeSettings(serverUrl = server.url("/").toString().removeSuffix("/"), token = "testtoken")
        val factory = ApiFactory()
        cache = OfflineCache(folder.newFolder("offline"), factory.json)
        repository = HttpTimetableRepository(settings, factory, cache, clock = { 123_456L })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `200 liefert Online mit den Daten des Servers und schreibt den Cache`() = runTest {
        server.enqueue(json(SampleJson.WEEK))
        val result = repository.loadWeek(monday)
        assertTrue(result is WeekResult.Online)
        val week = (result as WeekResult.Online).week
        assertEquals("2026-09-07", week.monday)
        assertEquals(5, week.days.size)
        val wednesday = week.days[2]
        assertEquals(listOf(EntryKind.GIVEN_AWAY, EntryKind.NEEDS_SUBSTITUTION, EntryKind.NEEDS_SUBSTITUTION, EntryKind.NEEDS_SUBSTITUTION), wednesday.entries.map { it.kind })
        assertEquals("MEI", wednesday.entries[0].original?.teacherShort)
        assertEquals(listOf("Krankmeldung"), wednesday.entries[0].reasons)

        val request = server.takeRequest()
        assertEquals("/api/me/timetable/week?date=2026-09-07", request.path)
        assertEquals("Bearer testtoken", request.getHeader("Authorization"))

        val cached = cache.loadWeek("2026-09-07")
        assertEquals(123_456L, cached?.savedAtEpochMillis)
        assertEquals(week, cached?.week)
    }

    @Test
    fun `401 liefert Unauthorized`() = runTest {
        server.enqueue(problem(401, SampleJson.PROBLEM_401))
        assertEquals(WeekResult.Unauthorized, repository.loadWeek(monday))
    }

    @Test
    fun `404 liefert NoTeacher - Konto ohne Lehrkraft`() = runTest {
        server.enqueue(problem(404, SampleJson.PROBLEM_404))
        assertEquals(WeekResult.NoTeacher, repository.loadWeek(monday))
    }

    @Test
    fun `500 liefert Failure mit dem detail-Text des Servers`() = runTest {
        server.enqueue(problem(500, """{"type":"urn:pushnpull:problem:internal","title":"Fehler","status":500,"detail":"Datenbank nicht erreichbar."}"""))
        val result = repository.loadWeek(monday)
        assertEquals(WeekResult.Failure("Datenbank nicht erreichbar."), result)
    }

    @Test
    fun `Server aus liefert Offline, der alte Cache bleibt lesbar`() = runTest {
        cache.saveWeek(SampleWeek.nextWeek, 99L)
        val dead = MockWebServer()
        dead.start()
        val url = dead.url("/").toString().removeSuffix("/")
        dead.shutdown()
        settings.serverUrlFlow.value = url
        assertEquals(WeekResult.Offline, repository.loadWeek(LocalDate.of(2026, 9, 28)))
        assertEquals(99L, repository.cachedWeek(LocalDate.of(2026, 9, 28))?.savedAtEpochMillis)
    }

    @Test
    fun `ohne Token gibt es keinen Netzaufruf, sondern Unauthorized`() = runTest {
        settings.tokenFlow.value = null
        assertEquals(WeekResult.Unauthorized, repository.loadWeek(monday))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `Login 200 legt Token und Anzeigename ab`() = runTest {
        settings.tokenFlow.value = null
        server.enqueue(json(SampleJson.LOGIN))
        val result = repository.login("meyer", "geheim")
        assertTrue(result is LoginResult.Success)
        assertEquals("Meinke", (result as LoginResult.Success).response.displayName)
        assertEquals("testtoken", settings.tokenFlow.value)
        assertEquals("Meinke", settings.displayNameFlow.value)
        val request = server.takeRequest()
        assertEquals("/api/auth/login", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("\"username\":\"meyer\""))
    }

    @Test
    fun `Login 401 heisst falsches Passwort, Token bleibt leer`() = runTest {
        settings.tokenFlow.value = null
        server.enqueue(problem(401, SampleJson.PROBLEM_LOGIN_401))
        assertEquals(LoginResult.WrongCredentials, repository.login("meyer", "falsch"))
        assertNull(settings.tokenFlow.value)
    }

    @Test
    fun `Abmelden ruft DELETE und loescht Token und Cache`() = runTest {
        cache.saveWeek(SampleWeek.week, 1L)
        server.enqueue(MockResponse().setResponseCode(204))
        repository.logout()
        assertEquals("DELETE", server.takeRequest().method)
        assertNull(settings.tokenFlow.value)
        assertNull(cache.loadWeek("2026-09-21"))
    }

    @Test
    fun `Raster wird geladen und gespeichert`() = runTest {
        server.enqueue(json(SampleJson.TIMEGRID))
        val grid = repository.loadTimegrid()
        assertNotNull(grid)
        assertEquals(35, grid?.slots?.size)
        assertEquals("08:00", grid?.slots?.first()?.startTime)
        assertNotNull(cache.loadTimegrid())
    }

    @Test
    fun `Raster wird bei jedem Abruf neu geholt, sonst blieben geaenderte Zeiten unsichtbar`() = runTest {
        server.enqueue(json(SampleJson.TIMEGRID))
        assertEquals("08:00", repository.loadTimegrid()?.slots?.first()?.startTime)

        val spaeter = SampleJson.TIMEGRID.replace("\"08:00\"", "\"07:45\"")
        server.enqueue(json(spaeter))
        assertEquals("07:45", repository.loadTimegrid()?.slots?.first()?.startTime)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `antwortet der Server nicht, gilt das gespeicherte Raster`() = runTest {
        cache.saveTimegrid(SampleWeek.timegrid, 7L)
        server.enqueue(problem(500, """{"detail":"kaputt","status":500}"""))
        assertEquals(SampleWeek.timegrid, repository.loadTimegrid())
    }

    @Test
    fun `Anmeldung loescht den Offline-Stand des vorigen Kontos`() = runTest {
        cache.saveWeek(SampleWeek.week, 1L)
        cache.saveTimegrid(SampleWeek.timegrid, 1L)
        settings.tokenFlow.value = null
        server.enqueue(json(SampleJson.LOGIN))

        assertTrue(repository.login("nissen", "geheim") is LoginResult.Success)

        assertEquals(emptyList<String>(), cache.storedMondays())
        assertNull(cache.loadTimegrid())
    }

    @Test
    fun `clearCache leert Wochen und Raster, das Token bleibt`() = runTest {
        cache.saveWeek(SampleWeek.week, 1L)
        cache.saveTimegrid(SampleWeek.timegrid, 1L)
        repository.clearCache()
        assertEquals(emptyList<String>(), cache.storedMondays())
        assertNull(cache.loadTimegrid())
        assertEquals("testtoken", settings.tokenFlow.value)
    }

    @Test
    fun `nach einem Adresswechsel kommt das Raster des neuen Servers`() = runTest {
        server.enqueue(json(SampleJson.TIMEGRID))
        assertEquals("08:00", repository.loadTimegrid()?.slots?.first()?.startTime)

        val andere = MockWebServer()
        andere.start()
        andere.enqueue(json(SampleJson.TIMEGRID.replace("\"08:00\"", "\"07:30\"")))
        settings.serverUrlFlow.value = andere.url("/").toString().removeSuffix("/")

        assertEquals("07:30", repository.loadTimegrid()?.slots?.first()?.startTime)
        andere.shutdown()
    }

    @Test
    fun `eine Adresse mit api am Ende trifft trotzdem die richtigen Pfade`() = runTest {
        settings.serverUrlFlow.value = server.url("/").toString().removeSuffix("/") + "/api"
        server.enqueue(json(SampleJson.WEEK))
        assertTrue(repository.loadWeek(monday) is WeekResult.Online)
        assertEquals("/api/me/timetable/week?date=2026-09-07", server.takeRequest().path)
    }

    @Test
    fun `Health zeigt den Schulnamen, Unsinn ist nicht erreichbar`() = runTest {
        server.enqueue(json(SampleJson.HEALTH))
        val result = repository.checkHealth(server.url("/").toString())
        assertEquals(HealthResult.Reachable("Gymnasium Musterstadt"), result)
        assertEquals(HealthResult.Unreachable, repository.checkHealth("10.0.2.2:1500"))
    }
}
