package de.wsp3_1.transfer.api;

import de.wsp3_1.transfer.api.TransferDtos.MasterDataExport;
import de.wsp3_1.transfer.api.TransferDtos.MasterDataImport;
import de.wsp3_1.transfer.api.TransferDtos.MasterDataImportResult;
import de.wsp3_1.transfer.domain.ExportService;
import de.wsp3_1.transfer.domain.ImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import de.wsp3_1.transfer.api.TransferDtos.MasterDataSection;
import de.wsp3_1.transfer.api.TransferDtos.SectionImportResult;
import org.springframework.web.bind.annotation.PathVariable;
import de.wsp3_1.shared.domain.ValidationException;
/**
 * Sicherung der Grunddaten: {@code /api/masterdata/export} und {@code /api/masterdata/import}.
 *
 * <p>Beide Pfade sind Planungssache — die Pfadregel steht in {@code auth.api.SecurityConfig}
 * (alles unter {@code /api/**} ohne eigene Zeile verlangt die Rolle {@code PLANNING}).</p>
 */
@RestController
@RequestMapping("/api/masterdata")
public class TransferController {

    private final ExportService export;
    private final ImportService importService;

    public TransferController(ExportService export, ImportService importService) {
        this.export = export;
        this.importService = importService;
    }

    @GetMapping("/export")
    public MasterDataExport export() {
        return export.export();
    }

    @PostMapping("/import")
    public MasterDataImportResult importMasterData(@Valid @RequestBody MasterDataImport body) {
        return importService.importMasterData(body);
    }

    @GetMapping("/{scope}/export")
    public MasterDataSection exportSection(@PathVariable String scope) {
        return export.exportSection(scope);
    }

    @PostMapping("/{scope}/import")
    public SectionImportResult importSection(@PathVariable String scope,
                                             @Valid @RequestBody MasterDataSection body) {
        if (!scope.equals(body.scope())) {
            throw new ValidationException("scope",
                    "Pfad und Dokument stimmen nicht überein (" + scope + " ≠ " + body.scope() + ")");
        }
        return importService.importSection(body);
    }
}