package com.codepill.catalog.application.metrics;

import com.codepill.catalog.domain.PillType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;

/**
 * Business metrics for the catalog service (OBSERVABILITY.md §4.3).
 * Tags are low-cardinality enums only — never IDs.
 */
public class CatalogMetrics {

    // "created" is a reserved OpenMetrics suffix (the Prometheus client would
    // silently rename codepill_pill_created_total → codepill_pill_total), so
    // the drafting event is named "drafted"
    public static final String PILL_DRAFTED = "codepill.pill.drafted";
    public static final String PILL_PUBLISHED = "codepill.pill.published";
    public static final String PILL_AUTHORING_TIME = "codepill.pill.authoring_time";
    public static final String TAG_PILL_TYPE = "pill_type";

    private final MeterRegistry registry;

    public CatalogMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    public void pillDrafted(PillType type) {
        Counter.builder(PILL_DRAFTED)
                .description("Pills drafted by authors")
                .tag(TAG_PILL_TYPE, type.name())
                .register(registry)
                .increment();
    }

    public void pillPublished(PillType type) {
        Counter.builder(PILL_PUBLISHED)
                .description("Pills published by curators")
                .tag(TAG_PILL_TYPE, type.name())
                .register(registry)
                .increment();
    }

    public void authoringTime(Duration duration, PillType type) {
        Timer.builder(PILL_AUTHORING_TIME)
                .description("Time from pill creation to publication")
                .publishPercentileHistogram()
                .tag(TAG_PILL_TYPE, type.name())
                .register(registry)
                .record(duration);
    }
}
