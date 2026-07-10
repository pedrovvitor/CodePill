package com.codepill.catalog.bootstrap;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@code @Observed} on use cases (OBSERVABILITY.md §2.3): each
 * invocation produces a span (exported via OTLP) and a timer metric named
 * {@code usecase} with the contextual name as a low-cardinality tag.
 */
@Configuration(proxyBeanMethods = false)
class ObservabilityConfig {

    @Bean
    ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
