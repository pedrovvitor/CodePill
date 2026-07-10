package com.codepill.catalog.bootstrap;

import com.codepill.catalog.adapter.out.cache.RedisPillCacheAdapter;
import com.codepill.catalog.application.port.out.PillCachePort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CacheConfiguration.CacheProperties.class)
class CacheConfiguration {

    @Bean
    PillCachePort pillCachePort(StringRedisTemplate redisTemplate, CacheProperties properties) {
        return new RedisPillCacheAdapter(redisTemplate, properties.pillTtl(), properties.pageTtl());
    }

    @ConfigurationProperties(prefix = "codepill.cache")
    record CacheProperties(
            @DefaultValue("10m") Duration pillTtl,
            @DefaultValue("60s") Duration pageTtl) {
    }
}
