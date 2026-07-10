package com.codepill.catalog.application.port.out;

import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.Slug;

import java.util.Optional;

/** Read access to pills, implemented by the persistence adapter. */
public interface LoadPillPort {

    Optional<Pill> loadById(PillId id);

    boolean existsBySlug(Slug slug);

    /** Published feed, newest first ({@code published_at DESC, id DESC}). */
    PillPage loadPublished(int page, int size);
}
