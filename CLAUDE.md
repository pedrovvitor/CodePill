# CodePill — Agent Operating Contract

CodePill is a microlearning platform. Java 25 + Spring Boot 4 backend (Clean Architecture, Maven multi-module), React 19 + TypeScript frontend.

## MANDATORY PROTOCOL — read this first, every session

Before executing ANY task in this repository you MUST read all governance standards:

1. `docs/standards/ARCHITECTURE.md` — Clean Architecture rules, module layout, tech baseline
2. `docs/standards/OBSERVABILITY.md` — OpenTelemetry, JSON logging (Loki/Sentry), metrics
3. `docs/standards/SECURITY.md` — OAuth2/OIDC + JWT, RBAC enforcement patterns
4. `docs/standards/TESTING_QUALITY.md` — TDD, 85%+ coverage, JUnit/Testcontainers/Playwright
5. `docs/standards/STATE_OF_THE_APP.md` — living changelog and current snapshot

Upon COMPLETING any task you MUST append an entry to `docs/standards/STATE_OF_THE_APP.md` using its entry template (newest first) and update its "Current Snapshot" if you changed it. **A task without a log entry is not done.**

## Hard rules (summaries — the standards are authoritative)

- TDD: failing test first, always. CI coverage gate is 85%.
- Domain modules stay framework-free; dependencies point inward only.
- Every endpoint authenticated (JWT resource server) and role-checked; deny by default.
- Every new code path ships with traces, structured JSON logs (trace-correlated), and metrics.
- No secrets, tokens, or PII in code, logs, or telemetry.
- Deviations require an ADR in `docs/adr/`.
