package com.codepill.catalog.application.port.out;

import com.codepill.catalog.domain.Pill;

/** Write access to pills, implemented by the persistence adapter. */
public interface SavePillPort {

    /**
     * Inserts or updates the pill and returns the persisted state (with
     * database-assigned timestamps and version).
     */
    Pill save(Pill pill);
}
