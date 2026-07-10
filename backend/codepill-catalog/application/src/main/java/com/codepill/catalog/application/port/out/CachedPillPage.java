package com.codepill.catalog.application.port.out;

import com.codepill.catalog.domain.PillSnapshot;

import java.util.List;

/** Serialization-friendly form of a {@link PillPage} for the cache adapter. */
public record CachedPillPage(List<PillSnapshot> pills, long totalElements, int page, int size) {
}
