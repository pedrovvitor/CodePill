# CodePill — Observability Standard

> **Status:** MANDATORY · **Owner:** Lead Principal Engineering · **Last updated:** 2026-07-09
>
> ⚠️ **AGENT PROTOCOL — NON-NEGOTIABLE**
> Every agent (human or AI) MUST read all files in `/docs/standards/` **before** executing any task in this repository, and MUST append an entry to `/docs/standards/STATE_OF_THE_APP.md` **upon completing** its task.

## 1. Principle

If it isn't observable, it isn't done. Every service ships with **OpenTelemetry (OTel) traces, metrics, and structured logs from its first commit** — not retrofitted. A feature PR that adds an endpoint, job, or consumer without telemetry is rejected.

Stack: **OpenTelemetry SDK → OTel Collector → Grafana Loki (logs), Prometheus/Mimir (metrics), Tempo (traces), Sentry (errors)**.

## 2. OpenTelemetry — mandatory setup

Every backend service includes:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

Required resource attributes (set via `OTEL_RESOURCE_ATTRIBUTES` / application config):

```
service.name=codepill-<service>        # e.g. codepill-catalog
service.version=<git sha or semver>
deployment.environment=<local|dev|staging|prod>
```

Rules:

1. **W3C Trace Context (`traceparent`) propagation** on every inbound/outbound HTTP call and message header. Never strip it.
2. Sampling: `prod` uses parent-based ratio sampling (start at 10%, errors always sampled via tail sampling in the Collector); non-prod samples 100%.
3. Custom spans for every use case: annotate application services with `@Observed(name = "usecase", contextualName = "complete-pill")` or use `ObservationRegistry` directly. Span names are **low-cardinality verbs** (`complete-pill`), never contain IDs.
4. IDs and business context go in **span attributes**, namespaced `codepill.*`: `codepill.pill.id`, `codepill.learner.id`, `codepill.course.id`.
5. Frontend uses `@opentelemetry/sdk-trace-web` + fetch instrumentation so browser spans join backend traces.

## 3. Structured JSON logging — Loki + Sentry

### 3.1 Format (exact pattern)

All services log **JSON, one object per line, to stdout** (collected by Promtail/Alloy into Loki). Use Logback with `logstash-logback-encoder`. Human-readable console logging is allowed only for the `local` profile.

Every log line MUST contain exactly these base fields:

```json
{
  "timestamp": "2026-07-09T14:31:22.123Z",
  "level": "INFO",
  "service": "codepill-catalog",
  "env": "prod",
  "version": "1.4.2",
  "logger": "com.codepill.catalog.application.CompletePillUseCase",
  "thread": "virtual-42",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "message": "pill completed",
  "context": { "pill_id": "p_9f2c", "learner_id": "u_5512", "duration_ms": 4210 }
}
```

Rules:

1. `trace_id`/`span_id` are injected from the OTel context via MDC — **every** log line inside a request carries them. This is the join key between Loki and Tempo.
2. Business fields go under `context`, `snake_case`, via structured arguments — never string-concatenated into `message`:
   ```java
   log.info("pill completed", kv("pill_id", pillId), kv("learner_id", learnerId));
   ```
3. `message` is a **static, grep-able phrase**; variability belongs in fields.
4. **Never log:** passwords, tokens, JWTs, full names, emails, or request bodies containing PII. Log opaque IDs only. (See `SECURITY.md`.)
5. Loki labels (set by the collector, not the app): `service`, `env`, `level` only — keep label cardinality low; everything else is queried from the JSON body.
6. Level policy: `ERROR` = requires action, pages someone; `WARN` = degraded but self-healing; `INFO` = business events and lifecycle; `DEBUG` = off in prod.

### 3.2 Sentry (error tracking)

- All services and the frontend integrate Sentry (`sentry-spring-boot-starter-jakarta`, `@sentry/react`).
- Every `ERROR`-level log and unhandled exception is forwarded to Sentry with `trace_id` attached (Sentry↔Tempo linking enabled).
- Releases are tagged with `service.version`; environments mirror `deployment.environment`.
- `beforeSend` scrubs PII (Sentry `sendDefaultPii=false`).
- Frontend: Sentry captures unhandled exceptions, React error boundaries, and Core Web Vitals.

## 4. Metrics — Spring Boot Actuator + Micrometer

### 4.1 Actuator baseline (every service)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      probes:
        enabled: true          # /actuator/health/{liveness,readiness}
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
  observations:
    key-values:
      service: ${spring.application.name}
```

Mandatory out-of-the-box metrics kept enabled: `http.server.requests`, `jvm.*`, `hikaricp.*`, `spring.data.repository.invocations`.

### 4.2 Query metrics

Database performance is observable by default:

1. Enable repository metrics: `spring.data.repository.invocations` (timer per repository method).
2. Datasource pool metrics via HikariCP (`hikaricp.connections.*`) — alert when `pending` > 0 sustained.
3. Slow-query visibility: wrap the `DataSource` with `datasource-micrometer-spring-boot` (Micrometer's JDBC observation) so each query becomes a timed span + `jdbc.query` metric. Queries slower than **200 ms p95** trigger review.
4. Every custom `@Query`/JDBC statement gets a low-cardinality name tag: `query.name=find_due_pills`.

### 4.3 Custom business metrics (exact patterns)

Naming: `codepill.<domain>.<noun>_<verb-past>` (dot-cased in code; the Prometheus exporter renders `codepill_pill_completed_total`). Tags must be **low-cardinality** (enums, not IDs).

```java
// Counter — business event
Counter.builder("codepill.pill.completed")
    .description("Pills completed by learners")
    .tag("course_category", category)     // enum-like, NEVER learner_id
    .register(meterRegistry)
    .increment();

// Timer — duration of a business operation
Timer.builder("codepill.session.duration")
    .publishPercentileHistogram()
    .tag("pill_type", type.name())
    .register(meterRegistry)
    .record(duration);

// Gauge — current state
Gauge.builder("codepill.reviews.due", scheduler, SpacedRepetitionScheduler::dueCount)
    .register(meterRegistry);
```

Minimum business metrics per service:

| Service | Required metrics |
|---|---|
| catalog | `codepill.pill.published`, `codepill.pill.authoring_time` |
| learning | `codepill.pill.completed`, `codepill.enrollment.created`, `codepill.reviews.due` |
| engagement | `codepill.streak.extended`, `codepill.streak.broken`, `codepill.notification.sent` |
| identity | `codepill.login.succeeded`, `codepill.login.failed` (tag: `reason`) |

## 5. Alerting & SLOs

- Each service defines SLOs in `/docs/slo/<service>.md`: availability ≥ 99.9%, p95 latency ≤ 300 ms for read endpoints, ≤ 500 ms for writes.
- Alerts are defined as code (Grafana provisioning in `/ops/observability/`), based on burn rate, not raw thresholds.
- Every alert links to a runbook.

## 6. Definition of Done (observability)

A change is complete only when: traces show the new path end-to-end; logs are structured JSON with trace correlation; new business events have metrics; dashboards/alerts updated if behavior changed; and none of it leaks PII.
