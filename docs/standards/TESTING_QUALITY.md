# CodePill — Testing & Quality Standard

> **Status:** MANDATORY · **Owner:** Lead Principal Engineering · **Last updated:** 2026-07-09
>
> ⚠️ **AGENT PROTOCOL — NON-NEGOTIABLE**
> Every agent (human or AI) MUST read all files in `/docs/standards/` **before** executing any task in this repository, and MUST append an entry to `/docs/standards/STATE_OF_THE_APP.md` **upon completing** its task.

## 1. TDD is mandatory

All production code is written **test-first**: red → green → refactor.

1. Write a failing test that expresses the requirement.
2. Write the minimum code to pass.
3. Refactor with the tests green.

Practical enforcement: PRs are expected to show test changes alongside (logically before) implementation; a PR that adds behavior with tests bolted on afterward — or not at all — is rejected. Bug fixes MUST start with a failing regression test reproducing the bug.

## 2. Coverage gate — 85%+

- **Minimum 85% line and branch coverage**, enforced in CI (build fails below it).
- Backend: **JaCoCo** with `<rule>` checks per module. Excluded from the metric: `bootstrap/` wiring, generated code (MapStruct impls, OpenAPI-generated), pure DTO records with no logic. Exclusions live in one shared build config — never inline per-PR.
- Frontend: **Vitest coverage (v8)**, same 85% threshold, configured in `vite.config.ts`.
- Coverage is a floor, not a target: `domain/` and `application/` are expected to sit near 100%. Tests written solely to inflate coverage (assertion-free, reflection-poking) are rejected.

## 3. Test pyramid & tooling

| Level | Tooling | Scope | Speed budget |
|---|---|---|---|
| Unit | **JUnit 5** + AssertJ + Mockito | Domain & application, in isolation | whole suite < 30 s/module |
| Integration | **Testcontainers** + Spring Boot Test | Adapters against real infra | < 5 min/service |
| Architecture | ArchUnit | Dependency rules | with unit suite |
| E2E | **Playwright** | Full user journeys through the real UI | < 15 min, critical paths only |

### 3.1 Unit tests (JUnit 5)

- Naming: `shouldCompletePill_whenAllStepsAnswered()` — behavior, not method names.
- Structure: Arrange–Act–Assert, one behavior per test; parameterize variants with `@ParameterizedTest`.
- `domain/` tests use **no mocks** — pure objects in, assertions out. Mock only **ports** at the application layer; never mock types you don't own, never mock value objects.
- AssertJ for assertions (`assertThat(...)`), no bare `assertTrue(x.equals(y))`.
- ArchUnit suite in every service asserts: domain has no framework imports; dependencies point inward; controllers don't touch repositories.

### 3.2 Integration tests (Testcontainers)

- Real dependencies, real wire: **PostgreSQL, Kafka, Keycloak** run as Testcontainers — **H2 and other in-memory stand-ins are banned.**
- Pattern: `@SpringBootTest` + `@ServiceConnection` on shared static containers (per-module singleton containers to keep runtime sane).

```java
@SpringBootTest
@Testcontainers
class PillRepositoryIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
    ...
}
```

- Every repository/adapter has integration coverage: persistence round-trips run against real Postgres with Flyway migrations applied (migrations are thereby tested on every build).
- Web-layer security matrix per endpoint: anonymous → 401, wrong role → 403, correct role → 2xx (JWTs minted against the Keycloak container or `spring-security-test` `jwt()` post-processor).
- Suffix `*IT`, run by Failsafe, separate CI stage from unit tests.

### 3.3 E2E tests (Playwright)

- Live in `/e2e`, TypeScript, run against a full docker-compose stack in CI (nightly + release gate; smoke subset on every PR).
- Cover **critical user journeys only**: sign-up/login, complete a pill, author-publish-consume flow, streak progression, admin role management.
- Selectors: `getByRole`/`getByTestId` only — no CSS/XPath coupling. No fixed `sleep`; rely on Playwright auto-waiting. Each test seeds and owns its data (API-based fixtures), independent and parallel-safe.
- Flaky tests are quarantined within 24 h and fixed or deleted within a week — a flaky suite is a broken suite.

## 4. Quality gates (CI, in order)

Pipeline stages — all blocking:

1. **Lint/format:** Spotless + Error Prone (backend); ESLint + Prettier + `tsc --noEmit` (frontend).
2. **Unit tests + ArchUnit** with coverage gate ≥ 85%.
3. **Integration tests** (Testcontainers).
4. **Security gates:** OWASP Dependency-Check / `pnpm audit`, gitleaks (see `SECURITY.md`).
5. **E2E smoke** (PR) / full E2E (nightly, release).

No `main` merge with a red pipeline. No skipped/`@Disabled` tests without a linked issue and expiry date.

## 5. Definition of Done (quality)

A change is complete only when: written test-first; all suites green; coverage ≥ 85% with domain/application near 100%; security test matrix present for new endpoints; no new flaky or disabled tests; and the change is recorded in `STATE_OF_THE_APP.md`.
