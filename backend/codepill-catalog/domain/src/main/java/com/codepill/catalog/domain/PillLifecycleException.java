package com.codepill.catalog.domain;

/** An operation is not allowed in the pill's current lifecycle state. Mapped to HTTP 409. */
public class PillLifecycleException extends RuntimeException {

    public PillLifecycleException(String message) {
        super(message);
    }
}
