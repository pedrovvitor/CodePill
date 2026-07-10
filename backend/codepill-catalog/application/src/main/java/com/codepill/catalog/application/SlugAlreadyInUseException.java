package com.codepill.catalog.application;

import com.codepill.catalog.domain.Slug;

/** Slug uniqueness conflict (ux_pills_slug). Mapped to HTTP 409. */
public class SlugAlreadyInUseException extends RuntimeException {

    public SlugAlreadyInUseException(Slug slug) {
        super("slug already in use: " + slug.value());
    }
}
