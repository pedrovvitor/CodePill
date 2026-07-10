package com.codepill.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests against real PostgreSQL and Redis
 * (TESTING_QUALITY.md §3.2): the complete security matrix per endpoint
 * (anonymous → 401, wrong role → 403, correct role → 2xx), the CRUD+publish
 * flow with ownership and existence hiding, read-through caching, and the
 * Prometheus metrics endpoint. JWTs are minted with the spring-security-test
 * jwt() post-processor; the live Keycloak flow is verified against the
 * docker-compose stack separately.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CatalogApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired MockMvc mockMvc;
    @Autowired StringRedisTemplate redisTemplate;

    private static final UUID AUTHOR_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_AUTHOR_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    private static RequestPostProcessor user(UUID subject, String... roles) {
        return jwt()
                .jwt(jwt -> jwt.subject(subject.toString()).claim("roles", List.of(roles)))
                .authorities(Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String body(String slug, String title) {
        return """
                {
                  "title": "%s",
                  "slug": "%s",
                  "summary": "Learn something small.",
                  "content": {"blocks": [{"kind": "text", "body": "hello"}]},
                  "type": "ARTICLE",
                  "estimatedDurationSeconds": 300
                }
                """.formatted(title, slug);
    }

    private String createPillAs(UUID author, String slug) throws Exception {
        var location = mockMvc.perform(post("/api/v1/pills")
                        .with(user(author, "AUTHOR", "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(slug, "Pill " + slug)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    // ── Security matrix: anonymous → 401 (SECURITY.md §3.2 rule 3) ─────────

    @Test
    void anonymousRequests_areRejectedWith401_onEveryEndpoint() throws Exception {
        var id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/pills"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
        mockMvc.perform(get("/api/v1/pills/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/pills").contentType(MediaType.APPLICATION_JSON)
                .content(body("anon-slug", "t"))).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/pills/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body("anon-slug", "t"))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/pills/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/pills/" + id + "/publish")).andExpect(status().isUnauthorized());
    }

    // ── Security matrix: wrong role → 403 ──────────────────────────────────

    @Test
    void learnerCannotWrite_403() throws Exception {
        mockMvc.perform(post("/api/v1/pills")
                        .with(user(UUID.randomUUID(), "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("learner-cannot-write", "t")))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorCannotPublish_403() throws Exception {
        var id = createPillAs(AUTHOR_ID, "author-cannot-publish");

        mockMvc.perform(post("/api/v1/pills/" + id + "/publish")
                        .with(user(AUTHOR_ID, "AUTHOR", "LEARNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreignAuthorCannotUpdateOrDelete_403_ownershipLayer() throws Exception {
        var id = createPillAs(AUTHOR_ID, "ownership-checked");

        mockMvc.perform(put("/api/v1/pills/" + id)
                        .with(user(OTHER_AUTHOR_ID, "AUTHOR", "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ownership-checked", "hijacked")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/pills/" + id)
                        .with(user(OTHER_AUTHOR_ID, "AUTHOR", "LEARNER")))
                .andExpect(status().isForbidden());
    }

    // ── Security matrix: correct role → 2xx, incl. role hierarchy ──────────

    @Test
    void adminInheritsAuthorRights_throughRoleHierarchy() throws Exception {
        mockMvc.perform(post("/api/v1/pills")
                        .with(user(UUID.randomUUID(), "ADMIN"))   // ROLE_ADMIN only
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("admin-via-hierarchy", "t")))
                .andExpect(status().isCreated());
    }

    // ── Existence hiding (SECURITY.md §3.2 rule 2) ──────────────────────────

    @Test
    void draftsAreHiddenFromOtherUsers_as404_butVisibleToAuthorAndCurator() throws Exception {
        var id = createPillAs(AUTHOR_ID, "drafts-are-hidden");

        mockMvc.perform(get("/api/v1/pills/" + id)
                        .with(user(UUID.randomUUID(), "LEARNER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/pills/" + id)
                        .with(user(AUTHOR_ID, "AUTHOR", "LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/pills/" + id)
                        .with(user(UUID.randomUUID(), "CURATOR", "AUTHOR", "LEARNER")))
                .andExpect(status().isOk());
    }

    // ── Full lifecycle with caching ─────────────────────────────────────────

    @Test
    void fullLifecycle_createPublishReadCacheUpdateDelete() throws Exception {
        var id = createPillAs(AUTHOR_ID, "full-lifecycle");

        // curator publishes
        mockMvc.perform(post("/api/v1/pills/" + id + "/publish")
                        .with(user(UUID.randomUUID(), "CURATOR", "AUTHOR", "LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").exists());

        // learner reads → served and cached read-through in Redis
        mockMvc.perform(get("/api/v1/pills/" + id)
                        .with(user(UUID.randomUUID(), "LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.blocks[0].body").value("hello"));
        assertThat(redisTemplate.hasKey("codepill:catalog:pill:" + id)).isTrue();

        // cached read still correct
        mockMvc.perform(get("/api/v1/pills/" + id)
                        .with(user(UUID.randomUUID(), "LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("full-lifecycle"));

        // appears in the published feed
        mockMvc.perform(get("/api/v1/pills").param("size", "50")
                        .with(user(UUID.randomUUID(), "LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == 'full-lifecycle')]").exists());

        // author updates own pill → pill cache evicted
        mockMvc.perform(put("/api/v1/pills/" + id)
                        .with(user(AUTHOR_ID, "AUTHOR", "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("full-lifecycle", "Updated Title")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
        assertThat(redisTemplate.hasKey("codepill:catalog:pill:" + id)).isFalse();

        // duplicate slug on another create → 409
        mockMvc.perform(post("/api/v1/pills")
                        .with(user(AUTHOR_ID, "AUTHOR", "LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("full-lifecycle", "dup")))
                .andExpect(status().isConflict());

        // owner deletes
        mockMvc.perform(delete("/api/v1/pills/" + id)
                        .with(user(AUTHOR_ID, "AUTHOR", "LEARNER")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/pills/" + id)
                        .with(user(AUTHOR_ID, "AUTHOR", "LEARNER")))
                .andExpect(status().isNotFound());
    }

    // ── Observability endpoints (OBSERVABILITY.md §4) ───────────────────────

    @Test
    void healthProbes_areAnonymous_andMetricsExposeBusinessCounters() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

        createPillAs(AUTHOR_ID, "metrics-probe-" + UUID.randomUUID().toString().substring(0, 8));

        var prometheus = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(prometheus).contains("codepill_pill_drafted_total");
        assertThat(prometheus).contains("http_server_requests");
    }

    @Test
    void otherActuatorEndpoints_requireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }
}
