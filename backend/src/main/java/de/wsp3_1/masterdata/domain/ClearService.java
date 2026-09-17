package de.wsp3_1.masterdata.domain;

import de.wsp3_1.shared.domain.DatabaseCleaner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClearService {

    private final DatabaseCleaner databaseCleaner;

    public ClearService(DatabaseCleaner databaseCleaner) {
        this.databaseCleaner = databaseCleaner;
    }

    @Transactional
    public void clear() {
        databaseCleaner.clear();
    }
}
