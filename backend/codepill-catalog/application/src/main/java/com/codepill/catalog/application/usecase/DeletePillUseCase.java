package com.codepill.catalog.application.usecase;

import com.codepill.catalog.application.PillNotFoundException;
import com.codepill.catalog.application.port.out.DeletePillPort;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.application.security.Caller;
import com.codepill.catalog.domain.PillId;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Hard-delete a pill. Only the author or a moderator (CURATOR+) may delete.
 * Deletion is a privileged action and is logged as an audit event
 * (SECURITY.md §4.8) — who acted comes from the token, correlated by trace_id.
 */
public class DeletePillUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeletePillUseCase.class);

    private final LoadPillPort loadPillPort;
    private final DeletePillPort deletePillPort;
    private final PillCachePort pillCachePort;

    public DeletePillUseCase(LoadPillPort loadPillPort, DeletePillPort deletePillPort,
                             PillCachePort pillCachePort) {
        this.loadPillPort = loadPillPort;
        this.deletePillPort = deletePillPort;
        this.pillCachePort = pillCachePort;
    }

    @PreAuthorize("hasRole('AUTHOR')")
    @Transactional
    @Observed(name = "usecase", contextualName = "delete-pill")
    public void delete(PillId id, Caller caller) {
        var pill = loadPillPort.loadById(id)
                .orElseThrow(() -> new PillNotFoundException(id));

        if (!pill.isOwnedBy(caller.asAuthor()) && !caller.canModerate()) {
            throw new AccessDeniedException("caller is not the author of this pill");
        }

        deletePillPort.deleteById(id);
        pillCachePort.evictPill(id);
        pillCachePort.evictPublishedPages();
        log.info("pill deleted",
                kv("pill_id", id.value()),
                kv("actor_id", caller.userId()));
    }
}
