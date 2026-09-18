# Local dependency security validation — 2026-09-18

Scope: [issue #1](https://github.com/pedrovvitor/CodePill/issues/1), application images only. This is a development validation, not a production-readiness claim.

## Before and after

The failed image gate in [run 35339723443](https://github.com/pedrovvitor/CodePill/actions/runs/35339723443) reported four fixable critical findings:

| Dependency | Previous | Validated | Findings addressed |
|---|---|---|---|
| Netty handler | 4.2.15.Final | 4.2.17.Final, managed by Spring Boot 4.0.8 | CVE-2026-75595 |
| Tomcat embed core | 11.0.22 | 11.0.26 | CVE-2026-65182, CVE-2026-65905, CVE-2026-68525 |

Spring Boot moves from 4.0.7 to 4.0.8. Its BOM manages Tomcat 11.0.24, so the project explicitly overrides the Tomcat family to 11.0.26 on the same major line. Remove the override once the Boot BOM supplies a sufficiently patched version. No scanner exclusions or severity thresholds changed.

## Reproduce

With JDK 25 and Docker available, from the repository root:

```sh
./backend/mvnw -f backend/pom.xml -B verify
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend test:coverage
docker build -t codepill-catalog:security-fix backend
docker build -t codepill-web:showcase frontend
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.72.0 image --scanners vuln --severity CRITICAL --ignore-unfixed --exit-code 1 codepill-catalog:security-fix
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.72.0 image --scanners vuln --severity CRITICAL --ignore-unfixed --exit-code 1 codepill-web:showcase
```

On Windows, use `backend/mvnw.cmd`. The socket mount requires a Linux-container Docker engine.

Both built images passed this critical gate locally on 2026-09-18. Backend `verify` passed before and after the upgrade, including Testcontainers, architecture tests and coverage gates. Frontend coverage passed (99.21% statements, 91.62% branches, 100% functions, 99.56% lines). All six existing mobile Chromium E2E journeys passed against the rebuilt images and real local Keycloak/PostgreSQL/Redis stack (20 seconds, no retries).

The scan only establishes the result for its severity filter, vulnerability database and scanned artifacts. It does not claim absence of lower-severity findings or validate the infrastructure images. Base-image tags and vulnerability data can change; CI must rebuild, scan and publish the same artifact after all required checks pass. No image was published by this local validation.
