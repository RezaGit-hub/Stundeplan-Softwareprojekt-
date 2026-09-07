package de.wsp3_1.sampledata.api;

import de.wsp3_1.sampledata.domain.SampleDataLoader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/sample-data} — den vollen Beispielbestand laden (12 Klassen, 24 Lehrkraefte,
 * 18 Raeume; E-46, Checkliste D-1). Nur die Rolle Planung.
 *
 * <p>Sind schon Grunddaten da, kommt 409 {@code data-exists}; die Oberflaeche fragt daraufhin
 * „Ersetzen?" und schickt {@code {"replace": true}}. Ersetzt werden Grunddaten, Plaene, Ereignisse,
 * Ausnahmen, Verlauf und die Konten der Lehrkraefte — Regelwerk, Systemkonfiguration und das Konto
 * der Planung bleiben. Konten fuer Lehrkraefte legt der Bestand nicht an (keine Passwoerter im
 * Quelltext); die legt die Planung ueber {@code POST /api/users} an.</p>
 */
@RestController
@RequestMapping("/api/sample-data")
public class SampleDataController {

    private final SampleDataLoader loader;

    public SampleDataController(SampleDataLoader loader) {
        this.loader = loader;
    }

    /** Schema {@code SampleDataRequest}; der ganze Rumpf ist freiwillig, {@code replace} ist standardmaessig falsch. */
    public record SampleDataRequest(Boolean replace) {
    }

    /** Schema {@code SampleDataResult}. */
    public record SampleDataResult(int classes, int teachers, int rooms) {
    }

    /**
     * Pruefung und Laden macht {@link SampleDataLoader#load(boolean)} in einer Transaktion. Stuenden sie
     * hier als zwei Aufrufe, koennte zwischen „sind schon Grunddaten da?" und dem Laden jemand eine
     * Klasse anlegen, die das Leeren dann mitnaehme — ohne dass „replace" gesetzt war.
     */
    @PostMapping
    public SampleDataResult load(@RequestBody(required = false) SampleDataRequest body) {
        boolean ersetzen = body != null && Boolean.TRUE.equals(body.replace());
        SampleDataLoader.Counts counts = loader.load(ersetzen);
        return new SampleDataResult(counts.classes(), counts.teachers(), counts.rooms());
    }
}
