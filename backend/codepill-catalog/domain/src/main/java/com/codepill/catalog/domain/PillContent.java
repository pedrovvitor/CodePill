package com.codepill.catalog.domain;

/**
 * Pill body as an opaque JSON document (persisted as jsonb). The domain treats
 * it as text; well-formedness is guaranteed at the web adapter, which only
 * accepts parsed JSON.
 */
public record PillContent(String json) {

    public PillContent {
        if (json == null || json.isBlank()) {
            throw new DomainValidationException("content must not be blank");
        }
    }

    public static PillContent empty() {
        return new PillContent("{}");
    }
}
