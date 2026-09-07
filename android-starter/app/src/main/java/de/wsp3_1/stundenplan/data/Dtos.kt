package de.wsp3_1.stundenplan.data

import kotlinx.serialization.Serializable

/**
 * Datenklassen zum API-Vertrag (api-contract/openapi.yaml, docs/VERTRAG.md).
 * Bezeichner englisch wie im Vertrag, von Hand geschrieben - es sind unter zehn.
 * Ueber die Leitung gehen nur Datum (yyyy-MM-dd), Uhrzeit (HH:mm) und Stundenposition,
 * nie ein Instant (Zeitzonen, backbone/android.md).
 */

/** GET /api/health */
@Serializable
data class HealthDto(
    val status: String,
    val schoolName: String? = null,
)

/** Fehlerantwort nach RFC 9457, wie das Backend sie liefert (application/problem+json). */
@Serializable
data class ProblemDetail(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
)

/** POST /api/auth/login - Anfrage */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

/** POST /api/auth/login - Antwort. role ist PLANNING oder TEACHER. */
@Serializable
data class LoginResponse(
    val token: String,
    val role: String,
    val displayName: String,
    val teacherId: Long? = null,
)

/** GET /api/me */
@Serializable
data class MeDto(
    val id: Long,
    val username: String,
    val role: String,
    val displayName: String,
    val teacherId: Long? = null,
)

/** Art eines Wocheneintrags (Vertrag: EntryKind). Die App loest nichts auf, sie faerbt und beschriftet nur. */
@Serializable
enum class EntryKind {
    REGULAR,
    NEEDS_SUBSTITUTION,
    SUBSTITUTED,
    GIVEN_AWAY,
    MOVED,
    CANCELLED,
    ADDED,
}

/** Nur die geaenderten Werte sind gesetzt: Vertretung (Lehrkraft/Raum), Verlegung (Datum/Position). */
@Serializable
data class EntryOriginal(
    val teacherId: Long? = null,
    val teacherShort: String? = null,
    val roomId: Long? = null,
    val roomShort: String? = null,
    val date: String? = null,
    val position: Int? = null,
)

/**
 * Eine Stunde an einem Kalendertag, fertig aufgeloest.
 *
 * `subjectName`, `roomName` und `teacherName` stehen **noch nicht** im Vertrag: das Pflichtenheft
 * erwartet im Stundendetail Langnamen ("Mathematik" statt "Ma", T-A2.1), `EntryDto` traegt aber nur
 * Kuerzel. Die Felder sind hier vorbereitet und optional - liefert der Server sie eines Tages
 * (additive Vertragsaenderung), zeigt das Stundendetail sie ohne weitere Aenderung; bis dahin bleibt
 * es beim Kuerzel. Offener Punkt samt Zustaendigkeit: ANLEITUNG.md, Abschnitt 3.
 */
@Serializable
data class EntryDto(
    val date: String,
    val timeslotId: Long,
    val day: String,
    val position: Int,
    val lessonId: Long? = null,
    val classId: Long,
    val classShort: String,
    val subjectShort: String,
    val subjectColor: String,
    val teacherId: Long? = null,
    val teacherShort: String? = null,
    val roomId: Long? = null,
    val roomShort: String? = null,
    val kind: EntryKind = EntryKind.REGULAR,
    val original: EntryOriginal? = null,
    val reasons: List<String> = emptyList(),
    val exceptionId: Long? = null,
    val subjectName: String? = null,
    val roomName: String? = null,
    val teacherName: String? = null,
)

/** Ein Kalendertag der Woche. schoolDay = false heisst unterrichtsfrei. */
@Serializable
data class DayDto(
    val date: String,
    val schoolDay: Boolean = true,
    val entries: List<EntryDto> = emptyList(),
)

/** GET /api/me/timetable/week?date= */
@Serializable
data class WeekDto(
    val monday: String,
    val days: List<DayDto> = emptyList(),
)

/** Ein Zeitfenster des Stundenrasters; isBreak = Pause. */
@Serializable
data class TimeslotDto(
    val id: Long,
    val day: String,
    val position: Int,
    val startTime: String,
    val endTime: String,
    val isBreak: Boolean = false,
)

/** GET /api/timegrid */
@Serializable
data class TimegridDto(
    val slots: List<TimeslotDto> = emptyList(),
)
