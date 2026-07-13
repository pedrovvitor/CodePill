package com.codepill.catalog.bootstrap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * SECURITY.md §4.6 — token-bucket rate limiting on write endpoints, enforced
 * in-service until a gateway exists. Runs after the security filter chain so
 * the bucket key is the validated JWT subject. Buckets live in a Caffeine
 * cache bounded by size and idle time.
 */
class WriteRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WriteRateLimitFilter.class);
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final long capacity;
    private final Duration refillPeriod;
    private final MeterRegistry meterRegistry;
    private final Cache<String, Bucket> buckets;

    WriteRateLimitFilter(long capacity, Duration refillPeriod, MeterRegistry meterRegistry) {
        this.capacity = capacity;
        this.refillPeriod = Objects.requireNonNull(refillPeriod, "refillPeriod must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.buckets = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(refillPeriod.multipliedBy(10))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(WRITE_METHODS.contains(request.getMethod())
                && request.getRequestURI().startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var bucket = buckets.get(clientKey(request), key -> newBucket());
        var probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        meterRegistry.counter("codepill.api.rate_limited", "method", request.getMethod())
                .increment();
        log.warn("write rate limit exceeded",
                kv("method", request.getMethod()),
                kv("path", request.getRequestURI()),
                kv("retry_after_s", retryAfterSeconds));

        response.setStatus(429);
        response.setContentType("application/problem+json");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write("""
                {"type":"about:blank","title":"Too many requests","status":429,\
                "detail":"write rate limit exceeded; retry later"}""");
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, refillPeriod))
                .build();
    }

    /** SECURITY.md §3.2 rule 1 — identity from the validated token, never from the request. */
    private static String clientKey(HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication()
                instanceof JwtAuthenticationToken token) {
            return "sub:" + token.getToken().getSubject();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
