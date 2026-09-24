package de.wsp3_1.stundenplan

import de.wsp3_1.stundenplan.data.Absence
import de.wsp3_1.stundenplan.data.AppSettings
import de.wsp3_1.stundenplan.data.CachedWeek
import de.wsp3_1.stundenplan.data.DayDto
import de.wsp3_1.stundenplan.data.EntryDto
import de.wsp3_1.stundenplan.data.EntryKind
import de.wsp3_1.stundenplan.data.EntryOriginal
import de.wsp3_1.stundenplan.data.HealthResult
import de.wsp3_1.stundenplan.data.LoginResult
import de.wsp3_1.stundenplan.data.NewAbsenceRequest
import de.wsp3_1.stundenplan.data.ThemeMode
import de.wsp3_1.stundenplan.data.TimegridDto
import de.wsp3_1.stundenplan.data.TimeslotDto
import de.wsp3_1.stundenplan.data.TimetableRepository
import de.wsp3_1.stundenplan.data.WeekDto
import de.wsp3_1.stundenplan.data.WeekResult
import de.wsp3_1.stundenplan.ui.ThemePreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * Testfixture nach dem Leitszenario (Woche ab Mo 21.09.2026, Lehrkraft MEY): 1. Stunde abgegeben,
 * drei Stunden noch nicht vertreten, Di 1. verlegt auf Mi 1.; dazu je eine Stunde SUBSTITUTED
 * (nur Raum), SUBSTITUTED (Lehrkraft) und ADDED, damit alle sieben Arten vorkommen.
 */
object SampleWeek {
    val monday: LocalDate = LocalDate.of(2026, 9, 21)

    private fun entry(
        date: String,
        day: String,
        position: Int,
        subject: String = "Ma",
        clazz: String = "7a",
        room: String? = "R101",
        teacher: String? = "MEY",
        kind: EntryKind = EntryKind.REGULAR,
        original: EntryOriginal? = null,
        reasons: List<String> = emptyList(),
        lessonId: Long? = 1,
    ) = EntryDto(
        date = date,
        timeslotId = (position + 7 * (listOf("MO", "TU", "WE", "TH", "FR").indexOf(day))).toLong(),
        day = day,
        position = position,
        lessonId = lessonId,
        classId = 1,
        classShort = clazz,
        subjectShort = subject,
        subjectColor = "#1976D2",
        teacherId = 1,
        teacherShort = teacher,
        roomId = 1,
        roomShort = room,
        kind = kind,
        original = original,
        reasons = reasons,
        exceptionId = null,
    )

    val givenAway = entry(
        "2026-09-21", "MO", 1, teacher = "SCZ", room = "R204", kind = EntryKind.GIVEN_AWAY,
        original = EntryOriginal(teacherId = 1, teacherShort = "MEY", roomId = 1, roomShort = "R101"),
        reasons = listOf("Krankmeldung", "Wasserschaden"),
    )
    val needsSubstitution = entry(
        "2026-09-21", "MO", 2, clazz = "8b", room = "R204", kind = EntryKind.NEEDS_SUBSTITUTION,
        reasons = listOf("Krankmeldung"),
    )
    val cancelledMoved = entry(
        "2026-09-22", "TU", 1, kind = EntryKind.CANCELLED, reasons = listOf("verlegt → Mi 1."),
    )
    val moved = entry(
        "2026-09-23", "WE", 1, kind = EntryKind.MOVED,
        original = EntryOriginal(date = "2026-09-22", position = 1),
    )
    val substitutedRoomOnly = entry(
        "2026-09-23", "WE", 4, subject = "Ph", room = "P2", kind = EntryKind.SUBSTITUTED,
        original = EntryOriginal(roomId = 3, roomShort = "P1"), reasons = listOf("Wasserschaden"),
    )
    val substitutedTeacher = entry(
        "2026-09-24", "TH", 2, clazz = "9c", room = "R204", kind = EntryKind.SUBSTITUTED,
        original = EntryOriginal(teacherId = 2, teacherShort = "SCZ"), reasons = listOf("Fortbildung"),
    )
    val added = entry(
        "2026-09-25", "FR", 6, subject = "Sp", room = "TH1", kind = EntryKind.ADDED, lessonId = null,
        reasons = listOf("Zusatzstunde"),
    )
    val cancelled = entry("2026-09-25", "FR", 2, kind = EntryKind.CANCELLED, reasons = listOf("Ausflug"))
    val regular = entry("2026-09-21", "MO", 4, subject = "Ph", room = "P1")

    val week = WeekDto(
        monday = "2026-09-21",
        days = listOf(
            DayDto("2026-09-21", true, listOf(
                givenAway, needsSubstitution, regular,
                entry("2026-09-21", "MO", 5, clazz = "8b", room = "R204", kind = EntryKind.NEEDS_SUBSTITUTION, reasons = listOf("Krankmeldung")),
                entry("2026-09-21", "MO", 6, clazz = "9c", kind = EntryKind.NEEDS_SUBSTITUTION, reasons = listOf("Krankmeldung")),
            )),
            DayDto("2026-09-22", true, listOf(cancelledMoved)),
            DayDto("2026-09-23", true, listOf(moved, substitutedRoomOnly)),
            DayDto("2026-09-24", true, listOf(substitutedTeacher)),
            DayDto("2026-09-25", true, listOf(cancelled, added)),
        ),
    )

    /** Regelwoche danach (28.09.) ohne Aenderungen. */
    val nextWeek = WeekDto(
        monday = "2026-09-28",
        days = listOf(
            DayDto("2026-09-28", true, listOf(entry("2026-09-28", "MO", 1), entry("2026-09-28", "MO", 4, subject = "Ph", room = "P1"))),
            DayDto("2026-09-29", true, listOf(entry("2026-09-29", "TU", 1))),
            DayDto("2026-09-30", true, emptyList()),
            DayDto("2026-10-01", true, emptyList()),
            DayDto("2026-10-02", false, emptyList()),
        ),
    )

    val timegrid = TimegridDto(
        slots = listOf("MO", "TU", "WE", "TH", "FR").flatMapIndexed { d, day ->
            listOf(
                TimeslotDto((d * 7 + 1).toLong(), day, 1, "08:00", "08:45", false),
                TimeslotDto((d * 7 + 2).toLong(), day, 2, "08:50", "09:35", false),
                TimeslotDto((d * 7 + 3).toLong(), day, 3, "09:35", "09:55", true),
                TimeslotDto((d * 7 + 4).toLong(), day, 4, "09:55", "10:40", false),
                TimeslotDto((d * 7 + 5).toLong(), day, 5, "10:45", "11:30", false),
                TimeslotDto((d * 7 + 6).toLong(), day, 6, "11:50", "12:35", false),
                TimeslotDto((d * 7 + 7).toLong(), day, 7, "12:40", "13:25", false),
            )
        },
    )
}

/** Einstellungen im Speicher statt im DataStore. */
class FakeSettings(
    serverUrl: String? = null,
    token: String? = null,
    displayName: String? = null,
    role: String? = null,
) : AppSettings {
    val serverUrlFlow = MutableStateFlow(serverUrl)
    val tokenFlow = MutableStateFlow(token)
    val displayNameFlow = MutableStateFlow(displayName)
    val roleFlow = MutableStateFlow(role)

    override val serverUrl: Flow<String?> get() = serverUrlFlow
    override val token: Flow<String?> get() = tokenFlow
    override val displayName: Flow<String?> get() = displayNameFlow
    override val role: Flow<String?> get() = roleFlow

    override suspend fun setServerUrl(url: String) { serverUrlFlow.value = url.trim() }

    override suspend fun saveLogin(token: String, displayName: String, role: String) {
        tokenFlow.value = token
        displayNameFlow.value = displayName
        roleFlow.value = role
    }

    override suspend fun clearToken() {
        tokenFlow.value = null
        displayNameFlow.value = null
        roleFlow.value = null
    }
}

/** Theme-Attrappe fuer JVM-Tests: haelt die Auswahl im Speicher. */
class FakeThemePreferences : ThemePreferences {
    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeMode: Flow<ThemeMode> = _mode
    override suspend fun saveThemeMode(themeMode: ThemeMode) { _mode.value = themeMode }
}

/** Repository-Attrappe fuer den ViewModel-Test: Antworten je Montag vorgeben. */
class FakeRepository(private val settings: FakeSettings) : TimetableRepository {
    var health: HealthResult = HealthResult.Reachable("Gymnasium Musterstadt")
    var loginResult: LoginResult = LoginResult.WrongCredentials
    var weekResults: MutableMap<LocalDate, WeekResult> = mutableMapOf()
    var cached: MutableMap<LocalDate, CachedWeek> = mutableMapOf()
    var timegrid: TimegridDto? = SampleWeek.timegrid
    val loadedMondays = mutableListOf<LocalDate>()
    var logoutCalls = 0
    var clearCacheCalls = 0

    /** Antwort auf [sendNewAbsence]; Tests koennen sie ersetzen. */
    var sendAbsenceResult: (NewAbsenceRequest) -> Result<Absence> =
        { Result.success(Absence(LocalDate.parse(it.dateFrom), LocalDate.parse(it.dateTo), it.reason)) }

    /** Antwort auf [loadAbsences]; Tests koennen sie ersetzen. */
    var absencesResult: Result<List<Absence>> = Result.success(emptyList())

    /**
     * Sperre je Woche: solange sie offen ist, haengt `loadWeek` - so laesst sich eine verspaetete
     * Antwort nachstellen, ohne mit echten Zeiten zu arbeiten.
     */
    val gates: MutableMap<LocalDate, CompletableDeferred<Unit>> = mutableMapOf()

    override suspend fun checkHealth(baseUrl: String): HealthResult = health

    override suspend fun login(username: String, password: String): LoginResult {
        val result = loginResult
        if (result is LoginResult.Success) {
            settings.saveLogin(
                result.response.token,
                result.response.displayName,
                result.response.role,
            )
        }
        return result
    }

    override suspend fun logout() {
        logoutCalls++
        settings.clearToken()
    }

    override suspend fun loadWeek(monday: LocalDate): WeekResult {
        loadedMondays += monday
        gates[monday]?.await()
        return weekResults[monday] ?: WeekResult.Offline
    }

    override suspend fun loadClassWeek(monday: LocalDate): WeekResult {
        loadedMondays += monday
        gates[monday]?.await()
        return weekResults[monday] ?: WeekResult.Offline
    }

    override suspend fun cachedWeek(monday: LocalDate): CachedWeek? = cached[monday]

    override suspend fun loadTimegrid(): TimegridDto? = timegrid

    override suspend fun cachedTimegrid(): TimegridDto? = timegrid

    override suspend fun clearCache() {
        clearCacheCalls++
    }

    override suspend fun sendNewAbsence(
        token: String,
        absence: NewAbsenceRequest,
    ): Result<Absence> = sendAbsenceResult(absence)

    override suspend fun loadAbsences(): Result<List<Absence>> = absencesResult
}