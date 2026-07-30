#!/bin/sh
# CodePill VPS bootstrap — run once as root on a fresh Ubuntu 24.04 host.
# Installs k3s (traefik + local-path included), Argo CD, creates the runtime
# Secrets from secrets.env, and applies the root app-of-apps. From that point
# on, everything reconciles from git.
#
# Usage:
#   cp secrets.env.example secrets.env && vi secrets.env && chmod 600 secrets.env
#   ./bootstrap.sh
set -eu

ARGOCD_VERSION="v3.0.11"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

[ -f "$SCRIPT_DIR/secrets.env" ] || { echo "secrets.env missing (copy secrets.env.example)"; exit 1; }
# shellcheck disable=SC1091
. "$SCRIPT_DIR/secrets.env"

for var in POSTGRES_PASSWORD CATALOG_DB_PASSWORD IDENTITY_DB_PASSWORD \
           KEYCLOAK_DB_PASSWORD REDIS_PASSWORD KEYCLOAK_ADMIN_USERNAME \
           KEYCLOAK_ADMIN_PASSWORD GRAFANA_ADMIN_USER GRAFANA_ADMIN_PASSWORD; do
  eval "value=\${$var:-}"
  [ -n "$value" ] || { echo "$var is empty in secrets.env"; exit 1; }
done

echo "==> Installing k3s (if not present)"
if ! command -v k3s >/dev/null 2>&1; then
  curl -sfL https://get.k3s.io | sh -s - --secrets-encryption
fi
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl wait --for=condition=Ready node --all --timeout=180s

echo "==> Installing Argo CD $ARGOCD_VERSION"
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd -f \
  "https://raw.githubusercontent.com/argoproj/argo-cd/$ARGOCD_VERSION/manifests/install.yaml"

echo "==> Creating namespaces and runtime secrets"
kubectl create namespace codepill --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace codepill-observability --dry-run=client -o yaml | kubectl apply -f -

kubectl -n codepill create secret generic codepill-db \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  --from-literal=CATALOG_DB_PASSWORD="$CATALOG_DB_PASSWORD" \
  --from-literal=IDENTITY_DB_PASSWORD="$IDENTITY_DB_PASSWORD" \
  --from-literal=KEYCLOAK_DB_PASSWORD="$KEYCLOAK_DB_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n codepill create secret generic codepill-redis \
  --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n codepill create secret generic keycloak-admin \
  --from-literal=KC_BOOTSTRAP_ADMIN_USERNAME="$KEYCLOAK_ADMIN_USERNAME" \
  --from-literal=KC_BOOTSTRAP_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n codepill-observability create secret generic grafana-admin \
  --from-literal=GF_SECURITY_ADMIN_USER="$GRAFANA_ADMIN_USER" \
  --from-literal=GF_SECURITY_ADMIN_PASSWORD="$GRAFANA_ADMIN_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

if [ -n "${GHCR_USERNAME:-}" ] && [ -n "${GHCR_TOKEN:-}" ]; then
  kubectl -n codepill create secret docker-registry ghcr-pull \
    --docker-server=ghcr.io \
    --docker-username="$GHCR_USERNAME" \
    --docker-password="$GHCR_TOKEN" \
    --dry-run=client -o yaml | kubectl apply -f -
fi

echo "==> Applying the root app-of-apps"
kubectl apply -f "$SCRIPT_DIR/../apps/root-app.yaml"

echo "==> Done. Argo CD reconciles ops/k8s/apps from git."
echo "    UI (not exposed publicly): kubectl -n argocd port-forward svc/argocd-server 8443:443"
echo "    Initial admin password:    kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d"
