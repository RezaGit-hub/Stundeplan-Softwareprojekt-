package de.wsp3_1.stundenplan.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Eine gespeicherte Woche samt Zeitpunkt des Abrufs (Epoche in Millisekunden, Geraetezeit). */
@Serializable
data class CachedWeek(
    val savedAtEpochMillis: Long,
    val week: WeekDto,
)

@Serializable
data class CachedTimegrid(
    val savedAtEpochMillis: Long,
    val timegrid: TimegridDto,
)

/**
 * Offline-Stand als JSON-Dateien im privaten Speicher der App (filesDir):
 * week-<montag>.json je Woche, hoechstens [maxWeeks] Stueck (die aelteste fliegt raus),
 * dazu timegrid.json. Kein Room - fuer ein paar Wochen Stundenplan waere das Ueberbau.
 * Reines Java-IO, deshalb ohne Geraet testbar.
 *
 * Geschrieben wird ueber eine Nebendatei und `renameTo`: bricht der Vorgang ab (Speicher voll,
 * Prozess beendet), bleibt der vorige Stand vollstaendig lesbar statt halb geschrieben liegen.
 * Alle Zugriffe laufen ueber dasselbe Schloss - Abruf und Abmelden koennen gleichzeitig kommen.
 */
class OfflineCache(
    private val dir: File,
    private val json: Json,
    private val maxWeeks: Int = 4,
) {

    private val lock = Any()

    fun saveWeek(week: WeekDto, savedAtEpochMillis: Long) {
        synchronized(lock) {
            dir.mkdirs()
            writeAtomic(
                weekFile(week.monday),
                json.encodeToString(CachedWeek.serializer(), CachedWeek(savedAtEpochMillis, week)),
            )
            prune()
        }
    }

    fun loadWeek(monday: String): CachedWeek? = synchronized(lock) {
        val file = weekFile(monday)
        if (!file.isFile) {
            null
        } else {
            try {
                json.decodeFromString(CachedWeek.serializer(), file.readText())
            } catch (e: Exception) {
                file.delete() // unlesbar (aelteres Format) - lieber weg als jedes Mal scheitern
                null
            }
        }
    }

    fun saveTimegrid(timegrid: TimegridDto, savedAtEpochMillis: Long) {
        synchronized(lock) {
            dir.mkdirs()
            writeAtomic(
                timegridFile(),
                json.encodeToString(CachedTimegrid.serializer(), CachedTimegrid(savedAtEpochMillis, timegrid)),
            )
        }
    }

    fun loadTimegrid(): CachedTimegrid? = synchronized(lock) {
        val file = timegridFile()
        if (!file.isFile) {
            null
        } else {
            try {
                json.decodeFromString(CachedTimegrid.serializer(), file.readText())
            } catch (e: Exception) {
                file.delete()
                null
            }
        }
    }

    /** Abmelden, Kontowechsel, 401 oder andere Serveradresse: nichts vom vorigen Stand stehen lassen. */
    fun clear() {
        synchronized(lock) {
            weekFiles().forEach { it.delete() }
            timegridFile().delete()
            tempFiles().forEach { it.delete() }
        }
    }

    /** Montage der gespeicherten Wochen, fuer Tests und Anzeige. */
    fun storedMondays(): List<String> = synchronized(lock) {
        weekFiles().map { it.name.removePrefix(PREFIX).removeSuffix(SUFFIX) }.sorted()
    }

    /** Erst in die Nebendatei, dann umbenennen - ein halb geschriebener Stand entsteht nie. */
    private fun writeAtomic(file: File, text: String) {
        val temp = File(file.parentFile, file.name + TEMP_SUFFIX)
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            // Manche Dateisysteme benennen nicht ueber eine vorhandene Datei hinweg um.
            file.delete()
            if (!temp.renameTo(file)) {
                file.writeText(text)
                temp.delete()
            }
        }
    }

    private fun prune() {
        val files = weekFiles().sortedBy { it.lastModified() }
        if (files.size <= maxWeeks) return
        files.take(files.size - maxWeeks).forEach { it.delete() }
    }

    private fun weekFiles(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            ?.toList() ?: emptyList()

    private fun tempFiles(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(TEMP_SUFFIX) }?.toList() ?: emptyList()

    private fun weekFile(monday: String): File = File(dir, "$PREFIX$monday$SUFFIX")

    private fun timegridFile(): File = File(dir, "timegrid.json")

    private companion object {
        const val PREFIX = "week-"
        const val SUFFIX = ".json"
        const val TEMP_SUFFIX = ".tmp"
    }
}
