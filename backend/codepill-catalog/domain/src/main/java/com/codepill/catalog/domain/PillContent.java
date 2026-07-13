package com.codepill.catalog.domain;

/**
 * Pill body as an opaque JSON document (persisted as jsonb). The domain treats
 * it as text; well-formedness is guaranteed at the web adapter, which only
 * accepts parsed JSON. Size is bounded here so oversized documents never reach
 * the database or the cache, regardless of which adapter produced them.
 */
public record PillContent(String json) {

    public static final int MAX_LENGTH = 65_536;

    public PillContent {
        if (json == null || json.isBlank()) {
            throw new DomainValidationException("content must not be blank");
        }
        if (json.length() > MAX_LENGTH) {
            throw new DomainValidationException(
                    "content must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static PillContent empty() {
        return new PillContent("{}");
    }
}
