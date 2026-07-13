package com.codepill.catalog.bootstrap;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SECURITY.md §4.6 — write endpoints are throttled per caller; reads are not.
 * The bucket key is the validated JWT subject (never client-supplied), falling
 * back to the remote address for the anonymous edge case.
 */
class WriteRateLimitFilterTest {

    private static final int CAPACITY = 3;

    SimpleMeterRegistry meterRegistry;
    WriteRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new WriteRateLimitFilter(CAPACITY, Duration.ofMinutes(1), meterRegistry);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReject_withProblemJsonAnd429_whenCapacityExhausted() throws Exception {
        MockHttpServletResponse last = null;
        for (int i = 0; i < CAPACITY + 1; i++) {
            last = doWrite("10.0.0.1");
        }

        assertThat(last.getStatus()).isEqualTo(429);
        assertThat(last.getContentType()).isEqualTo("application/problem+json");
        assertThat(last.getHeader("Retry-After")).isNotNull();
        assertThat(last.getContentAsString()).contains("\"status\":429");
        assertThat(meterRegistry.counter("codepill.api.rate_limited", "method", "POST").count())
                .isEqualTo(1.0);
    }

    @Test
    void shouldAllowWrites_underCapacity() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            assertThat(doWrite("10.0.0.2").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void shouldNotThrottleReads() throws Exception {
        for (int i = 0; i < CAPACITY * 3; i++) {
            var request = new MockHttpServletRequest("GET", "/api/v1/pills");
            request.setRemoteAddr("10.0.0.3");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void shouldNotThrottleNonApiPaths() throws Exception {
        for (int i = 0; i < CAPACITY * 3; i++) {
            var request = new MockHttpServletRequest("POST", "/actuator/loggers/root");
            request.setRemoteAddr("10.0.0.4");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void shouldTrackCallersIndependently() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            assertThat(doWrite("10.0.0.5").getStatus()).isEqualTo(200);
        }
        assertThat(doWrite("10.0.0.5").getStatus()).isEqualTo(429);

        // a different caller is unaffected
        assertThat(doWrite("10.0.0.6").getStatus()).isEqualTo(200);
    }

    @Test
    void shouldKeyAuthenticatedCallers_byTokenSubject_notByAddress() throws Exception {
        authenticateAs("11111111-0000-0000-0000-000000000001");
        for (int i = 0; i < CAPACITY; i++) {
            assertThat(doWrite("10.0.0.7").getStatus()).isEqualTo(200);
        }
        // same subject from a different address is still throttled
        assertThat(doWrite("10.0.0.8").getStatus()).isEqualTo(429);

        // a different subject from the same address is not
        authenticateAs("11111111-0000-0000-0000-000000000002");
        assertThat(doWrite("10.0.0.8").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse doWrite(String remoteAddr) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/pills");
        request.setRemoteAddr(remoteAddr);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static void authenticateAs(String subject) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_AUTHOR"))));
    }
}
