package com.codepill.catalog.application.observability;

import io.micrometer.observation.ObservationRegistry;

/**
 * OBSERVABILITY.md §2 rule 4 — IDs and business context belong in span
 * attributes namespaced {@code codepill.*}, never in span names. Use cases run
 * inside the observation opened by the {@code @Observed} aspect; this helper
 * enriches that current observation and is a strict no-op when none is active
 * (plain unit tests, NOOP registry), so use cases stay framework-agnostic.
 */
public final class SpanTags {

    private SpanTags() {
    }

    public static void put(ObservationRegistry registry, String key, Object value) {
        if (registry == null || value == null) {
            return;
        }
        var current = registry.getCurrentObservation();
        if (current == null || current.isNoop()) {
            return;
        }
        current.highCardinalityKeyValue(key, String.valueOf(value));
    }
}
