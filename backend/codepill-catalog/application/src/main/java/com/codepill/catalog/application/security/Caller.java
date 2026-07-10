package com.codepill.catalog.application.security;

import com.codepill.catalog.domain.AuthorId;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The acting user, derived exclusively from the validated JWT — never from
 * request data (SECURITY.md §3.2 rule 1). Roles gate operations (layer 2);
 * ownership checks in use cases gate instances (layer 3).
 */
public record Caller(UUID userId, Set<Role> roles) {

    public Caller {
        Objects.requireNonNull(userId, "userId must not be null");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** CURATOR and above may act on content they do not own. */
    public boolean canModerate() {
        return hasRole(Role.CURATOR) || hasRole(Role.ADMIN);
    }

    public AuthorId asAuthor() {
        return AuthorId.of(userId);
    }
}
