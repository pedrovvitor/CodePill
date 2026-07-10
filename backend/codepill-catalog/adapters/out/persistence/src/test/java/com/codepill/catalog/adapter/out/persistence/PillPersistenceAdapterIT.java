package com.codepill.catalog.adapter.out.persistence;

import com.codepill.catalog.application.SlugAlreadyInUseException;
import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.PillStatus;
import com.codepill.catalog.domain.Slug;
import com.codepill.catalog.domain.Summary;
import com.codepill.catalog.domain.Title;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence round-trips against real PostgreSQL 17 with the Flyway migration
 * applied — the migration itself is exercised on every build
 * (TESTING_QUALITY.md §3.2; in-memory stand-ins are banned).
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        // schema comes from Flyway; Hibernate must only validate it
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.test.database.replace=none"
})
class PillPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired SpringDataPillRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    private PillPersistenceAdapter adapter() {
        return new PillPersistenceAdapter(repository);
    }

    @Test
    void shouldRoundTripPill_withJsonbContent() {
        var adapter = adapter();
        var draft = PersistenceTestFixtures.newDraft("jsonb-round-trip");

        var saved = adapter.save(draft);
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isNotNull();
        assertThat(saved.version()).isZero();

        var reloaded = adapter.loadById(saved.id()).orElseThrow();
        assertThat(reloaded.title()).isEqualTo(draft.title());
        assertThat(reloaded.content()).isEqualTo(draft.content());

        // content really is jsonb, not text
        var pgType = jdbcTemplate.queryForObject(
                "select jsonb_typeof(content) from pills where id = ?",
                String.class, saved.id().value());
        assertThat(pgType).isEqualTo("object");
    }

    @Test
    void shouldEnforceSlugUniqueness_atDatabaseLevel() {
        var adapter = adapter();
        adapter.save(PersistenceTestFixtures.newDraft("unique-slug"));

        assertThatThrownBy(() -> adapter.save(PersistenceTestFixtures.newDraft("unique-slug")))
                .isInstanceOf(SlugAlreadyInUseException.class);
    }

    @Test
    void shouldDetectConcurrentModification_viaOptimisticLocking() {
        var adapter = adapter();
        var saved = adapter.save(PersistenceTestFixtures.newDraft("optimistic-locking"));

        var first = adapter.loadById(saved.id()).orElseThrow();
        var second = adapter.loadById(saved.id()).orElseThrow();

        first.update(new Title("first writer"), first.slug(), Summary.ofNullable(null),
                first.content(), first.type(), first.estimatedDuration());
        adapter.save(first);

        second.update(new Title("second writer"), second.slug(), Summary.ofNullable(null),
                second.content(), second.type(), second.estimatedDuration());
        assertThatThrownBy(() -> adapter.save(second))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldServePublishedFeed_newestFirst_excludingDrafts() {
        var adapter = adapter();
        var older = adapter.save(PersistenceTestFixtures.newDraft("feed-older"));
        var newer = adapter.save(PersistenceTestFixtures.newDraft("feed-newer"));
        adapter.save(PersistenceTestFixtures.newDraft("feed-draft-only"));

        older.publish(Instant.parse("2026-07-08T10:00:00Z"));
        var olderPublished = adapter.save(older);
        newer.publish(Instant.parse("2026-07-09T10:00:00Z"));
        var newerPublished = adapter.save(newer);

        var page = adapter.loadPublished(0, 10);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.pills()).extracting(p -> p.slug().value())
                .containsExactly("feed-newer", "feed-older");
        assertThat(olderPublished.status()).isEqualTo(PillStatus.PUBLISHED);
        assertThat(newerPublished.status()).isEqualTo(PillStatus.PUBLISHED);
    }

    @Test
    void shouldHardDeletePill() {
        var adapter = adapter();
        var saved = adapter.save(PersistenceTestFixtures.newDraft("to-be-deleted"));

        assertThat(adapter.deleteById(saved.id())).isTrue();
        assertThat(adapter.loadById(saved.id())).isEmpty();
        assertThat(adapter.deleteById(PillId.newId())).isFalse();
    }

    @Test
    void shouldDetectStaleSlugCheck_throughExistsBySlug() {
        var adapter = adapter();
        adapter.save(PersistenceTestFixtures.newDraft("exists-check"));

        assertThat(adapter.existsBySlug(new Slug("exists-check"))).isTrue();
        assertThat(adapter.existsBySlug(new Slug("does-not-exist"))).isFalse();
    }
}
