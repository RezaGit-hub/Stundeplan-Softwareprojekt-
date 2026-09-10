package de.wsp3_1.stundenplan

import android.app.Application

/**
 * Haelt den AppContainer. Keine Bibliothek fuer Injektion (kein Hilt, kein Koin) -
 * der Container wird von Hand gebaut und ueber die Application weitergereicht.
 */
class StundenplanApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
