package com.codepill.catalog.domain;

/** Optional short description of a pill: non-blank when present, at most {@value #MAX_LENGTH} characters. */
public record Summary(String value) {

    public static final int MAX_LENGTH = 500;

    public Summary {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("summary must not be blank; absence is modeled as null");
        }
        value = value.strip();
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException("summary must be at most " + MAX_LENGTH + " characters");
        }
    }

    /** Maps absent or blank input to {@code null} (a pill without a summary). */
    public static Summary ofNullable(String value) {
        return (value == null || value.isBlank()) ? null : new Summary(value);
    }
}
