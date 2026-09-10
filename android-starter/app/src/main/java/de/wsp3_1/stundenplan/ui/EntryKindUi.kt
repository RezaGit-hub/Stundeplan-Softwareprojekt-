package de.wsp3_1.stundenplan.ui

import de.wsp3_1.stundenplan.data.EntryDto
import de.wsp3_1.stundenplan.data.EntryKind
import de.wsp3_1.stundenplan.data.PlanDates

/**
 * kind -> Rahmen, Badge, Textlabel, durchgestrichen, abgeblendet. Dieselbe Tabelle wie
 * frontend/src/lib/entry-kind.ts (ui.md): Vertretung orange durchgezogen + "V", noch nicht
 * vertreten orange gestrichelt + "!", Entfall grau durchgestrichen + "✕", abgegeben abgeblendet,
 * Zusatzstunde orange + "+". Farbe ist nie der einzige Traeger - jede Art hat Badge und/oder Text.
 * Reines Kotlin ohne Compose, damit der Test auf der JVM laeuft.
 */
enum class Tone { NONE, SUBSTITUTION, CANCELLED, GIVEN_AWAY }

/** Textlabel als Datum; der Wortlaut steht in strings.xml (Sprache des Nutzers). */
sealed interface EntryLabel {
    data object Substitution : EntryLabel
    data object RoomChange : EntryLabel
    data object NeedsSubstitution : EntryLabel
    data class GivenAway(val toTeacherShort: String?) : EntryLabel
    data object Cancelled : EntryLabel

    /** Ursprung einer Verlegung: der Server liefert den Text ("verlegt → Mi 1.") in reasons. */
    data class CancelledMoved(val serverText: String) : EntryLabel

    /** Ziel einer Verlegung; from = "Di 1." aus original.date/position, falls vorhanden. */
    data class Moved(val from: String?) : EntryLabel
    data object Added : EntryLabel
}

data class EntryKindUi(
    val tone: Tone,
    val dashed: Boolean,
    val badge: String?,
    val label: EntryLabel?,
    val strike: Boolean,
    val dim: Boolean,
) {
    val changed: Boolean get() = label != null
}

fun EntryDto.kindUi(): EntryKindUi = when (kind) {
    EntryKind.REGULAR -> EntryKindUi(Tone.NONE, dashed = false, badge = null, label = null, strike = false, dim = false)

    EntryKind.NEEDS_SUBSTITUTION -> EntryKindUi(
        Tone.SUBSTITUTION, dashed = true, badge = "!", label = EntryLabel.NeedsSubstitution, strike = false, dim = false,
    )

    EntryKind.SUBSTITUTED -> EntryKindUi(
        Tone.SUBSTITUTION,
        dashed = false,
        badge = "V",
        label = if (original?.teacherShort != null) EntryLabel.Substitution else EntryLabel.RoomChange,
        strike = false,
        dim = false,
    )

    EntryKind.GIVEN_AWAY -> EntryKindUi(
        Tone.GIVEN_AWAY, dashed = false, badge = null, label = EntryLabel.GivenAway(teacherShort), strike = false, dim = true,
    )

    EntryKind.CANCELLED -> {
        val moved = reasons.firstOrNull { it.startsWith("verlegt") }
        EntryKindUi(
            Tone.CANCELLED,
            dashed = false,
            badge = "✕",
            label = if (moved != null) EntryLabel.CancelledMoved(moved) else EntryLabel.Cancelled,
            strike = true,
            dim = false,
        )
    }

    EntryKind.MOVED -> EntryKindUi(
        Tone.SUBSTITUTION, dashed = false, badge = "V", label = EntryLabel.Moved(movedFrom()), strike = false, dim = false,
    )

    EntryKind.ADDED -> EntryKindUi(
        Tone.SUBSTITUTION, dashed = false, badge = "+", label = EntryLabel.Added, strike = false, dim = false,
    )
}

/** "Di 1." aus original.date und original.position; null, wenn der Server nichts mitgibt. */
private fun EntryDto.movedFrom(): String? {
    val o = original ?: return null
    val date = o.date ?: return null
    val position = o.position ?: return null
    return "${PlanDates.shortGerman(PlanDates.parse(date))} $position."
}
