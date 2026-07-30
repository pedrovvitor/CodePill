/** Runtime configuration — 12-factor: environment first, local-dev defaults. */
export interface AppConfig {
  /** Empty string = same-origin (Vite dev proxy or reverse proxy in front). */
  apiBaseUrl: string
  oidcAuthority: string
  oidcClientId: string
  otlpTracesUrl: string
  deploymentEnv: string
  appVersion: string
  /** Root trace sampling ratio in [0, 1] — see OBSERVABILITY.md. */
  traceSamplingRatio: number
}

type RawEnv = Record<string, unknown>

function stringVar(env: RawEnv, key: string, fallback: string): string {
  const value = env[key]
  return typeof value === 'string' && value !== '' ? value : fallback
}

/**
 * Like {@link stringVar}, but the fallback is a dev-only convenience: a
 * production build with the variable missing must fail fast at boot instead
 * of silently pointing at localhost.
 */
function requiredVar(env: RawEnv, key: string, devFallback: string): string {
  const value = env[key]
  if (typeof value === 'string' && value !== '') return value
  if (env.PROD === true) {
    throw new Error(`Missing required environment variable ${key} in a production build`)
  }
  return devFallback
}

function ratioVar(env: RawEnv, key: string, fallback: number): number {
  const value = env[key]
  if (typeof value !== 'string' || value === '') return fallback
  const parsed = Number.parseFloat(value)
  if (Number.isNaN(parsed)) return fallback
  return Math.min(1, Math.max(0, parsed))
}

/**
 * Runtime configuration source: the container entrypoint writes
 * `/config.js` (`window.__CODEPILL_ENV__`) so one immutable image serves any
 * environment. Empty strings are dropped — an unset variable at deploy time
 * must not clobber a build-time value.
 */
export function readRuntimeEnv(source: unknown): RawEnv {
  if (typeof source !== 'object' || source === null) return {}
  return Object.fromEntries(
    Object.entries(source).filter(
      (entry): entry is [string, string] => typeof entry[1] === 'string' && entry[1] !== '',
    ),
  )
}

export function resolveAppConfig(env: RawEnv): AppConfig {
  return {
    apiBaseUrl: stringVar(env, 'VITE_API_BASE_URL', ''),
    oidcAuthority: requiredVar(env, 'VITE_OIDC_AUTHORITY', 'http://localhost:8180/realms/codepill'),
    oidcClientId: stringVar(env, 'VITE_OIDC_CLIENT_ID', 'codepill-web'),
    otlpTracesUrl: requiredVar(env, 'VITE_OTLP_TRACES_URL', 'http://localhost:4318/v1/traces'),
    deploymentEnv: stringVar(env, 'VITE_DEPLOYMENT_ENV', 'local'),
    appVersion: stringVar(env, 'VITE_APP_VERSION', '0.1.0'),
    traceSamplingRatio: ratioVar(env, 'VITE_TRACE_SAMPLING', 1),
  }
}

export const appConfig: AppConfig = resolveAppConfig({
  ...import.meta.env,
  ...readRuntimeEnv((globalThis as Record<string, unknown>).__CODEPILL_ENV__),
})
