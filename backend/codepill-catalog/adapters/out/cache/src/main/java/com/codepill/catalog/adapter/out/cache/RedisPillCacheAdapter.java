package com.codepill.catalog.adapter.out.cache;

import com.codepill.catalog.application.port.out.CachedPillPage;
import com.codepill.catalog.application.port.out.PillCachePort;
import com.codepill.catalog.domain.PillId;
import com.codepill.catalog.domain.PillSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Redis-backed {@link PillCachePort}.
 *
 * <p>Layout (all keys under {@code codepill:catalog:}):
 * <ul>
 *   <li>{@code pill:{id}} — one PUBLISHED pill snapshot, TTL {@code pillTtl}</li>
 *   <li>{@code pills:page:{v}:{page}:{size}} — one feed page, TTL {@code pageTtl}</li>
 *   <li>{@code pills:page-version} — generation counter; bumping it (INCR) on any
 *       write invalidates every page key in O(1), no SCAN needed. Orphaned
 *       generations simply expire via TTL.</li>
 * </ul>
 *
 * <p>Fail-open by contract: every Redis failure degrades to a database read and
 * logs WARN (degraded but self-healing, OBSERVABILITY.md §3.1) — never a request
 * failure.
 */
public class RedisPillCacheAdapter implements PillCachePort {

    static final String KEY_PREFIX = "codepill:catalog:";
    static final String PILL_KEY_PREFIX = KEY_PREFIX + "pill:";
    static final String PAGE_KEY_PREFIX = KEY_PREFIX + "pills:page:";
    static final String PAGE_VERSION_KEY = KEY_PREFIX + "pills:page-version";

    private static final Logger log = LoggerFactory.getLogger(RedisPillCacheAdapter.class);

    private final StringRedisTemplate redis;
    private final Duration pillTtl;
    private final Duration pageTtl;
    private final ObjectMapper json;

    public RedisPillCacheAdapter(StringRedisTemplate redis, Duration pillTtl, Duration pageTtl) {
        this.redis = Objects.requireNonNull(redis, "redis template must not be null");
        this.pillTtl = Objects.requireNonNull(pillTtl, "pillTtl must not be null");
        this.pageTtl = Objects.requireNonNull(pageTtl, "pageTtl must not be null");
        this.json = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    }

    @Override
    public Optional<PillSnapshot> getPill(PillId id) {
        try {
            var payload = redis.opsForValue().get(pillKey(id));
            return payload == null
                    ? Optional.empty()
                    : Optional.of(json.readValue(payload, PillSnapshot.class));
        } catch (Exception e) {
            log.warn("pill cache read failed", kv("pill_id", id.value()), e);
            return Optional.empty();
        }
    }

    @Override
    public void putPill(PillSnapshot snapshot) {
        try {
            redis.opsForValue().set(PILL_KEY_PREFIX + snapshot.id(),
                    json.writeValueAsString(snapshot), pillTtl);
        } catch (Exception e) {
            log.warn("pill cache write failed", kv("pill_id", snapshot.id()), e);
        }
    }

    @Override
    public void evictPill(PillId id) {
        try {
            redis.delete(pillKey(id));
        } catch (Exception e) {
            log.warn("pill cache eviction failed", kv("pill_id", id.value()), e);
        }
    }

    @Override
    public Optional<CachedPillPage> getPublishedPage(int page, int size) {
        try {
            var payload = redis.opsForValue().get(pageKey(currentPageVersion(), page, size));
            return payload == null
                    ? Optional.empty()
                    : Optional.of(json.readValue(payload, CachedPillPage.class));
        } catch (Exception e) {
            log.warn("page cache read failed", kv("page", page), kv("size", size), e);
            return Optional.empty();
        }
    }

    @Override
    public void putPublishedPage(CachedPillPage page) {
        try {
            redis.opsForValue().set(pageKey(currentPageVersion(), page.page(), page.size()),
                    json.writeValueAsString(page), pageTtl);
        } catch (Exception e) {
            log.warn("page cache write failed", kv("page", page.page()), kv("size", page.size()), e);
        }
    }

    @Override
    public void evictPublishedPages() {
        try {
            redis.opsForValue().increment(PAGE_VERSION_KEY);
        } catch (Exception e) {
            log.warn("page cache eviction failed", e);
        }
    }

    private String currentPageVersion() {
        var version = redis.opsForValue().get(PAGE_VERSION_KEY);
        return version == null ? "0" : version;
    }

    private static String pillKey(PillId id) {
        return PILL_KEY_PREFIX + id.value();
    }

    private static String pageKey(String version, int page, int size) {
        return PAGE_KEY_PREFIX + version + ":" + page + ":" + size;
    }
}
