package com.codepill.catalog.adapter.out.cache;

import com.codepill.catalog.domain.PillId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trips against a real Redis 7, wired exactly like the compose stack (no Spring context). */
@Testcontainers(disabledWithoutDocker = true)
class RedisPillCacheAdapterIT {

    @Container
    static final GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static RedisPillCacheAdapter adapter;
    static StringRedisTemplate template;

    @BeforeAll
    static void setUp() {
        var config = new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        adapter = new RedisPillCacheAdapter(template, Duration.ofMinutes(10), Duration.ofSeconds(60));
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void shouldRoundTripPillSnapshot_withTtl() {
        var snapshot = CacheTestFixtures.publishedSnapshot(UUID.randomUUID());

        adapter.putPill(snapshot);

        assertThat(adapter.getPill(PillId.of(snapshot.id()))).contains(snapshot);
        assertThat(template.getExpire(RedisPillCacheAdapter.PILL_KEY_PREFIX + snapshot.id()))
                .isPositive();

        adapter.evictPill(PillId.of(snapshot.id()));
        assertThat(adapter.getPill(PillId.of(snapshot.id()))).isEmpty();
    }

    @Test
    void shouldInvalidateEveryPage_whenGenerationBumps() {
        var page = CacheTestFixtures.page(0, 20);
        adapter.putPublishedPage(page);
        assertThat(adapter.getPublishedPage(0, 20)).contains(page);

        adapter.evictPublishedPages();

        assertThat(adapter.getPublishedPage(0, 20)).isEmpty();
    }
}
