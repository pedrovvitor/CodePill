package com.codepill.catalog.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Serialization-friendly memento of a {@link Pill} (JDK types only). The single
 * exchange format between the domain and the persistence/cache adapters, so
 * each side keeps its own model (ARCHITECTURE.md §3.1 rule 4).
 *
 * <p>{@code createdAt}/{@code updatedAt} are {@code null} until first persisted;
 * {@code publishedAt} is {@code null} unless status is PUBLISHED.
 */
public record PillSnapshot(
        UUID id,
        UUID authorId,
        String title,
        String slug,
        String summary,
        String content,
        String pillType,
        String status,
        int estimatedDurationSeconds,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
