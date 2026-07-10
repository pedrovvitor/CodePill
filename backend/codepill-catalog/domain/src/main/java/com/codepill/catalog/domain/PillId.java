package com.codepill.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link Pill}. */
public record PillId(UUID value) {

    public PillId {
        Objects.requireNonNull(value, "pill id must not be null");
    }

    public static PillId newId() {
        return new PillId(UUID.randomUUID());
    }

    public static PillId of(UUID value) {
        return new PillId(value);
    }
}
