package de.wsp3_1.substitution.domain;

import de.wsp3_1.substitution.domain.WeekViews.DayView;
import de.wsp3_1.substitution.domain.WeekViews.EntryView;
import de.wsp3_1.substitution.domain.WeekViews.OriginalView;
import de.wsp3_1.substitution.domain.WeekViews.WeekView;

import java.util.List;

/**
 * Der Datenfilter der Schuelersicht (E-50). Er nimmt die fertig aufgeloeste Woche des
 * {@link WeekResolver} und streicht daraus, was ein Klassenkonto nicht sehen darf:
 *
 * <ul>
 *   <li><b>{@code reasons} wird leer.</b> Der Anlass einer Vertretung („Krankmeldung") ist ein
 *       Gesundheitsdatum der Lehrkraft. Mehrere Aufsichtsbehoerden nennen die Nennung von
 *       Abwesenheitsgruenden ausdruecklich unzulaessig — Recherche
 *       {@code 07-Konzept/recherche/sammelzugang-schueler.md}, Abschnitt „Behoerdenpositionen".
 *       Die <em>Art</em> der Aenderung bleibt sichtbar: {@code NEEDS_SUBSTITUTION} heisst in der
 *       Oberflaeche „Vertretung offen", ohne Grund.</li>
 *   <li><b>{@code original} behaelt nur Kuerzel, Datum und Position.</b> Die Kennungen
 *       {@code teacherId} und {@code roomId} fallen weg: mit ihnen liesse sich ausserhalb der
 *       Oberflaeche eine Person nachschlagen, und mehr als das Kuerzel braucht die Klasse nicht.</li>
 * </ul>
 *
 * <p><b>Warum hier und nicht in der Oberflaeche?</b> Weil ein Filter im Browser keiner ist: die
 * Antwort kaeme trotzdem vollstaendig ueber die Leitung. Gefiltert wird deshalb, bevor die Daten den
 * Server verlassen. Klarnamen von Lehrkraeften stehen ohnehin in keinem Wocheneintrag — der Vertrag
 * kennt dort nur {@code teacherShort}.</p>
 *
 * <p>Reine Funktion ohne Datenbank und ohne Spring, damit sie sich einzeln pruefen laesst
 * ({@code StudentViewTest}) — wie {@link WeekResolver#resolveDay}.</p>
 */
public final class StudentView {

    private StudentView() {
    }

    /** Die Woche, wie ein Klassenkonto sie bekommt. */
    public static WeekView filter(WeekView week) {
        return new WeekView(week.monday(), week.days().stream().map(StudentView::filterDay).toList());
    }

    private static DayView filterDay(DayView day) {
        return new DayView(day.date(), day.schoolDay(), day.entries().stream().map(StudentView::filterEntry).toList());
    }

    private static EntryView filterEntry(EntryView entry) {
        return new EntryView(entry.date(), entry.timeslotId(), entry.day(), entry.position(), entry.lessonId(),
                entry.classId(), entry.classShort(), entry.subjectId(), entry.subjectShort(),
                entry.subjectColor(), entry.teacherId(), entry.teacherShort(), entry.roomId(),
                entry.roomShort(), entry.kind(), filterOriginal(entry.original()), List.of(),
                entry.exceptionId());
    }

    private static OriginalView filterOriginal(OriginalView original) {
        return original == null ? null
                : new OriginalView(null, original.teacherShort(), null, original.roomShort(),
                        original.date(), original.position());
    }
}
