package com.codepill.catalog.domain;

/** A value violates a domain invariant. Mapped to HTTP 400 by the web adapter. */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
