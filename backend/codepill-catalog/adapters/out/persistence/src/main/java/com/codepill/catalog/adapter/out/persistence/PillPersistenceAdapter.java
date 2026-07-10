package com.codepill.catalog.adapter.out.persistence;

import com.codepill.catalog.application.SlugAlreadyInUseException;
import com.codepill.catalog.application.port.out.DeletePillPort;
import com.codepill.catalog.application.port.out.LoadPillPort;
import com.codepill.catalog.application.port.out.PillPage;
import com.codepill.catalog.application.port.out.SavePillPort;
import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.PillStatus;
import com.codepill.catalog.domain.Slug;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implements the pill ports against PostgreSQL. Transactions are owned by the
 * use cases (ARCHITECTURE.md §3.1 rule 6); this adapter never opens its own.
 */
@Component
public class PillPersistenceAdapter implements LoadPillPort, SavePillPort, DeletePillPort {

    /** Feed ordering matching the partial index (published_at DESC, id DESC). */
    private static final Sort FEED_ORDER =
            Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final SpringDataPillRepository repository;

    public PillPersistenceAdapter(SpringDataPillRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Pill> loadById(PillId id) {
        return repository.findById(id.value()).map(PillEntityMapper::toDomain);
    }

    @Override
    public boolean existsBySlug(Slug slug) {
        return repository.existsBySlug(slug.value());
    }

    @Override
    public PillPage loadPublished(int page, int size) {
        var result = repository.findByStatus(PillStatus.PUBLISHED.name(),
                PageRequest.of(page, size, FEED_ORDER));
        return new PillPage(
                result.getContent().stream().map(PillEntityMapper::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public Pill save(Pill pill) {
        try {
            // flush inside the use-case transaction so constraint violations
            // surface here and can be translated, not at commit time
            var saved = repository.saveAndFlush(PillEntityMapper.toEntity(pill));
            return PillEntityMapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("ux_pills_slug")) {
                throw new SlugAlreadyInUseException(pill.slug());
            }
            throw e;
        }
    }

    @Override
    public boolean deleteById(PillId id) {
        if (!repository.existsById(id.value())) {
            return false;
        }
        repository.deleteById(id.value());
        return true;
    }
}
