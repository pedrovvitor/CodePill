/** Runtime configuration — 12-factor: environment first, local-dev defaults. */
export interface AppConfig {
  /** Empty string = same-origin (Vite dev proxy or reverse proxy in front). */
  apiBaseUrl: string
  oidcAuthority: string
  oidcClientId: string
  otlpTracesUrl: string
  deploymentEnv: string
  appVersion: string
}

type RawEnv = Record<string, unknown>

function stringVar(env: RawEnv, key: string, fallback: string): string {
  const value = env[key]
  return typeof value === 'string' && value !== '' ? value : fallback
}

export function resolveAppConfig(env: RawEnv): AppConfig {
  return {
    apiBaseUrl: stringVar(env, 'VITE_API_BASE_URL', ''),
    oidcAuthority: stringVar(env, 'VITE_OIDC_AUTHORITY', 'http://localhost:8180/realms/codepill'),
    oidcClientId: stringVar(env, 'VITE_OIDC_CLIENT_ID', 'codepill-web'),
    otlpTracesUrl: stringVar(env, 'VITE_OTLP_TRACES_URL', 'http://localhost:4318/v1/traces'),
    deploymentEnv: stringVar(env, 'VITE_DEPLOYMENT_ENV', 'local'),
    appVersion: stringVar(env, 'VITE_APP_VERSION', '0.1.0'),
  }
}

export const appConfig: AppConfig = resolveAppConfig(import.meta.env)
