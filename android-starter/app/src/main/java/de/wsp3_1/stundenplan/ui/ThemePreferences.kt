package de.wsp3_1.stundenplan.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.wsp3_1.stundenplan.data.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(
    name = "settings"
)

class ThemePreferences(
    private val context: Context
) {
    private val themeKey = stringPreferencesKey("themeMode")

    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { preferences ->
            when (preferences[themeKey]) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = themeMode.name
        }
    }
}