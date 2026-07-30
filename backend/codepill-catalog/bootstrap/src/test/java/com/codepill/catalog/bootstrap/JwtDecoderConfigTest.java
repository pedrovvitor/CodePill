package com.codepill.catalog.bootstrap;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SECURITY.md §2.2 — the decoder must support split-horizon deployments: the
 * issuer stays the browser-facing URL (validated against {@code iss}) while
 * the JWKS is fetched from an explicitly configured, network-local endpoint.
 * The stub IdP below serves ONLY the JWKS path — any discovery request
 * (.well-known) gets a 404, so these tests fail if the decoder ever falls
 * back to issuer discovery when {@code jwk-set-uri} is set.
 */
class JwtDecoderConfigTest {

    private static HttpServer idp;
    private static RSAKey signingKey;
    private static String issuer;
    private static String jwkSetUri;

    @BeforeAll
    static void startStubIdp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        var jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

        idp = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        idp.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath()
                    .equals("/realms/codepill/protocol/openid-connect/certs")) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                exchange.getResponseBody().write(jwks);
            } else {
                exchange.sendResponseHeaders(404, -1); // discovery is not on offer
            }
            exchange.close();
        });
        idp.start();

        // deliberately unreachable as a discovery base: the port serves 404 there
        issuer = "http://127.0.0.1:" + idp.getAddress().getPort() + "/realms/codepill";
        jwkSetUri = issuer + "/protocol/openid-connect/certs";
    }

    @AfterAll
    static void stopStubIdp() {
        idp.stop(0);
    }

    private static JwtDecoder decoderWithJwkSetUri() {
        return new SecurityConfig().jwtDecoder(issuer, List.of("codepill-api"), jwkSetUri);
    }

    private static String token(String iss, String aud) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer(iss)
                .audience(aud)
                .subject("3f9a0000-0000-4000-8000-000000000042")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    @DisplayName("with jwk-set-uri set, keys come from it and discovery is never consulted")
    void decodesWithoutIssuerDiscovery() throws Exception {
        var jwt = decoderWithJwkSetUri().decode(token(issuer, "codepill-api"));

        assertThat(jwt.getIssuer().toString()).isEqualTo(issuer);
        assertThat(jwt.getAudience()).containsExactly("codepill-api");
    }

    @Test
    @DisplayName("issuer validation still applies on the jwk-set-uri path")
    void rejectsWrongIssuer() throws Exception {
        var decoder = decoderWithJwkSetUri();
        var forged = token("http://evil.example/realms/codepill", "codepill-api");

        assertThatThrownBy(() -> decoder.decode(forged))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("iss");
    }

    @Test
    @DisplayName("audience validation still applies on the jwk-set-uri path")
    void rejectsWrongAudience() throws Exception {
        var decoder = decoderWithJwkSetUri();
        var otherApp = token(issuer, "some-other-api");

        assertThatThrownBy(() -> decoder.decode(otherApp))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("aud");
    }

    @Test
    @DisplayName("without jwk-set-uri the decoder still resolves via issuer discovery (fails here: stub serves none)")
    void fallsBackToIssuerDiscoveryWhenUnset() {
        var decoder = new SecurityConfig().jwtDecoder(issuer, List.of("codepill-api"), "");

        assertThatThrownBy(() -> decoder.decode("not-even-parsed-before-init"))
                .hasMessageContaining("Failed to lazily resolve");
    }
}
