package com.codepill.catalog.adapter.in.web;

import com.codepill.catalog.domain.Pill;
import com.codepill.catalog.domain.PillSnapshot;
import com.codepill.catalog.domain.PillStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class WebTestFixtures {

    static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final Instant CREATED_AT = Instant.parse("2026-07-01T08:00:00Z");
    static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    private WebTestFixtures() {
    }

    static JwtAuthenticationToken tokenWithRoles(Object rolesClaim) {
        var builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(USER.toString());
        if (rolesClaim != null) {
            builder.claim("roles", rolesClaim);
        }
        return new JwtAuthenticationToken(builder.build(), List.of());
    }

    static JwtAuthenticationToken token() {
        return tokenWithRoles(List.of("AUTHOR", "LEARNER"));
    }

    static Pill pill(UUID authorId, PillStatus status) {
        return Pill.fromSnapshot(new PillSnapshot(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                authorId,
                "Virtual Threads in 5 Minutes",
                "virtual-threads-in-5-minutes",
                "Java 25 virtual threads, condensed.",
                "{\"blocks\":[1]}",
                "ARTICLE",
                status.name(),
                300,
                status == PillStatus.PUBLISHED ? NOW : null,
                CREATED_AT,
                CREATED_AT,
                1L));
    }
}
