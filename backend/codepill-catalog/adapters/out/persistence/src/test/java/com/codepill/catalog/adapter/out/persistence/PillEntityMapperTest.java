package com.codepill.catalog.adapter.out.persistence;

import com.codepill.catalog.domain.PillStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PillEntityMapperTest {

    @Test
    void shouldMarkNeverPersistedPill_asNewEntity_withNullVersion() {
        var entity = PillEntityMapper.toEntity(PersistenceTestFixtures.newDraft("fresh-pill"));

        assertThat(entity.getVersion()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void shouldCarryVersion_forAlreadyPersistedPill() {
        var entity = PillEntityMapper.toEntity(
                PersistenceTestFixtures.persisted("known-pill", PillStatus.PUBLISHED, 7L));

        assertThat(entity.getVersion()).isEqualTo(7L);
        assertThat(entity.getPublishedAt()).isEqualTo(PersistenceTestFixtures.NOW);
        assertThat(entity.getCreatedAt()).isEqualTo(PersistenceTestFixtures.CREATED_AT);
    }

    @Test
    void shouldRoundTripAllFields() {
        var original = PersistenceTestFixtures.persisted("round-trip", PillStatus.PUBLISHED, 3L);

        var restored = PillEntityMapper.toDomain(PillEntityMapper.toEntity(original));

        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
    }

    @Test
    void shouldMapNullSummary_andNullVersionDefaults() {
        var draft = PersistenceTestFixtures.newDraft("no-summary");
        draft.update(draft.title(), draft.slug(), null, draft.content(), draft.type(),
                draft.estimatedDuration());

        var restored = PillEntityMapper.toDomain(PillEntityMapper.toEntity(draft));

        assertThat(restored.summary()).isNull();
        assertThat(restored.version()).isZero();
    }
}
