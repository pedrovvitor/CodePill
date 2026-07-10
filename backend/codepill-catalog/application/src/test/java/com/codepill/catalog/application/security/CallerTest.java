package com.codepill.catalog.application.security;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    void shouldRejectNulls() {
        assertThatThrownBy(() -> new Caller(null, Set.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Caller(USER, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldAnswerRoleMembership() {
        var caller = new Caller(USER, Set.of(Role.AUTHOR, Role.LEARNER));

        assertThat(caller.hasRole(Role.AUTHOR)).isTrue();
        assertThat(caller.hasRole(Role.CURATOR)).isFalse();
    }

    @Test
    void shouldTreatCuratorAndAdminAsModerators() {
        assertThat(new Caller(USER, Set.of(Role.CURATOR)).canModerate()).isTrue();
        assertThat(new Caller(USER, Set.of(Role.ADMIN)).canModerate()).isTrue();
        assertThat(new Caller(USER, Set.of(Role.AUTHOR, Role.LEARNER)).canModerate()).isFalse();
    }

    @Test
    void shouldActAsAuthorWithOwnUserId() {
        assertThat(new Caller(USER, Set.of(Role.AUTHOR)).asAuthor().value()).isEqualTo(USER);
    }
}
