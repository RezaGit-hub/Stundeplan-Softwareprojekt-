package de.wsp3_1.stundenplan.data

/** Ergebnis des Verbindungstests aus dem Einstellungs-Screen (A-3, T-A3.1). */
sealed interface HealthResult {
    /** Server antwortet. schoolName kann fehlen, wenn die Schule noch nicht benannt ist. */
    data class Reachable(val schoolName: String?) : HealthResult

    /** Keine Antwort, falsche Adresse oder Fehlercode. Die App bietet trotzdem Speichern an. */
    data object Unreachable : HealthResult
}
