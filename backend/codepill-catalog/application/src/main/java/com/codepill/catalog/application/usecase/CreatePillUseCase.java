package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.SlugAlreadyInUseException;
import com.codepill.catalog.application.metrics.CatalogMetrics;
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
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import static net.logstash.logback.argument.StructuredArguments.kv;

/** An author drafts a new pill. */
public class CreatePillUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreatePillUseCase.class);

    private final LoadPillPort loadPillPort;
    private final SavePillPort savePillPort;
    private final PillCachePort pillCachePort;
    private final CatalogMetrics metrics;

    public CreatePillUseCase(LoadPillPort loadPillPort, SavePillPort savePillPort,
                             PillCachePort pillCachePort, CatalogMetrics metrics) {
        this.loadPillPort = loadPillPort;
        this.savePillPort = savePillPort;
        this.pillCachePort = pillCachePort;
        this.metrics = metrics;
    }

    @PreAuthorize("hasRole('AUTHOR')")
    @Transactional
    @Observed(name = "usecase", contextualName = "create-pill")
    public Pill create(CreatePillCommand command, Caller caller) {
        var slug = new Slug(command.slug());
        if (loadPillPort.existsBySlug(slug)) {
            throw new SlugAlreadyInUseException(slug);
        }
        var pill = Pill.draft(
                PillId.newId(),
                caller.asAuthor(),
                new Title(command.title()),
                slug,
                Summary.ofNullable(command.summary()),
                new PillContent(command.content()),
                command.type(),
                new EstimatedDuration(command.estimatedDurationSeconds()));

        var saved = savePillPort.save(pill);
        pillCachePort.evictPublishedPages();
        metrics.pillDrafted(saved.type());
        log.info("pill created",
                kv("pill_id", saved.id().value()),
                kv("pill_type", saved.type().name()));
        return saved;
    }

    public record CreatePillCommand(
            String title,
            String slug,
            String summary,
            String content,
            PillType type,
            int estimatedDurationSeconds) {
    }
}
