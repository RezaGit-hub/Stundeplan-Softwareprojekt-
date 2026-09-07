package de.wsp3_1.stundenplan

import de.wsp3_1.stundenplan.ui.EntryLabel
import de.wsp3_1.stundenplan.ui.Tone
import de.wsp3_1.stundenplan.ui.kindUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alle sieben Arten -> erwartete Farbe, Rahmen, Badge und Textlabel (T-606, ui.md). */
class EntryKindUiTest {

    @Test
    fun `REGULAR hat weder Rahmen noch Badge noch Label`() {
        val ui = SampleWeek.regular.kindUi()
        assertEquals(Tone.NONE, ui.tone)
        assertNull(ui.badge)
        assertNull(ui.label)
        assertFalse(ui.changed)
        assertFalse(ui.strike)
        assertFalse(ui.dim)
    }

    @Test
    fun `NEEDS_SUBSTITUTION ist orange gestrichelt mit Ausrufezeichen und noch nicht vertreten`() {
        val ui = SampleWeek.needsSubstitution.kindUi()
        assertEquals(Tone.SUBSTITUTION, ui.tone)
        assertTrue(ui.dashed)
        assertEquals("!", ui.badge)
        assertEquals(EntryLabel.NeedsSubstitution, ui.label)
        assertFalse(ui.strike)
    }

    @Test
    fun `SUBSTITUTED mit anderer Lehrkraft heisst Vertretung, orange durchgezogen mit V`() {
        val ui = SampleWeek.substitutedTeacher.kindUi()
        assertEquals(Tone.SUBSTITUTION, ui.tone)
        assertFalse(ui.dashed)
        assertEquals("V", ui.badge)
        assertEquals(EntryLabel.Substitution, ui.label)
    }

    @Test
    fun `SUBSTITUTED nur mit anderem Raum heisst Raumwechsel`() {
        val ui = SampleWeek.substitutedRoomOnly.kindUi()
        assertEquals(EntryLabel.RoomChange, ui.label)
        assertEquals("V", ui.badge)
    }

    @Test
    fun `GIVEN_AWAY ist abgeblendet und nennt die uebernehmende Lehrkraft`() {
        val ui = SampleWeek.givenAway.kindUi()
        assertEquals(Tone.GIVEN_AWAY, ui.tone)
        assertTrue(ui.dim)
        assertNull(ui.badge)
        assertEquals(EntryLabel.GivenAway("SCZ"), ui.label)
    }

    @Test
    fun `CANCELLED ist grau durchgestrichen mit Kreuz und entfaellt`() {
        val ui = SampleWeek.cancelled.kindUi()
        assertEquals(Tone.CANCELLED, ui.tone)
        assertTrue(ui.strike)
        assertEquals("✕", ui.badge)
        assertEquals(EntryLabel.Cancelled, ui.label)
    }

    @Test
    fun `CANCELLED als Ursprung einer Verlegung traegt den Servertext verlegt`() {
        val ui = SampleWeek.cancelledMoved.kindUi()
        assertTrue(ui.strike)
        assertEquals(EntryLabel.CancelledMoved("verlegt → Mi 1."), ui.label)
    }

    @Test
    fun `MOVED ist das Ziel der Verlegung, orange mit V und Herkunft`() {
        val ui = SampleWeek.moved.kindUi()
        assertEquals(Tone.SUBSTITUTION, ui.tone)
        assertFalse(ui.strike)
        assertEquals("V", ui.badge)
        assertEquals(EntryLabel.Moved("Di 1."), ui.label)
    }

    @Test
    fun `ADDED ist orange mit Plus und Zusatzstunde`() {
        val ui = SampleWeek.added.kindUi()
        assertEquals(Tone.SUBSTITUTION, ui.tone)
        assertEquals("+", ui.badge)
        assertEquals(EntryLabel.Added, ui.label)
    }
}
