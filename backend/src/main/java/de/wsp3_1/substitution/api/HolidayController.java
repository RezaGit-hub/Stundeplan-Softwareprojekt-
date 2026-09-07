package de.wsp3_1.substitution.api;

import de.wsp3_1.substitution.api.SubstitutionDtos.HolidayDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.HolidayInput;
import de.wsp3_1.substitution.api.SubstitutionDtos.HolidayPresetInput;
import de.wsp3_1.substitution.api.SubstitutionDtos.HolidayPresetResultDto;
import de.wsp3_1.substitution.api.SubstitutionDtos.HolidayPresetYearsDto;
import de.wsp3_1.substitution.domain.HolidayService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/holidays**} — Ferien und Feiertage (Kann, E-13, Rolle PLANNING).
 *
 * <p>An diesen Tagen liefert die Wochensicht {@code schoolDay: false} und keine Eintraege. Die
 * Zeitraeume werden von Hand gepflegt; einen automatischen Bezug aus dem Internet gibt es
 * bewusst nicht.</p>
 *
 * <p>{@code /preset-years} und {@code /preset} arbeiten auf der <b>mitgelieferten</b> Vorlage je
 * Bundesland (E-51, Tabelle {@code holiday_preset} aus V8) — auch das ist kein Dienst, sondern eine
 * Tabelle im eigenen Schema. Das Bundesland kommt aus {@code GET /api/config}; keines steht im
 * Code.</p>
 */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidays;

    public HolidayController(HolidayService holidays) {
        this.holidays = holidays;
    }

    @GetMapping
    public List<HolidayDto> list() {
        return holidays.list().stream().map(HolidayDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayDto create(@Valid @RequestBody HolidayInput input) {
        return HolidayDto.of(holidays.create(input.toView()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        holidays.delete(id);
    }

    // ------------------------------------------------------------------ Vorlage (E-51)

    /** Die Schuljahre, fuer die es zum eingestellten Bundesland eine Vorlage gibt (leer = keine). */
    @GetMapping("/preset-years")
    public HolidayPresetYearsDto presetYears() {
        return HolidayPresetYearsDto.of(holidays.presetYears());
    }

    /**
     * Uebernimmt die Vorlage eines Schuljahres. Idempotent: was schon steht, wird uebersprungen.
     * Unbekanntes Land oder Schuljahr → 409 {@code preset-missing}.
     */
    @PostMapping("/preset")
    public HolidayPresetResultDto loadPreset(@Valid @RequestBody HolidayPresetInput input) {
        return HolidayPresetResultDto.of(holidays.loadPreset(input.schoolYear()));
    }
}
