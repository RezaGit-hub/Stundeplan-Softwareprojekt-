package de.wsp3_1.stundenplan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wsp3_1.stundenplan.AppContainer
import de.wsp3_1.stundenplan.Defaults
import de.wsp3_1.stundenplan.data.ApiFactory
import de.wsp3_1.stundenplan.data.AppSettings
import de.wsp3_1.stundenplan.data.EntryDto
import de.wsp3_1.stundenplan.data.HealthResult
import de.wsp3_1.stundenplan.data.LoginResult
import de.wsp3_1.stundenplan.data.PlanDates
import de.wsp3_1.stundenplan.data.ThemeMode
import de.wsp3_1.stundenplan.data.TimetableRepository
import de.wsp3_1.stundenplan.data.WeekResult
import de.wsp3_1.stundenplan.data.SubstitutionResult
import de.wsp3_1.stundenplan.data.SubstitutionRequestStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Das eine ViewModel der App (backbone/android.md). Haelt drei Zustaende: den Bildschirm
 * ([Screen]), den Datenzustand ([UiState]) und das Formular der Einstellungen ([SettingsUiState]).
 * Die Oberflaeche kennt kein Retrofit: sie bekommt Zustand und schickt Ereignisse zurueck.
 */
class TimetableViewModel(
    private val settings: AppSettings,
    private val repository: TimetableRepository,
    private val themePreferences: ThemePreferences,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Day(today()))
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** true, waehrend eine Woche neu geladen wird und alte Daten stehen bleiben (Pull-to-refresh). */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Zuletzt gezeigter Tag - bleibt beim Wechsel in Woche, Detail oder Einstellungen erhalten. */
    private var dayDate: LocalDate = today()

    /**
     * Es laeuft hoechstens ein Abruf. Jeder bekommt eine Nummer; nur der mit der hoechsten darf
     * Zustand schreiben. Ohne das gewinnt beim schnellen Blaettern die zuletzt *fertige* Antwort
     * statt der zuletzt *angeforderten*, und die Tagesansicht zeigt zur falschen Woche
     * "Unterrichtsfrei". Abmelden und Adresswechsel zaehlen die Nummer ebenfalls hoch - eine
     * verspaetete Antwort darf nach dem Abmelden nichts mehr anzeigen oder speichern.
     */
    private var loadJob: Job? = null
    private var loadTicket: Int = 0

    private var serverCheckJob: Job? = null
    private var substitutionJob: Job? = null
    private val _substitutionState = MutableStateFlow(SubstitutionUiState())
    val substitutionState: StateFlow<SubstitutionUiState> = _substitutionState.asStateFlow()

    fun openSubstitute() {
        if (_settingsState.value.role != "TEACHER") return
        _screen.value = Screen.Substitute
        loadSubstitutionRequests()
    }

    fun loadSubstitutionRequests() {
        if (_settingsState.value.role != "TEACHER" || substitutionJob?.isActive == true) return
        substitutionJob = viewModelScope.launch {
            _substitutionState.update { it.copy(loading = true, error = null) }
            when (val result = repository.loadSubstitutionRequests()) {
                is SubstitutionResult.Success -> _substitutionState.update {
                    it.copy(loading = false, requests = result.value.filter { request -> request.status == SubstitutionRequestStatus.UNKNOWN })
                }
                is SubstitutionResult.Failure -> handleSubstitutionFailure(result)
            }
        }
    }

    fun answerSubstitution(id: Long, status: SubstitutionRequestStatus) {
        if (_settingsState.value.role != "TEACHER" || substitutionJob?.isActive == true ||
            status == SubstitutionRequestStatus.UNKNOWN || _substitutionState.value.requests.none { it.id == id }) return
        substitutionJob = viewModelScope.launch {
            _substitutionState.update { it.copy(answeringId = id, error = null, notice = null) }
            when (val result = repository.answerSubstitutionRequest(id, status)) {
                is SubstitutionResult.Success -> _substitutionState.update {
                    it.copy(answeringId = null, requests = it.requests.filterNot { request -> request.id == id },
                        notice = if (status == SubstitutionRequestStatus.ACCEPTED) "Anfrage angenommen." else "Anfrage abgelehnt.")
                }
                is SubstitutionResult.Failure -> handleSubstitutionFailure(result)
            }
        }
    }

    private suspend fun handleSubstitutionFailure(failure: SubstitutionResult.Failure) {
        if (failure.code == 401) {
            loadTicket++
            loadJob?.cancelAndJoin()
            settings.clearToken()
            repository.clearCache()
            _substitutionState.value = SubstitutionUiState()
            _settingsState.update { it.copy(displayName = null, role = null) }
            _uiState.value = UiState.LoginRequired(LoginReason.SESSION_EXPIRED)
            _screen.value = Screen.Settings
        } else {
            _substitutionState.update { it.copy(loading = false, answeringId = null, error = failure.message) }
        }
    }

    init {
        viewModelScope.launch {
            themePreferences.themeMode.collect { mode ->
                _themeMode.value = mode
            }
        }

        viewModelScope.launch {
            loadSettingsIntoForm()
        }
    }

    private suspend fun loadSettingsIntoForm() {
        val savedUrl = settings.serverUrl.first()
        val token = settings.token.first()
        val name = settings.displayName.first()
        val role = settings.role.first()

        _settingsState.update {
            it.copy(
                serverUrlInput = savedUrl ?: Defaults.DEFAULT_SERVER_URL,
                savedServerUrl = savedUrl,
                displayName = if (token != null) name else null,
                role = if (token != null) role else null
            )
        }
    }

    // ---------------------------------------------------------------- Abruf

    /** Bei onStart, nach Anmeldung, beim Ziehen und beim Wechsel ueber eine Wochengrenze. */
    fun refresh() {
        if (_screen.value == Screen.Substitute) {
            loadSubstitutionRequests()
            return
        }
        startLoad()
    }

    /** Neuen Abruf beginnen und den laufenden abbrechen - es zaehlt immer der neueste. */
    private fun startLoad() {
        val ticket = ++loadTicket
        loadJob?.cancel()
        loadJob = viewModelScope.launch { refreshNow(ticket) }
    }

    /** Laufende Abrufe entwerten und abwarten - danach schreibt keine alte Antwort mehr. */
    private suspend fun stopLoads() {
        substitutionJob?.cancelAndJoin()
        substitutionJob = null
        _substitutionState.value = SubstitutionUiState()
        loadTicket++
        loadJob?.cancelAndJoin()
        loadJob = null
    }

    private suspend fun refreshNow(ticket: Int) {
        val url = settings.serverUrl.first()
        if (url == null) {
            _uiState.value = UiState.NoServerConfigured
            _screen.value = Screen.Settings
            return
        }
        val token = settings.token.first()
        if (token == null) {
            _uiState.value = UiState.LoginRequired()
            _screen.value = Screen.Settings
            return
        }
        val monday = PlanDates.mondayOf(dayDate)
        val current = _uiState.value
        val keepOld = current is UiState.Data && current.week.monday == monday.toString()
        if (!keepOld) _uiState.value = UiState.Loading
        _refreshing.value = true
        val now = today()

        val role = settings.role.first()

        try {
            val result = if (role == "STUDENT") {
                repository.loadClassWeek(monday)
            } else {
                repository.loadWeek(monday)
            }
            // Ist inzwischen eine andere Woche gefragt oder die Sitzung beendet, wird diese
            // Antwort verworfen - sie gehoert nicht mehr zu dem, was auf dem Schirm steht.
            if (!isCurrent(ticket, monday)) return
            val next = when (result) {
                is WeekResult.Online -> UiState.Data(
                    week = result.week,
                    timegrid = repository.loadTimegrid(),
                    loadedAtEpochMillis = clock(),
                    offline = false,
                    changedToday = WeekView.changedToday(result.week, now),
                )

                WeekResult.Offline -> {
                    val cached = repository.cachedWeek(monday)
                    if (cached != null) {
                        UiState.Data(
                            week = cached.week,
                            timegrid = repository.cachedTimegrid(),
                            loadedAtEpochMillis = cached.savedAtEpochMillis,
                            offline = true,
                            changedToday = WeekView.changedToday(cached.week, now),
                        )
                    } else {
                        UiState.Error(Message.UnreachableNoCache)
                    }
                }

                WeekResult.Unauthorized -> {
                    substitutionJob?.cancelAndJoin()
                    _substitutionState.value = SubstitutionUiState()
                    settings.clearToken()
                    // Der gespeicherte Stand gehoert zu einer Sitzung, die es nicht mehr gibt:
                    // sonst sieht das naechste Konto am Geraet den Plan des vorigen (A-1).
                    repository.clearCache()
                    _settingsState.update { it.copy(displayName = null, role = null) }
                    _screen.value = Screen.Settings
                    UiState.LoginRequired(LoginReason.SESSION_EXPIRED)
                }

                WeekResult.NoTeacher -> UiState.Error(Message.NotATeacher)

                is WeekResult.Failure -> UiState.Error(Message.ServerText(result.message))
            }
            if (!isCurrent(ticket, monday)) return
            _uiState.value = next
        } finally {
            if (ticket == loadTicket) _refreshing.value = false
        }
    }

    /** true, solange dieser Abruf der neueste ist und noch dieselbe Woche gezeigt wird. */
    private fun isCurrent(ticket: Int, monday: LocalDate): Boolean =
        ticket == loadTicket && monday == PlanDates.mondayOf(dayDate)

    // ---------------------------------------------------------------- Navigation

    // DayScreen
    fun showDay(date: LocalDate) {
        val crossesWeek = PlanDates.mondayOf(date) != PlanDates.mondayOf(dayDate) || !hasWeek(date)
        dayDate = date
        _screen.value = Screen.Day(date)
        if (crossesWeek) refresh()
    }

    fun nextDay() = showDay(dayDate.plusDays(1))

    fun previousDay() = showDay(dayDate.minusDays(1))

    fun showToday() = showDay(today())


    // WeekScreen
    fun showWeek() {
        _screen.value = Screen.Week(PlanDates.mondayOf(dayDate))
    }

    fun nextWeek() = shiftWeek(7)

    fun previousWeek() = shiftWeek(-7)

    fun selectWeek(date: LocalDate) {
        val crossesWeek = PlanDates.mondayOf(date) != PlanDates.mondayOf(dayDate)
        dayDate = date
        _screen.value = Screen.Week(PlanDates.mondayOf(date))
        if (crossesWeek) refresh()
    }

    private fun shiftWeek(days: Long) {
        val monday = PlanDates.mondayOf(dayDate).plusDays(days)
        dayDate = monday
        _screen.value = Screen.Week(monday)
        refresh()
    }

    fun openDetail(entry: EntryDto) {
        val from = _screen.value.let { if (it is Screen.Detail) it.from else it }
        _screen.value = Screen.Detail(entry, from)
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    // AbsenceScreen

    fun openAbsence() {
        _screen.value = Screen.Absence(PlanDates.mondayOf(dayDate))
    }

    /**
     * Zurueck-Taste. Detail -> darunterliegender Screen, Woche -> Tag, Einstellungen -> Tag (nur
     * angemeldet). Liefert false, wenn das System die Taste behandeln soll (App verlassen).
     */
    fun back(): Boolean = when (val s = _screen.value) {
        is Screen.Detail -> { _screen.value = s.from; true }
        Screen.Substitute -> { _screen.value = Screen.Day(dayDate); true }
        is Screen.Absence -> { _screen.value = Screen.Day(dayDate); true }
        is Screen.Week -> { _screen.value = Screen.Day(dayDate); true }
        Screen.Settings -> {
            val state = _uiState.value
            if (state is UiState.NoServerConfigured || state is UiState.LoginRequired) {
                false
            } else {
                _screen.value = Screen.Day(dayDate); true
            }
        }
        is Screen.Day -> false
    }

    private fun hasWeek(date: LocalDate): Boolean {
        val state = _uiState.value
        return state is UiState.Data && state.week.monday == PlanDates.mondayOf(date).toString()
    }

    // ---------------------------------------------------------------- Einstellungen

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode

        viewModelScope.launch {
            themePreferences.saveThemeMode(mode)
        }
    }

    fun onServerUrlChange(value: String) {
        _settingsState.update {
            it.copy(
                serverUrlInput = value,
                check = CheckState.Idle,
                savedNotice = false
            )
        }

        serverCheckJob?.cancel()
        val url = ApiFactory.stripApiSuffix(value)
        if (!ApiFactory.looksLikeHttpUrl(url)) {
            _settingsState.update { it.copy(check = CheckState.InvalidUrl) }
            return
        }
        serverCheckJob = viewModelScope.launch {
            delay(500)
            performCheck(url)
        }
    }

    fun checkConnection() {
        val url = ApiFactory.stripApiSuffix(_settingsState.value.serverUrlInput)
        if (!ApiFactory.looksLikeHttpUrl(url)) {
            _settingsState.update { it.copy(check = CheckState.InvalidUrl) }
            return
        }
        // Ein getipptes "/api" am Ende faellt weg; das Feld zeigt danach, was geprueft wurde.
        _settingsState.update {
            it.copy(
                serverUrlInput = url,
                savedNotice = false
            )
        }
        viewModelScope.launch { performCheck(url) }
    }

    private suspend fun performCheck(url: String) {
        _settingsState.update { it.copy(check = CheckState.Running) }
        val result = repository.checkHealth(url)
        _settingsState.update {
            it.copy(
                check = when (result) {
                    is HealthResult.Reachable -> CheckState.Reachable(result.schoolName)
                    HealthResult.Unreachable -> CheckState.Unreachable
                }
            )
        }
    }

    /**
     * Speichert auch eine Adresse, die den Test nicht bestanden hat (T-A3.1).
     *
     * Eine **andere** Adresse ist ein anderer Server: Token, Anzeigename und Offline-Stand gehoeren
     * zum vorigen und werden geloescht. Sonst ginge das Token an einen fremden Rechner und der
     * gespeicherte Plan bliebe unter einer neuen Anmeldung sichtbar.
     */
    fun saveServerUrl() {
        val url = ApiFactory.stripApiSuffix(_settingsState.value.serverUrlInput)
        if (!ApiFactory.looksLikeHttpUrl(url)) {
            _settingsState.update { it.copy(check = CheckState.InvalidUrl) }
            return
        }
        viewModelScope.launch {
            val previous = settings.serverUrl.first()
            val changed = previous != null && previous != url
            stopLoads()
            if (changed) {
                settings.clearToken()
                repository.clearCache()
            }
            settings.setServerUrl(url)
            _settingsState.update {
                it.copy(
                    serverUrlInput = url,
                    savedServerUrl = url,
                    savedNotice = true,
                    loginError = null,
                    displayName = if (changed) null else it.displayName,
                    password = if (changed) "" else it.password,
                )
            }
            if (changed) {
                _uiState.value = UiState.LoginRequired()
                _screen.value = Screen.Settings
                return@launch
            }
            if (_uiState.value is UiState.NoServerConfigured) _uiState.value = UiState.LoginRequired()
            if (settings.token.first() != null) refreshNow(++loadTicket)
        }
    }

    fun onUsernameChange(value: String) {
        _settingsState.update {
            it.copy(
                username = value,
                loginError = null
            )
        }
    }

    fun onPasswordChange(value: String) {
        _settingsState.update { it.copy(password = value, loginError = null) }
    }

    fun login() {
        val form = _settingsState.value
        val username = form.username.trim()
        if (username.isEmpty() || form.password.isEmpty()) {
            _settingsState.update { it.copy(loginError = LoginError.EmptyFields) }
            return
        }
        viewModelScope.launch {
            if (settings.serverUrl.first() == null) {
                _settingsState.update { it.copy(loginError = LoginError.NoServer) }
                return@launch
            }
            // Eine neue Anmeldung beendet die alte Sitzung: laufende Abrufe duerfen danach nichts
            // mehr in den Zustand oder in den Offline-Stand schreiben.
            stopLoads()
            _settingsState.update { it.copy(loginRunning = true, loginError = null) }
            when (val result = repository.login(username, form.password)) {
                is LoginResult.Success -> {
                    _settingsState.update {
                        it.copy(
                            loginRunning = false,
                            password = "",
                            displayName = result.response.displayName,
                            role = result.response.role
                        )
                    }

                    dayDate = today()
                    _screen.value = Screen.Day(dayDate)
                    _uiState.value = UiState.Loading
                    refreshNow(++loadTicket)
                }
                LoginResult.WrongCredentials ->
                    _settingsState.update { it.copy(loginRunning = false, loginError = LoginError.WrongCredentials) }
                LoginResult.Unreachable ->
                    _settingsState.update { it.copy(loginRunning = false, loginError = LoginError.Unreachable) }
                is LoginResult.Failure ->
                    _settingsState.update { it.copy(loginRunning = false, loginError = LoginError.ServerText(result.message)) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Erst den laufenden Abruf beenden, dann abmelden - sonst schreibt seine Antwort den
            // gerade geloeschten Offline-Stand wieder hin.
            stopLoads()
            repository.logout()
            _settingsState.update {
                it.copy(
                    displayName = null,
                    role = null,
                    username = "",
                    password = "",
                    loginError = null) }
            _uiState.value = UiState.LoginRequired()
            _screen.value = Screen.Settings
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TimetableViewModel(
                    container.settings,
                    container.repository,
                    container.themePreferences
                )
            }
        }
    }
}
