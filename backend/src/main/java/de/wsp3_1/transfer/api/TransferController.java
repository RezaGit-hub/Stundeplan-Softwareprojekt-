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
}