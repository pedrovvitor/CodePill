import { describe, expect, it, vi } from 'vitest'
import { trace, TraceFlags } from '@opentelemetry/api'
import { ExportResultCode, type ExportResult } from '@opentelemetry/core'
import type { ReadableSpan } from '@opentelemetry/sdk-trace-web'

// The real exporter opens network connections on shutdown/flush, coupling the
// suite to whatever answers on the OTLP port (nothing on CI, the collector
// locally, a firewall black-hole otherwise). Determinism per
// TESTING_QUALITY.md §3.3: no network in unit tests.
vi.mock('@opentelemetry/exporter-trace-otlp-http', () => ({
  OTLPTraceExporter: class {
    export(_spans: ReadableSpan[], resultCallback: (result: ExportResult) => void): void {
      resultCallback({ code: ExportResultCode.SUCCESS })
    }
    shutdown(): Promise<void> {
      return Promise.resolve()
    }
    forceFlush(): Promise<void> {
      return Promise.resolve()
    }
  },
}))
import {
  buildTelemetryAttributes,
  buildTracerProvider,
  initTelemetry,
  tracePropagationTargets,
} from './otel'

const config = {
  serviceName: 'codepill-web',
  appVersion: '0.1.0',
  deploymentEnv: 'local',
  otlpTracesUrl: 'http://localhost:4318/v1/traces',
  apiBaseUrl: '',
  traceSamplingRatio: 1,
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

describe('buildTracerProvider', () => {
  it('samples root spans according to the configured ratio (parent-based)', async () => {
    const dropAll = buildTracerProvider({ ...config, traceSamplingRatio: 0 })
    const keepAll = buildTracerProvider({ ...config, traceSamplingRatio: 1 })

    const dropped = dropAll.getTracer('test').startSpan('probe')
    const kept = keepAll.getTracer('test').startSpan('probe')
    dropped.end()
    kept.end()

    expect(dropped.spanContext().traceFlags & TraceFlags.SAMPLED).toBe(0)
    expect(kept.spanContext().traceFlags & TraceFlags.SAMPLED).toBe(TraceFlags.SAMPLED)

    await dropAll.shutdown()
    await keepAll.shutdown()
  })

  it('merges our attributes with the SDK default resource', async () => {
    const provider = buildTracerProvider(config)

    const span = provider.getTracer('test').startSpan('probe')
    span.end()
    const resource = (span as unknown as ReadableSpan).resource

    // Ours survive the merge …
    expect(resource.attributes['service.name']).toBe('codepill-web')
    expect(resource.attributes['deployment.environment']).toBe('local')
    // … and the SDK defaults are present too.
    expect(resource.attributes['telemetry.sdk.name']).toBe('opentelemetry')
    expect(resource.attributes['telemetry.sdk.language']).toBeDefined()

    await provider.shutdown()
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

  it('flushes pending spans on pagehide (tab close / bfcache) without rejecting', () => {
    const provider = initTelemetry(config)
    const forceFlush = vi
      .spyOn(provider, 'forceFlush')
      .mockRejectedValue(new Error('exporter gone'))

    window.dispatchEvent(new Event('pagehide'))

    expect(forceFlush).toHaveBeenCalledTimes(1)
    forceFlush.mockRestore()
  })
})
