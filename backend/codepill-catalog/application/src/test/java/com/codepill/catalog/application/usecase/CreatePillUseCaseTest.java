package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.SlugAlreadyInUseException;
import com.codepill.catalog.application.metrics.CatalogMetrics;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.port.out.SavePillPort;
import com.codepill.catalog.application.security.Role;
import com.codepill.catalog.application.usecase.CreatePillUseCase.CreatePillCommand;
import com.codepill.catalog.domain.DomainValidationException;
import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillStatus;
import com.codepill.catalog.domain.PillType;
import com.codepill.catalog.domain.Slug;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePillUseCaseTest {

    @Mock LoadPillPort loadPillPort;
    @Mock SavePillPort savePillPort;
    @Mock PillCachePort pillCachePort;

    SimpleMeterRegistry meterRegistry;
    CreatePillUseCase useCase;

    private static final UUID AUTHOR = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new CreatePillUseCase(loadPillPort, savePillPort, pillCachePort,
                new CatalogMetrics(meterRegistry));
    }

    private static CreatePillCommand command() {
        return new CreatePillCommand("Records Deep Dive", "records-deep-dive",
                "All about records", "{\"blocks\":[]}", PillType.ARTICLE, 240);
    }

    @Test
    void shouldPersistDraftOwnedByCaller_whenSlugIsFree() {
        when(loadPillPort.existsBySlug(new Slug("records-deep-dive"))).thenReturn(false);
        when(savePillPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.create(command(), TestFixtures.caller(AUTHOR, Role.AUTHOR, Role.LEARNER));

        var saved = ArgumentCaptor.forClass(Pill.class);
        verify(savePillPort).save(saved.capture());
        assertThat(saved.getValue().authorId().value()).isEqualTo(AUTHOR);
        assertThat(saved.getValue().status()).isEqualTo(PillStatus.DRAFT);
        assertThat(result.slug()).isEqualTo(new Slug("records-deep-dive"));
        verify(pillCachePort).evictPublishedPages();
    }

    @Test
    void shouldIncrementCreatedCounter_taggedByType() {
        when(loadPillPort.existsBySlug(any())).thenReturn(false);
        when(savePillPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.create(command(), TestFixtures.caller(AUTHOR, Role.AUTHOR));

        var counter = meterRegistry.get(CatalogMetrics.PILL_DRAFTED)
                .tag(CatalogMetrics.TAG_PILL_TYPE, "ARTICLE")
                .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRejectDuplicateSlug_withoutSaving() {
        when(loadPillPort.existsBySlug(new Slug("records-deep-dive"))).thenReturn(true);

        assertThatThrownBy(() -> useCase.create(command(), TestFixtures.caller(AUTHOR, Role.AUTHOR)))
                .isInstanceOf(SlugAlreadyInUseException.class);

        verify(savePillPort, never()).save(any());
        verify(pillCachePort, never()).evictPublishedPages();
    }

    @Test
    void shouldRejectInvalidValues_beforeTouchingPersistence() {
        var invalid = new CreatePillCommand("", "records-deep-dive", null, "{}",
                PillType.ARTICLE, 240);
        when(loadPillPort.existsBySlug(any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.create(invalid, TestFixtures.caller(AUTHOR, Role.AUTHOR)))
                .isInstanceOf(DomainValidationException.class);

        verify(savePillPort, never()).save(any());
    }
}
