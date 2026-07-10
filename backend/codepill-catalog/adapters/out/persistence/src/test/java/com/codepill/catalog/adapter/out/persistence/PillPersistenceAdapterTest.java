package com.codepill.catalog.adapter.out.persistence;

import com.codepill.catalog.application.SlugAlreadyInUseException;
import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.PillStatus;
import com.codepill.catalog.domain.Slug;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PillPersistenceAdapterTest {

    @Mock SpringDataPillRepository repository;
    @InjectMocks PillPersistenceAdapter adapter;

    @Test
    void shouldMapLoadedEntityToDomain() {
        var pill = PersistenceTestFixtures.persisted("loaded-pill", PillStatus.PUBLISHED, 2L);
        when(repository.findById(pill.id().value()))
                .thenReturn(Optional.of(PillEntityMapper.toEntity(pill)));

        var loaded = adapter.loadById(pill.id());

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().snapshot()).isEqualTo(pill.snapshot());
    }

    @Test
    void shouldReturnEmpty_whenPillMissing() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertThat(adapter.loadById(PillId.newId())).isEmpty();
    }

    @Test
    void shouldDelegateSlugExistence() {
        when(repository.existsBySlug("some-slug")).thenReturn(true);

        assertThat(adapter.existsBySlug(new Slug("some-slug"))).isTrue();
    }

    @Test
    void shouldQueryPublishedFeed_withIndexAlignedOrdering() {
        var pill = PersistenceTestFixtures.persisted("feed-pill", PillStatus.PUBLISHED, 1L);
        var pageable = ArgumentCaptor.forClass(PageRequest.class);
        when(repository.findByStatus(any(), pageable.capture()))
                .thenReturn(new PageImpl<>(List.of(PillEntityMapper.toEntity(pill)),
                        PageRequest.of(0, 20), 57));

        var page = adapter.loadPublished(0, 20);

        assertThat(page.totalElements()).isEqualTo(57);
        assertThat(page.pills()).hasSize(1);
        verify(repository).findByStatus(org.mockito.ArgumentMatchers.eq("PUBLISHED"), any());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
    }

    @Test
    void shouldSaveAndReturnPersistedState() {
        var draft = PersistenceTestFixtures.newDraft("saved-pill");
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var saved = adapter.save(draft);

        assertThat(saved.slug()).isEqualTo(new Slug("saved-pill"));
    }

    @Test
    void shouldTranslateSlugConstraintViolation() {
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "ERROR: duplicate key value violates unique constraint \"ux_pills_slug\""));

        assertThatThrownBy(() -> adapter.save(PersistenceTestFixtures.newDraft("dup-slug")))
                .isInstanceOf(SlugAlreadyInUseException.class);
    }

    @Test
    void shouldRethrowOtherIntegrityViolations() {
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("ck_pills_duration_positive"));

        assertThatThrownBy(() -> adapter.save(PersistenceTestFixtures.newDraft("bad-pill")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldDeleteExistingPill_andReportMissingOne() {
        var id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true, false);

        assertThat(adapter.deleteById(PillId.of(id))).isTrue();
        verify(repository).deleteById(id);

        assertThat(adapter.deleteById(PillId.of(id))).isFalse();
        verify(repository, never()).deleteById(org.mockito.ArgumentMatchers.eq(UUID.randomUUID()));
    }
}
