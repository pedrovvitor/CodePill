package com.codepill.catalog.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * SECURITY.md §3.2 layer 1 — deny-by-default HTTP baseline. Layer 2 lives on
 * the use cases (@PreAuthorize); layer 3 (ownership) inside them.
 *
 * <p>Anonymous surface is the §3.3 allowlist: health probes always; the
 * Prometheus scrape endpoint only where {@code codepill.security.prometheus-public}
 * says so (local compose stack — prod keeps it authenticated).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter,
                                       @Value("${codepill.security.prometheus-public:false}")
                                       boolean prometheusPublic)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**").permitAll();
                    if (prometheusPublic) {
                        auth.requestMatchers("/actuator/prometheus").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(CsrfConfigurer::disable); // stateless JWT API only
        return http.build();
    }

    /**
     * JWKS-backed decoder with bounded HTTP timeouts (a hung IdP must not stall
     * authentication). {@link SupplierJwtDecoder} keeps initialization lazy so
     * the service boots while the IdP is unreachable; issuer + audience
     * validation per SECURITY.md §2.2.
     */
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") List<String> audiences) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        var rest = new RestTemplate(requestFactory);
        return new SupplierJwtDecoder(() -> {
            var decoder = NimbusJwtDecoder.withIssuerLocation(issuer)
                    .restOperations(rest)
                    .build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuer),
                    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                            aud -> aud != null && audiences.stream().anyMatch(aud::contains))));
            return decoder;
        });
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
