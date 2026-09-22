package de.wsp3_1.absences.domain;

import de.wsp3_1.substitution.domain.SubstitutionViews.EventView;

/** Ergebnis von {@link AbsencesService#convert(Long)}: aktualisierte Meldung und neues Ereignis. */
public record ConvertedAbsenceView(AbsenceView absence, EventView event) {
}