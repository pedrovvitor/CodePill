# Run the development showcase locally

No hosted demo or official release exists. This walkthrough uses Docker and synthetic local accounts. Hosting/provider selection is deferred.

## Start

Install Docker with Compose 2.24.4 or later and run from the repository root:

```sh
docker compose -p codepill-showcase -f docker-compose.yml -f ops/local-demo/docker-compose.demo.yml -f ops/local-demo/docker-compose.isolated.yml up -d --build
```

The isolation overlay scopes container names and data volumes to `codepill-showcase` and exposes PostgreSQL on loopback port 15433 (override with `CODEPILL_DEMO_DB_PORT`). It preserves older CodePill containers and avoids the development default 5433. Other published ports remain those of the base stack: 5173, 8080, 8180/8181, 6379, 4317/4318, 9090, 3000, 3100, 3200. Check for port conflicts first; two complete stacks cannot share those host ports.

Check startup:

```sh
docker compose -p codepill-showcase -f docker-compose.yml -f ops/local-demo/docker-compose.demo.yml -f ops/local-demo/docker-compose.isolated.yml ps
curl http://localhost:8080/actuator/health/readiness
```

Expect readiness `UP`, then open <http://localhost:5173>. Local users are `dev-learner`, `dev-author`, `dev-curator` and `dev-admin`, all with password `codepill-local`. These fixtures are for local use only. Keep the stack bound to loopback.

## Seed a disposable showcase

The optional command below **replaces the catalog's pills** with 17 curated synthetic examples (16 published and one draft). Use it only on this disposable showcase project; omit it to preserve your authored pills. It does not reset Keycloak accounts.

```sh
docker compose -p codepill-showcase -f docker-compose.yml -f ops/local-demo/docker-compose.demo.yml -f ops/local-demo/docker-compose.isolated.yml --profile demo-seed up demo-seed
```

## Five-minute product walkthrough

1. Sign in as `dev-learner`; browse the feed and select **Read pill** to read the full text.
2. Sign out and sign in as `dev-author`; select **Create**. Enter a title, summary and body, for example `{"body":"A complete short lesson.\n\nTry the technique in a small example."}`. Save, then select **View draft**.
3. Copy the draft page URL. Authors can read their own drafts but cannot publish them without the curator role.
4. Open that URL in a separate browser session and sign in as `dev-curator`. Review the content and select **Publish pill**.
5. Return to the learner feed and open the newly published lesson. A learner opening the draft before publication receives the same unavailable response as for a missing pill.

Text and Markdown bodies are rendered as escaped plain text. Unknown structured blocks are displayed as JSON. Rich media, interactive quizzes and persisted learning completion remain future work; do not present them as implemented.

## Verification and observability

With Node/pnpm available and the stack running:

```sh
pnpm --dir e2e install --frozen-lockfile
pnpm --dir e2e exec playwright install chromium
pnpm --dir e2e test
```

The tests use real Keycloak, catalog, PostgreSQL and Redis; they create synthetic test records. Seven journeys cover login/logout, navigation, authoring, pagination and author → curator → learner. Trace/screenshot artifacts are retained on failure under `e2e/test-results`.

Grafana is at <http://localhost:3000> (`admin` / `admin` with unchanged local defaults). Prometheus targets are at <http://localhost:9090/targets>. The application propagates browser traces to the collector and backend; publication uses the existing backend's role checks, structured logs and business metric. Local telemetry availability is not evidence of a production SLO or load capacity.

## Stop without discarding data

```sh
docker compose -p codepill-showcase -f docker-compose.yml -f ops/local-demo/docker-compose.demo.yml -f ops/local-demo/docker-compose.isolated.yml stop
```

Use the same project name and files to resume. Do not add volume-removal flags unless intentionally discarding the synthetic demo data. Hosting, TLS/ingress validation and production backup/restore checks remain tracked in [#4](https://github.com/pedrovvitor/CodePill/issues/4).
