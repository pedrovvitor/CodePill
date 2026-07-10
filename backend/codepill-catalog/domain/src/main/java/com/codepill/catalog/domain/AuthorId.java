package com.codepill.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a pill author. Owned by codepill-identity; referenced here as an
 * opaque value, never a cross-service foreign key (ARCHITECTURE.md §3.2).
 */
public record AuthorId(UUID value) {

    public AuthorId {
        Objects.requireNonNull(value, "author id must not be null");
    }

    public static AuthorId of(UUID value) {
        return new AuthorId(value);
    }
}
