package com.codepill.catalog.bootstrap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SECURITY.md §3.2 layer 1 — deny-by-default HTTP baseline. Layer 2 lives on
 * the use cases (@PreAuthorize); layer 3 (ownership) inside them.
 *
 * <p>Anonymous surface is limited to the §3.3 allowlist: health probes plus the
 * Prometheus scrape endpoint (network-restricted outside local, same category
 * as health per OBSERVABILITY.md §4.1).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(CsrfConfigurer::disable); // stateless JWT API only
        return http.build();
    }

    /** Maps the flat {@code roles} claim (minted by the codepill-api scope) to ROLE_* authorities. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var granted = new JwtGrantedAuthoritiesConverter();
        granted.setAuthoritiesClaimName("roles");
        granted.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(granted);
        return converter;
    }

    /**
     * SECURITY.md §3.1 — hierarchy declared once, never re-implemented in if-chains.
     * Defense in depth: Keycloak already expands composites into the claim.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_CURATOR
                ROLE_CURATOR > ROLE_AUTHOR
                ROLE_AUTHOR > ROLE_LEARNER
                """);
    }
}
