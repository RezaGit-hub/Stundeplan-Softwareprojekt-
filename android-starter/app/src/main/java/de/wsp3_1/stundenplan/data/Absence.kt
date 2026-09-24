package de.wsp3_1.stundenplan.data

import de.wsp3_1.stundenplan.ui.LoginReason
import kotlinx.serialization.Serializable
import java.time.LocalDate

class Absence (
    val from: LocalDate,
    val to: LocalDate,
    val reason: String
)