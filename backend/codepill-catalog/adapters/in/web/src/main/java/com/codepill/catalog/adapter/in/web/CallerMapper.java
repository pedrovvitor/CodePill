package com.codepill.catalog.adapter.in.web;

import com.codepill.catalog.application.security.Caller;
import com.codepill.catalog.application.security.Role;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Derives the acting user from the validated JWT — the only trusted identity
 * source (SECURITY.md §3.2 rule 1). {@code sub} is the IdP subject; the flat
 * {@code roles} claim is minted by the codepill-api client scope.
 */
final class CallerMapper {

    private CallerMapper() {
    }

    static Caller from(JwtAuthenticationToken authentication) {
        var jwt = authentication.getToken();
        UUID userId = UUID.fromString(jwt.getSubject());
        List<String> claimedRoles = jwt.getClaimAsStringList("roles");
        Set<Role> roles = claimedRoles == null
                ? Set.of()
                : claimedRoles.stream()
                        .map(CallerMapper::parseRole)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toUnmodifiableSet());
        return new Caller(userId, roles);
    }

    private static Role parseRole(String value) {
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException unknownRole) {
            return null; // IdP may mint roles this service does not know — ignore them
        }
    }
}
