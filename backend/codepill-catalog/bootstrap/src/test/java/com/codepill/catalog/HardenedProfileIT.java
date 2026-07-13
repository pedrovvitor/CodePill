package com.codepill.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the hardened (non-local) security posture that CatalogApiIT cannot:
 * with {@code prometheus-public=false} the scrape endpoint joins the
 * deny-by-default surface (SECURITY.md §3.3), and with rate limiting enabled a
 * caller exceeding the write budget receives 429 with Retry-After
 * (SECURITY.md §4.6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "codepill.security.prometheus-public=false",
        "codepill.rate-limit.enabled=true",
        "codepill.rate-limit.capacity=2",
        "codepill.rate-limit.refill-period=1m"
})
@Testcontainers(disabledWithoutDocker = true)
class HardenedProfileIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired MockMvc mockMvc;

    private static RequestPostProcessor curator(UUID subject) {
        return jwt()
                .jwt(jwt -> jwt.subject(subject.toString()).claim("roles", List.of("CURATOR")))
                .authorities(new SimpleGrantedAuthority("ROLE_CURATOR"));
    }

    @Test
    void prometheusScrape_requiresAuthentication_whenNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void healthProbes_stayAnonymous() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void writesBeyondCapacity_areRejectedWith429_andRetryAfter() throws Exception {
        var subject = UUID.randomUUID();
        // capacity=2 — the bucket counts attempts regardless of their outcome
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/pills/" + UUID.randomUUID() + "/publish")
                            .with(curator(subject))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post("/api/v1/pills/" + UUID.randomUUID() + "/publish")
                        .with(curator(subject))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
