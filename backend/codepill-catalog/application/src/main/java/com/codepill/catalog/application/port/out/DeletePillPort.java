package com.codepill.catalog.application.port.out;

import com.codepill.catalog.domain.PillId;

/** Hard delete of pills, implemented by the persistence adapter. */
public interface DeletePillPort {

    /** @return {@code true} if a pill was deleted, {@code false} if none existed */
    boolean deleteById(PillId id);
}
