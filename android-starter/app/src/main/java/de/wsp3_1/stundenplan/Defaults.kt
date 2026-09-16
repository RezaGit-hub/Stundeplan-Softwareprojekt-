package de.wsp3_1.stundenplan

/**
 * Vorbelegungen, die ohne Neukompilieren aenderbar bleiben muessen, liegen im DataStore
 * (siehe data/Settings.kt). Hier steht nur der Startwert des Eingabefelds.
 *
 * Merksatz: http://localhost:1500 funktioniert im Emulator nie.
 * Emulator -> 10.0.2.2 (= der Rechner, auf dem der Emulator laeuft), echtes Geraet -> LAN-Adresse
 * des Rechners, Projekt-VM -> http://134.245.1.240:1500.
 */
object Defaults {
    const val DEFAULT_SERVER_URL: String = "http://10.0.2.2:1500"

    /** Wird in der Fusszeile der Einstellungen angezeigt. */
    const val APP_VERSION: String = "0.3"
}
