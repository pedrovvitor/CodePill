package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.PillNotFoundException;
import com.codepill.catalog.application.metrics.CatalogMetrics;
import com.codepill.catalog.application.observability.SpanTags;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.port.out.SavePillPort;
import com.codepill.catalog.application.security.Caller;
import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillId;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * A curator publishes a pill into the public feed. Publishing is a privileged
 * action and is logged as an audit event (SECURITY.md §4.8).
 */
public class PublishPillUseCase {

    private static final Logger log = LoggerFactory.getLogger(PublishPillUseCase.class);

    private final LoadPillPort loadPillPort;
    private final SavePillPort savePillPort;
    private final PillCachePort pillCachePort;
    private final CatalogMetrics metrics;
    private final Clock clock;
    private final ObservationRegistry observations;

    public PublishPillUseCase(LoadPillPort loadPillPort, SavePillPort savePillPort,
                              PillCachePort pillCachePort, CatalogMetrics metrics, Clock clock) {
        this(loadPillPort, savePillPort, pillCachePort, metrics, clock, ObservationRegistry.NOOP);
    }

    public PublishPillUseCase(LoadPillPort loadPillPort, SavePillPort savePillPort,
                              PillCachePort pillCachePort, CatalogMetrics metrics, Clock clock,
                              ObservationRegistry observations) {
        this.loadPillPort = loadPillPort;
        this.savePillPort = savePillPort;
        this.pillCachePort = pillCachePort;
        this.metrics = metrics;
        this.clock = clock;
        this.observations = observations;
    }

    @PreAuthorize("hasRole('CURATOR')")
    @Transactional
    @Observed(name = "usecase", contextualName = "publish-pill")
    public Pill publish(PillId id, Caller caller) {
        SpanTags.put(observations, "codepill.pill.id", id.value());
        var pill = loadPillPort.loadById(id)
                .orElseThrow(() -> new PillNotFoundException(id));

        pill.publish(clock.instant());

        var saved = savePillPort.save(pill);
        pillCachePort.evictPill(id);
        pillCachePort.evictPublishedPages();

        metrics.pillPublished(saved.type());
        if (saved.createdAt() != null) {
            metrics.authoringTime(Duration.between(saved.createdAt(), saved.publishedAt()), saved.type());
        }
        log.info("pill published",
                kv("pill_id", saved.id().value()),
                kv("pill_type", saved.type().name()),
                kv("actor_id", caller.userId()));
        return saved;
    }
}
