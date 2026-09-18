package de.wsp3_1.masterdata.api;

import de.wsp3_1.masterdata.domain.ClearService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clear-data")
public class ClearController {

    private final ClearService service;

    public ClearController(ClearService service) {
        this.service = service;
    }

    @DeleteMapping("/all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllData() {
        service.clear();
    }
}
