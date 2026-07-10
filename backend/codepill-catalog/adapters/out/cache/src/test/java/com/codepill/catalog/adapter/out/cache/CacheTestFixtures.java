package com.codepill.catalog.adapter.out.cache;

import com.codepill.catalog.application.port.out.CachedPillPage;
import com.codepill.catalog.domain.PillSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class CacheTestFixtures {

    static final Instant CREATED_AT = Instant.parse("2026-07-01T08:00:00Z");
    static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    private CacheTestFixtures() {
    }

    static PillSnapshot publishedSnapshot(UUID id) {
        return new PillSnapshot(
                id, UUID.randomUUID(),
                "Virtual Threads in 5 Minutes", "virtual-threads-in-5-minutes",
                "Java 25 virtual threads, condensed.", "{\"blocks\":[1]}",
                "ARTICLE", "PUBLISHED", 300, NOW, CREATED_AT, CREATED_AT, 1L);
    }

    static CachedPillPage page(int page, int size) {
        return new CachedPillPage(List.of(publishedSnapshot(UUID.randomUUID())), 42, page, size);
    }
}
