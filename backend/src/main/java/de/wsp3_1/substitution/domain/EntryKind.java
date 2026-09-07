package de.wsp3_1.substitution.domain;

/**
 * Art eines Wocheneintrags. Wortgleich mit dem Enum {@code EntryKind} im Vertrag.
 *
 * <p><b>Stand backend2 Teil 1:</b> es entstehen nur {@code REGULAR}-Eintraege. Die Wochensicht loest
 * bisher allein den Regelplan auf; Ereignisse (W-5) und datierte Ausnahmen (W-6) kommen in Teil 2, und
 * erst dann tragen Eintraege die uebrigen Arten. Das Enum steht schon vollstaendig, damit Vertrag,
 * Oberflaeche und Android-App sich nicht noch einmal aendern muessen.</p>
 */
public enum EntryKind {

    /** Regelplan, unveraendert. */
    REGULAR,

    /** Faellt aus und ist noch nicht vertreten („vertretungsbeduerftig"). */
    NEEDS_SUBSTITUTION,

    /** Andere Lehrkraft und/oder anderer Raum. */
    SUBSTITUTED,

    /** Nur in der Sicht TEACHER: die eigene Stunde, die eine andere Lehrkraft uebernimmt. */
    GIVEN_AWAY,

    /** Ziel einer Verlegung. */
    MOVED,

    /** Entfaellt — auch der Ursprung einer Verlegung. */
    CANCELLED,

    /** Zusatzstunde ohne Regelplanstunde. */
    ADDED
}
