# CodePill on Kubernetes

Production/demo deployment: **k3s on a single VPS**, reconciled by **Argo CD**
(GitOps — this directory is the source of truth), TLS by **cert-manager** +
Let's Encrypt through k3s' bundled **traefik** ingress.

```
ops/k8s/
├── bootstrap/          # one-time VPS setup: k3s + Argo CD + runtime Secrets
├── apps/               # Argo CD Applications (app-of-apps, synced from git)
└── charts/
    ├── codepill-infra          # PostgreSQL, Redis, Keycloak (prod realm), backups, ClusterIssuer
    ├── codepill                # catalog service + web SPA + ingress + HPA
    └── codepill-observability  # OTel Collector, Prometheus, Grafana, Loki, Tempo
```

## Topology

| Host | Backend | Notes |
|---|---|---|
| `<domain>` | `/` → codepill-web, `/api` → codepill-catalog | SPA is same-origin with its API (no CORS surface) |
| `id.<domain>` | Keycloak | OIDC issuer `https://id.<domain>/realms/codepill` |
| `grafana.<domain>` | Grafana | admin credentials from Secret |
| `otel.<domain>` | OTel Collector | **only** `POST /v1/traces` (browser spans) |

Everything else is cluster-internal, with **default-deny NetworkPolicies** per
namespace and explicit allow rules per caller (enforced by k3s' embedded
network policy controller). The catalog's actuator lives on a management port
(8081) that the ingress never routes; `/actuator/prometheus` is anonymous *on
that port only*, reachable solely from the Prometheus pod — this satisfies
SECURITY.md §3.3's "network-restricted" scrape exception with an actual
network restriction.

Secrets are created once by `bootstrap/bootstrap.sh` from `secrets.env` and
never live in git. The committed prod realm contains no confidential clients;
the demo user credentials in it are public by design (shown on the login
screen). k3s runs with `--secrets-encryption` (encrypted at rest).

## First deploy (VPS)

1. DNS: `A` records for `<domain>`, `id.`, `grafana.`, `otel.` → VPS IP.
2. Harden the host: SSH keys only, `ufw allow 22,80,443/tcp` + `ufw enable`,
   unattended-upgrades. (fail2ban optional.)
3. Edit the three `apps/*.yaml` `valuesObject` blocks: real `domain`,
   ACME `email`, image tags. Commit and push.
4. On the VPS:
   ```sh
   git clone https://github.com/pedrovvitor/CodePill.git && cd CodePill/ops/k8s/bootstrap
   cp secrets.env.example secrets.env && vi secrets.env && chmod 600 secrets.env
   sudo ./bootstrap.sh
   ```
5. Watch it converge: `kubectl get applications -n argocd` /
   `kubectl get pods -A`.

Argo CD needs read access to this repo: public repo works out of the box;
while private, register a deploy key (`argocd repo add`).

## Day-2

- **Deploys:** CI pushes images tagged with the commit SHA; promote by editing
  the tag in `apps/codepill.yaml` and pushing — Argo CD does the rest.
  Rollback = revert the commit.
- **Backups:** nightly `pg_dump` CronJob to a PVC (7-day rotation). Restore:
  `pg_restore -h postgres -U codepill -d <db> --clean <dump>`. Single-node
  scope: this protects data, not the node — copy dumps off-host if the demo
  ever holds anything you can't recreate.
- **Argo CD UI:** deliberately not exposed; `kubectl -n argocd port-forward
  svc/argocd-server 8443:443`.

## Local validation (no VPS needed)

```sh
helm lint ops/k8s/charts/*
# full smoke on a throwaway cluster (kind):
kind create cluster --name codepill
kind load docker-image codepill-catalog:local codepill-web:local --name codepill
helm install infra ops/k8s/charts/codepill-infra -n codepill --create-namespace \
  --set ingress.enabled=false --set clusterIssuer.enabled=false --set domain=localhost
# create the secrets first (see bootstrap.sh) — then:
helm install codepill ops/k8s/charts/codepill -n codepill \
  --set ingress.enabled=false --set domain=localhost \
  --set imageRegistry=docker.io/library --set catalog.image.tag=local --set web.image.tag=local
```

## Recorded decisions

- **k3s over managed Kubernetes:** full control-plane + workload demo on a
  single cheap VPS; the trade-off (no HA) is acceptable for a portfolio demo.
- **Own charts over upstream charts** for postgres/keycloak/observability:
  the configs are shared verbatim with the local compose stack (same service
  DNS names), keeping one mental model across dev and prod at demo scale.
- **Public OTLP `/v1/traces`:** required for browser spans; bounded by the
  collector's `memory_limiter`, path-exact ingress routing and CORS.
  Revisit with an auth proxy if abuse appears.
