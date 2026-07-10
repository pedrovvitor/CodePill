package com.codepill.catalog.adapter.in.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A pill as exposed by the API. Distinct from the domain model and the JPA entity. */
public record PillResponse(
        UUID id,
        UUID authorId,
        String title,
        String slug,
        String summary,
        Map<String, Object> content,
        String type,
        String status,
        int estimatedDurationSeconds,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
