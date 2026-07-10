package com.codepill.catalog.adapter.out.cache;

import com.codepill.catalog.domain.PillId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests with a mocked template: key layout, serialization round-trips,
 * and — critically — the fail-open contract when Redis is down.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisPillCacheAdapterTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;

    RedisPillCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        adapter = new RedisPillCacheAdapter(redis, Duration.ofMinutes(10), Duration.ofSeconds(60));
    }

    @Test
    void shouldRoundTripPillSnapshot_throughJson() {
        var snapshot = CacheTestFixtures.publishedSnapshot(UUID.randomUUID());
        var captured = new String[1];
        when(values.get(RedisPillCacheAdapter.PILL_KEY_PREFIX + snapshot.id()))
                .thenAnswer(inv -> captured[0]);
        org.mockito.Mockito.doAnswer(inv -> {
            captured[0] = inv.getArgument(1);
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));

        adapter.putPill(snapshot);
        var restored = adapter.getPill(PillId.of(snapshot.id()));

        assertThat(restored).contains(snapshot);
        verify(values).set(eq(RedisPillCacheAdapter.PILL_KEY_PREFIX + snapshot.id()),
                anyString(), eq(Duration.ofMinutes(10)));
    }

    @Test
    void shouldReturnEmpty_onPillCacheMiss() {
        when(values.get(anyString())).thenReturn(null);

        assertThat(adapter.getPill(PillId.newId())).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenCachedPayloadIsCorrupt() {
        when(values.get(anyString())).thenReturn("{corrupt");

        assertThat(adapter.getPill(PillId.newId())).isEmpty();
    }

    @Test
    void shouldEvictPillKey() {
        var id = PillId.newId();

        adapter.evictPill(id);

        verify(redis).delete(RedisPillCacheAdapter.PILL_KEY_PREFIX + id.value());
    }

    @Test
    void shouldStampPageKeysWithCurrentGeneration() {
        when(values.get(RedisPillCacheAdapter.PAGE_VERSION_KEY)).thenReturn("7");
        var page = CacheTestFixtures.page(2, 10);

        adapter.putPublishedPage(page);

        verify(values).set(eq(RedisPillCacheAdapter.PAGE_KEY_PREFIX + "7:2:10"),
                anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void shouldDefaultToGenerationZero_whenVersionKeyMissing() {
        when(values.get(RedisPillCacheAdapter.PAGE_VERSION_KEY)).thenReturn(null);
        when(values.get(startsWith(RedisPillCacheAdapter.PAGE_KEY_PREFIX))).thenReturn(null);

        adapter.getPublishedPage(0, 20);

        verify(values).get(RedisPillCacheAdapter.PAGE_KEY_PREFIX + "0:0:20");
    }

    @Test
    void shouldRoundTripPage_throughJson() throws Exception {
        var page = CacheTestFixtures.page(1, 20);
        when(values.get(RedisPillCacheAdapter.PAGE_VERSION_KEY)).thenReturn("3");
        var payload = new String[1];
        org.mockito.Mockito.doAnswer(inv -> {
            payload[0] = inv.getArgument(1);
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));

        adapter.putPublishedPage(page);
        when(values.get(RedisPillCacheAdapter.PAGE_KEY_PREFIX + "3:1:20")).thenReturn(payload[0]);

        assertThat(adapter.getPublishedPage(1, 20)).contains(page);
    }

    @Test
    void shouldInvalidateAllPages_byBumpingGeneration() {
        adapter.evictPublishedPages();

        verify(values).increment(RedisPillCacheAdapter.PAGE_VERSION_KEY);
    }

    @Test
    void shouldFailOpen_whenRedisIsDown() {
        var down = new RedisConnectionFailureException("connection refused");
        when(values.get(anyString())).thenThrow(down);
        org.mockito.Mockito.doThrow(down).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.increment(anyString())).thenThrow(down);
        when(redis.delete(anyString())).thenThrow(down);

        var snapshot = CacheTestFixtures.publishedSnapshot(UUID.randomUUID());

        // none of these may propagate — reads degrade to the database
        assertThat(adapter.getPill(PillId.newId())).isEmpty();
        assertThat(adapter.getPublishedPage(0, 20)).isEmpty();
        adapter.putPill(snapshot);
        adapter.putPublishedPage(CacheTestFixtures.page(0, 20));
        adapter.evictPill(PillId.newId());
        adapter.evictPublishedPages();
    }
}
