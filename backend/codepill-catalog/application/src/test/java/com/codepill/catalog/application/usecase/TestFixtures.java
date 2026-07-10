package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.security.Caller;
import com.codepill.catalog.application.security.Role;
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
import java.util.Set;
import java.util.UUID;

final class TestFixtures {

    static final Instant CREATED_AT = Instant.parse("2026-07-01T08:00:00Z");
    static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    private TestFixtures() {
    }

    static Caller caller(UUID userId, Role... roles) {
        return new Caller(userId, Set.of(roles));
    }

    static Pill newDraft(UUID authorId) {
        return Pill.draft(
                PillId.newId(),
                AuthorId.of(authorId),
                new Title("Virtual Threads in 5 Minutes"),
                new Slug("virtual-threads-in-5-minutes"),
                new Summary("Java 25 virtual threads, condensed."),
                new PillContent("{\"blocks\":[]}"),
                PillType.ARTICLE,
                new EstimatedDuration(300));
    }

    /** A pill as it would come back from the persistence adapter. */
    static Pill persisted(UUID authorId, PillStatus status) {
        var draft = newDraft(authorId).snapshot();
        return Pill.fromSnapshot(new PillSnapshot(
                draft.id(), authorId, draft.title(), draft.slug(), draft.summary(),
                draft.content(), draft.pillType(), status.name(),
                draft.estimatedDurationSeconds(),
                status == PillStatus.PUBLISHED ? NOW : null,
                CREATED_AT, CREATED_AT, 1L));
    }
}
