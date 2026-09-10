package de.wsp3_1.substitution.domain;

import de.wsp3_1.config.domain.SystemConfigService;
import de.wsp3_1.shared.domain.ConflictException;
import de.wsp3_1.shared.domain.NotFoundException;
import de.wsp3_1.shared.domain.ValidationException;
import de.wsp3_1.substitution.domain.SubstitutionViews.HolidayPresetResultView;
import de.wsp3_1.substitution.domain.SubstitutionViews.HolidayPresetYearsView;
import de.wsp3_1.substitution.domain.SubstitutionViews.HolidayView;
import de.wsp3_1.substitution.domain.SubstitutionViews.NewHoliday;
import de.wsp3_1.substitution.persistence.Holiday;
import de.wsp3_1.substitution.persistence.HolidayPreset;
import de.wsp3_1.substitution.persistence.HolidayPresetRepository;
import de.wsp3_1.substitution.persistence.HolidayRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unterrichtsfreie Zeitraeume: Ferien und Feiertage (Kann, E-13).
 *
 * <p>An diesen Tagen liefert die Wochensicht {@code schoolDay: false} und keine Eintraege; es entsteht
 * dort auch kein Vertretungsbedarf. Die Zeitraeume werden von Hand gepflegt — es gibt bewusst keinen
 * automatischen Bezug aus dem Internet (Abgrenzung im Pflichtenheft, keine Laufzeit-Dienste).</p>
 *
 * <p>Damit niemand 14 bis 18 Zeitraeume je Schuljahr abtippen muss, liegt eine <b>mitgelieferte
 * Vorlage</b> je Bundesland und Schuljahr in der Tabelle {@code holiday_preset} (E-51). Sie ist
 * ebenfalls nur Daten: {@link #loadPreset(String)} kopiert die Zeilen des in der Systemkonfiguration
 * eingestellten Bundeslands hierher, danach ist jeder Eintrag wie jeder andere aenderbar und
 * loeschbar.</p>
 */
@Service
public class HolidayService {

    private final HolidayRepository holidays;
    private final HolidayPresetRepository presets;
    private final SystemConfigService systemConfig;

    public HolidayService(HolidayRepository holidays, HolidayPresetRepository presets,
                          SystemConfigService systemConfig) {
        this.holidays = holidays;
        this.presets = presets;
        this.systemConfig = systemConfig;
    }

    @Transactional(readOnly = true)
    public List<HolidayView> list() {
        return holidays.findAll(Sort.by("dateFrom", "id")).stream().map(HolidayService::view).toList();
    }

    @Transactional
    public HolidayView create(NewHoliday input) {
        if (input.dateTo().isBefore(input.dateFrom())) {
            throw new ValidationException("dateTo", "muss nach dem Beginn liegen");
        }
        return view(holidays.save(new Holiday(input.name().trim(), input.dateFrom(), input.dateTo(),
                input.kind())));
    }

    @Transactional
    public void delete(Long id) {
        Holiday holiday = holidays.findById(id)
                .orElseThrow(() -> new NotFoundException("Den Zeitraum " + id + " gibt es nicht."));
        holidays.delete(holiday);
    }

    // ------------------------------------------------------------------ Vorlage (E-51)

    /**
     * Die Schuljahre, fuer die es zum eingestellten Bundesland eine Vorlage gibt. Leere Liste, wenn
     * das Land keine hat — das ist kein Fehler, sondern der Grund, den Knopf „Vorlage laden"
     * auszublenden.
     */
    @Transactional(readOnly = true)
    public HolidayPresetYearsView presetYears() {
        String state = systemConfig.get().state();
        return new HolidayPresetYearsView(state, presets.findSchoolYears(state));
    }

    /**
     * Uebernimmt die Vorlage des eingestellten Bundeslands fuer ein Schuljahr nach {@code holiday}.
     *
     * <p>Der Aufruf ist <b>idempotent</b>: eine Zeile wird uebersprungen, wenn schon ein Zeitraum mit
     * derselben Bezeichnung und demselben Anfang und Ende steht — genau der Schluessel, ueber den auch
     * {@code holiday_preset_unique} die Vorlage eindeutig haelt. Ein zweiter Aufruf liefert also
     * {@code added = 0}. Von Hand geaenderte Eintraege bleiben unberuehrt; sie zaehlen dann nicht als
     * Duplikat und kommen als zweite Zeile dazu.</p>
     *
     * @throws ConflictException 409 {@code preset-missing}, wenn es zu Land und Schuljahr keine Zeile gibt
     */
    @Transactional
    public HolidayPresetResultView loadPreset(String schoolYear) {
        String state = systemConfig.get().state();
        List<HolidayPreset> vorlage = presets.findByStateAndSchoolYearOrderByDateFromAscLabelAsc(state, schoolYear);
        if (vorlage.isEmpty()) {
            List<String> vorhanden = presets.findSchoolYears(state);
            throw new ConflictException("preset-missing",
                    "Fuer " + state + " und das Schuljahr " + schoolYear + " gibt es keine Vorlage."
                            + (vorhanden.isEmpty()
                            ? " Fuer dieses Bundesland liegt keine Vorlage bei."
                            : " Vorhanden: " + String.join(", ", vorhanden) + "."));
        }

        // Ein Satz vorhandener Schluessel statt einer Abfrage je Zeile: 14 bis 18 Vorlagenzeilen
        // gegen die bestehende Liste, das ist eine Abfrage insgesamt.
        Set<String> vorhandene = new HashSet<>();
        for (Holiday bestehend : holidays.findAll()) {
            vorhandene.add(schluessel(bestehend.getDateFrom().toString(), bestehend.getDateTo().toString(), bestehend.getName()));
        }

        int uebernommen = 0;
        int uebersprungen = 0;
        for (HolidayPreset zeile : vorlage) {
            String schluessel = schluessel(zeile.getDateFrom().toString(), zeile.getDateTo().toString(), zeile.getLabel());
            if (!vorhandene.add(schluessel)) {
                uebersprungen++;
                continue;
            }
            holidays.save(new Holiday(zeile.getLabel(), zeile.getDateFrom(), zeile.getDateTo(), zeile.getKind()));
            uebernommen++;
        }
        return new HolidayPresetResultView(uebernommen, uebersprungen, state, schoolYear);
    }

    /** Anfang, Ende und Bezeichnung — der Schluessel, ueber den das Uebernehmen Duplikate erkennt. */
    private static String schluessel(String von, String bis, String name) {
        return von + '|' + bis + '|' + name.trim();
    }

    private static HolidayView view(Holiday holiday) {
        return new HolidayView(holiday.getId(), holiday.getName(), holiday.getDateFrom(),
                holiday.getDateTo(), holiday.getKind());
    }
}
