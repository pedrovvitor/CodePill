package com.codepill.catalog.adapter.out.persistence;

import com.codepill.catalog.domain.AuthorId;
import com.codepill.catalog.domain.EstimatedDuration;
import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillContent;
import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.PillSnapshot;
import com.codepill.catalog.domain.PillStatus;
import com.codepill.catalog.domain.PillType;
import com.codepill.catalog.domain.Slug;
import com.codepill.catalog.domain.Summary;
import com.codepill.catalog.domain.Title;

import java.time.Instant;
import java.util.UUID;

final class PersistenceTestFixtures {

    static final Instant CREATED_AT = Instant.parse("2026-07-01T08:00:00Z");
    static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    private PersistenceTestFixtures() {
    }

    static Pill newDraft(String slug) {
        return Pill.draft(
                PillId.newId(),
                AuthorId.of(UUID.randomUUID()),
                new Title("Title for " + slug),
                new Slug(slug),
                new Summary("Summary for " + slug),
                new PillContent("{\"blocks\":[{\"kind\":\"text\",\"body\":\"hi\"}]}"),
                PillType.ARTICLE,
                new EstimatedDuration(300));
    }

    static Pill persisted(String slug, PillStatus status, long version) {
        var s = newDraft(slug).snapshot();
        return Pill.fromSnapshot(new PillSnapshot(
                s.id(), s.authorId(), s.title(), s.slug(), s.summary(), s.content(),
                s.pillType(), status.name(), s.estimatedDurationSeconds(),
                status == PillStatus.PUBLISHED ? NOW : null,
                CREATED_AT, CREATED_AT, version));
    }
}
