package de.wsp3_1.stundenplan.data

import kotlinx.serialization.Serializable

@Serializable
data class NewAbsenceRequest (
    val dateFrom : String,
    val dateTo: String,
    val reason: String
)