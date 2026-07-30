#!/bin/sh
# Writes the runtime config consumed by src/infrastructure/config/env.ts
# (window.__CODEPILL_ENV__) so one immutable image serves any environment.
# Unset variables become empty strings, which readRuntimeEnv discards in
# favor of build-time values.
set -eu

cat > /usr/share/nginx/html/config.js <<EOF
window.__CODEPILL_ENV__ = {
  VITE_API_BASE_URL: '${VITE_API_BASE_URL:-}',
  VITE_OIDC_AUTHORITY: '${VITE_OIDC_AUTHORITY:-}',
  VITE_OIDC_CLIENT_ID: '${VITE_OIDC_CLIENT_ID:-}',
  VITE_OTLP_TRACES_URL: '${VITE_OTLP_TRACES_URL:-}',
  VITE_DEPLOYMENT_ENV: '${VITE_DEPLOYMENT_ENV:-}',
  VITE_APP_VERSION: '${VITE_APP_VERSION:-}',
  VITE_TRACE_SAMPLING: '${VITE_TRACE_SAMPLING:-}',
}
EOF
