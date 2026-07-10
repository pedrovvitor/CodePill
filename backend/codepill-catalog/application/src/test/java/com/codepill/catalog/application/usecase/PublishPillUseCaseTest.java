package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.PillNotFoundException;
import com.codepill.catalog.application.metrics.CatalogMetrics;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.port.out.SavePillPort;
import com.codepill.catalog.application.security.Role;
import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.PillLifecycleException;
import com.codepill.catalog.domain.PillStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishPillUseCaseTest {

    @Mock LoadPillPort loadPillPort;
    @Mock SavePillPort savePillPort;
    @Mock PillCachePort pillCachePort;

    SimpleMeterRegistry meterRegistry;
    PublishPillUseCase useCase;

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID CURATOR = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new PublishPillUseCase(loadPillPort, savePillPort, pillCachePort,
                new CatalogMetrics(meterRegistry), Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldPublishDraft_atClockInstant_andEvictCaches() {
        var draft = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(loadPillPort.loadById(draft.id())).thenReturn(Optional.of(draft));
        when(savePillPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.publish(draft.id(), TestFixtures.caller(CURATOR, Role.CURATOR));

        assertThat(result.isPublished()).isTrue();
        assertThat(result.publishedAt()).isEqualTo(TestFixtures.NOW);
        verify(pillCachePort).evictPill(draft.id());
        verify(pillCachePort).evictPublishedPages();
    }

    @Test
    void shouldRecordPublishMetrics_includingAuthoringTime() {
        var draft = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(loadPillPort.loadById(draft.id())).thenReturn(Optional.of(draft));
        when(savePillPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.publish(draft.id(), TestFixtures.caller(CURATOR, Role.CURATOR));

        assertThat(meterRegistry.get(CatalogMetrics.PILL_PUBLISHED)
                .tag(CatalogMetrics.TAG_PILL_TYPE, "ARTICLE").counter().count()).isEqualTo(1.0);
        var timer = meterRegistry.get(CatalogMetrics.PILL_AUTHORING_TIME)
                .tag(CatalogMetrics.TAG_PILL_TYPE, "ARTICLE").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isEqualTo(Duration.between(TestFixtures.CREATED_AT, TestFixtures.NOW).toSeconds());
    }

    @Test
    void shouldSkipAuthoringTime_whenCreationInstantUnknown() {
        var neverPersisted = TestFixtures.newDraft(AUTHOR);
        when(loadPillPort.loadById(neverPersisted.id())).thenReturn(Optional.of(neverPersisted));
        when(savePillPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.publish(neverPersisted.id(), TestFixtures.caller(CURATOR, Role.CURATOR));

        assertThat(meterRegistry.find(CatalogMetrics.PILL_AUTHORING_TIME).timer()).isNull();
        assertThat(meterRegistry.get(CatalogMetrics.PILL_PUBLISHED)
                .tag(CatalogMetrics.TAG_PILL_TYPE, "ARTICLE").counter().count()).isEqualTo(1.0);
    }

    @Test
    void shouldSurfaceLifecycleViolation_whenAlreadyPublished() {
        var published = TestFixtures.persisted(AUTHOR, PillStatus.PUBLISHED);
        when(loadPillPort.loadById(published.id())).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> useCase.publish(published.id(), TestFixtures.caller(CURATOR, Role.CURATOR)))
                .isInstanceOf(PillLifecycleException.class);

        verify(savePillPort, never()).save(any());
    }

    @Test
    void shouldThrowNotFound_whenPillDoesNotExist() {
        var id = PillId.newId();
        when(loadPillPort.loadById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.publish(id, TestFixtures.caller(CURATOR, Role.CURATOR)))
                .isInstanceOf(PillNotFoundException.class);
    }
}
