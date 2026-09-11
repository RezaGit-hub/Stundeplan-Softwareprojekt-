package de.wsp3_1.stundenplan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/**
 * Wurzel der Oberflaeche: when (screen) ueber den Screen-Zustand des ViewModels.
 * Kein Navigation-Compose; die Zurueck-Taste ist ein BackHandler mit viewModel.back().
 */
@Composable
fun App(viewModel: TimetableViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    // Abruf bei jedem Start bzw. jeder Rueckkehr in den Vordergrund (kein Push, kein WorkManager).
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    val backEnabled = when (screen) {
        is Screen.Day -> false
        Screen.Settings -> uiState !is UiState.NoServerConfigured && uiState !is UiState.LoginRequired
        is Screen.Week, is Screen.Detail -> true
    }
    BackHandler(enabled = backEnabled) { viewModel.back() }

    val base = screen.let { if (it is Screen.Detail) it.from else it }
    when (base) {
        Screen.Settings -> SettingsScreen(
            state = settingsState,
            uiState = uiState,
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
            onPrev = viewModel::previousDay,
            onNext = viewModel::nextDay,
            onToday = viewModel::showToday,
            onWeek = viewModel::showWeek,
            onRefresh = viewModel::refresh,
            onEntry = viewModel::openDetail,
            onSettings = viewModel::openSettings,
            onPickDate = viewModel::showDay,

        )

        is Screen.Week -> WeekScreen(
            uiState = uiState,
            monday = base.monday,
            today = LocalDate.now(),
            refreshing = refreshing,
            onPrev = viewModel::previousWeek,
            onNext = viewModel::nextWeek,
            onDay = viewModel::showDay,
            onRefresh = viewModel::refresh,
            onEntry = viewModel::openDetail,
            onSettings = viewModel::openSettings,
            onPickDate = viewModel::selectWeek,
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
