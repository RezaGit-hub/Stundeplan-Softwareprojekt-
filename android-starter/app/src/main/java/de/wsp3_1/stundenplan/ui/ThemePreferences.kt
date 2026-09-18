package de.wsp3_1.stundenplan.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.wsp3_1.stundenplan.data.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Schnittstelle, damit ViewModel-Tests ohne Android-Context auskommen. */
interface ThemePreferences {
    val themeMode: Flow<ThemeMode>
    suspend fun saveThemeMode(themeMode: ThemeMode)
}

private val Context.dataStore by preferencesDataStore(
    name = "settings"
)

class DataStoreThemePreferences(
    private val context: Context
) : ThemePreferences {

    private val themeKey = stringPreferencesKey("themeMode")

    override val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { preferences ->
            when (preferences[themeKey]) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

    override suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = themeMode.name
        }
    }
}