package com.codepill.catalog.adapter.in.web;

import com.codepill.catalog.application.security.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallerMapperTest {

    @Test
    void shouldMapSubjectAndKnownRoles() {
        var caller = CallerMapper.from(WebTestFixtures.tokenWithRoles(List.of("ADMIN", "CURATOR")));

        assertThat(caller.userId()).isEqualTo(WebTestFixtures.USER);
        assertThat(caller.roles()).containsExactlyInAnyOrder(Role.ADMIN, Role.CURATOR);
    }

    @Test
    void shouldIgnoreUnknownRoles() {
        var caller = CallerMapper.from(WebTestFixtures.tokenWithRoles(List.of("LEARNER", "COFFEE_LORD")));

        assertThat(caller.roles()).containsExactly(Role.LEARNER);
    }

    @Test
    void shouldMapMissingRolesClaimToEmptySet() {
        var caller = CallerMapper.from(WebTestFixtures.tokenWithRoles(null));

        assertThat(caller.roles()).isEmpty();
    }
}
