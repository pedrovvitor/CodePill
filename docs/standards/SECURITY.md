# CodePill — Security Standard

> **Status:** MANDATORY · **Owner:** Lead Principal Engineering · **Last updated:** 2026-07-09
>
> ⚠️ **AGENT PROTOCOL — NON-NEGOTIABLE**
> Every agent (human or AI) MUST read all files in `/docs/standards/` **before** executing any task in this repository, and MUST append an entry to `/docs/standards/STATE_OF_THE_APP.md` **upon completing** its task.

## 1. Principle

**Every endpoint is authenticated and authorized by default.** There is no anonymous access unless explicitly allowlisted in this document. Security is deny-by-default, enforced in code and verified by tests.

## 2. Authentication — OAuth2 / OIDC + JWT

### 2.1 Topology

- A central **OIDC Identity Provider** (Keycloak self-hosted; swappable per ADR) owns credentials, MFA, and token issuance. CodePill services **never** see or store passwords.
- The React SPA authenticates with **Authorization Code Flow + PKCE** (`react-oidc-context` / `oidc-client-ts`). The **implicit flow and password grant are banned.**
- Service-to-service calls use **Client Credentials Flow** with per-service clients.
- Every backend service is an **OAuth2 Resource Server** validating JWTs locally against the IdP's JWKS:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${CODEPILL_OIDC_ISSUER}
          audiences: codepill-api
```

### 2.2 Token rules

1. **Access tokens:** JWT, RS256/ES256 (asymmetric only — HS256 is banned), TTL ≤ 15 minutes, `aud=codepill-api` verified.
2. **Refresh tokens:** rotating, one-time use, TTL ≤ 30 days, revocable at the IdP.
3. Browser storage: tokens live **in memory** in the SPA (never `localStorage`); silent renew via the OIDC client. If a BFF is introduced later, it uses `HttpOnly; Secure; SameSite=Strict` session cookies (ADR required).
4. Required claims: `sub`, `iss`, `aud`, `exp`, `iat`, `roles`, and `email` only where the service genuinely needs it.
5. Clock skew tolerance ≤ 60 s; expired or malformed tokens → `401` with `WWW-Authenticate`, no detail leakage.
6. Tokens are **never logged**, never put in URLs, and never forwarded to third parties.

## 3. Authorization — RBAC

### 3.1 Roles

Roles are defined in the IdP and delivered in the JWT `roles` claim.

| Role | Description |
|---|---|
| `LEARNER` | Consumes pills, tracks own progress. Default role on signup. |
| `AUTHOR` | Creates and edits own pills/courses. Includes `LEARNER`. |
| `CURATOR` | Reviews and publishes any content. Includes `AUTHOR`. |
| `ADMIN` | User management, platform configuration. Includes `CURATOR`. |
| `service` | Machine clients (client-credentials). Scoped, non-human. |

Role hierarchy is declared once per service via `RoleHierarchy` — never re-implemented in `if` chains.

### 3.2 Enforcement pattern (defense in depth — all three layers)

**Layer 1 — HTTP baseline (`SecurityFilterChain`):** deny by default, coarse route rules.

```java
http
  .authorizeHttpRequests(auth -> auth
      .requestMatchers("/actuator/health/**").permitAll()   // probes only
      .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
      .anyRequest().authenticated())                        // DEFAULT: authenticated
  .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
  .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
  .csrf(CsrfConfigurer::disable);   // stateless JWT API only
```

**Layer 2 — Method security on use cases:** `@EnableMethodSecurity`; every application service method carries an explicit annotation.

```java
@PreAuthorize("hasRole('CURATOR')")
public PublishResult publishPill(PublishPillCommand cmd) { ... }
```

**Layer 3 — Ownership (resource-level) checks in the domain/use case:** roles gate the operation; ownership gates the instance.

```java
@PreAuthorize("hasRole('AUTHOR')")
public void updatePill(UpdatePillCommand cmd, UserId caller) {
    var pill = loadPillPort.load(cmd.pillId());
    if (!pill.isOwnedBy(caller) && !caller.hasRole(CURATOR)) {
        throw new AccessDeniedException("not the author");   // -> 403
    }
    ...
}
```

Rules:

1. **Never trust client-supplied identity.** The acting user always comes from the validated token (`JwtAuthenticationToken`), never from a request body or header.
2. Authorization failures return `403` (`404` for existence-hiding on ownership checks where enumeration matters), with a Problem Details body and no internal information.
3. All auth decisions are testable: every secured endpoint has tests for anonymous (401), wrong role (403), and correct role (2xx) — see `TESTING_QUALITY.md`.
4. Frontend role checks (`hasRole` hooks hiding buttons/routes) are **UX only** — the backend is the sole authority.

### 3.3 Public allowlist

Only the following are anonymous: `/actuator/health/*` (network-restricted), OIDC discovery endpoints on the IdP, and the public marketing/catalog preview (`GET /api/v1/catalog/preview` — explicitly read-only, no PII).

## 4. Baseline hygiene (all services and the SPA)

1. **Transport:** TLS 1.3 everywhere; HSTS; no plaintext listeners outside `local`.
2. **Input validation** at the web adapter (Jakarta Bean Validation) *and* domain invariants; all persistence via parameterized queries (JPA/JDBC bind params) — string-built SQL is banned.
3. **Headers/CORS:** strict CORS allowlist per environment (no `*` with credentials); `Content-Security-Policy`, `X-Content-Type-Options: nosniff` on the SPA.
4. **Secrets** come from the environment/secret manager; never committed, never logged. Pre-commit secret scanning (gitleaks) in CI.
5. **Dependencies:** Dependabot/Renovate enabled; OWASP Dependency-Check / `pnpm audit` gate in CI — builds fail on known critical CVEs.
6. **Rate limiting** on auth-sensitive and write endpoints at the gateway (Bucket4j/gateway policy).
7. **PII:** logs and metrics carry opaque IDs only (see `OBSERVABILITY.md`); data deletion honors right-to-erasure — design for it now.
8. **Audit trail:** privileged actions (role grants, content publish/unpublish, deletions) emit an immutable audit event: who (`sub`), what, when, `trace_id`.

## 5. Definition of Done (security)

A change is complete only when: no new anonymous surface; role + ownership rules annotated and tested (401/403/2xx matrix); inputs validated; no secrets/PII in code, logs, or telemetry; CI security gates green.
