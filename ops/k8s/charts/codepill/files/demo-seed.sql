-- Demo catalog content — idempotent full reset (the demo database is
-- disposable by design; this runs as an Argo CD PostSync hook and nightly).
-- Author/curator IDs are the fixed demo-user UUIDs pinned in the prod realm
-- (codepill-infra/files/realm-codepill.json). Pills teach the stack this very
-- platform runs on, so the demo content doubles as documentation.

TRUNCATE pills CASCADE;

INSERT INTO pills
  (author_id, title, slug, summary, content, pill_type, status,
   estimated_duration_seconds, published_at)
VALUES
  ('d0000001-0000-4000-8000-000000000002',
   'The Dependency Rule in 3 minutes',
   'clean-architecture-dependency-rule',
   'The single rule that makes Clean Architecture work: source code dependencies only point inward.',
   '{"body": "Every architecture diagram you have seen has arrows. Clean Architecture only cares about one kind: source-code dependencies. The rule: they point inward, toward the domain, and never outward.\n\nYour domain never imports Spring, JPA, or Jackson. Use cases depend on the domain, adapters depend on use cases, and frameworks stay at the edge. When the web framework changes, your business rules do not.\n\nOn this platform, ArchUnit tests fail the build if anyone breaks the rule — architecture as executable tests, not tribal knowledge."}',
   'ARTICLE', 'PUBLISHED', 180, now() - interval '1 hour'),

  ('d0000001-0000-4000-8000-000000000002',
   'Ports and adapters: the only pattern you need for testable I/O',
   'ports-and-adapters',
   'Define an interface where you need I/O, implement it at the edge, mock it in tests.',
   '{"body": "A port is an interface owned by your application layer: LoadPillPort, SavePillPort. An adapter implements it with real technology: a JPA repository, a Redis client, an HTTP call.\n\nThe payoff is testability. Use cases are tested with in-memory fakes; adapters are tested against real infrastructure with Testcontainers. Nothing in between needs mocking frameworks pointed at code you do not own.\n\nSmell test: if your use case imports a persistence annotation, the port boundary leaked."}',
   'ARTICLE', 'PUBLISHED', 240, now() - interval '5 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'Kubernetes probes: startup vs readiness vs liveness',
   'kubernetes-probes',
   'Three probes, three different questions. Confusing them causes restart loops and dropped traffic.',
   '{"body": "Startup probe: has the process finished booting? Protects slow JVMs from being killed mid-start.\n\nReadiness probe: can this pod serve traffic RIGHT NOW? Failing it removes the pod from the Service endpoints without restarting it — right response to a temporarily unreachable dependency.\n\nLiveness probe: is the process irrecoverably wedged? Failing it restarts the container. Never wire liveness to a dependency check: a flapping database would restart your whole fleet.\n\nSpring Boot maps these to /actuator/health/readiness and /liveness out of the box."}',
   'ARTICLE', 'PUBLISHED', 300, now() - interval '9 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'Why your traces need span attributes, not span names',
   'span-attributes-not-names',
   'Span names must be low-cardinality; IDs and business context belong in attributes.',
   '{"body": "A span named get-pill-3f9a... is a cardinality bomb: every dashboard, every trace search groups by name, and you just created millions of them.\n\nKeep names as verbs: get-pill, publish-pill. Put the variability in attributes: codepill.pill.id, codepill.feed.page. Attributes are indexed for search but do not explode aggregation.\n\nSame discipline in logs: a static, greppable message with structured fields, never string concatenation."}',
   'ARTICLE', 'PUBLISHED', 240, now() - interval '14 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'TDD: the failing test is the point',
   'tdd-failing-test-first',
   'Red, green, refactor — and why skipping red silently breaks the loop.',
   '{"body": "Writing the test first is not ceremony. A test you have never seen fail proves nothing: it might pass for the wrong reason, assert nothing, or test the mock instead of the code.\n\nRed: write the smallest test that expresses the requirement and watch it fail for the RIGHT reason. Green: minimum code to pass. Refactor: clean up with the safety net on.\n\nBug fixes double down on this: reproduce the bug as a failing regression test before touching the fix. This repo rejects PRs that add behavior without the test that demanded it."}',
   'ARTICLE', 'PUBLISHED', 240, now() - interval '19 hours'),

  ('d0000001-0000-4000-8000-000000000003',
   'JWT validation: what a resource server actually checks',
   'jwt-resource-server-validation',
   'Signature, issuer, audience, expiry — and why audience is the one everyone forgets.',
   '{"body": "A resource server validates four things on every request: the signature against the IdP''s published JWKS keys, the iss claim against the expected issuer, the exp/iat window, and the aud claim.\n\nAudience is the one that separates real deployments from tutorials. Without aud=codepill-api enforcement, any token minted by your IdP for ANY app is accepted here — a mobile app''s token works against your admin API.\n\nAsymmetric signatures only (RS256/ES256): the IdP signs with a private key, services verify with the public JWKS. Shared-secret HS256 means every service can forge tokens."}',
   'ARTICLE', 'PUBLISHED', 300, now() - interval '1 day 3 hours'),

  ('d0000001-0000-4000-8000-000000000003',
   'Deny by default: the security posture that survives refactors',
   'deny-by-default',
   'Allowlists fail closed. Every other posture fails open the day someone forgets an annotation.',
   '{"body": "anyRequest().authenticated() at the HTTP layer, @PreAuthorize on every use case, ownership checks inside them. Three layers, each assuming the others failed.\n\nThe test matrix is the enforcement: every endpoint ships tests for anonymous (401), wrong role (403), and correct role (2xx). A new endpoint without its matrix does not merge.\n\nDefense in depth sounds expensive until you watch a refactor move a route and silently drop its guard. The layer you kept is the one that catches it."}',
   'ARTICLE', 'PUBLISHED', 240, now() - interval '1 day 8 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'Cache invalidation that survives transactions',
   'cache-eviction-after-commit',
   'Evicting inside the transaction reopens the stale-read window you were closing.',
   '{"body": "The bug: evict the cache, then commit. Between those two moments a concurrent reader misses the cache, reads the OLD committed data, and repopulates the cache with it — now stale for a full TTL.\n\nThe fix: defer evictions with TransactionSynchronization.afterCommit. On rollback nothing changed, so nothing evicts.\n\nBonus rule: never do cache I/O while holding a DB connection with default timeouts. A hung Redis pinning every Hikari connection is a full outage born from a cache."}',
   'ARTICLE', 'PUBLISHED', 300, now() - interval '2 days 2 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'Quiz: Kubernetes resource management',
   'quiz-kubernetes-resources',
   'Requests, limits, and what actually happens when a node runs out of memory.',
   '{"questions": [{"q": "What does the scheduler use to place pods?", "options": ["limits", "requests", "actual usage"], "answer": 1}, {"q": "A container exceeds its memory limit. What happens?", "options": ["throttled", "OOMKilled", "evicted politely"], "answer": 1}, {"q": "CPU over the limit is...", "options": ["throttled", "killed", "fine"], "answer": 0}]}',
   'QUIZ', 'PUBLISHED', 180, now() - interval '2 days 7 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'GitOps in one sentence',
   'gitops-one-sentence',
   'If it is not in git, it is not in the cluster; if it is in git, the cluster converges to it.',
   '{"body": "GitOps means the cluster state is declared in a repository and an operator (Argo CD here) continuously reconciles reality toward it. kubectl apply from a laptop becomes an anti-pattern: it drifts, it is unaudited, and selfHeal will revert it.\n\nDeploys become commits: promote an image by changing a tag in git; roll back with git revert. The audit log of production IS the git history.\n\nThe subtle win is recovery: rebuilding the cluster is `bootstrap.sh` plus one root Application — everything else converges from the repo."}',
   'ARTICLE', 'PUBLISHED', 180, now() - interval '3 days 1 hour'),

  ('d0000001-0000-4000-8000-000000000003',
   'Flashcards: OpenTelemetry vocabulary',
   'flashcards-otel-vocabulary',
   'Trace, span, context propagation, resource — the four words that unlock the docs.',
   '{"cards": [{"front": "Trace", "back": "The end-to-end journey of one request across services, identified by a trace_id."}, {"front": "Span", "back": "One timed operation inside a trace, with attributes and a parent."}, {"front": "Context propagation", "back": "Carrying trace identity across boundaries — the W3C traceparent header."}, {"front": "Resource", "back": "Immutable attributes of the emitting entity: service.name, version, environment."}]}',
   'FLASHCARD', 'PUBLISHED', 240, now() - interval '3 days 6 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'Keyset pagination: why OFFSET dies at scale',
   'keyset-pagination',
   'OFFSET 100000 reads and throws away 100k rows. Keyset seeks straight to the page.',
   '{"body": "OFFSET n LIMIT k scans n+k index entries every time — page 5000 of your feed costs 5000 pages of work, plus a COUNT(*) if you show totals.\n\nKeyset pagination remembers the last (published_at, id) seen and asks WHERE (published_at, id) < (?, ?) ORDER BY published_at DESC, id DESC LIMIT k. With a matching composite index this is a pure seek: page 1 and page 5000 cost the same.\n\nThe trade: no random page jumps, cursor tokens in the API. For infinite-scroll feeds nobody jumps to page 5000 anyway."}',
   'ARTICLE', 'PUBLISHED', 300, now() - interval '4 days 3 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'The 12-factor config rule, applied to containers',
   'twelve-factor-config-containers',
   'One immutable image, promoted through environments; only the environment changes.',
   '{"body": "Baking configuration into images means rebuilding to change a URL — and testing an artifact that is not the one you ship. 12-factor says config lives in the environment.\n\nBackends read env vars at boot and fail closed when required ones are missing. SPAs are trickier: the bundle is static files. The pattern: the container entrypoint writes a config.js from env at startup, loaded before the app bundle.\n\nRule of thumb: if you cannot run the same image in staging and prod, your config is in the wrong layer."}',
   'ARTICLE', 'PUBLISHED', 240, now() - interval '4 days 9 hours'),

  ('d0000001-0000-4000-8000-000000000003',
   'Rate limiting with token buckets',
   'token-bucket-rate-limiting',
   'Bursts allowed, sustained abuse denied — and always answer 429 with Retry-After.',
   '{"body": "A token bucket holds N tokens, refilled at a steady rate. Each request spends one; an empty bucket means 429. Bursts up to N pass, sustained load levels to the refill rate.\n\nKey by the authenticated subject (JWT sub), not by IP — NATs share IPs, and attackers rotate them.\n\nProtocol matters: return 429 with Retry-After and a problem+json body, and emit a metric per rejection. A rate limiter you cannot observe is indistinguishable from an outage."}',
   'ARTICLE', 'PUBLISHED', 240, now() - interval '5 days 4 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'Quiz: HTTP status codes for API designers',
   'quiz-http-status-codes',
   'When 401 vs 403 vs 404, and what 409 is actually for.',
   '{"questions": [{"q": "Valid token, insufficient role. Status?", "options": ["401", "403", "404"], "answer": 1}, {"q": "No token at all?", "options": ["401", "403", "400"], "answer": 0}, {"q": "Draft exists but requester may not know it does. Best existence-hiding response?", "options": ["403", "404", "410"], "answer": 1}, {"q": "Slug already taken on create?", "options": ["400", "409", "422"], "answer": 1}]}',
   'QUIZ', 'PUBLISHED', 180, now() - interval '5 days 10 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'Structured logs are a database, not a diary',
   'structured-logs',
   'One JSON object per line, static messages, trace-correlated. Grep is a query, not archaeology.',
   '{"body": "Logs answer questions when they are structured: a static message field (\"pill published\"), business context as fields (pill_id, duration_ms), and the trace_id/span_id injected on every line.\n\nThat trace_id is the join key between your logging store and your tracing store: from any log line, jump to the full distributed trace, and back.\n\nHard rules: no PII, no tokens, opaque IDs only. Level policy with teeth: ERROR pages someone; if nobody should wake up, it is WARN."}',
   'ARTICLE', 'PUBLISHED', 240, now() - interval '6 days 2 hours'),

  ('d0000001-0000-4000-8000-000000000002',
   'Draft: NetworkPolicies — the firewall nobody writes',
   'networkpolicies-draft',
   'Default-deny ingress per namespace, then allow each legitimate caller explicitly.',
   '{"body": "WORK IN PROGRESS — kubernetes network segmentation from first principles. Default-deny, then enumerate callers: who talks to the database? Exactly two deployments and a backup job. Everything else is lateral movement waiting to happen."}',
   'ARTICLE', 'DRAFT', 300, NULL);
