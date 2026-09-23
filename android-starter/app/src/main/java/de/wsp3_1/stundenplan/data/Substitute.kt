package de.wsp3_1.stundenplan.data

import kotlinx.serialization.Serializable

@Serializable
enum class SubstitutionRequestStatus { UNKNOWN, ACCEPTED, DECLINED }

/** Anfrage für eine konkrete Unterrichtsstunde mit Anzeigedaten aus der API. */
@Serializable
data class SubstitutionRequestDto(
    val id: Long,
    val teacherId: Long,
    val lessonId: Long,
    val date: String,
    val classShort: String,
    val subjectShort: String,
    val position: Int,
    val originalTeacherShort: String,
    val status: SubstitutionRequestStatus,
    val roomShort: String? = null,
)

@Serializable
data class AnswerSubstitutionRequestDto(val status: SubstitutionRequestStatus)

sealed interface SubstitutionResult<out T> {
    data class Success<T>(val value: T) : SubstitutionResult<T>
    data class Failure(val code: Int?, val message: String) : SubstitutionResult<Nothing>
}
