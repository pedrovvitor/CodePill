# CodePill — Architecture Standard

> **Status:** MANDATORY · **Owner:** Lead Principal Engineering · **Last updated:** 2026-07-09
>
> ⚠️ **AGENT PROTOCOL — NON-NEGOTIABLE**
> Every agent (human or AI) MUST read all files in `/docs/standards/` **before** executing any task in this repository, and MUST append an entry to `/docs/standards/STATE_OF_THE_APP.md` **upon completing** its task. Work that does not follow this protocol is rejected in review.

## 1. Purpose

CodePill is a microlearning platform that delivers short, focused learning "pills." This document defines the mandatory architectural style for all backend and frontend code. Deviations require a written ADR (Architecture Decision Record) in `/docs/adr/` approved by the Lead Principal Engineer.

## 2. Technology Baseline

| Layer | Technology | Version policy |
|---|---|---|
| Backend language | Java | **25 (LTS)** — virtual threads on, preview features off |
| Backend framework | Spring Boot | **4.x** (Spring Framework 7) |
| Build | Maven (multi-module) | Wrapper (`mvnw`) committed |
| Persistence | PostgreSQL + Spring Data JPA | Flyway for all schema changes |
| Frontend | React 19 + TypeScript (`strict: true`) | Vite build, pnpm |
| API style | REST/JSON, OpenAPI 3.1 contract-first | Versioned under `/api/v{n}` |

## 3. Backend — Clean Architecture

Each service is a Maven multi-module project enforcing the **dependency rule: source code dependencies point inward only.** The `domain` module has **zero** framework dependencies — no Spring, no JPA, no Jackson annotations.

```
codepill-<service>/
├── domain/          # Entities, value objects, domain events, domain services.
│                    # Pure Java 25. No frameworks. No I/O.
├── application/     # Use cases (one class per use case), ports (interfaces),
│                    # command/query DTOs. Depends ONLY on domain.
├── adapters/
│   ├── in/web/      # REST controllers, request/response DTOs, mappers.
│   ├── in/events/   # Message listeners (Kafka/etc.).
│   ├── out/persistence/  # JPA entities, repositories implementing ports.
│   └── out/clients/ # HTTP clients to other services, implementing ports.
└── bootstrap/       # Spring Boot main class, configuration, wiring.
```

### 3.1 Rules

1. **Domain purity.** `domain/` compiles with no dependencies beyond the JDK. Enforced by ArchUnit tests (see `TESTING_QUALITY.md`).
2. **Use cases are the only entry point to business logic.** Controllers and listeners call use cases; they never touch repositories or domain services directly.
3. **Ports and adapters.** `application/` defines interfaces (`LoadPillPort`, `SavePillPort`, `NotifyLearnerPort`); adapters implement them. Adapters never call each other directly.
4. **Separate models per layer.** Web DTOs, JPA entities, and domain objects are distinct types with explicit mappers (MapStruct permitted in adapters only). Never expose a JPA entity through a controller.
5. **Java 25 idioms.** Records for value objects, commands, and DTOs; sealed interfaces for domain result/event hierarchies; pattern matching over `instanceof` chains; virtual threads for blocking I/O (default executor).
6. **Transactions** begin and end in the use case (`@Transactional` on the application service via bootstrap configuration), never in controllers or repositories.
7. **Errors.** Domain throws typed domain exceptions or returns sealed result types. The web adapter maps them to RFC 9457 `application/problem+json` responses in one central `@RestControllerAdvice`.

### 3.2 Service boundaries

Services own their data; no shared databases. Cross-service communication is REST (synchronous, via out-adapters) or domain events (asynchronous). Initial service cut:

- `codepill-identity` — accounts, roles (delegates auth to the IdP; see `SECURITY.md`)
- `codepill-catalog` — pills, courses, tracks, authoring
- `codepill-learning` — enrollment, progress, spaced repetition scheduling
- `codepill-engagement` — streaks, notifications, gamification

Start as separate Maven modules in a monorepo; extract to independent deployables only when scaling demands it (ADR required).

## 4. Frontend — Clean Architecture (React/TypeScript)

```
web/src/
├── domain/          # Pure TS: types, business rules, validation. No React, no fetch.
├── application/     # Use-case hooks and services; TanStack Query definitions;
│                    # depends on domain + port interfaces.
├── infrastructure/  # API clients (generated from OpenAPI), storage, telemetry.
├── ui/
│   ├── components/  # Presentational, stateless where possible.
│   ├── pages/       # Route-level composition.
│   └── design-system/ # Tokens, primitives. No business logic.
└── app/             # Router, providers, bootstrap.
```

### 4.1 Rules

1. `tsconfig` with `strict: true`, `noUncheckedIndexedAccess: true`. `any` is banned (ESLint error); use `unknown` + narrowing.
2. **Server state** lives in TanStack Query; **client state** in local state or a small store (Zustand). No global state dumping ground.
3. API types are **generated from the OpenAPI contract** — never hand-written duplicates.
4. Components do not call `fetch`/axios directly; they consume application-layer hooks (`usePill(id)`, `useCompletePill()`).
5. Dependency direction enforced with `eslint-plugin-boundaries`: `ui → application → domain`; `infrastructure` implements application ports.

## 5. Cross-cutting

- **API contract first:** OpenAPI spec is reviewed before implementation; breaking changes require a new API version.
- **Configuration** via environment (12-factor); no secrets in the repo, ever.
- **ADRs:** every significant decision gets a numbered ADR in `/docs/adr/` (`NNNN-title.md`, MADR format).
- Observability, security, and testing requirements are defined in their own mandatory standards: `OBSERVABILITY.md`, `SECURITY.md`, `TESTING_QUALITY.md`.
