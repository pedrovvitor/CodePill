package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.PillNotFoundException;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.security.Role;
import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.PillStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPillUseCaseTest {

    @Mock LoadPillPort loadPillPort;
    @Mock PillCachePort pillCachePort;
    @InjectMocks GetPillUseCase useCase;

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void shouldServeFromCache_withoutLoading() {
        var published = TestFixtures.persisted(AUTHOR, PillStatus.PUBLISHED);
        when(pillCachePort.getPill(published.id())).thenReturn(Optional.of(published.snapshot()));

        var result = useCase.get(published.id(), TestFixtures.caller(OTHER, Role.LEARNER));

        assertThat(result.snapshot()).isEqualTo(published.snapshot());
        verifyNoInteractions(loadPillPort);
    }

    @Test
    void shouldLoadAndCachePublishedPill_onCacheMiss() {
        var published = TestFixtures.persisted(AUTHOR, PillStatus.PUBLISHED);
        when(pillCachePort.getPill(published.id())).thenReturn(Optional.empty());
        when(loadPillPort.loadById(published.id())).thenReturn(Optional.of(published));

        var result = useCase.get(published.id(), TestFixtures.caller(OTHER, Role.LEARNER));

        assertThat(result).isSameAs(published);
        verify(pillCachePort).putPill(published.snapshot());
    }

    @Test
    void shouldThrowNotFound_whenPillDoesNotExist() {
        var id = PillId.newId();
        when(pillCachePort.getPill(id)).thenReturn(Optional.empty());
        when(loadPillPort.loadById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(id, TestFixtures.caller(OTHER, Role.LEARNER)))
                .isInstanceOf(PillNotFoundException.class);
    }

    @Test
    void shouldShowDraftToItsAuthor_withoutCachingIt() {
        var draft = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(pillCachePort.getPill(draft.id())).thenReturn(Optional.empty());
        when(loadPillPort.loadById(draft.id())).thenReturn(Optional.of(draft));

        var result = useCase.get(draft.id(), TestFixtures.caller(AUTHOR, Role.AUTHOR, Role.LEARNER));

        assertThat(result).isSameAs(draft);
        verify(pillCachePort, never()).putPill(any());
    }

    @Test
    void shouldShowDraftToModerators() {
        var draft = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(pillCachePort.getPill(draft.id())).thenReturn(Optional.empty());
        when(loadPillPort.loadById(draft.id())).thenReturn(Optional.of(draft));

        assertThat(useCase.get(draft.id(), TestFixtures.caller(OTHER, Role.CURATOR))).isSameAs(draft);
        assertThat(useCase.get(draft.id(), TestFixtures.caller(OTHER, Role.ADMIN))).isSameAs(draft);
    }

    @Test
    void shouldHideDraftFromOtherUsers_asNotFound() {
        var draft = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(pillCachePort.getPill(draft.id())).thenReturn(Optional.empty());
        when(loadPillPort.loadById(draft.id())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> useCase.get(draft.id(), TestFixtures.caller(OTHER, Role.LEARNER)))
                .isInstanceOf(PillNotFoundException.class);
        verify(pillCachePort, never()).putPill(any());
    }
}
