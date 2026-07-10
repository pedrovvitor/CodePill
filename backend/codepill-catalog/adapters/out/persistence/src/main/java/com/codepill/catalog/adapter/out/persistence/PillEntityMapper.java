package com.codepill.catalog.adapter.out.persistence;

import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillSnapshot;

/** Explicit mapping between the JPA entity and the domain aggregate, via {@link PillSnapshot}. */
final class PillEntityMapper {

    private PillEntityMapper() {
    }

    static PillJpaEntity toEntity(Pill pill) {
        var s = pill.snapshot();
        return new PillJpaEntity(
                s.id(),
                s.authorId(),
                s.title(),
                s.slug(),
                s.summary(),
                s.content(),
                s.pillType(),
                s.status(),
                s.estimatedDurationSeconds(),
                s.publishedAt(),
                // createdAt == null marks a never-persisted pill: version null → JPA insert
                s.createdAt() == null ? null : s.version(),
                s.createdAt(),
                s.updatedAt());
    }

    static Pill toDomain(PillJpaEntity entity) {
        return Pill.fromSnapshot(new PillSnapshot(
                entity.getId(),
                entity.getAuthorId(),
                entity.getTitle(),
                entity.getSlug(),
                entity.getSummary(),
                entity.getContent(),
                entity.getPillType(),
                entity.getStatus(),
                entity.getEstimatedDurationSeconds(),
                entity.getPublishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion() == null ? 0L : entity.getVersion()));
    }
}
