package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.port.out.CachedPillPage;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.port.out.PillPage;
import com.codepill.catalog.domain.DomainValidationException;
import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListPillsUseCaseTest {

    @Mock LoadPillPort loadPillPort;
    @Mock PillCachePort pillCachePort;
    @InjectMocks ListPillsUseCase useCase;

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 101"})
    void shouldRejectInvalidPaging(int page, int size) {
        assertThatThrownBy(() -> useCase.list(page, size))
                .isInstanceOf(DomainValidationException.class);
        verifyNoInteractions(loadPillPort, pillCachePort);
    }

    @Test
    void shouldServePageFromCache_withoutLoading() {
        var published = TestFixtures.persisted(UUID.randomUUID(), PillStatus.PUBLISHED);
        var cached = new CachedPillPage(List.of(published.snapshot()), 1, 0, 20);
        when(pillCachePort.getPublishedPage(0, 20)).thenReturn(Optional.of(cached));

        var result = useCase.list(0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.pills()).extracting(Pill::id).containsExactly(published.id());
        verifyNoInteractions(loadPillPort);
    }

    @Test
    void shouldLoadAndCachePage_onCacheMiss() {
        var published = TestFixtures.persisted(UUID.randomUUID(), PillStatus.PUBLISHED);
        var page = new PillPage(List.of(published), 42, 1, 10);
        when(pillCachePort.getPublishedPage(1, 10)).thenReturn(Optional.empty());
        when(loadPillPort.loadPublished(1, 10)).thenReturn(page);

        var result = useCase.list(1, 10);

        assertThat(result).isSameAs(page);
        verify(pillCachePort).putPublishedPage(
                new CachedPillPage(List.of(published.snapshot()), 42, 1, 10));
    }
}
