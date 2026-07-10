package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.PillNotFoundException;
import com.codepill.catalog.application.SlugAlreadyInUseException;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.port.out.SavePillPort;
import com.codepill.catalog.application.security.Role;
import com.codepill.catalog.application.usecase.UpdatePillUseCase.UpdatePillCommand;
import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillLifecycleException;
import com.codepill.catalog.domain.PillStatus;
import com.codepill.catalog.domain.PillType;
import com.codepill.catalog.domain.Slug;
import com.codepill.catalog.domain.Title;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdatePillUseCaseTest {

    @Mock LoadPillPort loadPillPort;
    @Mock SavePillPort savePillPort;
    @Mock PillCachePort pillCachePort;
    @InjectMocks UpdatePillUseCase useCase;

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static UpdatePillCommand command(UUID pillId, String slug) {
        return new UpdatePillCommand(pillId, "Updated Title", slug, "Updated summary",
                "{\"v\":2}", PillType.QUIZ, 180);
    }

    @Test
    void shouldUpdateOwnPill_keepingSlug_withoutUniquenessCheck() {
        var pill = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(loadPillPort.loadById(pill.id())).thenReturn(Optional.of(pill));
        when(savePillPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.update(command(pill.id().value(), pill.slug().value()),
                TestFixtures.caller(AUTHOR, Role.AUTHOR));

        assertThat(result.title()).isEqualTo(new Title("Updated Title"));
        assertThat(result.type()).isEqualTo(PillType.QUIZ);
        verify(loadPillPort, never()).existsBySlug(any());
        verify(pillCachePort).evictPill(pill.id());
        verify(pillCachePort).evictPublishedPages();
    }

    @Test
    void shouldUpdateSlug_whenNewSlugIsFree() {
        var pill = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(loadPillPort.loadById(pill.id())).thenReturn(Optional.of(pill));
        when(loadPillPort.existsBySlug(new Slug("fresh-slug"))).thenReturn(false);
        when(savePillPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.update(command(pill.id().value(), "fresh-slug"),
                TestFixtures.caller(AUTHOR, Role.AUTHOR));

        assertThat(result.slug()).isEqualTo(new Slug("fresh-slug"));
    }

    @Test
    void shouldRejectSlugChange_whenNewSlugIsTaken() {
        var pill = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(loadPillPort.loadById(pill.id())).thenReturn(Optional.of(pill));
        when(loadPillPort.existsBySlug(new Slug("taken-slug"))).thenReturn(true);

        assertThatThrownBy(() -> useCase.update(command(pill.id().value(), "taken-slug"),
                TestFixtures.caller(AUTHOR, Role.AUTHOR)))
                .isInstanceOf(SlugAlreadyInUseException.class);

        verify(savePillPort, never()).save(any());
    }

    @Test
    void shouldAllowModeratorToUpdateForeignPill() {
        var pill = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(loadPillPort.loadById(pill.id())).thenReturn(Optional.of(pill));
        when(savePillPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.update(command(pill.id().value(), pill.slug().value()),
                TestFixtures.caller(OTHER, Role.CURATOR));

        assertThat(result.title()).isEqualTo(new Title("Updated Title"));
    }

    @Test
    void shouldDenyForeignPill_forPlainAuthor() {
        var pill = TestFixtures.persisted(AUTHOR, PillStatus.DRAFT);
        when(loadPillPort.loadById(pill.id())).thenReturn(Optional.of(pill));

        assertThatThrownBy(() -> useCase.update(command(pill.id().value(), pill.slug().value()),
                TestFixtures.caller(OTHER, Role.AUTHOR)))
                .isInstanceOf(AccessDeniedException.class);

        verify(savePillPort, never()).save(any());
    }

    @Test
    void shouldThrowNotFound_whenPillDoesNotExist() {
        var id = UUID.randomUUID();
        when(loadPillPort.loadById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(command(id, "any-slug"),
                TestFixtures.caller(AUTHOR, Role.AUTHOR)))
                .isInstanceOf(PillNotFoundException.class);
    }

    @Test
    void shouldSurfaceLifecycleViolation_whenPillIsArchived() {
        var archived = TestFixtures.persisted(AUTHOR, PillStatus.ARCHIVED);
        when(loadPillPort.loadById(archived.id())).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> useCase.update(command(archived.id().value(), archived.slug().value()),
                TestFixtures.caller(AUTHOR, Role.AUTHOR)))
                .isInstanceOf(PillLifecycleException.class);

        verify(savePillPort, never()).save(any(Pill.class));
    }
}
