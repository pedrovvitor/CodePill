package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.PillNotFoundException;
import com.codepill.catalog.application.SlugAlreadyInUseException;
import com.codepill.catalog.application.observability.SpanTags;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.port.out.SavePillPort;
import com.codepill.catalog.application.security.Caller;
import com.codepill.catalog.domain.EstimatedDuration;
import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillContent;
import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.PillType;
import com.codepill.catalog.domain.Slug;
import com.codepill.catalog.domain.Summary;
import com.codepill.catalog.domain.Title;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Edit a pill. Role gates the operation; ownership gates the instance —
 * only the author or a moderator (CURATOR+) may edit (SECURITY.md §3.2 layer 3).
 */
public class UpdatePillUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdatePillUseCase.class);

    private final LoadPillPort loadPillPort;
    private final SavePillPort savePillPort;
    private final PillCachePort pillCachePort;
    private final ObservationRegistry observations;

    public UpdatePillUseCase(LoadPillPort loadPillPort, SavePillPort savePillPort,
                             PillCachePort pillCachePort) {
        this(loadPillPort, savePillPort, pillCachePort, ObservationRegistry.NOOP);
    }

    public UpdatePillUseCase(LoadPillPort loadPillPort, SavePillPort savePillPort,
                             PillCachePort pillCachePort, ObservationRegistry observations) {
        this.loadPillPort = loadPillPort;
        this.savePillPort = savePillPort;
        this.pillCachePort = pillCachePort;
        this.observations = observations;
    }

    @PreAuthorize("hasRole('AUTHOR')")
    @Transactional
    @Observed(name = "usecase", contextualName = "update-pill")
    public Pill update(UpdatePillCommand command, Caller caller) {
        var id = PillId.of(command.pillId());
        SpanTags.put(observations, "codepill.pill.id", id.value());
        var pill = loadPillPort.loadById(id)
                .orElseThrow(() -> new PillNotFoundException(id));

        if (!pill.isOwnedBy(caller.asAuthor()) && !caller.canModerate()) {
            throw new AccessDeniedException("caller is not the author of this pill");
        }

        var newSlug = new Slug(command.slug());
        if (!pill.slug().equals(newSlug) && loadPillPort.existsBySlug(newSlug)) {
            throw new SlugAlreadyInUseException(newSlug);
        }

        pill.update(
                new Title(command.title()),
                newSlug,
                Summary.ofNullable(command.summary()),
                new PillContent(command.content()),
                command.type(),
                new EstimatedDuration(command.estimatedDurationSeconds()));

        var saved = savePillPort.save(pill);
        pillCachePort.evictPill(id);
        pillCachePort.evictPublishedPages();
        log.info("pill updated", kv("pill_id", saved.id().value()));
        return saved;
    }

    public record UpdatePillCommand(
            UUID pillId,
            String title,
            String slug,
            String summary,
            String content,
            PillType type,
            int estimatedDurationSeconds) {
    }
}
