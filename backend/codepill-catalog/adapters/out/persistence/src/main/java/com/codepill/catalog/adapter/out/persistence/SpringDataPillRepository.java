package com.codepill.catalog.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository over {@code pills}. Derived queries only; each method
 * is timed by the {@code spring.data.repository.invocations} metric
 * (OBSERVABILITY.md §4.2).
 */
public interface SpringDataPillRepository extends JpaRepository<PillJpaEntity, UUID> {

    boolean existsBySlug(String slug);

    /** Backed by the partial index ix_pills_published_feed when status = PUBLISHED. */
    Page<PillJpaEntity> findByStatus(String status, Pageable pageable);
}
