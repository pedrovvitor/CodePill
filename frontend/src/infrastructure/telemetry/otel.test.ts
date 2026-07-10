import { describe, expect, it } from 'vitest'
import { trace } from '@opentelemetry/api'
import { buildTelemetryAttributes, initTelemetry, tracePropagationTargets } from './otel'

const config = {
  serviceName: 'codepill-web',
  appVersion: '0.1.0',
  deploymentEnv: 'local',
  otlpTracesUrl: 'http://localhost:4318/v1/traces',
  apiBaseUrl: '',
}

describe('buildTelemetryAttributes (OBSERVABILITY.md §2 — required resource attributes)', () => {
  it('sets service.name, service.version and deployment.environment', () => {
    expect(buildTelemetryAttributes(config)).toEqual({
      'service.name': 'codepill-web',
      'service.version': '0.1.0',
      'deployment.environment': 'local',
    })
  })
})

describe('tracePropagationTargets', () => {
  it('propagates traceparent to same-origin API calls', () => {
    const targets = tracePropagationTargets(config)
    expect(targets.some((t) => t.test('/api/v1/pills'))).toBe(true)
  })

  it('propagates traceparent to an absolute API base URL', () => {
    const targets = tracePropagationTargets({ ...config, apiBaseUrl: 'https://api.codepill.dev' })
    expect(targets.some((t) => t.test('https://api.codepill.dev/api/v1/pills'))).toBe(true)
  })

  it('does not propagate to arbitrary third-party origins', () => {
    const targets = tracePropagationTargets(config)
    expect(targets.some((t) => t.test('https://evil.example.com/api/v1/pills'))).toBe(false)
  })
})

describe('initTelemetry', () => {
  it('registers a global web tracer provider and is idempotent', () => {
    const first = initTelemetry(config)
    const second = initTelemetry(config)

    expect(first).toBe(second)
    expect(trace.getTracerProvider()).toBeDefined()
    expect(first.constructor.name).toBe('WebTracerProvider')
  })
})
