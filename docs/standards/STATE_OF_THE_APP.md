# CodePill — State of the App (Living Changelog)

> **Status:** MANDATORY · **Owner:** Everyone · **Last updated:** 2026-07-09
>
> ⚠️ **AGENT PROTOCOL — NON-NEGOTIABLE**
> This file is the single source of truth for what has been done, what is in flight, and what is next.
> Every agent (human or AI) MUST:
> 1. **Read** all files in `/docs/standards/` (`ARCHITECTURE.md`, `OBSERVABILITY.md`, `SECURITY.md`, `TESTING_QUALITY.md`, and this file) **before** executing any task.
> 2. **Append** an entry to the log below **immediately upon completing** a task, following the entry template. Newest entries go on top.
> 3. Update the "Current Snapshot" section if the task changed it.
>
> A task without a log entry here is considered **not done**.

---

## Current Snapshot

- **Phase:** 1 — First vertical slice (catalog service + web SPA scaffolded; E2E wiring pending)
- **Deployable services:** `codepill-catalog` (Java 25, Spring Boot 4.0.7, Maven multi-module under `/backend`, wrapper committed). Runs on host port **8080** (`CODEPILL_CATALOG_PORT`), scraped by Prometheus via `host.docker.internal:8080`.
- **Local dev infrastructure:** ✅ docker-compose stack (PostgreSQL 17, Redis 7, Keycloak 26.2, OTel Collector, Prometheus, Grafana, Loki, Tempo) — see "Local Dev Stack" below.
- **Database schema:** Flyway v1 for `codepill_identity` (users, still under `ops/db/migrations/identity/`) and `codepill_catalog` (pills, tags, pill_tags — now owned by the service at `backend/codepill-catalog/adapters/out/persistence/src/main/resources/db/migration/`, checksum-identical; compose mounts that path).
- **Frontend:** ✅ `codepill-web` SPA under `/frontend` (React 19 + TypeScript strict, Vite 8, pnpm, Tailwind 4). Clean Architecture layers per `ARCHITECTURE.md` §4 enforced by `eslint-plugin-boundaries`; OAuth2 Code+PKCE via `react-oidc-context` (tokens in memory); TanStack Query 5 for all server state; API types generated from the OpenAPI contract; OTel browser tracing → collector `:4318`. Dev server on **5173** (Vite proxy `/api` → `localhost:8080`). See the 2026-07-10 entry for structure and decisions.
- **CI/CD:** not configured (JaCoCo 85% line+branch gate already enforced by `mvnw verify`)
- **Test coverage:** ≥85% line+branch per module enforced at build; domain/application near 100%. Backend: 125 unit tests + 17 integration tests (Testcontainers PostgreSQL/Redis) + 4 ArchUnit rules. Frontend: 117 Vitest tests, 99% statements / 90% branches / 100% functions (85% gate wired in `vite.config.ts`). All green.
- **Known risks / open decisions:**
  - IdP choice defaulted to Keycloak (`SECURITY.md`) — revisit via ADR if managed IdP preferred.
  - Monorepo module cut defined in `ARCHITECTURE.md` §3.2 — extraction to independent deployables deferred.
  - Sentry integration (`OBSERVABILITY.md` §3.2) deferred — needs a Boot 4-compatible `sentry-spring-boot-starter` validation and a DSN; tracked in catalog scaffold follow-ups.
  - `ops/db/migrations/catalog/` still contains the superseded copy of the catalog V1 migration (deletion pending manual confirmation); the authoritative copy lives in the service module.

## codepill-catalog — API, security, metrics (2026-07-09)

**Routes** (all under Bearer-JWT unless noted; errors are RFC 9457 `application/problem+json`; contract: `backend/codepill-catalog/api/openapi.yaml`):

| Method & path | Role required | Notes |
|---|---|---|
| `GET /api/v1/pills?page&size` | LEARNER | Published feed, newest first; Redis page cache (60s TTL, generation-key invalidation) |
| `GET /api/v1/pills/{id}` | LEARNER | PUBLISHED cached in Redis (10m TTL); drafts visible only to author/CURATOR+, others get 404 (existence hiding) |
| `POST /api/v1/pills` | AUTHOR | 201 + Location; caller (`sub`) becomes author; slug unique → 409 |
| `PUT /api/v1/pills/{id}` | AUTHOR + ownership (CURATOR+ overrides) | ARCHIVED not editable (409); optimistic-lock conflict → 409 |
| `DELETE /api/v1/pills/{id}` | AUTHOR + ownership (CURATOR+ overrides) | 204; hard delete, audited log event |
| `POST /api/v1/pills/{id}/publish` | CURATOR | From DRAFT/IN_REVIEW; audited log event |
| `GET /actuator/health[/liveness|/readiness]` | anonymous | probes (SECURITY.md §3.3) |
| `GET /actuator/prometheus` | anonymous (network-restricted outside local) | Prometheus scrape |
| `GET /actuator/metrics`, `/actuator/info` | authenticated | deny-by-default catch-all |

**Security:** OAuth2 Resource Server validating JWTs against Keycloak JWKS (`issuer http://localhost:8180/realms/codepill`, `aud=codepill-api` enforced); flat `roles` claim → `ROLE_*` authorities; `RoleHierarchy` ADMIN>CURATOR>AUTHOR>LEARNER; stateless, CSRF off; `@PreAuthorize` on every use case (layer 2) + ownership checks inside them (layer 3). Full 401/403/2xx matrix in `CatalogApiIT`.

**Metrics/observability:** `/actuator/prometheus` exposes `http_server_requests_*` (percentile histograms), `jvm_*`, `hikaricp_*`, `spring_data_repository_invocations_*`, `jdbc_query_*` (datasource-micrometer 2.2.1) and business metrics `codepill_pill_drafted_total`, `codepill_pill_published_total`, `codepill_pill_authoring_time_seconds*` (tag `pill_type`; "created" avoided — reserved OpenMetrics suffix). Traces: `@Observed` use-case spans + HTTP/JDBC spans via OTLP → collector `:4318` → Tempo (verified live). Logs: JSON per `OBSERVABILITY.md` §3.1 with `trace_id`/`span_id` from MDC; human-readable console under the `local` profile.

## Local Dev Stack — Network Topology & Exposed Ports

Single Docker bridge network **`codepill-net`** (project name `codepill`). Only the ports below are published to the host; all other traffic is container-to-container on the bridge. Spring services run **on the host** during development: they reach infrastructure via `localhost:<host port>` and are scraped back by Prometheus via `host.docker.internal`.

| Container | Image | Host port → container | Purpose |
|---|---|---|---|
| `codepill-postgres` | `postgres:17-alpine` | `5433 → 5432` | Primary DB. Databases: `codepill_identity`, `codepill_catalog`, `keycloak`. (Host 5433 because 5432 is taken by another local project.) |
| `codepill-redis` | `redis:7-alpine` | `6379 → 6379` | Cache (LRU, AOF persistence, password-protected). |
| `codepill-keycloak` | `quay.io/keycloak/keycloak:26.2` | `8180 → 8080` | OIDC IdP. Issuer: `http://localhost:8180/realms/codepill`. Realm auto-imported from `ops/keycloak/realm-codepill.json`. |
| `codepill-keycloak` (mgmt) | — | `8181 → 9000` | Keycloak health (`/health/*`) and metrics (`/metrics`). |
| `codepill-otel-collector` | `otel/opentelemetry-collector-contrib:0.126.0` | `4317 → 4317` (gRPC), `4318 → 4318` (HTTP) | Single OTLP front door: traces → Tempo, logs → Loki, metrics → re-exposed for Prometheus (internal `:8889`). |
| `codepill-prometheus` | `prom/prometheus:v3.4.0` | `9090 → 9090` | Metrics. Scrapes itself, the collector, Keycloak, and host apps on `host.docker.internal:8080-8083` (`/actuator/prometheus`). |
| `codepill-grafana` | `grafana/grafana:12.0.0` | `3000 → 3000` | Dashboards. Datasources provisioned (Prometheus/Loki/Tempo) with `trace_id` log↔trace correlation. |
| `codepill-loki` | `grafana/loki:3.5.0` | `3100 → 3100` | Logs (native OTLP ingest, 7-day local retention). |
| `codepill-tempo` | `grafana/tempo:2.7.1` | `3200 → 3200` | Traces (query API; OTLP `:4317` internal-only, fed by the collector). |
| `codepill-flyway-identity` / `-catalog` | `flyway/flyway:11-alpine` | — (run-once) | Apply `ops/db/migrations/<service>/` on `docker compose up`. |
| `codepill-db-seed` | `postgres:17-alpine` | — (profile `seed`) | Loads 50 tags / 100k pills / ~300k pill_tags for read-load testing. |

**Usage:** `docker compose up -d` (migrations auto-apply) · `docker compose --profile seed up db-seed` (high-volume data) · credentials are local-only defaults overridable via `.env` (see `.env.example`). Dev realm users: `dev-learner|author|curator|admin` / `codepill-local`.

## Next Up (prioritized backlog)

1. ~~Scaffold monorepo: Maven multi-module backend skeleton (`codepill-catalog` first) + Vite React/TS frontend~~ ✅ Done — backend 2026-07-09, frontend 2026-07-10.
2. CI pipeline with all quality gates from `TESTING_QUALITY.md` §4 (coverage gate already wired into `mvnw verify`; missing: Spotless/Error Prone, OWASP Dependency-Check, gitleaks, pipeline itself).
3. ~~Local dev stack: docker-compose with PostgreSQL, Keycloak, OTel Collector, Grafana/Loki/Tempo/Prometheus.~~ ✅ Done 2026-07-09 (Redis included).
4. `codepill-identity` integration with Keycloak; security test matrix.
5. First vertical slice: author creates a pill → learner completes it (TDD, fully observable, secured).

---

## Entry Template (copy this)

```markdown
## [YYYY-MM-DD] <Task title>
- **Agent/Author:** <name or agent id>
- **Task:** <what was requested>
- **Changes:** <files/modules created or modified; key decisions taken>
- **Standards compliance:** <TDD followed? coverage %? telemetry added? security matrix? — or explicit N/A with reason>
- **Tests:** <suites added/updated and their status>
- **Follow-ups / debt:** <anything deferred, with issue links>
```

---

## Log (newest first)

## [2026-07-10] Scaffold frontend: codepill-web SPA (React 19/TS, Tailwind, TanStack Query, OAuth2 PKCE, RHF+Zod, OTel browser tracing)
- **Agent/Author:** Claude (Senior Frontend Engineer session)
- **Task:** Initialize the React + TypeScript SPA under `/frontend` (Vite); Tailwind mobile-first vertical-scroll UI; TanStack Query against the catalog API; OAuth2 login flow; "Create Pill" form with React Hook Form + Zod; OpenTelemetry browser tracing joining backend traces; record structure and state decisions here.
- **Changes:**
  - `/frontend` — `codepill-web` (React 19.2, TS 6 `strict` + `noUncheckedIndexedAccess`, Vite 8, pnpm, Tailwind 4 via `@tailwindcss/vite`). Vite dev server pinned to **5173** (matches Keycloak redirect URIs + collector CORS) with proxy `/api → localhost:8080` so dev is same-origin (no backend CORS change needed; `VITE_API_BASE_URL` overrides for real environments).
  - **Component structure** (`ARCHITECTURE.md` §4, enforced by `eslint-plugin-boundaries`: `ui → application → domain`, `infrastructure` implements application ports, `app` wires):
    - `src/domain/` — pure TS: `catalog/api-types.gen.ts` (**generated** from `backend/codepill-catalog/api/openapi.yaml` via `openapi-typescript`, script `pnpm generate:api`); `catalog/pill.ts` (aliases over generated types + `formatEstimatedDuration`); `catalog/pill-input-schema.ts` (Zod `createPillFormSchema` mirroring contract constraints, `toPillInput` minutes→seconds mapper, `suggestSlug`, `parsePillContent`); `auth/roles.ts` (LEARNER⊂AUTHOR⊂CURATOR⊂ADMIN hierarchy, `hasRole`).
    - `src/application/` — `ports/pill-api-port.ts` (`PillApiPort` + `ApiError` w/ RFC 9457 problem); use-case hooks `usePillFeed` (infinite query), `usePill`, `useCreatePill` (traced span `create-pill`, primes detail cache, invalidates feed); `pill-api-context.ts` (DI seam); `auth/use-session.ts` (single auth view: roles decoded from access token, signIn/signOut) + `auth/token-claims.ts`; `tracing.ts` (`traceUseCase` over vendor-neutral `@opentelemetry/api`).
    - `src/infrastructure/` — `api/http-pill-api.ts` (fetch adapter: Bearer from in-memory supplier, problem+json → `ApiError`, AbortSignal support); `auth/oidc-config.ts` (Keycloak realm `codepill`, client `codepill-web`, Code+PKCE, `automaticSilentRenew`, **tokens in `InMemoryWebStorage`** per SECURITY.md §2.2.3); `telemetry/otel.ts` (WebTracerProvider + OTLP→`:4318`, resource attrs per OBSERVABILITY.md §2, `FetchInstrumentation` propagating `traceparent` **only** to the API); `config/env.ts` (12-factor `VITE_*` with local defaults).
    - `src/ui/` — `design-system/` (Button, TextField/TextAreaField/SelectField with a11y error wiring, Badge, Spinner on `@theme` tokens); `components/` (PillCard, AppShell — sticky header + fixed bottom nav, safe-area aware; ProtectedRoute; RoleGate); `pages/` (FeedPage: infinite vertical snap feed w/ IntersectionObserver sentinel + button fallback, empty/error/retry states; CreatePillPage: RHF + `zodResolver`, live slug suggestion, 409→slug field error, 400 problem `errors`→field errors, generic problem banner, success panel; LoginPage; AuthCallbackPage).
    - `src/app/` — providers (AuthProvider → QueryClientProvider → PillApiContext wiring; API adapter memoized per access token), router (protected layout routes), telemetry bootstrap, `main.tsx`.
  - **State management decisions:** ALL server state in TanStack Query (30s staleTime, background refetch on focus/reconnect, no retry on 4xx, central `pillKeys` registry); auth/session is client state owned by `react-oidc-context` (in-memory user store) and exposed through the single `useSession` hook; form state local to React Hook Form; **no global store** (Zustand deferred until a real cross-cutting client state appears). Components never fetch — they consume application hooks only.
  - Contract fix: added missing `required` lists to `Pill`/`PillPage` in `backend/codepill-catalog/api/openapi.yaml` (backend always returns these fields; generator was emitting all-optional types). Non-breaking clarification; types regenerated.
  - Quality tooling: ESLint 10 flat config (typescript-eslint, react-hooks 7, react-refresh, boundaries, prettier; `no-explicit-any` = error) — replaced the Vite template's oxlint per TESTING_QUALITY.md §4; Prettier; Vitest 4 + Testing Library + jsdom with **85% coverage thresholds in `vite.config.ts`** (exclusions: `src/app/**` bootstrap wiring, generated `api-types.gen.ts`, `main.tsx`, test support — per TESTING_QUALITY.md §2).
  - `frontend/.env.example` (SPA config is public; no secrets), pill favicon, dark mobile-first theme tokens in `index.css`.
- **Standards compliance:** Clean Architecture dependency rule lint-enforced; TDD red→green per unit (domain → infrastructure → application → ui; failing runs verified before implementations); coverage gate ≥85% wired into `vitest run --coverage` (actual: 99% stmts / 90% branches / 100% funcs / 99.5% lines); OAuth2 Code+PKCE only, tokens in memory, roles UX-gating only with backend authoritative; `traceparent` never sent to third parties; no tokens/PII logged or traced; `pnpm audit` clean.
- **Tests:** 117 Vitest tests across 19 files, all green: domain (schema constraint matrix, slug suggestion, roles hierarchy, duration format), application (feed paging/error, create-pill cache priming + invalidation, tracing passthrough, JWT claim decoding, session facade), infrastructure (fetch adapter incl. auth header/problem+json/204/base URL, OIDC props incl. no-web-storage proof, OTel resource attrs/propagation targets/idempotency), ui (feed states incl. auto-load sentinel, create form validation/server-error mapping/success flow/role gate, login/callback, guards, shell nav). `pnpm lint`, `tsc -b`, `pnpm build`, dev-server smoke (200 + title) all green.
- **Follow-ups / debt:**
  - E2E Playwright journeys (`/e2e`, TESTING_QUALITY.md §3.3) — needs the full compose stack + dev realm users; not started.
  - Sentry for the frontend (`@sentry/react`, OBSERVABILITY.md §3.2) deferred alongside the backend Sentry decision (needs DSN).
  - Bundle is 559 kB minified (OTel SDK + zone.js dominate) — add route-level code splitting / lazy OTel init before first real deploy.
  - CSP + `X-Content-Type-Options` response headers (SECURITY.md §4.3) belong to the serving infra (gateway/static host) — define when deployment target exists.
  - `eslint-plugin-boundaries` v7 emits deprecation warnings for the v6 rule syntax in `eslint.config.js` — migrate to `boundaries/dependencies` + `policies` on the next lint touch.
  - Frontend CI stage (lint → typecheck → coverage → `pnpm audit` → build) still pending with backlog #2.

## [2026-07-09] Scaffold backend: codepill-catalog service (Clean Architecture, OAuth2 RS, Pills CRUD, Redis cache, full observability)
- **Agent/Author:** Claude (Senior Java Engineer session)
- **Task:** Scaffold the Java 25 / Spring Boot 4 backend under `/backend`; REST API for Pills (CRUD) in Clean Architecture; OAuth2 Resource Server against local Keycloak; Redis caching on retrieval endpoints; Micrometer + OpenTelemetry on every service method; JUnit suites at the 85% gate; update this file.
- **Changes:**
  - `/backend` Maven multi-module (Spring Boot **4.0.7** parent, Java 25, virtual threads on, `mvnw` committed). Modules per `ARCHITECTURE.md` §3: `codepill-catalog/{domain, application, adapters/in/web, adapters/out/persistence, adapters/out/cache, bootstrap}`.
  - **domain** (JDK-only, ArchUnit-enforced): `Pill` aggregate (draft/update/publish lifecycle, ownership), value objects (`Title`, `Slug`, `Summary`, `PillContent`, `EstimatedDuration`), `PillSnapshot` memento as the persistence/cache exchange format.
  - **application**: six use cases (create/get/list/update/delete/publish) — each `@PreAuthorize` (layer 2) + ownership checks (layer 3), `@Transactional`, `@Observed` (span + timer per invocation); ports `LoadPillPort`/`SavePillPort`/`DeletePillPort`/`PillCachePort`; `CatalogMetrics` (`codepill.pill.drafted|published|authoring_time`, tag `pill_type`). Unpublished pills answered with 404 to non-owners (existence hiding).
  - **adapters/in/web**: `PillController` (`/api/v1/pills` CRUD + `/{id}/publish`), Bean-Validation request DTOs (content bound as JSON object → guaranteed well-formed), `Caller` derived exclusively from the validated JWT, central `ApiExceptionHandler` → RFC 9457 problems (400/403/404/409/500, no internals leaked).
  - **adapters/out/persistence**: `PillJpaEntity` (jsonb content, `@Version` optimistic locking), Spring Data repository, adapter translating `ux_pills_slug` violations to 409; owns Flyway V1 (moved from `ops/db/migrations/catalog/`, checksum-identical — `docker-compose.yml` flyway-catalog volume updated).
  - **adapters/out/cache**: `RedisPillCacheAdapter` — pill-by-id (10m TTL, published only) + feed pages (60s TTL) with O(1) generation-counter invalidation; **fail-open**: any Redis failure degrades to DB reads with WARN logs, never a request failure.
  - **bootstrap**: deny-by-default `SecurityFilterChain` (allowlist: health probes + prometheus scrape), JWKS validation with `aud=codepill-api`, `roles`-claim converter, `RoleHierarchy`; `ObservedAspect`; use-case wiring; `application.yml` (port 8080, compose defaults, env-overridable); JSON Logback per `OBSERVABILITY.md` §3.1 (exact base fields, `context` nesting, trace-correlated MDC).
  - Observability wiring: `spring-boot-starter-opentelemetry` + OTLP traces → collector `:4318` → Tempo (**verified live**); Prometheus endpoint + `datasource-micrometer` **2.2.1** (JDBC query spans/metrics; 1.x is Boot-3-only); `ops/observability/prometheus/prometheus.yml` target comment clarified (catalog = 8080).
  - API contract: `backend/codepill-catalog/api/openapi.yaml` (OpenAPI 3.1).
  - `git init` executed (was outstanding debt); no commit made.
- **Standards compliance:** Clean Architecture with dependency rule enforced by 4 ArchUnit rules (domain purity, inward-only, adapter isolation, controllers↛repositories). TDD red→green per unit (domain → application → adapters; suites written with each behavior, verified failing paths first). Coverage gate ≥85% line+branch per module via JaCoCo `check` (bootstrap wiring excluded per `TESTING_QUALITY.md` §2); domain/application near 100%. Security matrix tested per endpoint. Telemetry on every use case + HTTP + JDBC path; no secrets/PII in code, logs, or metrics (opaque IDs only).
- **Tests:** 125 unit + 17 IT + 4 ArchUnit, all green (`mvnw verify`). ITs on real infra (Testcontainers postgres:17-alpine — Flyway V1 exercised every build — and redis:7-alpine): persistence round-trips/jsonb/unique-slug/optimistic-lock/feed-order; cache TTL+generation invalidation; full-stack `CatalogApiIT` — 401/403/2xx matrix incl. RoleHierarchy (ADMIN→create), existence hiding, lifecycle+slug conflicts (409), read-through caching + eviction, anonymous health probes, `codepill_pill_drafted_total` on `/actuator/prometheus`. Live verification against the compose stack: boot with Flyway validate against pre-applied schema; anonymous → 401 (`WWW-Authenticate`); real Keycloak client-credentials JWT without CodePill roles → 403 (JWKS + audience validation proven); JSON log shape confirmed; Prometheus target `host.docker.internal:8080` **up**; traces visible in Tempo.
- **Follow-ups / debt:**
  - Delete superseded `ops/db/migrations/catalog/` copy (removal was denied by session permissions; authoritative copy is in the service module and compose already points there).
  - Live 2xx verification with a *user* token not performed: password grant is banned and the dev realm (correctly) has no direct-access clients; the 2xx matrix is covered by `CatalogApiIT`. Optional: grant `CURATOR` to `service-account-codepill-service` locally for curl-based smoke tests.
  - Sentry (`OBSERVABILITY.md` §3.2) deferred — validate Boot 4-compatible starter + DSN wiring.
  - Metric naming: `codepill.pill.created` is unusable on Prometheus (OpenMetrics reserves the `_created` suffix — client silently renames); adopted `codepill.pill.drafted`. Consider noting this in `OBSERVABILITY.md` §4.3.
  - CI pipeline (backlog #2); Spotless/Error Prone, OWASP DC, gitleaks not yet wired.
  - Tags (`tags`, `pill_tags`) modeled in DB but not yet exposed via the API; rate limiting (SECURITY.md §4.6) deferred to gateway work.

## [2026-07-09] Local development infrastructure (docker-compose, Flyway v1, read-optimized schema)
- **Agent/Author:** Claude (Senior DevOps/Data Engineer session)
- **Task:** Establish local dev infrastructure: docker-compose with PostgreSQL, Redis, Prometheus, Grafana, and Keycloak; initial Flyway migrations for Users/Pills/Tags with read-heavy indexing; document topology and ports here.
- **Changes:**
  - `docker-compose.yml` — 5 requested services **plus** OTel Collector, Loki, and Tempo (mandated by `OBSERVABILITY.md` §1 and backlog item 3). Single bridge network `codepill-net`; healthchecks on stateful services; Postgres runs with `pg_stat_statements` preloaded (`OBSERVABILITY.md` §4.2). Postgres published on host **5433** (5432 occupied by an unrelated local project).
  - `ops/db/init/01-create-databases.sql` — one database per owning service (`codepill_identity`, `codepill_catalog`) + `keycloak`, honoring "services own their data" (`ARCHITECTURE.md` §3.2). No cross-service FKs.
  - `ops/db/migrations/identity/V1__initial_identity_schema.sql` — `users` (IdP subject mapping only; credentials stay in Keycloak per `SECURITY.md` §2.1). Unique indexes on `idp_subject` (token→user resolution) and citext `email`; `(status, created_at DESC)` for admin listings.
  - `ops/db/migrations/catalog/V1__initial_catalog_schema.sql` — `pills`, `tags`, `pill_tags`. Read-heavy indexing: partial `(published_at DESC, id DESC) WHERE status='PUBLISHED'` for keyset-paginated feed, unique slug lookups, `(author_id, updated_at DESC)` workspace index, partial review-queue index, GIN on generated `search_vector` (full-text) and trigram GIN on title (fuzzy match), covering `(tag_id, pill_id)` for tag→pills joins. Optimistic-lock `version` column; `updated_at` triggers; CHECK-constrained status/type enums.
  - `ops/db/seed/seed_catalog_high_volume.sql` + `db-seed` compose service (profile `seed`) — idempotent 100k-pill / 50-tag / ~300k pill_tag load for index validation. Kept out of Flyway history (test data, not schema).
  - `ops/keycloak/realm-codepill.json` — realm `codepill`: role hierarchy LEARNER⊂AUTHOR⊂CURATOR⊂ADMIN via composites; SPA client `codepill-web` (Auth Code + PKCE S256, implicit/password grants disabled); `codepill-service` (client credentials); `codepill-api` scope adds `aud=codepill-api` and flat `roles` claim; access-token TTL 900s, rotating one-time refresh tokens (`SECURITY.md` §2.2). Four dev users (`dev-learner|author|curator|admin`).
  - `ops/observability/` — Prometheus scrape config (collector, Keycloak, host-run Spring services via `host.docker.internal`), Grafana datasource provisioning with Loki↔Tempo `trace_id` correlation, OTel Collector pipelines (traces→Tempo, logs→Loki OTLP, metrics→Prometheus), minimal single-binary Loki/Tempo configs.
  - `.env.example`, `.gitignore` — local-only default credentials, overridable; `.env` ignored.
  - This file — snapshot, topology/ports section, backlog item 3 marked done.
- **Standards compliance:** TDD N/A (infrastructure/SQL, no application code). Migrations verified by applying against real PostgreSQL 17 via the Flyway containers (both DBs at v1) — same migrations will be exercised by Testcontainers ITs per `TESTING_QUALITY.md` §3.2. No secrets committed: all credentials are documented local-dev defaults injected via env with `.env` overrides. Observability backbone provisioned per `OBSERVABILITY.md` (OTLP endpoints 4317/4318 ready for first service).
- **Tests:** Full stack booted and verified: all 10 containers healthy; OIDC discovery + client-credentials token minted (`aud=codepill-api`, `iss=http://localhost:8180/realms/codepill`, TTL 900s, PKCE S256 advertised); Loki/Tempo/Grafana/Prometheus ready; Redis AUTH+PING ok; Prometheus targets up (except intentional placeholder for future services). Index validation under 100k-row seed via `EXPLAIN ANALYZE`: published feed 0.08ms (partial index), slug lookup 0.07ms, tag-filtered feed 0.96ms (index-only scans), ranked full-text 0.22ms (GIN) — no seq scans on hot paths.
- **Follow-ups / debt:**
  - Move Flyway scripts into each service's `adapters/out/persistence/src/main/resources/db/migration` when the Maven modules are scaffolded (keep checksums identical or `flyway repair`).
  - Grafana dashboards + alert provisioning (`/ops/observability/`, burn-rate alerts per `OBSERVABILITY.md` §5) not yet defined.
  - Postgres/Redis exporters for infra-level metrics not included — add if DB dashboards are wanted before the first service ships.
  - Repo still not a git repository — `git init` before first code task (`.gitignore` already in place).

## [2026-07-09] Initialize architectural governance
- **Agent/Author:** Claude (Lead Principal Engineer session)
- **Task:** Establish architectural governance before any application code.
- **Changes:** Created `/docs/standards/` with `ARCHITECTURE.md` (Clean Architecture for Java 25 / Spring Boot 4 backend and React/TypeScript frontend), `OBSERVABILITY.md` (OpenTelemetry, structured JSON logging to Loki + Sentry, Actuator query metrics, custom business metrics), `SECURITY.md` (OAuth2/OIDC + JWT on all endpoints, three-layer RBAC), `TESTING_QUALITY.md` (mandatory TDD, 85%+ coverage, JUnit 5, Testcontainers, Playwright), and this changelog. Added root `CLAUDE.md` binding all future agents to the read-standards-then-log protocol.
- **Standards compliance:** N/A — documentation only; no application code written.
- **Tests:** N/A — no code yet.
- **Follow-ups / debt:** Backlog items 1–5 in "Next Up". Repo is not yet a git repository — initialize git before first code task.
