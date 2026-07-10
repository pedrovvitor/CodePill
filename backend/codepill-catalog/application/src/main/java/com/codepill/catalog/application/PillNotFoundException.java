package com.codepill.catalog.application;

import com.codepill.catalog.domain.PillId;

/**
 * The pill does not exist — or the caller may not know whether it exists
 * (existence hiding on ownership checks, SECURITY.md §3.2). Mapped to HTTP 404.
 */
public class PillNotFoundException extends RuntimeException {

    public PillNotFoundException(PillId id) {
        super("pill not found: " + id.value());
    }
}
