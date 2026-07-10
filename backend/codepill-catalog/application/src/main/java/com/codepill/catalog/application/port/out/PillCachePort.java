package com.codepill.catalog.application.port.out;

import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.PillSnapshot;

import java.util.Optional;

/**
 * Read-through cache for retrieval use cases, implemented by the Redis adapter.
 *
 * <p>Contract: only PUBLISHED pills may be cached (drafts have per-caller
 * visibility). Implementations must fail open — a cache outage degrades to
 * database reads, never to request failures.
 */
public interface PillCachePort {

    Optional<PillSnapshot> getPill(PillId id);

    void putPill(PillSnapshot snapshot);

    void evictPill(PillId id);

    Optional<CachedPillPage> getPublishedPage(int page, int size);

    void putPublishedPage(CachedPillPage page);

    /** Invalidates every cached feed page (any write may reorder the feed). */
    void evictPublishedPages();
}
