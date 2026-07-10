package com.codepill.catalog.domain;

/** Expected time to complete a pill, in seconds: positive, at most 24 hours (microlearning). */
public record EstimatedDuration(int seconds) {

    public static final int MAX_SECONDS = 86_400;

    public EstimatedDuration {
        if (seconds <= 0) {
            throw new DomainValidationException("estimated duration must be positive");
        }
        if (seconds > MAX_SECONDS) {
            throw new DomainValidationException("estimated duration must be at most 24 hours");
        }
    }
}
