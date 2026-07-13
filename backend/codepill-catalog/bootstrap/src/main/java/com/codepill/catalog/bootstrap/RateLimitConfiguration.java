package com.codepill.catalog.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitConfiguration.RateLimitProperties.class)
class RateLimitConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "codepill.rate-limit", name = "enabled", havingValue = "true")
    FilterRegistrationBean<WriteRateLimitFilter> writeRateLimitFilter(
            RateLimitProperties properties, MeterRegistry meterRegistry) {
        var registration = new FilterRegistrationBean<>(new WriteRateLimitFilter(
                properties.capacity(), properties.refillPeriod(), meterRegistry));
        // after the security filter chain (order -100), which authenticates the JWT
        registration.setOrder(10);
        return registration;
    }

    @ConfigurationProperties(prefix = "codepill.rate-limit")
    record RateLimitProperties(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("30") long capacity,
            @DefaultValue("1m") Duration refillPeriod) {
    }
}
