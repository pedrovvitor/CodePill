import { describe, expect, it } from 'vitest'
import { resolveAppConfig } from './env'

describe('resolveAppConfig', () => {
  it('falls back to local-dev defaults (12-factor: env overrides, sane defaults)', () => {
    const config = resolveAppConfig({})
    expect(config).toEqual({
      apiBaseUrl: '',
      oidcAuthority: 'http://localhost:8180/realms/codepill',
      oidcClientId: 'codepill-web',
      otlpTracesUrl: 'http://localhost:4318/v1/traces',
      deploymentEnv: 'local',
      appVersion: '0.1.0',
    })
  })

  it('honors environment overrides', () => {
    const config = resolveAppConfig({
      VITE_API_BASE_URL: 'https://api.codepill.dev',
      VITE_OIDC_AUTHORITY: 'https://id.codepill.dev/realms/codepill',
      VITE_OIDC_CLIENT_ID: 'codepill-web-dev',
      VITE_OTLP_TRACES_URL: 'https://otel.codepill.dev/v1/traces',
      VITE_DEPLOYMENT_ENV: 'dev',
      VITE_APP_VERSION: '1.2.3',
    })
    expect(config.apiBaseUrl).toBe('https://api.codepill.dev')
    expect(config.oidcAuthority).toBe('https://id.codepill.dev/realms/codepill')
    expect(config.oidcClientId).toBe('codepill-web-dev')
    expect(config.otlpTracesUrl).toBe('https://otel.codepill.dev/v1/traces')
    expect(config.deploymentEnv).toBe('dev')
    expect(config.appVersion).toBe('1.2.3')
  })

  it('ignores non-string values (import.meta.env booleans)', () => {
    expect(resolveAppConfig({ VITE_API_BASE_URL: true }).apiBaseUrl).toBe('')
  })
})
