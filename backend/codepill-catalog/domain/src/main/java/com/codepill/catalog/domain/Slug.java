package com.codepill.catalog.domain;

import java.util.regex.Pattern;

/**
 * URL slug: lowercase alphanumeric words separated by single hyphens,
 * at most {@value #MAX_LENGTH} characters. Unique per pill (ux_pills_slug).
 */
public record Slug(String value) {

    public static final int MAX_LENGTH = 180;
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public Slug {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("slug must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException("slug must be at most " + MAX_LENGTH + " characters");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new DomainValidationException(
                    "slug must be lowercase alphanumeric words separated by single hyphens");
        }
    }
}
