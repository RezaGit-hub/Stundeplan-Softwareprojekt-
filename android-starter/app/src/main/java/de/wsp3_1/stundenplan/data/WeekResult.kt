package de.wsp3_1.stundenplan.data

/** Ergebnis von [TimetableRepository.loadWeek] - Netz oder Fehlerklasse, nie eine Exception. */
sealed interface WeekResult {
    /** 200: frische Woche vom Server (wurde bereits im Offline-Cache abgelegt). */
    data class Online(val week: WeekDto) : WeekResult

    /** Server nicht erreichbar (kein Netz, Zeitueberschreitung, Verbindung abgelehnt). */
    data object Offline : WeekResult

    /** 401: Token fehlt, ist abgelaufen oder ungueltig. */
    data object Unauthorized : WeekResult

    /** 404: das Konto gehoert zu keiner Lehrkraft (z. B. admin). */
    data object NoTeacher : WeekResult

    /** Alles andere (5xx, unlesbare Antwort). detail-Text des Servers, falls vorhanden. */
    data class Failure(val message: String?) : WeekResult
}

/** Ergebnis von [TimetableRepository.login]. */
sealed interface LoginResult {
    data class Success(val response: LoginResponse) : LoginResult

    /** 401: "Benutzername oder Passwort ist falsch." */
    data object WrongCredentials : LoginResult

    data object Unreachable : LoginResult

    data class Failure(val message: String?) : LoginResult
}
