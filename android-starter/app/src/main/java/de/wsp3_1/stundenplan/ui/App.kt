package de.wsp3_1.stundenplan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wsp3_1.stundenplan.data.ThemeMode
import java.time.LocalDate

/**
 * Wurzel der Oberflaeche: when (screen) ueber den Screen-Zustand des ViewModels.
 * Kein Navigation-Compose; die Zurueck-Taste ist ein BackHandler mit viewModel.back().
 */
@Composable
fun App(viewModel: TimetableViewModel, themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val absences by viewModel.absences.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val substitutionState by viewModel.substitutionState.collectAsStateWithLifecycle()

    // Abruf bei jedem Start bzw. jeder Rueckkehr in den Vordergrund (kein Push, kein WorkManager).
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    val backEnabled = when (screen) {
        is Screen.Day -> false
        Screen.Settings -> uiState !is UiState.NoServerConfigured && uiState !is UiState.LoginRequired
        is Screen.Week, is Screen.Detail -> true
        is Screen.Absence -> true
        Screen.Substitute -> true
    }
    BackHandler(enabled = backEnabled) { viewModel.back() }

    val base = screen.let { if (it is Screen.Detail) it.from else it }
    when (base) {
        Screen.Substitute -> SubstituteScreen(
            state = substitutionState,
            onDay = { viewModel.back() },
            onWeek = viewModel::showWeek,
            onAbsence = viewModel::openAbsence,
            onSettings = viewModel::openSettings,
            onAnswer = viewModel::answerSubstitution,
            onRefresh = viewModel::loadSubstitutionRequests,
        )
        Screen.Settings -> SettingsScreen(
            state = settingsState,
            uiState = uiState,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onServerUrlChange = viewModel::onServerUrlChange,
            onCheck = viewModel::checkConnection,
            onSave = viewModel::saveServerUrl,
            onUsernameChange = viewModel::onUsernameChange,
            onPasswordChange = viewModel::onPasswordChange,
            onLogin = viewModel::login,
            onLogout = viewModel::logout,
            onBack = { viewModel.back() },
        )

        is Screen.Day -> DayScreen(
            uiState = uiState,
            date = base.date,
            today = LocalDate.now(),
            refreshing = refreshing,
            isTeacher = settingsState.role == "TEACHER",
            onPrev = viewModel::previousDay,
            onNext = viewModel::nextDay,
            onToday = viewModel::showToday,
            onWeek = viewModel::showWeek,
            onAbsence = viewModel::openAbsence,
            onRefresh = viewModel::refresh,
            onEntry = viewModel::openDetail,
            onSettings = viewModel::openSettings,
            onPickDate = viewModel::showDay,
            onSubstitute = viewModel::openSubstitute,
        )

        is Screen.Week -> WeekScreen(
            uiState = uiState,
            monday = base.monday,
            today = LocalDate.now(),
            isTeacher = settingsState.role == "TEACHER",
            refreshing = refreshing,
            onPrev = viewModel::previousWeek,
            onNext = viewModel::nextWeek,
            onDay = viewModel::showDay,
            onAbsence = viewModel::openAbsence,
            onRefresh = viewModel::refresh,
            onEntry = viewModel::openDetail,
            onSettings = viewModel::openSettings,
            onPickDate = viewModel::selectWeek,
            onSubstitute = viewModel::openSubstitute,
        )

        is Screen.Absence -> AbsenceScreen(
            monday = base.monday,
            today = LocalDate.now(),
            isTeacher = settingsState.role == "TEACHER",
            absences = absences,
            onDay = viewModel::showDay,
            onWeek = viewModel::showWeek,
            onSettings = viewModel::openSettings,
            onSubstitute = viewModel::openSubstitute,
            onSendAbsence = { fromDate, toDate, reason ->
                viewModel.sendAbsence(
                    dateFrom = fromDate,
                    dateTo = toDate,
                    reason = reason,
                )
            }
        )

        is Screen.Detail -> Unit // kommt nicht vor: base ist nie ein Detail
    }

    val detail = screen
    if (detail is Screen.Detail) {
        val data = uiState as? UiState.Data
        EntryDetailSheet(
            entry = detail.entry,
            timegrid = data?.timegrid,
            onDismiss = { viewModel.back() },
        )
    }
}
