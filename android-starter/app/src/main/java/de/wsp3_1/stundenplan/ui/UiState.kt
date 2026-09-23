package de.wsp3_1.stundenplan.ui

import de.wsp3_1.stundenplan.data.EntryDto
import de.wsp3_1.stundenplan.data.TimegridDto
import de.wsp3_1.stundenplan.data.WeekDto
import java.time.LocalDate

/**
 * Die fuenf Zustaende der App (backbone/android.md, SPEZIFIKATION 5.1). Weil der Compiler jeden
 * Zweig einfordert, kann niemand vergessen, Erststart, Offline-Betrieb oder 401 darzustellen.
 * Offline ist bewusst kein eigener Zustand: es ist [Data] mit offline = true - die Liste bleibt
 * bedienbar, oben steht ein Banner. Erst ohne Cache wird daraus [Error].
 */
sealed interface UiState {
    data object Loading : UiState

    data class Data(
        val week: WeekDto,
        val timegrid: TimegridDto?,
        /** Zeitpunkt des letzten erfolgreichen Abrufs (Geraetezeit, Epoche in ms) - auch offline. */
        val loadedAtEpochMillis: Long,
        /** true = Stand aus dem Cache, Server nicht erreichbar. */
        val offline: Boolean,
        /** Eintraege mit date == heute und kind != REGULAR (Soll-Kriterium, Hinweis beim Abruf). */
        val changedToday: Int,
    ) : UiState

    data object NoServerConfigured : UiState

    /** Erststart ohne Token, Abmelden oder 401. */
    data class LoginRequired(val reason: LoginReason? = null) : UiState

    data class Error(val message: Message) : UiState
}

enum class LoginReason { SESSION_EXPIRED }

/** Fehlertexte in der Sprache des Nutzers - aufgeloest erst in der Oberflaeche (strings.xml). */
sealed interface Message {
    /** Server nicht erreichbar und kein gespeicherter Stand. */
    data object UnreachableNoCache : Message

    /** 404 - das Konto gehoert zu keiner Lehrkraft (z. B. Planungskonto). */
    data object NotATeacher : Message

    /** detail-Text des Servers (deutsch), falls vorhanden; sonst allgemeiner Serverfehler. */
    data class ServerText(val text: String?) : Message
}

/** Bildschirmwechsel als Zustand im ViewModel - keine Navigations-Bibliothek. */
sealed interface Screen {
    data object Settings : Screen
    data object Substitute : Screen
    data class Absence(val monday: LocalDate) : Screen
    data class Day(val date: LocalDate) : Screen
    data class Week(val monday: LocalDate) : Screen
    /** Stundendetail als Bottom Sheet ueber [from]. */
    data class Detail(val entry: EntryDto, val from: Screen) : Screen
}

/** Zustand des Einstellungs-Screens (Serveradresse, Verbindungstest, Anmeldung). */
data class SettingsUiState(
    val serverUrlInput: String = "",
    val savedServerUrl: String? = null,
    val check: CheckState = CheckState.Idle,
    val savedNotice: Boolean = false,
    val username: String = "",
    val password: String = "",
    val loginRunning: Boolean = false,
    val loginError: LoginError? = null,
    /** Anzeigename der angemeldeten Lehrkraft; null = nicht angemeldet. */
    val displayName: String? = null,
    val role: String? = null,
) {
    val loggedIn: Boolean get() = displayName != null
}

sealed interface CheckState {
    data object Idle : CheckState
    data object Running : CheckState
    data class Reachable(val schoolName: String?) : CheckState
    data object Unreachable : CheckState
    data object InvalidUrl : CheckState
}

sealed interface LoginError {
    data object WrongCredentials : LoginError
    data object Unreachable : LoginError
    data object NoServer : LoginError
    data object EmptyFields : LoginError
    data class ServerText(val text: String?) : LoginError
}
