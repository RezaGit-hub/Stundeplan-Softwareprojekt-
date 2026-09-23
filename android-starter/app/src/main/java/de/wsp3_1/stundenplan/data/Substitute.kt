package de.wsp3_1.stundenplan.data

import kotlinx.serialization.Serializable

@Serializable
enum class SubstitutionRequestStatus { UNKNOWN, ACCEPTED, DECLINED }

/** The current contract identifies an event, but supplies no lesson details. */
@Serializable
data class SubstitutionRequestDto(
    val id: Long,
    val teacherId: Long,
    val eventId: Long,
    val status: SubstitutionRequestStatus,
)

@Serializable
data class AnswerSubstitutionRequestDto(val status: SubstitutionRequestStatus)

sealed interface SubstitutionResult<out T> {
    data class Success<T>(val value: T) : SubstitutionResult<T>
    data class Failure(val code: Int?, val message: String) : SubstitutionResult<Nothing>
}
