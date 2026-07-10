package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.PillNotFoundException;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.security.Caller;
import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillId;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Retrieve a single pill. PUBLISHED pills are visible to any authenticated
 * user and served read-through from the cache; unpublished pills are visible
 * only to their author and to moderators, and answered with 404 for everyone
 * else (existence hiding, SECURITY.md §3.2 rule 2).
 */
public class GetPillUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetPillUseCase.class);

    private final LoadPillPort loadPillPort;
    private final PillCachePort pillCachePort;

    public GetPillUseCase(LoadPillPort loadPillPort, PillCachePort pillCachePort) {
        this.loadPillPort = loadPillPort;
        this.pillCachePort = pillCachePort;
    }

    @PreAuthorize("hasRole('LEARNER')")
    @Transactional(readOnly = true)
    @Observed(name = "usecase", contextualName = "get-pill")
    public Pill get(PillId id, Caller caller) {
        var cached = pillCachePort.getPill(id);
        if (cached.isPresent()) {
            log.debug("pill cache hit", kv("pill_id", id.value()));
            return Pill.fromSnapshot(cached.get());
        }

        var pill = loadPillPort.loadById(id)
                .orElseThrow(() -> new PillNotFoundException(id));

        if (!pill.isPublished() && !pill.isOwnedBy(caller.asAuthor()) && !caller.canModerate()) {
            throw new PillNotFoundException(id);
        }
        if (pill.isPublished()) {
            pillCachePort.putPill(pill.snapshot());
        }
        return pill;
    }
}
