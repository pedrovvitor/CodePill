import { describe, expect, it } from 'vitest'
import { readRuntimeEnv, resolveAppConfig } from './env'

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
      traceSamplingRatio: 1,
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

  describe('production fail-fast (no localhost baked into prod builds)', () => {
    const prodEnv = {
      PROD: true,
      VITE_OIDC_AUTHORITY: 'https://id.codepill.dev/realms/codepill',
      VITE_OTLP_TRACES_URL: 'https://otel.codepill.dev/v1/traces',
    }

    it('throws naming the variable when VITE_OIDC_AUTHORITY is missing', () => {
      const env = { PROD: true, VITE_OTLP_TRACES_URL: prodEnv.VITE_OTLP_TRACES_URL }
      expect(() => resolveAppConfig(env)).toThrow(/VITE_OIDC_AUTHORITY/)
    })

    it('throws naming the variable when VITE_OTLP_TRACES_URL is missing', () => {
      const env = { PROD: true, VITE_OIDC_AUTHORITY: prodEnv.VITE_OIDC_AUTHORITY }
      expect(() => resolveAppConfig(env)).toThrow(/VITE_OTLP_TRACES_URL/)
    })

    it('resolves normally when all required variables are set', () => {
      const config = resolveAppConfig(prodEnv)
      expect(config.oidcAuthority).toBe('https://id.codepill.dev/realms/codepill')
      expect(config.otlpTracesUrl).toBe('https://otel.codepill.dev/v1/traces')
    })

    it('keeps localhost defaults for dev builds (PROD false)', () => {
      const config = resolveAppConfig({ PROD: false })
      expect(config.oidcAuthority).toBe('http://localhost:8180/realms/codepill')
      expect(config.otlpTracesUrl).toBe('http://localhost:4318/v1/traces')
    })
  })

  describe('runtime environment (container-injected config.js)', () => {
    it('returns an empty source when the global is absent', () => {
      expect(readRuntimeEnv(undefined)).toEqual({})
    })

    it('returns an empty source for non-object values', () => {
      expect(readRuntimeEnv('VITE_OIDC_CLIENT_ID=x')).toEqual({})
      expect(readRuntimeEnv(null)).toEqual({})
    })

    it('keeps only non-empty string entries', () => {
      const runtime = readRuntimeEnv({
        VITE_OIDC_CLIENT_ID: 'codepill-web-prod',
        VITE_API_BASE_URL: '',
        VITE_TRACE_SAMPLING: 0.1,
      })
      expect(runtime).toEqual({ VITE_OIDC_CLIENT_ID: 'codepill-web-prod' })
    })

    it('overrides build-time values when spread over import.meta.env', () => {
      const buildEnv = { VITE_OIDC_AUTHORITY: 'http://localhost:8180/realms/codepill' }
      const runtime = readRuntimeEnv({
        VITE_OIDC_AUTHORITY: 'https://id.codepill.dev/realms/codepill',
      })
      const config = resolveAppConfig({ ...buildEnv, ...runtime })
      expect(config.oidcAuthority).toBe('https://id.codepill.dev/realms/codepill')
    })

    it('leaves build-time values intact when the runtime entry is empty (unset envsubst var)', () => {
      const buildEnv = { VITE_OIDC_AUTHORITY: 'https://id.codepill.dev/realms/codepill' }
      const runtime = readRuntimeEnv({ VITE_OIDC_AUTHORITY: '' })
      const config = resolveAppConfig({ ...buildEnv, ...runtime })
      expect(config.oidcAuthority).toBe('https://id.codepill.dev/realms/codepill')
    })
  })

  describe('VITE_TRACE_SAMPLING', () => {
    it('defaults to sampling everything', () => {
      expect(resolveAppConfig({}).traceSamplingRatio).toBe(1)
    })

    it('parses the ratio as a float', () => {
      expect(resolveAppConfig({ VITE_TRACE_SAMPLING: '0.25' }).traceSamplingRatio).toBe(0.25)
    })

    it('clamps values above 1 down to 1', () => {
      expect(resolveAppConfig({ VITE_TRACE_SAMPLING: '7' }).traceSamplingRatio).toBe(1)
    })

    it('clamps negative values up to 0', () => {
      expect(resolveAppConfig({ VITE_TRACE_SAMPLING: '-0.5' }).traceSamplingRatio).toBe(0)
    })

    it('falls back to the default when the value is not a number', () => {
      expect(resolveAppConfig({ VITE_TRACE_SAMPLING: 'not-a-ratio' }).traceSamplingRatio).toBe(1)
    })
  })
})
