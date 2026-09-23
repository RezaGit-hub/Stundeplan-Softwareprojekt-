package de.wsp3_1.stundenplan.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate

/**
 * Einzige Stelle, an der die App mit dem Server spricht. Die Entscheidung "Netz oder Cache"
 * faellt im ViewModel anhand des [WeekResult]; das Repository liefert Ergebnisse, nie Exceptions
 * (Ausnahme: [CancellationException] wird durchgereicht, sonst laesst sich ein Abruf nicht abbrechen).
 * Schnittstelle, damit das ViewModel in JVM-Tests eine Attrappe bekommt.
 */
interface TimetableRepository {
    suspend fun loadSubstitutionRequests(): SubstitutionResult<List<SubstitutionRequestDto>> =
        SubstitutionResult.Failure(null, "Vertretungsanfragen sind nicht verfügbar.")

    suspend fun answerSubstitutionRequest(id: Long, status: SubstitutionRequestStatus): SubstitutionResult<SubstitutionRequestDto> =
        SubstitutionResult.Failure(null, "Vertretungsanfragen sind nicht verfügbar.")

    /** Prueft eine noch nicht gespeicherte Adresse (A-3, T-A3.1). */
    suspend fun checkHealth(baseUrl: String): HealthResult

    /** POST /api/auth/login gegen die gespeicherte Adresse; bei Erfolg liegt das Token im DataStore. */
    suspend fun login(username: String, password: String): LoginResult

    /** DELETE /api/auth/token (bestmoeglich), dann Token und Offline-Stand loeschen. */
    suspend fun logout()

    /** GET /api/me/timetable/week?date=<montag>; bei Erfolg wird der Offline-Stand aktualisiert. */
    suspend fun loadWeek(monday: LocalDate): WeekResult

    suspend fun loadClassWeek(monday: LocalDate): WeekResult

            /** Letzter gespeicherter Stand dieser Woche, falls vorhanden. */
    suspend fun cachedWeek(monday: LocalDate): CachedWeek?

    /** Stundenraster vom Server (und dann gespeichert), sonst aus dem Cache. */
    suspend fun loadTimegrid(): TimegridDto?

    /** Stundenraster nur aus Speicher oder Cache - fuer den Offline-Fall. */
    suspend fun cachedTimegrid(): TimegridDto?

    /**
     * Offline-Stand loeschen, ohne sich beim Server abzumelden. Noetig bei 401 (das Token gehoert
     * einem Konto, das nicht mehr angemeldet ist) und beim Wechsel der Serveradresse: sonst zeigt
     * die App der naechsten Lehrkraft am selben Geraet den Plan der vorigen (Zugriffsschutz, A-1).
     */
    suspend fun clearCache()
}

class HttpTimetableRepository(
    private val settings: AppSettings,
    private val apiFactory: ApiFactory,
    private val cache: OfflineCache,
    private val clock: () -> Long = System::currentTimeMillis,
) : TimetableRepository {

    private var api: TimetableApi? = null
    private var apiBaseUrl: String? = null
    private var timegridInMemory: TimegridDto? = null

    override suspend fun loadSubstitutionRequests() = substitutionCall { api, auth ->
        api.substitutionRequests(auth)
    }

    override suspend fun answerSubstitutionRequest(id: Long, status: SubstitutionRequestStatus): SubstitutionResult<SubstitutionRequestDto> {
        require(status != SubstitutionRequestStatus.UNKNOWN)
        return substitutionCall { api, auth ->
            api.answerSubstitutionRequest(auth, id, AnswerSubstitutionRequestDto(status))
        }
    }

    private suspend fun <T> substitutionCall(call: suspend (TimetableApi, String) -> Response<T>): SubstitutionResult<T> = withContext(Dispatchers.IO) {
        val url = settings.serverUrl.first()
            ?: return@withContext SubstitutionResult.Failure(null, "Bitte zuerst einen Server speichern.")
        val token = settings.token.first()
            ?: return@withContext SubstitutionResult.Failure(401, "Bitte erneut anmelden.")
        try {
            val response = call(apiFor(url), ApiFactory.bearer(token))
            val body = response.body()
            if (response.isSuccessful && body != null) SubstitutionResult.Success(body)
            else SubstitutionResult.Failure(response.code(), when (response.code()) {
                401 -> "Die Sitzung ist abgelaufen. Bitte erneut anmelden."
                403 -> "Vertretungsanfragen sind auf dem Server derzeit nicht verfügbar."
                404 -> "Die Anfrage ist nicht mehr verfügbar. Bitte aktualisieren."
                409 -> "Die Anfrage wurde bereits beantwortet. Bitte aktualisieren."
                else -> problemDetail(response)?.detail ?: "Die Anfrage konnte nicht verarbeitet werden."
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SubstitutionResult.Failure(null, "Keine Verbindung. Bitte erneut versuchen; die Antwort wurde nicht bestätigt.")
        } catch (e: Exception) {
            SubstitutionResult.Failure(null, "Die Serverantwort konnte nicht verarbeitet werden.")
        }
    }

    /** Adresse geaendert -> Retrofit neu bauen (A-3); das Raster des alten Servers faellt dabei weg. */
    private fun apiFor(baseUrl: String): TimetableApi {
        val current = api
        if (current != null && apiBaseUrl == baseUrl) return current
        timegridInMemory = null // Uhrzeiten gehoeren zum Server, nicht zur App
        return apiFactory.create(baseUrl).also {
            api = it
            apiBaseUrl = baseUrl
        }
    }

    override suspend fun checkHealth(baseUrl: String): HealthResult = withContext(Dispatchers.IO) {
        if (!ApiFactory.looksLikeHttpUrl(baseUrl)) return@withContext HealthResult.Unreachable
        try {
            val response = apiFactory.create(baseUrl).health()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                HealthResult.Reachable(body.schoolName)
            } else {
                HealthResult.Unreachable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Netzfehler sind hier ein erwartetes Ergebnis, kein Absturz.
            HealthResult.Unreachable
        }
    }

    override suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val url = settings.serverUrl.first() ?: return@withContext LoginResult.Unreachable
        val response = try {
            apiFor(url).login(LoginRequest(username, password))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return@withContext LoginResult.Unreachable
        } catch (e: Exception) {
            return@withContext LoginResult.Failure(null)
        }
        val body = response.body()
        when {
            response.isSuccessful && body != null -> {
                // Wer sich anmeldet, faengt bei Null an: der gespeicherte Stand kann zu einem anderen
                // Konto gehoeren (Geraet gewechselt, Sitzung abgelaufen). Die aktuelle Woche kommt
                // gleich danach frisch vom Server.
                cache.clear()
                timegridInMemory = null
                settings.saveLogin(body.token, body.displayName, body.role)
                LoginResult.Success(body)
            }
            response.code() == 401 -> LoginResult.WrongCredentials
            else -> LoginResult.Failure(problemDetail(response)?.detail)
        }
    }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        val url = settings.serverUrl.first()
        val token = settings.token.first()
        if (url != null && token != null) {
            try {
                apiFor(url).logout(ApiFactory.bearer(token))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline abmelden ist erlaubt: das Token laeuft serverseitig nach 12 h ab.
            }
        }
        settings.clearToken()
        cache.clear()
        timegridInMemory = null
    }

    override suspend fun loadWeek(monday: LocalDate): WeekResult = withContext(Dispatchers.IO) {
        val url = settings.serverUrl.first() ?: return@withContext WeekResult.Failure(null)
        val token = settings.token.first() ?: return@withContext WeekResult.Unauthorized
        val response = try {
            apiFor(url).myWeek(ApiFactory.bearer(token), monday.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return@withContext WeekResult.Offline
        } catch (e: Exception) {
            return@withContext WeekResult.Failure(null)
        }
        val body = response.body()
        when {
            response.isSuccessful && body != null -> {
                // Der Plan ist da; misslingt nur das Speichern (Speicher voll), zeigt die App ihn
                // trotzdem an - der Offline-Stand ist dann eben aelter.
                try {
                    cache.saveWeek(body, clock())
                } catch (e: Exception) {
                    // kein Offline-Stand fuer diese Woche, sonst nichts (cache.saveWeek haelt nicht an)
                }
                WeekResult.Online(body)
            }
            response.code() == 401 -> WeekResult.Unauthorized
            response.code() == 404 -> WeekResult.NoTeacher
            else -> WeekResult.Failure(problemDetail(response)?.detail)
        }
    }

    override suspend fun loadClassWeek(monday: LocalDate): WeekResult = withContext(Dispatchers.IO) {
        val url = settings.serverUrl.first() ?: return@withContext WeekResult.Failure(null)
        val token = settings.token.first() ?: return@withContext WeekResult.Unauthorized
        val response = try {
            apiFor(url).myClassWeek(ApiFactory.bearer(token), monday.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return@withContext WeekResult.Offline
        } catch (e: Exception) {
            return@withContext WeekResult.Failure(null)
        }
        val body = response.body()
        when {
            response.isSuccessful && body != null -> {
                // Der Plan ist da; misslingt nur das Speichern (Speicher voll), zeigt die App ihn
                // trotzdem an - der Offline-Stand ist dann eben aelter.
                try {
                    cache.saveWeek(body, clock())
                } catch (e: Exception) {
                    // kein Offline-Stand fuer diese Woche, sonst nichts (cache.saveWeek haelt nicht an)
                }
                WeekResult.Online(body)
            }
            response.code() == 401 -> WeekResult.Unauthorized
            else -> WeekResult.Failure(problemDetail(response)?.detail)
        }
    }

    override suspend fun cachedWeek(monday: LocalDate): CachedWeek? = withContext(Dispatchers.IO) {
        cache.loadWeek(monday.toString())
    }

    /**
     * Das Raster wird bei jedem Abruf mitgeholt: aendert die Planung die Unterrichtszeiten, zeigt
     * das Ziehen zum Aktualisieren die neuen an. Erst wenn der Server nichts liefert, gilt der
     * gespeicherte Stand.
     */
    override suspend fun loadTimegrid(): TimegridDto? = withContext(Dispatchers.IO) {
        val url = settings.serverUrl.first()
        val token = settings.token.first()
        if (url != null && token != null) {
            try {
                val response = apiFor(url).timegrid(ApiFactory.bearer(token))
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    try {
                        cache.saveTimegrid(body, clock())
                    } catch (e: Exception) {
                        // Raster nicht gespeichert - im Speicher liegt es trotzdem
                    }
                    timegridInMemory = body
                    return@withContext body
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // dann eben aus dem Cache
            }
        }
        timegridInMemory ?: cache.loadTimegrid()?.timegrid
    }

    override suspend fun cachedTimegrid(): TimegridDto? = withContext(Dispatchers.IO) {
        timegridInMemory ?: cache.loadTimegrid()?.timegrid
    }

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        cache.clear()
        timegridInMemory = null
    }

    private fun problemDetail(response: Response<*>): ProblemDetail? {
        val text = try {
            response.errorBody()?.string()
        } catch (e: IOException) {
            null
        } ?: return null
        return try {
            apiFactory.json.decodeFromString(ProblemDetail.serializer(), text)
        } catch (e: Exception) {
            null
        }
    }
}
