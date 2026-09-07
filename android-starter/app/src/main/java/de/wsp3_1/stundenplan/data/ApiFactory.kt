package de.wsp3_1.stundenplan.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Baut fuer eine Serveradresse ein Retrofit-Objekt. Aendert sich die Adresse,
 * wird ein neues gebaut - deshalb ist hier nichts zwischengespeichert ausser dem
 * OkHttp-Client (Verbindungs-Pool teilen ist billiger als neu aufbauen).
 */
class ApiFactory {

    /** Dieselbe Json-Instanz liest die Serverantworten und schreibt den Offline-Stand. */
    val json: Json = Json {
        ignoreUnknownKeys = true   // additive Vertragsaenderungen (neue Felder) brechen die App nicht
        explicitNulls = false
        coerceInputValues = true   // null fuer ein Feld mit Vorgabe -> Vorgabe statt Absturz
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        // Keine Weiterleitungen: jede Anfrage geht an genau das Schema, den Rechner und den Port,
        // die in den Einstellungen stehen. Sonst koennte eine 307/308-Antwort den Anmelderumpf
        // (Benutzername und Passwort) oder das Token an eine andere Adresse weiterreichen.
        // Eine Weiterleitung erscheint dadurch als Fehlercode - richtig, denn der Vertrag kennt keine.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun create(baseUrl: String): TimetableApi =
        Retrofit.Builder()
            .baseUrl(normalize(baseUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TimetableApi::class.java)

    companion object {
        /** Retrofit verlangt eine Basis-Adresse mit Schraegstrich am Ende. */
        fun withTrailingSlash(url: String): String =
            if (url.endsWith("/")) url else "$url/"

        /**
         * Die App haengt selbst `api/...` an. Wer die Adresse aus der Web-App abschreibt, traegt
         * leicht `http://134.245.1.240:1500/api` ein - daraus wuerde `/api/api/health`, und der
         * Server antwortet darauf mit 401 statt mit dem Gesundheitsbericht. Ein `/api` am Ende des
         * Pfads faellt deshalb weg. Ein Rechnername `api` (kein Pfad) bleibt unangetastet.
         */
        fun stripApiSuffix(url: String): String {
            val cleaned = url.trim().trimEnd('/')
            val schemeEnd = cleaned.indexOf("://")
            if (schemeEnd < 0) return cleaned
            if (cleaned.indexOf('/', schemeEnd + 3) < 0) return cleaned // nur Rechner und Port, kein Pfad
            return if (cleaned.endsWith("/api", ignoreCase = true)) cleaned.dropLast(4) else cleaned
        }

        /** Eingetippte Adresse -> Basis-Adresse fuer Retrofit (ohne `/api`, mit Schraegstrich). */
        fun normalize(url: String): String = withTrailingSlash(stripApiSuffix(url))

        /** Genuegt fuer die Eingabepruefung im Einstellungs-Screen. */
        fun looksLikeHttpUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.startsWith("http://") || trimmed.startsWith("https://")
        }

        /** Wert des Authorization-Headers. */
        fun bearer(token: String): String = "Bearer $token"
    }
}
