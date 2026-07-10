package com.codepill.catalog.domain;

/** Pill title: non-blank, at most {@value #MAX_LENGTH} characters. */
public record Title(String value) {

    public static final int MAX_LENGTH = 160;

    public Title {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("title must not be blank");
        }
        value = value.strip();
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException("title must be at most " + MAX_LENGTH + " characters");
        }
    }
}
