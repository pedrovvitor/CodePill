package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.observability.SpanTags;
import com.codepill.catalog.application.port.out.CachedPillPage;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.port.out.PillPage;
import com.codepill.catalog.domain.DomainValidationException;
import com.codepill.catalog.domain.Pill;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import static net.logstash.logback.argument.StructuredArguments.kv;

/** The published pill feed, newest first, cached per page with a short TTL. */
public class ListPillsUseCase {

    public static final int MAX_PAGE_SIZE = 100;

    /** Bounds OFFSET cost — an unbounded page number is a cheap DoS lever. */
    public static final int MAX_PAGE = 500;

    private static final Logger log = LoggerFactory.getLogger(ListPillsUseCase.class);

    private final LoadPillPort loadPillPort;
    private final PillCachePort pillCachePort;
    private final ObservationRegistry observations;

    public ListPillsUseCase(LoadPillPort loadPillPort, PillCachePort pillCachePort) {
        this(loadPillPort, pillCachePort, ObservationRegistry.NOOP);
    }

    public ListPillsUseCase(LoadPillPort loadPillPort, PillCachePort pillCachePort,
                            ObservationRegistry observations) {
        this.loadPillPort = loadPillPort;
        this.pillCachePort = pillCachePort;
        this.observations = observations;
    }

    @PreAuthorize("hasRole('LEARNER')")
    @Transactional(readOnly = true)
    @Observed(name = "usecase", contextualName = "list-pills")
    public PillPage list(int page, int size) {
        if (page < 0) {
            throw new DomainValidationException("page must not be negative");
        }
        if (page > MAX_PAGE) {
            throw new DomainValidationException("page must not exceed " + MAX_PAGE);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainValidationException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        SpanTags.put(observations, "codepill.feed.page", page);
        SpanTags.put(observations, "codepill.feed.size", size);

        var cached = pillCachePort.getPublishedPage(page, size);
        if (cached.isPresent()) {
            log.debug("published page cache hit", kv("page", page), kv("size", size));
            return toPage(cached.get());
        }

        var result = loadPillPort.loadPublished(page, size);
        pillCachePort.putPublishedPage(toCached(result));
        return result;
    }

    private static PillPage toPage(CachedPillPage cached) {
        return new PillPage(
                cached.pills().stream().map(Pill::fromSnapshot).toList(),
                cached.totalElements(), cached.page(), cached.size());
    }

    private static CachedPillPage toCached(PillPage page) {
        return new CachedPillPage(
                page.pills().stream().map(Pill::snapshot).toList(),
                page.totalElements(), page.page(), page.size());
    }
}
