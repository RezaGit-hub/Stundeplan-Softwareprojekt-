package de.wsp3_1.stundenplan.data

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(
    name = "stundenplan"
)

/**
 * Dauerhafte Einstellungen: Serveradresse, Token, Anzeigename. Als Schnittstelle, damit
 * ViewModel und Repository in JVM-Tests eine Attrappe ohne Android-Context bekommen.
 */
interface AppSettings {
    val serverUrl: Flow<String?>
    val token: Flow<String?>
    val displayName: Flow<String?>
    val role: Flow<String?>

    suspend fun setServerUrl(url: String)

    /** Nach erfolgreicher Anmeldung: Token und Anzeigename zusammen ablegen. */
    suspend fun saveLogin(token: String, displayName: String, role: String)

    /** Abmelden oder 401: Token und Anzeigename entfernen. Die Serveradresse bleibt. */
    suspend fun clearToken()
}

/**
 * DataStore-Umsetzung. Die Serveradresse ueberlebt den Neustart (T-A3.1) und ist ohne
 * Neukompilieren aenderbar. Das Token liegt unverschluesselt im DataStore - das Geraet ist
 * persoenlich (Annahme aus backbone/android.md, kein EncryptedSharedPreferences).
 */
class Settings(private val context: Context) : AppSettings {

    override val serverUrl: Flow<String?> = context.dataStore.data.map { it[KEY_SERVER_URL] }
    override val token: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    override val displayName: Flow<String?> = context.dataStore.data.map { it[KEY_DISPLAY_NAME] }
    override val role: Flow<String?> = context.dataStore.data.map { it[KEY_ROLE] }

    override suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url.trim() }
    }

    override suspend fun saveLogin(token: String, displayName: String, role: String) {
        context.dataStore.edit {
            it[KEY_TOKEN] = token
            it[KEY_DISPLAY_NAME] = displayName
            it[KEY_ROLE] = role
        }
    }

    override suspend fun clearToken() {
        context.dataStore.edit { prefs ->
            prefs.minusAssign(KEY_TOKEN)
            prefs.minusAssign(KEY_DISPLAY_NAME)
            prefs.minusAssign(KEY_ROLE)
        }
    }

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_ROLE = stringPreferencesKey("role")
    }
}
