package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.PillNotFoundException;
import com.codepill.catalog.application.port.out.DeletePillPort;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeletePillUseCaseTest {

    @Mock LoadPillPort loadPillPort;
    @Mock DeletePillPort deletePillPort;
    @Mock PillCachePort pillCachePort;
    @InjectMocks DeletePillUseCase useCase;

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void shouldDeleteOwnPill_andEvictCaches() {
        var pill = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(loadPillPort.loadById(pill.id())).thenReturn(Optional.of(pill));

        useCase.delete(pill.id(), TestFixtures.caller(AUTHOR, Role.AUTHOR));

        verify(deletePillPort).deleteById(pill.id());
        verify(pillCachePort).evictPill(pill.id());
        verify(pillCachePort).evictPublishedPages();
    }

    @Test
    void shouldAllowAdminToDeleteForeignPill() {
        var pill = TestFixtures.persisted(AUTHOR, PillStatus.PUBLISHED);
        when(loadPillPort.loadById(pill.id())).thenReturn(Optional.of(pill));

        useCase.delete(pill.id(), TestFixtures.caller(OTHER, Role.ADMIN));

        verify(deletePillPort).deleteById(pill.id());
    }

    @Test
    void shouldDenyForeignPill_forPlainAuthor() {
        var pill = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(loadPillPort.loadById(pill.id())).thenReturn(Optional.of(pill));

        assertThatThrownBy(() -> useCase.delete(pill.id(), TestFixtures.caller(OTHER, Role.AUTHOR)))
                .isInstanceOf(AccessDeniedException.class);

        verify(deletePillPort, never()).deleteById(any());
    }

    @Test
    void shouldThrowNotFound_whenPillDoesNotExist() {
        var id = PillId.newId();
        when(loadPillPort.loadById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.delete(id, TestFixtures.caller(AUTHOR, Role.AUTHOR)))
                .isInstanceOf(PillNotFoundException.class);
    }
}
