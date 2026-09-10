package de.wsp3_1.stundenplan

import de.wsp3_1.stundenplan.data.CachedWeek
import de.wsp3_1.stundenplan.data.LoginResponse
import de.wsp3_1.stundenplan.data.LoginResult
import de.wsp3_1.stundenplan.data.WeekResult
import de.wsp3_1.stundenplan.ui.LoginError
import de.wsp3_1.stundenplan.ui.LoginReason
import de.wsp3_1.stundenplan.ui.Message
import de.wsp3_1.stundenplan.ui.Screen
import de.wsp3_1.stundenplan.ui.TimetableViewModel
import de.wsp3_1.stundenplan.ui.UiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Der Zustandsautomat (SPEZIFIKATION 5.1): Erststart -> NoServerConfigured, ohne Token ->
 * LoginRequired, Online -> Data, Server aus mit Cache -> Data(offline), ohne Cache -> Error,
 * 401 -> Token weg + LoginRequired, 404 -> Error; dazu Navigation und Zurueck-Taste.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModelTest {

    private val today = LocalDate.of(2026, 9, 23) // Mittwoch der Leitszenario-Woche
    private val monday = LocalDate.of(2026, 9, 21)
    private lateinit var settings: FakeSettings
    private lateinit var repository: FakeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settings = FakeSettings(serverUrl = "http://10.0.2.2:1500", token = "t", displayName = "Meyer")
        repository = FakeRepository(settings)
        repository.weekResults[monday] = WeekResult.Online(SampleWeek.week)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TimetableViewModel(settings, repository, today = { today }, clock = { 555L })

    @Test
    fun `ohne Serveradresse springt die App in die Einstellungen`() = runTest {
        settings.serverUrlFlow.value = null
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        assertEquals(UiState.NoServerConfigured, vm.uiState.value)
        assertEquals(Screen.Settings, vm.screen.value)
        assertEquals(Defaults.DEFAULT_SERVER_URL, vm.settingsState.value.serverUrlInput)
        assertFalse(vm.back()) // Zurueck verlaesst die App, es gibt nichts darunter
    }

    @Test
    fun `ohne Token verlangt die App die Anmeldung`() = runTest {
        settings.tokenFlow.value = null
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        assertEquals(UiState.LoginRequired(), vm.uiState.value)
        assertEquals(Screen.Settings, vm.screen.value)
        assertFalse(vm.settingsState.value.loggedIn)
    }

    @Test
    fun `Online liefert Data mit Raster, Zeitstempel und Zaehler Heute geaendert`() = runTest {
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        val state = vm.uiState.value as UiState.Data
        assertEquals(SampleWeek.week, state.week)
        assertEquals(SampleWeek.timegrid, state.timegrid)
        assertFalse(state.offline)
        assertEquals(555L, state.loadedAtEpochMillis)
        assertEquals(2, state.changedToday) // Mi 23.09.: MOVED + SUBSTITUTED (Raum)
        assertEquals(Screen.Day(today), vm.screen.value)
        assertEquals("Meyer", vm.settingsState.value.displayName)
    }

    @Test
    fun `Server aus mit Cache liefert Data offline mit altem Zeitstempel`() = runTest {
        repository.weekResults[monday] = WeekResult.Offline
        repository.cached[monday] = CachedWeek(111L, SampleWeek.week)
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        val state = vm.uiState.value as UiState.Data
        assertTrue(state.offline)
        assertEquals(111L, state.loadedAtEpochMillis)
        assertEquals(SampleWeek.week, state.week)
    }

    @Test
    fun `Server aus ohne Cache ist ein Fehler mit verstaendlichem Text`() = runTest {
        repository.weekResults[monday] = WeekResult.Offline
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        assertEquals(UiState.Error(Message.UnreachableNoCache), vm.uiState.value)
    }

    @Test
    fun `401 loescht Token und Offline-Stand und fuehrt zur Anmeldung, nicht zu einem Fehler`() = runTest {
        repository.weekResults[monday] = WeekResult.Unauthorized
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        assertEquals(UiState.LoginRequired(LoginReason.SESSION_EXPIRED), vm.uiState.value)
        assertEquals(Screen.Settings, vm.screen.value)
        assertNull(settings.tokenFlow.value)
        assertNull(vm.settingsState.value.displayName)
        // Sonst sieht das naechste Konto am selben Geraet den Plan des vorigen (A-1).
        assertEquals(1, repository.clearCacheCalls)
    }

    @Test
    fun `die zuletzt angeforderte Woche gewinnt, auch wenn die erste spaeter antwortet`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gates[monday] = gate                                   // erste Woche haengt
        repository.weekResults[monday.plusDays(7)] = WeekResult.Online(SampleWeek.nextWeek)
        val vm = viewModel()

        vm.refresh()                                                      // wartet an der Sperre
        vm.showDay(LocalDate.of(2026, 9, 28)); advanceUntilIdle()         // zweite Woche kommt durch
        assertEquals(SampleWeek.nextWeek, (vm.uiState.value as UiState.Data).week)

        gate.complete(Unit); advanceUntilIdle()                           // erste Antwort trifft verspaetet ein
        assertEquals(SampleWeek.nextWeek, (vm.uiState.value as UiState.Data).week)
        assertEquals(Screen.Day(LocalDate.of(2026, 9, 28)), vm.screen.value)
        assertFalse(vm.refreshing.value)
    }

    @Test
    fun `andere Serveradresse meldet ab, loescht den Offline-Stand und entfernt ein getipptes api`() = runTest {
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        assertTrue(vm.uiState.value is UiState.Data)

        vm.onServerUrlChange("http://134.245.1.240:1500/api")
        vm.saveServerUrl(); advanceUntilIdle()

        assertEquals("http://134.245.1.240:1500", settings.serverUrlFlow.value)
        assertEquals("http://134.245.1.240:1500", vm.settingsState.value.savedServerUrl)
        assertNull(settings.tokenFlow.value)
        assertNull(vm.settingsState.value.displayName)
        assertEquals(1, repository.clearCacheCalls)
        assertEquals(UiState.LoginRequired(), vm.uiState.value)
        assertEquals(Screen.Settings, vm.screen.value)
    }

    @Test
    fun `dieselbe Adresse erneut speichern behaelt die Anmeldung`() = runTest {
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        vm.onServerUrlChange("http://10.0.2.2:1500")
        vm.saveServerUrl(); advanceUntilIdle()
        assertEquals("t", settings.tokenFlow.value)
        assertEquals(0, repository.clearCacheCalls)
        assertTrue(vm.uiState.value is UiState.Data)
    }

    @Test
    fun `404 heisst Konto ohne Lehrkraft`() = runTest {
        repository.weekResults[monday] = WeekResult.NoTeacher
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        assertEquals(UiState.Error(Message.NotATeacher), vm.uiState.value)
    }

    @Test
    fun `Serverfehler traegt den detail-Text`() = runTest {
        repository.weekResults[monday] = WeekResult.Failure("Datenbank nicht erreichbar.")
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        assertEquals(UiState.Error(Message.ServerText("Datenbank nicht erreichbar.")), vm.uiState.value)
    }

    @Test
    fun `Tageswechsel innerhalb der Woche laedt nicht neu, ueber die Grenze schon`() = runTest {
        repository.weekResults[monday.plusDays(7)] = WeekResult.Online(SampleWeek.nextWeek)
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        assertEquals(listOf(monday), repository.loadedMondays)

        vm.nextDay(); advanceUntilIdle()
        assertEquals(Screen.Day(LocalDate.of(2026, 9, 24)), vm.screen.value)
        assertEquals(listOf(monday), repository.loadedMondays)

        vm.showDay(LocalDate.of(2026, 9, 28)); advanceUntilIdle()
        assertEquals(listOf(monday, monday.plusDays(7)), repository.loadedMondays)
        assertEquals(SampleWeek.nextWeek, (vm.uiState.value as UiState.Data).week)

        vm.showToday(); advanceUntilIdle()
        assertEquals(Screen.Day(today), vm.screen.value)
        assertEquals(SampleWeek.week, (vm.uiState.value as UiState.Data).week)
    }

    @Test
    fun `Woche, Detail und Zurueck-Taste`() = runTest {
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()

        vm.showWeek()
        assertEquals(Screen.Week(monday), vm.screen.value)

        vm.openDetail(SampleWeek.givenAway)
        assertEquals(Screen.Detail(SampleWeek.givenAway, Screen.Week(monday)), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(Screen.Week(monday), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(Screen.Day(today), vm.screen.value)

        vm.openSettings()
        assertTrue(vm.back()) // angemeldet: zurueck zum Tag
        assertEquals(Screen.Day(today), vm.screen.value)

        assertFalse(vm.back()) // Tagesansicht: System uebernimmt
    }

    @Test
    fun `Wochenwechsel laedt die neue Woche`() = runTest {
        repository.weekResults[monday.plusDays(7)] = WeekResult.Online(SampleWeek.nextWeek)
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        vm.showWeek()
        vm.nextWeek(); advanceUntilIdle()
        assertEquals(Screen.Week(monday.plusDays(7)), vm.screen.value)
        assertEquals(SampleWeek.nextWeek, (vm.uiState.value as UiState.Data).week)
        assertEquals(0, (vm.uiState.value as UiState.Data).changedToday)
    }

    @Test
    fun `Anmeldung mit falschem Passwort zeigt den Text, mit richtigem laedt sie den Plan`() = runTest {
        settings.tokenFlow.value = null
        settings.displayNameFlow.value = null
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        assertEquals(Screen.Settings, vm.screen.value)

        vm.login(); advanceUntilIdle()
        assertEquals(LoginError.EmptyFields, vm.settingsState.value.loginError)

        vm.onUsernameChange("meyer")
        vm.onPasswordChange("falsch")
        repository.loginResult = LoginResult.WrongCredentials
        vm.login(); advanceUntilIdle()
        assertEquals(LoginError.WrongCredentials, vm.settingsState.value.loginError)
        assertEquals(Screen.Settings, vm.screen.value)

        vm.onPasswordChange("richtig")
        repository.loginResult = LoginResult.Success(LoginResponse("tok", "TEACHER", "Meyer", 1))
        vm.login(); advanceUntilIdle()
        assertNull(vm.settingsState.value.loginError)
        assertEquals("Meyer", vm.settingsState.value.displayName)
        assertEquals("", vm.settingsState.value.password)
        assertEquals(Screen.Day(today), vm.screen.value)
        assertTrue(vm.uiState.value is UiState.Data)
    }

    @Test
    fun `Abmelden fuehrt zurueck zur Anmeldung`() = runTest {
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        vm.logout(); advanceUntilIdle()
        assertEquals(1, repository.logoutCalls)
        assertEquals(UiState.LoginRequired(), vm.uiState.value)
        assertEquals(Screen.Settings, vm.screen.value)
        assertNull(settings.tokenFlow.value)
    }

    @Test
    fun `Serveradresse speichern ohne Schema wird abgelehnt, mit Schema uebernommen`() = runTest {
        settings.serverUrlFlow.value = null
        settings.tokenFlow.value = null
        val vm = viewModel()
        vm.refresh(); advanceUntilIdle()
        vm.onServerUrlChange("192.0.2.1:9999")
        vm.saveServerUrl(); advanceUntilIdle()
        assertNull(settings.serverUrlFlow.value)

        vm.onServerUrlChange("http://192.0.2.1:9999")
        vm.saveServerUrl(); advanceUntilIdle()
        assertEquals("http://192.0.2.1:9999", settings.serverUrlFlow.value)
        assertTrue(vm.settingsState.value.savedNotice)
        assertEquals(UiState.LoginRequired(), vm.uiState.value)
    }
}
