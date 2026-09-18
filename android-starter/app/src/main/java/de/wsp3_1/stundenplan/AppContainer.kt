package de.wsp3_1.stundenplan

import android.content.Context
import de.wsp3_1.stundenplan.data.ApiFactory
import de.wsp3_1.stundenplan.data.HttpTimetableRepository
import de.wsp3_1.stundenplan.data.OfflineCache
import de.wsp3_1.stundenplan.data.Settings
import de.wsp3_1.stundenplan.data.TimetableRepository
import de.wsp3_1.stundenplan.ui.ThemePreferences
import java.io.File

/**
 * Manuelle Konstruktor-Injektion: eine Stelle, an der alle langlebigen Objekte entstehen.
 * Settings (DataStore), OfflineCache (JSON-Dateien in filesDir/offline), ApiFactory, Repository.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val settings: Settings = Settings(appContext)

    val themePreferences: ThemePreferences = DataStoreThemePreferences(appContext)

    private val apiFactory: ApiFactory = ApiFactory()

    val offlineCache: OfflineCache = OfflineCache(File(appContext.filesDir, "offline"), apiFactory.json)

    val repository: TimetableRepository = HttpTimetableRepository(settings, apiFactory, offlineCache)
}
