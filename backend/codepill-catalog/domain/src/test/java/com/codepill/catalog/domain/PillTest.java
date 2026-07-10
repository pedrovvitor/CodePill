package com.codepill.catalog.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PillTest {

    private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    private static Pill newDraft() {
        return Pill.draft(
                PillId.newId(),
                AuthorId.of(UUID.randomUUID()),
                new Title("Virtual Threads in 5 Minutes"),
                new Slug("virtual-threads-in-5-minutes"),
                new Summary("Java 25 virtual threads, condensed."),
                new PillContent("{\"blocks\":[]}"),
                PillType.ARTICLE,
                new EstimatedDuration(300));
    }

    private static Pill withStatus(Pill pill, PillStatus status) {
        var s = pill.snapshot();
        return Pill.fromSnapshot(new PillSnapshot(
                s.id(), s.authorId(), s.title(), s.slug(), s.summary(), s.content(),
                s.pillType(), status.name(), s.estimatedDurationSeconds(),
                status == PillStatus.PUBLISHED ? NOW : null,
                NOW, NOW, 3L));
    }

    @Nested
    class Drafting {

        @Test
        void shouldStartAsDraft_withNoPersistenceMetadata() {
            var pill = newDraft();

            assertThat(pill.status()).isEqualTo(PillStatus.DRAFT);
            assertThat(pill.isPublished()).isFalse();
            assertThat(pill.publishedAt()).isNull();
            assertThat(pill.createdAt()).isNull();
            assertThat(pill.updatedAt()).isNull();
            assertThat(pill.version()).isZero();
        }
    }

    @Nested
    class Updating {

        @Test
        void shouldReplaceEditableFields_whenDraft() {
            var pill = newDraft();

            pill.update(new Title("New title"), new Slug("new-slug"), null,
                    new PillContent("{\"v\":2}"), PillType.QUIZ, new EstimatedDuration(120));

            assertThat(pill.title()).isEqualTo(new Title("New title"));
            assertThat(pill.slug()).isEqualTo(new Slug("new-slug"));
            assertThat(pill.summary()).isNull();
            assertThat(pill.content()).isEqualTo(new PillContent("{\"v\":2}"));
            assertThat(pill.type()).isEqualTo(PillType.QUIZ);
            assertThat(pill.estimatedDuration()).isEqualTo(new EstimatedDuration(120));
        }

        @Test
        void shouldAllowEditing_whenPublished() {
            var pill = withStatus(newDraft(), PillStatus.PUBLISHED);

            pill.update(new Title("Fixed typo"), pill.slug(), pill.summary(), pill.content(),
                    pill.type(), pill.estimatedDuration());

            assertThat(pill.title()).isEqualTo(new Title("Fixed typo"));
            assertThat(pill.status()).isEqualTo(PillStatus.PUBLISHED);
        }

        @Test
        void shouldRejectEditing_whenArchived() {
            var pill = withStatus(newDraft(), PillStatus.ARCHIVED);

            assertThatThrownBy(() -> pill.update(new Title("t"), new Slug("s"), null,
                    PillContent.empty(), PillType.ARTICLE, new EstimatedDuration(60)))
                    .isInstanceOf(PillLifecycleException.class)
                    .hasMessageContaining("archived");
        }
    }

    @Nested
    class Publishing {

        @Test
        void shouldPublish_whenDraft() {
            var pill = newDraft();

            pill.publish(NOW);

            assertThat(pill.status()).isEqualTo(PillStatus.PUBLISHED);
            assertThat(pill.isPublished()).isTrue();
            assertThat(pill.publishedAt()).isEqualTo(NOW);
        }

        @Test
        void shouldPublish_whenInReview() {
            var pill = withStatus(newDraft(), PillStatus.IN_REVIEW);

            pill.publish(NOW);

            assertThat(pill.isPublished()).isTrue();
        }

        @Test
        void shouldRejectPublishing_whenAlreadyPublished() {
            var pill = withStatus(newDraft(), PillStatus.PUBLISHED);

            assertThatThrownBy(() -> pill.publish(NOW))
                    .isInstanceOf(PillLifecycleException.class)
                    .hasMessageContaining("already published");
        }

        @Test
        void shouldRejectPublishing_whenArchived() {
            var pill = withStatus(newDraft(), PillStatus.ARCHIVED);

            assertThatThrownBy(() -> pill.publish(NOW))
                    .isInstanceOf(PillLifecycleException.class)
                    .hasMessageContaining("archived");
        }
    }

    @Nested
    class Ownership {

        @Test
        void shouldRecognizeOwner() {
            var author = AuthorId.of(UUID.randomUUID());
            var pill = Pill.draft(PillId.newId(), author, new Title("t"), new Slug("t"),
                    null, PillContent.empty(), PillType.ARTICLE, new EstimatedDuration(60));

            assertThat(pill.isOwnedBy(author)).isTrue();
            assertThat(pill.isOwnedBy(AuthorId.of(UUID.randomUUID()))).isFalse();
        }
    }

    @Nested
    class Snapshotting {

        @Test
        void shouldRoundTripThroughSnapshot() {
            var original = withStatus(newDraft(), PillStatus.PUBLISHED);

            var restored = Pill.fromSnapshot(original.snapshot());

            assertThat(restored.snapshot()).isEqualTo(original.snapshot());
            assertThat(restored.id()).isEqualTo(original.id());
            assertThat(restored.authorId()).isEqualTo(original.authorId());
            assertThat(restored.status()).isEqualTo(PillStatus.PUBLISHED);
            assertThat(restored.publishedAt()).isEqualTo(NOW);
            assertThat(restored.version()).isEqualTo(3L);
        }

        @Test
        void shouldRoundTripNullSummary() {
            var pill = newDraft();
            pill.update(pill.title(), pill.slug(), null, pill.content(), pill.type(),
                    pill.estimatedDuration());

            var restored = Pill.fromSnapshot(pill.snapshot());

            assertThat(restored.summary()).isNull();
            assertThat(restored.snapshot().summary()).isNull();
        }
    }
}
