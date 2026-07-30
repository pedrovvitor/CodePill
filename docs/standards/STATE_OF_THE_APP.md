# CodePill — State of the App (Living Changelog)

> **Status:** MANDATORY · **Owner:** Everyone · **Last updated:** 2026-07-29
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

- **Phase:** 1 — First vertical slice complete (catalog service + web SPA + CI pipeline + E2E journeys)
- **Deployable services:** `codepill-catalog` (Java 25, Spring Boot 4.0.7, Maven multi-module under `/backend`, wrapper committed). Runs on host port **8080** (`CODEPILL_CATALOG_PORT`), scraped by Prometheus via `host.docker.internal:8080`.
- **Local dev infrastructure:** ✅ docker-compose stack (PostgreSQL 17, Redis 7, Keycloak 26.2, OTel Collector, Prometheus, Grafana, Loki, Tempo) — see "Local Dev Stack" below.
- **Database schema:** Flyway v1 for `codepill_identity` (users, still under `ops/db/migrations/identity/`) and `codepill_catalog` (pills, tags, pill_tags — now owned by the service at `backend/codepill-catalog/adapters/out/persistence/src/main/resources/db/migration/`, checksum-identical; compose mounts that path).
- **Frontend:** ✅ `codepill-web` SPA under `/frontend` (React 19 + TypeScript strict, Vite 8, pnpm, Tailwind 4). Clean Architecture layers per `ARCHITECTURE.md` §4 enforced by `eslint-plugin-boundaries`; OAuth2 Code+PKCE via `react-oidc-context` (tokens in memory); TanStack Query 5 for all server state; API types generated from the OpenAPI contract; OTel browser tracing → collector `:4318`. Dev server on **5173** (Vite proxy `/api` → `localhost:8080`). See the 2026-07-10 entry for structure and decisions.
- **CI/CD:** ✅ GitHub Actions (`.github/workflows/main.yml`) on push/PR to `main` + nightly + manual. Jobs: **backend** (`mvnw verify`: JUnit + Testcontainers ITs + ArchUnit + JaCoCo ≥85% gate, then SonarQube `codepill-backend` with blocking quality gate), **frontend** (ESLint boundaries + Prettier + Vitest ≥85% gate + `tsc`/build, then SonarQube `codepill-frontend`), **security** (gitleaks full-history, `pnpm audit --audit-level critical`, Trivy CRITICAL, OWASP Dependency-Check CVSS≥9 when `NVD_API_KEY` set), **e2e** (Playwright vs ephemeral compose stack). Sonar/OWASP steps self-skip until the `SONAR_HOST_URL`/`SONAR_TOKEN`/`NVD_API_KEY` repo secrets exist; every coverage and vulnerability gate fails the build unconditionally.
- **E2E:** ✅ Playwright suite in `/e2e` (6 journeys, mobile viewport): OAuth2 Code+PKCE login/logout via Keycloak, role-gated authoring nav, create-pill form (+client-side contract validation), feed infinite scroll with API-seeded data (author drafts → curator publishes). See the 2026-07-10 CI entry for how to run locally.
- **Test coverage:** ≥85% line+branch per module enforced at build; domain/application near 100%. Backend: 133 unit tests + 20 integration tests (Testcontainers PostgreSQL/Redis) + 6 ArchUnit rules. Frontend: 134 Vitest tests, 99% statements / 91% branches / 100% functions (85% gate wired in `vite.config.ts`). All green.
- **Container images (2026-07-29):** ✅ `backend/Dockerfile` (multi-stage, Spring Boot layered-jar extraction, non-root uid 1001) and `frontend/Dockerfile` (pnpm build → `nginx-unprivileged`, CSP + security headers via env-templated nginx conf, runtime config injection through `/config.js` so one image serves any environment). CI `images` job pushes both to GHCR (`codepill-catalog`, `codepill-web`, tags `latest` + commit SHA) on green main. SPA initial bundle split 559 kB → 327 kB (OTel SDK and CreatePill/RHF+Zod lazy-loaded).
- **Kubernetes deployment (2026-07-30):** ✅ `ops/k8s/` — Helm charts for app/data/observability tiers targeting k3s on a single VPS, reconciled by Argo CD (app-of-apps), TLS via cert-manager + traefik, default-deny NetworkPolicies, prod Keycloak realm (no committed secrets, pinned demo users), nightly backups + demo-content reset, one-shot VPS bootstrap script. Validated end-to-end on a local kind cluster. Awaiting VPS + domain for go-live (phase 5).
- **Hardening (2026-07-11 adversarial review):** Redis 250ms fail-fast timeouts + afterCommit cache eviction; Hikari sized w/ leak detection + 5s `statement_timeout`; JWKS decoder with 2s HTTP timeouts; Bucket4j rate limiting on writes; 64k pill-content cap; `page ≤ 500`; prometheus scrape auth-gated outside local; fail-closed `prod` profile; per-service Postgres roles; loopback-only compose ports; SHA-pinned third-party CI actions; `codepill.*` span attributes on every use case. Details in the 2026-07-11 log entry.
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
| `codepill-postgres` | `postgres:17-alpine` | `5433 → 5432` | Primary DB. Databases: `codepill_identity`, `codepill_catalog`, `keycloak` — each owned by its own least-privilege role (`*_svc`, cross-DB CONNECT revoked). All host ports on this stack bind `127.0.0.1` only. (Host 5433 because 5432 is taken by another local project.) |
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

**Go-live track — publish CodePill as a public, navigable demo (Kubernetes deploy) + public repo.** Full rationale in the 2026-07-29 log entry.

1. ~~**Containerize:** multi-stage Dockerfiles for `codepill-catalog` (Temurin 25 JRE, non-root) and `codepill-web` (static build behind nginx with CSP + `X-Content-Type-Options`); CI job pushes images to GHCR tagged by SHA; frontend code splitting / lazy OTel init.~~ ✅ Done 2026-07-29 (see log entry).
2. ~~**Kubernetes deploy (k3s on a single VPS):** Helm charts, probes/HPA/SecurityContext/NetworkPolicies, ingress + cert-manager, in-cluster data + observability tiers, GitOps CD via Argo CD.~~ ✅ Done 2026-07-30 (kind-validated; runs on the VPS at go-live).
3. **Production hardening (remaining):** ingress-level rate limiting (traefik middleware); final secrets pass (gitleaks full history) **before flipping the repo public**. ✅ Already done: prod realm (no committed secrets, prod-domain redirect URIs), Secrets-fed fail-closed config, nightly demo reset + backups, Trivy image scan in CI.
4. **Demo content & showcase (remaining):** README gains Live Demo section + screenshots; SonarCloud badges; repo → public. ✅ Already done: 17 curated pills seeded via GitOps hook, demo credentials panel on the login page, MIT `LICENSE`.
5. **Go-live (needs the VPS + domain):** DNS records; run `ops/k8s/bootstrap/bootstrap.sh`; set real `domain`/ACME email/image tags in `ops/k8s/apps/*.yaml`; E2E smoke against the prod URL; anonymous 401/403 matrix live; securityheaders.com + Lighthouse; re-verify NetworkPolicies on k3s (kind can't enforce them); light load check.

**Product track (post-demo):**

6. `codepill-identity` integration with Keycloak; security test matrix.
7. First vertical slice: author creates a pill → learner completes it (TDD, fully observable, secured).
8. Backend Spotless/Error Prone lint stage (CI §4 stage 1, pre-existing debt).
9. **AI authoring aid (post-launch, phase 6):** LLM-assisted "draft a pill" generative tooling + embeddings-based semantic feed search (pgvector), model access as a k8s workload — mirrors the growing AI/ML focus of the target role.

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

## [2026-07-30] Local demo stack (all-Docker) + split-horizon JWT fix it uncovered
- **Agent/Author:** Claude (platform engineering session)
- **Task:** Run the full platform locally in Docker (production images, demo pills seeded) as a pre-deploy rehearsal.
- **Changes:**
  - **`ops/local-demo/`** — compose overlay adding the two production images to the base infra stack: catalog on the **prod profile** (every VPS-bound variable spelled out), web's nginx standing in for the ingress (`/api` proxy, same-origin — `web-local.conf.template`), published at `localhost:5173` so the dev realm's redirect URIs work unchanged. `demo-seed` service (opt-in `--profile demo-seed`, it resets the catalog) applies the k8s demo seed after rewriting the pinned prod-realm author UUIDs to the IDs Keycloak actually assigned to `dev-author`/`dev-curator` — ownership flows testable locally. Gotcha recorded inline: relative paths in compose overlays resolve against the FIRST file's directory.
  - **Backend (TDD):** the rehearsal caught a real bug — `SecurityConfig#jwtDecoder` always resolved keys via **issuer discovery**, so any deployment where the browser-facing issuer URL is not reachable from inside the network (this stack; also the k8s pods but for the ingress hairpin) failed all authenticated requests with 401 (`JwtDecoderInitializationException`, lazily — kind validation never saw it because nothing exercised an authenticated call). Fix: honor the standard `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` property when set (keys from it, no discovery; `iss` + `aud` validation unchanged). `JwtDecoderConfigTest` (4 tests) proves it against a stub IdP that 404s discovery.
  - **k8s charts:** catalog now fetches the JWKS **in-cluster** (`http://keycloak:8080/...`) instead of round-tripping through the public issuer URL; `keycloak-ingress` NetworkPolicy gains the catalog→8080 allowance. Egress needs of the pod stay local.
- **Verification:** backend `mvnw verify` green (coverage gate included); `helm lint` clean on both changed charts; live smoke on the stack: catalog readiness UP, anon `/api/v1/pills` → 401, `dev-learner` token via the web proxy → **200 with the seeded feed** (16 PUBLISHED + 1 DRAFT in the DB), all 5 Prometheus targets up (catalog scrape unchanged via `host.docker.internal:8080`). Token minted by temporarily toggling direct grant on via kcadm and restoring it to `false` right after.
- **Standards compliance:** failing test first for the backend change; no secrets added (local-dev defaults only); deviation none — the fix aligns the custom decoder with the standard Spring property.
- **Follow-ups / debt:** demo login panel is off locally (`VITE_DEMO_MODE=false` — the panel advertises the prod realm's `demo-*` accounts; local realm has `dev-*`); at go-live, verify the catalog→keycloak:8080 NP allowance on k3s together with the rest of the deny-by-default posture.

## [2026-07-30] Go-live phase 2: Kubernetes (Helm + k3s + Argo CD) — validated on a local kind cluster
- **Agent/Author:** Claude (platform engineering session)
- **Task:** Execute go-live phase 2 (Kubernetes deployment via own Helm charts, GitOps CD, VPS bootstrap) plus the phase-3/4 items doable without a VPS (prod Keycloak realm, backups, demo content lifecycle, demo login panel, LICENSE, image scanning).
- **Changes:**
  - **`ops/k8s/charts/codepill`** — app tier: catalog Deployment (prod profile, fail-closed env, startup/readiness/liveness on a separate **management port 8081** that the Ingress never routes; `/actuator/prometheus` opened via `SPRING_APPLICATION_JSON` but reachable only from the Prometheus pod by NetworkPolicy — the SECURITY.md §3.3 "network-restricted" exception, actually network-restricted), HPA (CPU 70%), web Deployment (runtime config + CSP allowlists derived from `domain`), single-host Ingress (`/` → web, `/api` → catalog; same-origin, no CORS), default-deny NetworkPolicies, and the **demo content lifecycle**: 17 curated pills (`files/demo-seed.sql`, the platform teaching its own stack) seeded by an Argo CD PostSync hook and reset nightly by CronJob.
  - **`ops/k8s/charts/codepill-infra`** — data tier: Postgres StatefulSet with the per-service least-privilege roles recreated from Secret-fed init (mirrors F-10), Redis (AOF, LRU, password), **Keycloak 26 in production mode** (`start --import-realm`, `KC_HOSTNAME`, proxy headers) importing a Helm-templated **prod realm**: no confidential clients (nothing secret in git), prod-domain redirect URIs, demo users with **pinned UUIDs** (seed data references them) and public-by-design credentials; nightly `pg_dump` CronJob to PVC (7-day rotation); Let's Encrypt ClusterIssuer.
  - **`ops/k8s/charts/codepill-observability`** — collector/Prometheus/Grafana/Loki/Tempo reusing the compose configs nearly verbatim (same Service DNS names); public surface limited to `POST /v1/traces` on `otel.<domain>` (browser spans) and Grafana; Prometheus scrapes catalog:8081, Keycloak:9000 and the collector.
  - **`ops/k8s/apps/` + `ops/k8s/bootstrap/`** — Argo CD app-of-apps (cert-manager wave -1 → infra/observability 0 → app 1, automated sync + selfHeal + prune) and a one-shot VPS bootstrap script (k3s with `--secrets-encryption`, pinned Argo CD, Secrets from a local `secrets.env`, root Application). `ops/k8s/README.md` documents topology, day-2 and recorded decisions.
  - **Frontend demo mode (TDD):** `VITE_DEMO_MODE` in `env.ts`, demo-accounts panel on `LoginPage` (props-driven — UI stays boundary-clean; the app router wires it from config), runtime entrypoint passes the flag; +4 tests (143 total, 99.2%/91.6%).
  - **CI:** Trivy scans both images (CRITICAL, `--ignore-unfixed`) before the GHCR push. **`LICENSE`** (MIT) added.
- **Validated on a throwaway kind cluster** (all three charts installed with test Secrets): 10/10 workloads Ready; Flyway ran via the catalog boot; demo seed applied (16 PUBLISHED + 1 DRAFT); prod realm imported ("Import finished successfully"); Prometheus showed **all 5 targets up** including catalog via the management port; catalog logs ended at zero ERROR. Two real integration bugs found and fixed by this validation:
  1. **Kubernetes service links broke the catalog boot** — the Service name `codepill-catalog` makes k8s inject `CODEPILL_CATALOG_PORT=tcp://…`, colliding with the app's `server.port` variable → `enableServiceLinks: false` on both Deployments.
  2. **Boot 4 pushes OTLP metrics to localhost by default** (`OtlpMeterRegistry` WARN-spam in-cluster; silently redundant locally where the collector happens to listen). Metrics ship via the Prometheus scrape per OBSERVABILITY.md §4.1, so the push is now disabled (`management.otlp.metrics.export.enabled=false` — note: unlike tracing, the metrics property kept its Boot 3 name). Also pinned `CODEPILL_ENV=prod` so `deployment.environment` stops reporting `local`.
- **Standards compliance:** TDD for all frontend code; charts carry restrictive SecurityContexts (non-root, read-only root FS where the runtime allows, seccomp, drop ALL), deny-by-default network posture, no secrets in git; telemetry topology per OBSERVABILITY.md; deviations recorded inline (in-cluster anonymous scrape bounded by NetworkPolicy; public OTLP traces path bounded by memory_limiter + exact-path routing).
- **Tests:** frontend 143/143 green (lint/format/build clean); `helm lint` clean ×3; templated realm JSON parse-verified; kind end-to-end as above. Backend untouched.
- **Follow-ups / debt:** ingress-level rate limiting (traefik middleware) on top of in-service Bucket4j; Grafana dashboards as code (provisioned datasources only so far); Argo CD Image Updater or a small CD step to bump image tags automatically; kind validation can't exercise NetworkPolicies (kindnet doesn't enforce) — re-verify the deny-by-default posture on k3s at go-live.

## [2026-07-29] Go-live phase 1: container images, GHCR publish, SPA bundle split
- **Agent/Author:** Claude (platform engineering session)
- **Task:** Execute go-live phase 1 — containerize both deployables, publish images from CI, and close the SPA bundle/headers debt.
- **Changes:**
  - **`backend/Dockerfile`** (+ `.dockerignore`) — multi-stage: `eclipse-temurin:25-jdk` Maven build (BuildKit m2 cache) → layered-jar extraction (`-Djarmode=tools extract --layers --launcher`) → `eclipse-temurin:25-jre` runtime with one image layer per Boot layer (dependency pulls stay cached across code-only changes), non-root `codepill` (uid 1001), `JarLauncher` entrypoint. Image is environment-agnostic: all config via env (application.yml placeholders).
  - **`frontend/Dockerfile`** (+ `.dockerignore`, `docker/`) — multi-stage: `node:24-alpine` pnpm build → **`nginxinc/nginx-unprivileged:1.29-alpine`** (uid 101, port 8080). `docker/default.conf.template` ships the SECURITY.md §4.3 headers — **CSP** (env-expandable `connect-src`/`frame-src` lists for the IdP and OTLP origins; the OIDC silent-renew iframe needs the IdP in `frame-src`), `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, `frame-ancestors 'none'` — plus SPA fallback, immutable caching for hashed `/assets/`, no-cache for `index.html`/`config.js`, and a static `/healthz`. HSTS stays at the TLS terminator.
  - **Runtime configuration (12-factor, one image per all environments):** `docker/40-codepill-runtime-env.sh` writes `/config.js` (`window.__CODEPILL_ENV__`) from container env at startup; `index.html` loads it before the bundle; `env.ts` gains `readRuntimeEnv` (TDD — 5 failing tests first) which merges runtime values over `import.meta.env`, discarding empty strings so unset deploy vars never clobber build-time values. Dev/preview serve an empty `public/config.js`.
  - **Bundle split (closes the 559 kB debt):** route-level `React.lazy` for all pages + dynamic-import OTel bootstrap. Initial chunk **327 kB (99 kB gzip)**; OTel SDK (100 kB) and CreatePillPage with react-hook-form/zod (103 kB) load lazily. Fetch instrumentation still patches in before any API call (data fetching starts only after the OIDC exchange).
  - **CI (`main.yml`):** new `images` job — plain docker CLI + `GITHUB_TOKEN` (no new third-party actions to pin), runs only on green `main` after all gates (`needs: backend, frontend, security, e2e`), pushes `ghcr.io/<owner>/codepill-{catalog,web}` tagged `latest` + commit SHA.
  - **Flaky-test fix:** `otel.test.ts` had a hidden network dependency — `provider.shutdown()` exported spans to `:4318`, hanging 5s per test when nothing listened (Windows drops the SYN; only passed with the local collector up). The OTLP exporter is now mocked in the test file per TESTING_QUALITY.md §3.3 (no network in unit tests).
- **Standards compliance:** TDD for the code change (failing `readRuntimeEnv` tests first); coverage 99.21% stmts / 91.32% branches (≥85% gate green); CSP/`nosniff` debt from 2026-07-10 closed; no new anonymous API surface (nginx `/healthz` is static, backend untouched); no secrets in images (verified: config comes from env at runtime).
- **Tests:** Frontend 139 Vitest tests (+5) green, lint/format/typecheck/build clean. **Container smoke (both images run locally):** web — headers present with env-expanded CSP, `/config.js` reflects container env, `/healthz` 200, SPA fallback 200, immutable asset caching, uid 101; catalog — on the compose network: readiness/liveness UP (Flyway validate against the real schema), anonymous API → 401, `/actuator/metrics` → 401, JSON logs zero ERROR, uid 1001. **E2E 6/6 green (27.8s)** against the code-split production build with `config.js`.
- **Follow-ups / debt:** images are amd64-only (pick an x86 VPS or add buildx/QEMU for arm64); CI image job re-downloads Maven deps (no cross-run BuildKit cache) — consider `actions/cache` + buildx cache mounts if it gets slow; phase 2 (k8s manifests via Helm) consumes these images.

## [2026-07-29] Production/demo deployment plan (public side-project showcase)
- **Agent/Author:** Claude (planning session)
- **Task:** Assess production-readiness and plan publishing CodePill as a publicly navigable demo + public repo, as a portfolio side project targeting a Senior Full Stack (Java & Kubernetes) role.
- **Changes:** Docs only. Replaced the completed "Next Up" items with a phased go-live backlog (containerize → k3s Kubernetes deploy → prod hardening → demo content/showcase → go-live checklist). Key decisions proposed: **k3s on a single VPS** as the Kubernetes target (real k8s primitives at hobby cost; the deployment gap becomes the showcased skill); **pre-created demo accounts, no self-registration** (no real PII, no abuse surface); **separate prod Keycloak realm** generated at deploy time (the committed realm file stays local-dev only); repo flips **public with MIT license** only after a final full-history secrets pass. Current gaps confirmed: no Dockerfiles, no LICENSE, repo private, no deployment target — everything else (security hardening, fail-closed prod profile, CI gates, E2E) already landed in the 2026-07-11 review.
- **Standards compliance:** N/A — planning/documentation; no application code changed.
- **Tests:** N/A.
- **Follow-ups / debt:** execute go-live phases 1–5 in "Next Up".

## [2026-07-13] Commit adversarial-review fixes; add project README
- **Agent/Author:** Claude (DevRel/Technical Writer session)
- **Task:** Land the adversarial-review changes as semantic commits (per team decision: no `docs/adr/` directory — decision rationale lives in this file), strip fix-narrating/self-evident code comments, re-verify everything, and write a root `README.md` aimed at developers and recruiters.
- **Changes:** 6 commits (`5d510c8`…`5b02173`): backend hardening, frontend hardening, ops hardening, CI action pinning, this changelog, and the new `README.md` (badges, Mermaid architecture diagram, engineering highlights, zero-config quickstart with dev-realm accounts, curl walkthrough, observability tour, env-var reference). Comment cleanup only — no behavior changes beyond the already-reviewed fixes.
- **Standards compliance:** N/A for the README (docs). All gates re-verified after cleanup: backend `mvnw verify` green (unit+IT+ArchUnit+JaCoCo), frontend 134 tests / 99% stmts / 91% branches + lint + build, E2E 6/6 (25.8s) against the freshly built jar with zero ERROR log lines.
- **Tests:** No new tests (docs/cleanup); full regression suites re-run green as above.
- **Follow-ups / debt:** ARCHITECTURE.md §5 and CLAUDE.md still reference `docs/adr/` — align the standards text with the no-ADR-directory decision on the next standards touch.

## [2026-07-11] Adversarial Review Findings & Fixes
- **Agent/Author:** Claude (Principal Security & Reliability Engineer session)
- **Task:** Adversarial production-readiness audit of `/backend`, `/frontend`, `/ops`, and CI against all `/docs/standards`, followed by implementation of the fixes (resiliency, security hardening, query/cache correctness) and this record.
- **Changes:** See the full findings table below. Two deliberate deviations are recorded inline there (in-service rate limiting until a gateway exists; bounded offset pagination retained in the v1 contract) — the team decided against a separate ADR directory, so this file is the decision record.
- **Standards compliance:** All fixes TDD'd (failing tests written first for the domain cap, deferred eviction, rate limiter, span tags, page cap); coverage gates unchanged at ≥85% and green (backend per-module JaCoCo; frontend 99.2% stmts / 91.0% branches); new code paths carry metrics (`codepill_api_rate_limited_total`), structured logs, and span attributes; no new anonymous surface (one removed outside local); security matrix extended (`HardenedProfileIT`: prometheus 401, 429 + Retry-After).
- **Tests:** Backend `mvnw verify` green — 133 unit (+8) / 20 IT (+3) / 6 ArchUnit (+1). Frontend 134 Vitest tests (+17) green, lint/typecheck/build clean. E2E 6/6 green (20.5s) against the hardened stack — and the suite caught a login-breaking flaw in one audit recommendation before it shipped (see F-16).
- **Follow-ups / debt:** listed at the end of the findings section below.

### Adversarial Review Findings & Fixes

**Verdict before fixes:** architecture, RBAC layering, log/metric formats and test discipline were genuinely solid; the production-readiness gaps were concentrated in *resiliency* (no timeouts anywhere), *cache/transaction interaction*, *input bounds*, and *infra defaults that bleed into non-local environments*.

**Resiliency & data layer (backend)**
- **F-01 · CRITICAL — A degraded Redis could take down the DB path.** `@Transactional` use cases performed cache round-trips while holding the DB connection, Lettuce had its 60s default command timeout, and Hikari ran at default size 10 with no leak detection — a hung Redis would pin every connection. Fixed threefold: `spring.data.redis.timeout/connect-timeout: 250ms` (fail-open now fails *fast*); pool sized explicitly (`maximum-pool-size` 20, `connection-timeout` 5s, `leak-detection-threshold` 10s); and evictions moved out of the transaction (F-02).
- **F-02 · HIGH — Stale-cache window: evictions ran *before* commit.** A concurrent reader could repopulate the cache with pre-commit data that then lived a full TTL. `RedisPillCacheAdapter.evictPill/evictPublishedPages` now defer via `TransactionSynchronization.afterCommit` when a transaction is active (and are discarded on rollback — nothing changed); unit-tested for defer, fire and rollback paths.
- **F-03 · HIGH — No statement timeout.** Runaway queries (deep OFFSET, lock waits) could hold connections forever. Postgres `statement_timeout=5000` is now set via Hikari datasource properties.
- **F-04 · HIGH — JWKS fetches to Keycloak had no HTTP timeouts** (the service's only external HTTP call, on the auth hot path). Replaced the auto-configured decoder with a `SupplierJwtDecoder`-wrapped `NimbusJwtDecoder` using 2s connect/read timeouts, preserving lazy init (boots without the IdP) and issuer+audience validation.
- **F-05 · MEDIUM — Unbounded OFFSET + per-request COUNT on the feed** (100k-row table; the purpose-built keyset index can't help OFFSET). Bounded: `page ≤ 500` enforced at web adapter (`@Min/@Max` → 400 via new `HandlerMethodValidationException` handler), in the use case, and in the OpenAPI contract. **Decision:** cursor/keyset pagination deferred to a future `/api/v2` — removing `totalPages` is a breaking change and ARCHITECTURE.md §5 requires a new API version for that.

**Security (backend + infra)**
- **F-06 · HIGH — Unbounded pill `content` JSON** (memory/DoS + cache-amplification vector; the DTO javadoc's "domain invariants re-validate depth" claim was false). `PillContent` now enforces `MAX_LENGTH` 64k as a domain invariant covering create and update paths; javadoc corrected.
- **F-07 · HIGH — No rate limiting on writes** (SECURITY.md §4.6 unimplemented; no gateway exists). Added `WriteRateLimitFilter` (Bucket4j token bucket per validated JWT `sub`, Caffeine-bounded store, 30 writes/min default, RFC 9457 `429` + `Retry-After`, `codepill_api_rate_limited_total` metric, structured WARN). Runs after the security chain; env-tunable via `codepill.rate-limit.*`. **Decision:** the standard places this at the gateway, but none exists yet — enforced in-service until then (per-instance buckets; move to a distributed store when scaling out).
- **F-08 · MEDIUM — `/actuator/prometheus` anonymous everywhere** — a self-granted exception to the SECURITY.md §3.3 allowlist, "network-restricted" only by comment. Now gated by `codepill.security.prometheus-public` (default **false**; `true` only in the local config; prod profile pins false). `HardenedProfileIT` proves the locked posture.
- **F-09 · MEDIUM — Prod could silently inherit local credentials/issuer.** `application.yml` fallbacks (`codepill-local`, localhost issuer) applied in any environment. New `application-prod.yml` uses no-default placeholders — a missing env var now aborts startup (fail closed) — and sets 10% trace sampling per OBSERVABILITY.md §2.
- **F-10 · HIGH — One Postgres role owned every database** (identity, catalog, *and* Keycloak's user store) — a leaked catalog credential meant full lateral movement. Init script now creates `codepill_identity_svc`/`codepill_catalog_svc`/`keycloak_svc`, each owning only its DB with `REVOKE CONNECT … FROM PUBLIC` (verified: cross-DB connect denied). Compose, `.env.example` and `application.yml` updated; the running local volume was migrated **additively** (roles + grants, no data loss). Fresh stacks get it from init; existing external stacks need the same one-time grant script.
- **F-11 · MEDIUM — gitleaks was blinded exactly where a secret would land**: a whole-file allowlist on `realm-codepill.json` (which ships the committed local `codepill-service` client secret). Allowlist narrowed to the fixture literal only — a real secret pasted into the realm file is now caught. Realm also pins `sslRequired: external`.
- **F-12 · MEDIUM — Every compose port bound 0.0.0.0** with fixture passwords — Postgres/Redis/Keycloak/Grafana reachable from any LAN. All published ports now bind `127.0.0.1`.
- **F-13 · MEDIUM — Third-party GitHub Actions pinned to mutable tags** (supply-chain vector with `SONAR_TOKEN`/`NVD_API_KEY` in scope). `pnpm/action-setup`, `gitleaks/gitleaks-action`, `sonarsource/sonarqube-scan-action` now pinned to commit SHAs.

**Observability compliance (backend)**
- **F-14 · HIGH — `codepill.*` span attributes missing entirely** (OBSERVABILITY.md §2 rule 4): traces carried no pill/feed IDs. Added `SpanTags` helper (application layer, no-op without an active observation) + `ObservationRegistry` wiring; every use case now tags `codepill.pill.id`/`codepill.pill.type`/`codepill.feed.page|size` on its `@Observed` span.
- **F-15 · LOW — `service.name` resource attribute implicit** → now explicit in `application.yml`. Also: new ArchUnit rule pins `@Transactional` to `..application.usecase..` (the standard's rule 6 was previously unenforced).

**Frontend**
- **F-16 · HIGH — audit recommendation REFUTED by E2E:** the auditor flagged PKCE state in `localStorage` and recommended in-memory storage. In-memory `stateStore` **breaks every login** (the code flow's full-page redirect wipes the JS heap → "No matching state found"). Final fix: `stateStore` pinned to **sessionStorage** (tab-scoped, cleared on close, one-time entries) — off persistent disk without breaking the redirect. Tokens remain in-memory (`userStore`) per SECURITY.md §2.2. E2E login journeys prove it.
- **F-17 · HIGH — Infinite feed grew without bound** (`useInfiniteQuery` without `maxPages`; all pages rendered). Capped at 5 pages with `getPreviousPageParam` for bidirectional paging; `FeedSentinel` observer no longer recreated every render and prefetches via `rootMargin: 200px`.
- **F-18 · MEDIUM — Query cache survived sign-out** (user A's data visible to a subsequent session on the same tab). `signOut` now `queryClient.clear()`s before the end-session redirect.
- **F-19 · MEDIUM — No fetch timeout; mutations unabortable** (a black-holed request pended forever, pinning the UI). All requests now race a 15s `AbortSignal.timeout`, merged with caller signals via `AbortSignal.any`.
- **F-20 · MEDIUM — Prod builds silently shipped `localhost` fallbacks** for the IdP and OTLP collector. `env.ts` now throws at boot in prod builds when `VITE_OIDC_AUTHORITY`/`VITE_OTLP_TRACES_URL` are unset (E2E preview configures them explicitly — that failure mode was itself caught by the E2E suite). Browser tracing gains env-driven ratio sampling (`VITE_TRACE_SAMPLING`) and a `pagehide` `forceFlush` so tab-close spans aren't lost.

**Infra reliability**
- **F-21 · LOW — Observability tier had no healthchecks/ordering**: otel-collector could start before Loki/Tempo accepted writes (early telemetry dropped). Added `wget`-based healthchecks (verified live: all healthy) and `service_healthy` conditions for collector and Grafana.

**Explicitly reviewed and left as-is (with reasoning)**
- Scanner thresholds (Trivy CRITICAL, `pnpm audit` critical, DC CVSS≥9) match SECURITY.md §4.5's "critical CVEs" wording — tightening to HIGH is a standards change, not a compliance fix.
- No N+1 queries exist (the aggregate is a single flat table, zero JPA relationships) — verified, not assumed.
- Feed over-fetches `content` into cache/responses — real, but fixing it cleanly belongs to the v2 cursor contract since `content` is required on `Pill` in the v1 contract.
- Sentry (OBSERVABILITY.md §3.2) remains deferred — needs a Boot-4-validated starter + DSN; unchanged known risk.
- `logstash-logback-encoder` compile-dep in the application layer (for `kv()`) — accepted annotation-facade-style compromise, flagged for a future logging abstraction.

**Follow-ups / debt from this review**
- `/api/v2` cursor pagination — also drops the per-request COUNT and lets the feed omit `content`.
- Distributed rate-limit store when scaling past one instance.
- Existing non-local stacks (if any ever created) need the one-time per-service-role grant script from F-10.
- Backend Spotless/Error Prone lint stage (pre-existing debt, unchanged).
- Consider a standards PR: scanner thresholds to HIGH, and documenting the annotation-facade exception in ARCHITECTURE.md.

## [2026-07-10] CI pipeline (GitHub Actions) + Playwright E2E suite + two realm-import bug fixes
- **Agent/Author:** Claude (DevSecOps Engineer session)
- **Task:** GitHub Actions pipeline (`.github/workflows/main.yml`) running backend JUnit, frontend Vitest, SonarQube scans, failing on <85% coverage or vulnerabilities; Playwright E2E suite (OAuth2 login, create pill, feed scroll) run against ephemeral containers in CI; publish the repo to GitHub; document everything here.
- **Changes:**
  - **`.github/workflows/main.yml`** — triggers: push/PR to `main`, nightly cron (03:00 UTC), manual. Stages (TESTING_QUALITY.md §4 order, all blocking):
    1. **backend** — Temurin 25, `mvnw -B verify` (125 unit + 17 Testcontainers ITs + 4 ArchUnit; JaCoCo ≥85% line+branch per module fails the build), then SonarQube scan `codepill-backend` (`sonar.qualitygate.wait=true`) when `SONAR_HOST_URL`+`SONAR_TOKEN` secrets are set; test reports uploaded on failure.
    2. **frontend** — pnpm 11 / Node 24: `pnpm lint` (ESLint + boundaries), `format:check`, `test:coverage` (Vitest thresholds ≥85% fail the build; lcov now emitted for Sonar), `build` (`tsc -b` + vite), then SonarQube scan `codepill-frontend` (`frontend/sonar-project.properties`, blocking quality gate) under the same secret condition.
    3. **security** — gitleaks (full history; `.gitleaks.toml` = default rules + allowlist for the documented local-dev fixtures), `pnpm audit --audit-level critical` for `frontend/` and `e2e/` (fails on known critical CVEs, SECURITY.md §4.5), Trivy fs scan (CRITICAL, fail-on-find), OWASP Dependency-Check (`failBuildOnCVSS=9`) when the `NVD_API_KEY` secret is set.
    4. **e2e** (needs backend+frontend) — **ephemeral containers**: `docker compose up -d --wait postgres redis keycloak otel-collector` (realm auto-imported), `docker compose run --rm flyway-identity|flyway-catalog`, catalog jar built and started on the runner (same host-process topology as local dev), Playwright (chromium, mobile viewport) with the SPA production build served by `vite preview` on 5173; report uploaded on failure; `docker compose down -v` always.
  - **`/e2e`** — Playwright/TypeScript project (pnpm), 6 journeys in 3 specs, `getByRole`/`getByLabel` selectors only, zero fixed sleeps, `retries: 0`:
    - `auth.spec.ts` — learner OAuth2 Code+PKCE login via Keycloak's hosted form reaches the protected feed (and has no Create entry); author sees the role-gated Create destination; sign-out via end-session returns to login.
    - `create-pill.spec.ts` — author creates a pill through the form (slug auto-suggestion asserted, success panel), and client-side Zod validation blocks contract violations before any API call.
    - `feed-scroll.spec.ts` — API-based fixture per §3.3: author and curator tokens are captured from real Authorization headers after UI logins (tokens are in-memory by design; the network boundary is the only readable place), 12 pills drafted via `POST /api/v1/pills` and published via `/publish`, then a learner scrolls and the IntersectionObserver sentinel auto-loads page 2 (asserted via article count and a page-2 title).
    - Runs **serially** (`workers: 1`): Keycloak's brute-force quick-login check rejects same-user logins <1s apart, so parallel workers are inherently racy.
  - **🐛 Two real integration bugs found by the E2E suite, fixed in `ops/keycloak/realm-codepill.json`** (a full Keycloak realm import does NOT create the built-in client scopes; the previously declared `defaultClientScopes` were silently dropped):
    1. `invalid_scope` on every SPA login — the `profile`/`email` client scopes did not exist in the realm. Added both (username/full-name/email mappers); `codepill-web` defaults now `basic, profile, email, codepill-api` (dangling `roles` entry removed — the flat roles claim comes from `codepill-api`).
    2. **500 on `POST /api/v1/pills`** — access tokens carried no `sub` claim (Keycloak 24+ moved `sub` into the built-in `basic` scope), so `CallerMapper`'s `UUID.fromString(jwt.getSubject())` threw NPE. Added the `basic` scope (`oidc-sub-mapper`) as default for `codepill-web` **and** `codepill-service`. Backend ITs never caught this because `spring-security-test` JWTs always carry `sub` — exactly the gap E2E exists to close.
    - The running local realm was patched **additively** via the admin API to match the file (realm deletion/reimport deliberately avoided); fresh environments (CI) import the corrected file from scratch.
  - Supporting: `frontend/vite.config.ts` now emits `lcov` coverage; `frontend/sonar-project.properties`; `.gitleaks.toml`; repo published to GitHub (`pedrovvitor`).
- **How to run the E2E suite locally:**
  1. `docker compose up -d` (infra + realm import; migrations auto-apply)
  2. Start the catalog service on :8080 — `cd backend && ./mvnw -B -DskipTests package && java -jar codepill-catalog/bootstrap/target/codepill-catalog-bootstrap-*.jar`
  3. `cd e2e && pnpm install && pnpm exec playwright install chromium`
  4. `pnpm test` — Playwright builds the SPA and serves it via `vite preview` on :5173 by itself (outside CI an already-running server on 5173 is reused). `pnpm test:headed` to watch; `pnpm report` for the HTML report.
  - Stacks created before 2026-07-10 need the realm fix once: recreate the realm from the updated import file, or add the `basic`/`profile`/`email` client scopes via the admin console/API.
- **Standards compliance:** pipeline implements TESTING_QUALITY.md §4 stages 2–5 (stage-1 backend lint = Spotless/Error Prone still pending, tracked below); coverage gates ≥85% enforced by build tooling (not only Sonar); security gates per SECURITY.md §4.4/§4.5; E2E per §3.3 (critical journeys only, role-based selectors, API-seeded data, auto-waiting, no retries). E2E verified green locally against the real stack — and the suite caught two production-blocking IdP config bugs before first deploy.
- **Tests:** 6 Playwright journeys, 6/6 green locally (11.2s, serial); `e2e` typechecks clean. Backend/frontend suites unchanged and green.
- **Follow-ups / debt:**
  - Backend lint/format gate (Spotless + Error Prone) — the only §4 stage still missing.
  - Configure repo secrets to arm the optional gates: `SONAR_HOST_URL` + `SONAR_TOKEN` (SonarQube, blocking quality gates), `NVD_API_KEY` (OWASP Dependency-Check). Until then those steps self-skip visibly in the run log.
  - PR-smoke vs nightly-full E2E split (§3.3) — the full 6-test suite currently runs on every trigger (it is small); split when it grows.
  - E2E seeds are not cleaned up (each local run adds 12 published pills + 1 draft; CI stacks are destroyed). Add API-based cleanup if local noise becomes a problem.
  - Consider Dockerfiles + containerized app images so E2E runs the apps in containers too (today they run as host processes, mirroring the local dev topology).

## [2026-07-10] Initialize git repository with semantic history
- **Agent/Author:** Claude (DevOps session)
- **Task:** Initialize git and commit all existing work step by step with concise semantic messages.
- **Changes:** Repo initialized on branch `main`; 18 conventional commits reconstructing the development order: governance docs → repo hygiene (`.gitignore`, `.env.example`) → infra (db / keycloak / observability / compose) → `codepill-catalog` by layer (build → OpenAPI contract → domain → application → adapters → bootstrap) → frontend by layer (scaffold → domain/application → infrastructure → ui/app) → changelog. Added `.gitattributes` pinning `*.sql` to LF (Flyway checksums are byte-sensitive; `autocrlf` on a fresh clone would otherwise break `flyway validate`) and `mvnw`/`*.cmd` endings.
- **Standards compliance:** N/A — version-control housekeeping, no application code changed. No secrets tracked (verified: 180 files, no `.env`, `node_modules`, `dist`, `coverage`, `target`, `.idea`).
- **Tests:** N/A — no code changes; working tree clean after commits.
- **Follow-ups / debt:** Add a remote and push; layer-split commits are semantic units and intermediate backend commits don't build standalone (parent POM references all modules) — irrelevant going forward but noted for anyone bisecting before `ec42a83`.

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
